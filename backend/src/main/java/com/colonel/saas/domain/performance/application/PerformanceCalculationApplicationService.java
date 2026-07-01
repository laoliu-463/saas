package com.colonel.saas.domain.performance.application;

import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.entity.PerformanceRecord;
import com.colonel.saas.mapper.PerformanceRecordMapper;
import com.colonel.saas.service.CommissionService;
import com.colonel.saas.service.OrderCommissionPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 业绩核心计算应用服务（DDD-PERFORMANCE Slice 7）。
 *
 * <p>从 {@code service.PerformanceCalculationService} 整体迁移过来的核心计算逻辑：
 * <ul>
 *   <li>{@link #upsertFromOrder} - 从订单构建业绩记录并执行 upsert</li>
 * </ul>
 *
 * <p>本类是 performance 域核心计算的入口。承接原 Service 的所有 private helper
 * （{@code buildRecord} / {@code zeroCommissions} / {@code nvl} / {@code nvlInt}）。</p>
 *
 * <p>依赖：
 * <ul>
 *   <li>{@link PerformanceRecordMapper} —— 业绩记录数据访问</li>
 *   <li>{@link CommissionService} —— 提成计算服务（双轨 commission 计算）</li>
 *   <li>{@link OrderCommissionPolicy} —— 订单状态判断（已取消置零）</li>
 * </ul>
 */
@Service
public class PerformanceCalculationApplicationService {

    private final PerformanceRecordMapper performanceRecordMapper;
    private final CommissionService commissionService;

    public PerformanceCalculationApplicationService(
            PerformanceRecordMapper performanceRecordMapper,
            CommissionService commissionService) {
        this.performanceRecordMapper = performanceRecordMapper;
        this.commissionService = commissionService;
    }

    /**
     * 从订单数据构建业绩记录并执行 upsert（存在则更新，不存在则插入）。
     *
     * <ol>
     *   <li>第一步：校验订单非空且 orderId 有效</li>
     *   <li>第二步：查询是否已有该订单的业绩记录</li>
     *   <li>第三步：调用 {@link #buildRecord} 构建业绩记录（含双轨提成计算）</li>
     *   <li>第四步：执行 upsert 持久化</li>
     * </ol>
     *
     * @param order 订单实体
     * @return 写入的业绩记录，若订单无效返回 null
     */
    @Transactional(rollbackFor = Exception.class)
    public PerformanceRecord upsertFromOrder(ColonelsettlementOrder order) {
        // 第一步：参数校验
        if (order == null || !StringUtils.hasText(order.getOrderId())) {
            return null;
        }
        // 第二步：查询已有记录
        PerformanceRecord existing = performanceRecordMapper.findByOrderId(order.getOrderId());
        // 第三步：构建业绩记录
        PerformanceRecord record = buildRecord(order, existing);
        // 第四步：upsert 持久化
        performanceRecordMapper.upsert(record);
        return record;
    }

    /**
     * 根据订单数据构建完整的业绩记录，包含双轨提成计算。
     */
    private PerformanceRecord buildRecord(ColonelsettlementOrder order, PerformanceRecord existing) {
        PerformanceRecord record = new PerformanceRecord();
        // 第一步：基础字段
        record.setId(existing == null ? UUID.randomUUID() : existing.getId());
        record.setOrderId(order.getOrderId());
        record.setOrderRowId(order.getId());

        // 第一步（续）：归因信息 — 渠道和招商
        UUID channelUserId = order.getChannelUserId();
        UUID recruiterUserId = order.getColonelUserId() != null ? order.getColonelUserId() : order.getUserId();
        record.setDefaultChannelUserId(channelUserId);
        record.setDefaultRecruiterUserId(recruiterUserId);
        record.setFinalChannelUserId(channelUserId);
        record.setFinalRecruiterUserId(recruiterUserId);
        record.setChannelAttribution(channelUserId != null ? "pick_source" : "unattributed");
        record.setRecruiterAttribution(recruiterUserId != null ? "activity_owner" : "unattributed");

        // 第一步（续）：关联实体
        record.setTalentId(order.getTalentId());
        record.setPartnerId(order.getShopId());
        record.setProductId(order.getProductId());
        record.setActivityId(order.getActivityId());

        // 第二步：映射双轨金额（单位：分）
        long payAmount = nvl(order.getOrderAmount());
        long settleAmount = nvl(order.getSettleAmount());
        long estimateServiceFee = nvl(order.getEstimateServiceFee());
        long effectiveServiceFee = nvl(order.getEffectiveServiceFee());
        long estimateTechServiceFee = nvl(order.getEstimateTechServiceFee());
        long effectiveTechServiceFee = nvl(order.getEffectiveTechServiceFee());
        long estimateServiceFeeExpense = nvl(order.getEstimateServiceFeeExpense());
        long effectiveServiceFeeExpense = nvl(order.getEffectiveServiceFeeExpense());
        long talentCommission = nvl(order.getSettleSecondColonelCommission());

        record.setPayAmount(payAmount);
        record.setSettleAmount(settleAmount);
        record.setEstimateServiceFee(estimateServiceFee);
        record.setEffectiveServiceFee(effectiveServiceFee);
        record.setEstimateTechServiceFee(estimateTechServiceFee);
        record.setEffectiveTechServiceFee(effectiveTechServiceFee);
        record.setEstimateServiceFeeExpense(estimateServiceFeeExpense);
        record.setEffectiveServiceFeeExpense(effectiveServiceFeeExpense);

        // 第三步：判断是否已取消/失效
        boolean reversed = !OrderCommissionPolicy.countsTowardPerformance(order.getOrderStatus());
        record.setValid(!reversed);
        record.setReversed(reversed);
        record.setOrderStatus(order.getOrderStatus());
        record.setSettleTime(order.getSettleTime());
        record.setOrderCreateTime(order.getCreateTime());
        record.setCalculationVersion(existing == null ? 1 : nvlInt(existing.getCalculationVersion()) + 1);
        LocalDateTime now = LocalDateTime.now();
        record.setCalculatedAt(now);
        record.setCreatedAt(existing == null ? now : existing.getCreatedAt());
        record.setUpdatedAt(now);

        // 注意：已取消订单所有提成和收益字段置零
        if (reversed) {
            zeroCommissions(record);
            return record;
        }

        // 第四步：双轨提成计算
        // 预估轨：传入技术服务费和服务费支出；结算轨同理。
        // 提成基数 = 服务费收益 = 收入 - 支出 - 技术费
        // 毛利 = 服务费收益 - 招商提成 - 渠道提成
        // 预估轨：talentCommission 参数为 0（预估轨不考虑达人佣金）
        CommissionService.CommissionSummary estimateTrack = commissionService.calculateTrack(
                estimateServiceFee,
                estimateTechServiceFee,
                estimateServiceFeeExpense,
                0L,
                order.getActivityId(),
                order.getProductId(),
                recruiterUserId,
                order.getSettleTime());
        // 结算轨：使用实际达人佣金，不重复扣 effectiveTechServiceFee。
        CommissionService.CommissionSummary effectiveTrack = commissionService.calculateTrack(
                effectiveServiceFee,
                0L,
                effectiveServiceFeeExpense,
                talentCommission,
                order.getActivityId(),
                order.getProductId(),
                recruiterUserId,
                order.getSettleTime());

        // 将计算结果映射到记录字段
        record.setEstimateServiceProfit(estimateTrack.serviceFeeNet());
        record.setEffectiveServiceProfit(effectiveTrack.serviceFeeNet());
        record.setEstimateRecruiterCommission(estimateTrack.bizCommission());
        record.setEffectiveRecruiterCommission(effectiveTrack.bizCommission());
        record.setEstimateChannelCommission(estimateTrack.channelCommission());
        record.setEffectiveChannelCommission(effectiveTrack.channelCommission());
        record.setEstimateGrossProfit(estimateTrack.grossProfit());
        record.setEffectiveGrossProfit(effectiveTrack.grossProfit());
        record.setRecruiterCommissionRate(estimateTrack.bizRatio());
        record.setChannelCommissionRate(estimateTrack.channelRatio());
        return record;
    }

    /**
     * 将业绩记录的所有提成和收益字段置零（用于已取消/失效订单）。
     */
    private void zeroCommissions(PerformanceRecord record) {
        record.setEstimateServiceProfit(0L);
        record.setEffectiveServiceProfit(0L);
        record.setEstimateServiceFeeExpense(0L);
        record.setEffectiveServiceFeeExpense(0L);
        record.setEstimateRecruiterCommission(0L);
        record.setEffectiveRecruiterCommission(0L);
        record.setEstimateChannelCommission(0L);
        record.setEffectiveChannelCommission(0L);
        record.setEstimateGrossProfit(0L);
        record.setEffectiveGrossProfit(0L);
    }

    /**
     * null 安全的 Long 转 long，null 视为 0。
     */
    private long nvl(Long value) {
        return value == null ? 0L : value;
    }

    /**
     * null 安全的 Integer 转 int，null 视为 0。
     */
    private int nvlInt(Integer value) {
        return value == null ? 0 : value;
    }
}