package com.colonel.saas.service;

import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.entity.PerformanceRecord;
import com.colonel.saas.mapper.PerformanceRecordMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class PerformanceCalculationService {

    private final PerformanceRecordMapper performanceRecordMapper;
    private final CommissionService commissionService;

    public PerformanceCalculationService(
            PerformanceRecordMapper performanceRecordMapper,
            CommissionService commissionService) {
        this.performanceRecordMapper = performanceRecordMapper;
        this.commissionService = commissionService;
    }

    @Transactional(rollbackFor = Exception.class)
    public PerformanceRecord upsertFromOrder(ColonelsettlementOrder order) {
        if (order == null || !StringUtils.hasText(order.getOrderId())) {
            return null;
        }
        PerformanceRecord existing = performanceRecordMapper.findByOrderId(order.getOrderId());
        PerformanceRecord record = buildRecord(order, existing);
        performanceRecordMapper.upsert(record);
        return record;
    }

    private PerformanceRecord buildRecord(ColonelsettlementOrder order, PerformanceRecord existing) {
        PerformanceRecord record = new PerformanceRecord();
        record.setId(existing == null ? UUID.randomUUID() : existing.getId());
        record.setOrderId(order.getOrderId());
        record.setOrderRowId(order.getId());

        UUID channelUserId = order.getChannelUserId();
        UUID recruiterUserId = order.getColonelUserId() != null ? order.getColonelUserId() : order.getUserId();
        record.setDefaultChannelUserId(channelUserId);
        record.setDefaultRecruiterUserId(recruiterUserId);
        record.setFinalChannelUserId(channelUserId);
        record.setFinalRecruiterUserId(recruiterUserId);
        record.setChannelAttribution(channelUserId != null ? "pick_source" : "unattributed");
        record.setRecruiterAttribution(recruiterUserId != null ? "activity_owner" : "unattributed");

        record.setTalentId(order.getTalentId());
        record.setPartnerId(order.getShopId());
        record.setProductId(order.getProductId());
        record.setActivityId(order.getActivityId());

        long payAmount = nvl(order.getOrderAmount());
        long settleAmount = nvl(order.getSettleAmount()) > 0 ? nvl(order.getSettleAmount()) : nvl(order.getActualAmount());
        long estimateServiceFee = nvl(order.getEstimateServiceFee());
        long effectiveServiceFee = nvl(order.getEffectiveServiceFee());
        long estimateTechServiceFee = nvl(order.getEstimateTechServiceFee());
        long effectiveTechServiceFee = nvl(order.getEffectiveTechServiceFee());
        long talentCommission = nvl(order.getSettleSecondColonelCommission());

        record.setPayAmount(payAmount);
        record.setSettleAmount(settleAmount);
        record.setEstimateServiceFee(estimateServiceFee);
        record.setEffectiveServiceFee(effectiveServiceFee);
        record.setEstimateTechServiceFee(estimateTechServiceFee);
        record.setEffectiveTechServiceFee(effectiveTechServiceFee);

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

        if (reversed) {
            zeroCommissions(record);
            return record;
        }

        CommissionService.CommissionSummary estimateTrack = commissionService.calculateTrack(
                estimateServiceFee,
                estimateTechServiceFee,
                0L,
                order.getActivityId(),
                order.getProductId(),
                recruiterUserId,
                order.getSettleTime());
        CommissionService.CommissionSummary effectiveTrack = commissionService.calculateTrack(
                effectiveServiceFee,
                effectiveTechServiceFee,
                talentCommission,
                order.getActivityId(),
                order.getProductId(),
                recruiterUserId,
                order.getSettleTime());

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

    private void zeroCommissions(PerformanceRecord record) {
        record.setEstimateServiceProfit(0L);
        record.setEffectiveServiceProfit(0L);
        record.setEstimateRecruiterCommission(0L);
        record.setEffectiveRecruiterCommission(0L);
        record.setEstimateChannelCommission(0L);
        record.setEffectiveChannelCommission(0L);
        record.setEstimateGrossProfit(0L);
        record.setEffectiveGrossProfit(0L);
    }

    private long nvl(Long value) {
        return value == null ? 0L : value;
    }

    private int nvlInt(Integer value) {
        return value == null ? 0 : value;
    }
}
