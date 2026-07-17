package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.domain.order.application.OrderAttributionRouter;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.mapper.ColonelsettlementOrderMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 订单归因回放服务，对未归因或需要重新归因的订单执行批量归因重算。
 *
 * <p>核心逻辑：加载目标订单列表，逐笔调用归因服务重新解析归因结果，
 * 仅在满足安全条件（归因结果早于订单创建时间）时才持久化更新。</p>
 *
 * <ul>
 *   <li>支持 dry-run 模式：仅统计不写入，用于预览归因结果</li>
 *   <li>安全检查：当原生键映射的创建时间晚于订单创建时间时，标记为不安全并跳过更新</li>
 *   <li>支持按订单 ID 列表或未归因状态 + 原因筛选待回放订单</li>
 *   <li>提供详细的回放统计：归因成功数、未归因数、原生键匹配数、不安全跳过数等</li>
 * </ul>
 *
 * <p><b>业务领域：</b>业绩域 — 归因回放</p>
 * <p><b>协作关系：</b>依赖 {@link OrderAttributionRouter} 执行与订单同步一致的归因策略；
 * 依赖 {@link OrderSyncPersistenceService} 持久化更新后的订单；
 * 依赖 {@link ColonelsettlementOrderMapper} 查询待回放订单</p>
 *
 * @see OrderAttributionRouter
 * @see OrderSyncPersistenceService
 */
@Service
public class OrderAttributionReplayService {

    /** 默认查询订单数上限 */
    private static final int DEFAULT_LIMIT = 50;

    /** 单次查询最大订单数上限 */
    private static final int MAX_LIMIT = 200;

    /** 订单 Mapper，查询待回放的订单列表 */
    private final ColonelsettlementOrderMapper orderMapper;

    /** 归因路由，保证同步与历史回放使用同一策略并写入双维状态 */
    private final OrderAttributionRouter orderAttributionRouter;

    /** 订单持久化服务，用于更新归因结果并查询用户信息 */
    private final OrderSyncPersistenceService persistenceService;

    public OrderAttributionReplayService(
            ColonelsettlementOrderMapper orderMapper,
            OrderAttributionRouter orderAttributionRouter,
            OrderSyncPersistenceService persistenceService) {
        this.orderMapper = orderMapper;
        this.orderAttributionRouter = orderAttributionRouter;
        this.persistenceService = persistenceService;
    }

    /**
     * 执行订单归因回放，对目标订单重新解析归因并选择性持久化。
     *
     * <ol>
     *   <li>第一步：通过 {@link #loadOrders} 加载待回放订单列表</li>
     *   <li>第二步：逐笔归一化 extraData 并调用归因服务解析归因结果</li>
     *   <li>第三步：统计原生键匹配、归因状态、安全检查等指标</li>
     *   <li>第四步：若非 dry-run 且安全检查通过，应用归因结果并持久化</li>
     *   <li>第五步：构建并返回回放结果统计</li>
     * </ol>
     *
     * @param orderIds 指定订单 ID 列表（优先级高于按状态筛选），可为 null
     * @param reason   筛选条件：归因备注内容，仅在 orderIds 为空时生效
     * @param limit    查询订单数上限，超过 {@value MAX_LIMIT} 会被截断
     * @param dryRun   是否为试运行模式（true 仅统计不写入）
     * @return 回放结果，包含详细统计数据
     */
    @Transactional(rollbackFor = Exception.class)
    public ReplayResult replay(List<String> orderIds, String reason, Integer limit, boolean dryRun) {
        // 第一步：加载待回放订单
        List<ColonelsettlementOrder> orders = loadOrders(orderIds, reason, limit);
        // 初始化各统计计数器
        int scanned = 0;
        int attributed = 0;
        int unattributed = 0;
        int updated = 0;
        int nativeKeyMatched = 0;
        int safeToUpdate = 0;
        int unsafeBecauseCreatedAfterOrder = 0;
        int colonelBuyinIdMismatch = 0;
        int ambiguousMapping = 0;
        int stillUnattributed = 0;

        // 第二步：逐笔归一化 extraData 并执行归因解析
        for (ColonelsettlementOrder order : orders) {
            scanned++;
            // 注意：先归一化 extraData，再走与订单同步相同的归因路由。
            Map<String, Object> normalizedSource = AttributionSourceNormalizer.normalize(order.getExtraData());
            AttributionService.AttributionResult result = orderAttributionRouter.resolveAndApply(
                    order, normalizedSource, order.getTalentName());
            AttributionService.NativeMappingTrace nativeTrace = result.nativeTrace();
            if (nativeTrace != null && nativeTrace.nativeKeyMatched()) {
                nativeKeyMatched++;
            }
            if (nativeTrace != null && nativeTrace.colonelBuyinIdMismatch()) {
                colonelBuyinIdMismatch++;
            }
            if (nativeTrace != null && nativeTrace.ambiguousMapping()) {
                ambiguousMapping++;
            }
            // 第三步：统计原生键匹配和归因状态指标
            if (AttributionService.STATUS_ATTRIBUTED.equals(result.attributionStatus())) {
                attributed++;
            } else {
                unattributed++;
                stillUnattributed++;
            }
            boolean safeForHistoricalUpdate = isSafeForHistoricalUpdate(order, result);
            if (nativeTrace != null && nativeTrace.nativeKeyMatched() && AttributionService.STATUS_ATTRIBUTED.equals(result.attributionStatus())) {
                if (safeForHistoricalUpdate) {
                    safeToUpdate++;
                } else if (nativeTrace.mappingCreatedAt() != null && order.getCreateTime() != null
                        && nativeTrace.mappingCreatedAt().isAfter(order.getCreateTime())) {
                    unsafeBecauseCreatedAfterOrder++;
                }
            }
            // 第四步：dry-run 模式仅统计，不持久化
            if (dryRun) {
                continue;
            }
            // 注意：安全检查未通过的订单跳过更新
            if (!safeForHistoricalUpdate) {
                continue;
            }
            // 第四步（续）：归因路由已写回默认归属与双维状态，这里只补充审计字段并持久化。
            finalizeAttribution(order);
            persistenceService.persistOrder(order);
            updated++;
        }

        return new ReplayResult(
                scanned,
                attributed,
                unattributed,
                updated,
                dryRun,
                nativeKeyMatched,
                safeToUpdate,
                unsafeBecauseCreatedAfterOrder,
                colonelBuyinIdMismatch,
                ambiguousMapping,
                stillUnattributed
        );
    }

    /**
     * 加载待回放订单列表，支持按订单 ID 或未归因状态筛选。
     *
     * <ol>
     *   <li>第一步：若提供了订单 ID 列表，直接按 ID 查询</li>
     *   <li>第二步：否则按未归因状态筛选，可选附加 reason 条件</li>
     *   <li>第三步：按更新时间、创建时间倒序排列，限制返回数量</li>
     * </ol>
     */
    private List<ColonelsettlementOrder> loadOrders(List<String> orderIds, String reason, Integer limit) {
        LambdaQueryWrapper<ColonelsettlementOrder> wrapper = new LambdaQueryWrapper<ColonelsettlementOrder>()
                .eq(ColonelsettlementOrder::getDeleted, 0);
        if (orderIds != null && !orderIds.isEmpty()) {
            wrapper.in(ColonelsettlementOrder::getOrderId, orderIds);
        } else {
            wrapper.eq(ColonelsettlementOrder::getAttributionStatus, AttributionService.STATUS_UNATTRIBUTED);
            if (StringUtils.hasText(reason)) {
                wrapper.eq(ColonelsettlementOrder::getAttributionRemark, reason.trim());
            }
            wrapper.last("limit " + normalizeLimit(limit));
        }
        wrapper.orderByDesc(ColonelsettlementOrder::getUpdateTime)
                .orderByDesc(ColonelsettlementOrder::getCreateTime);
        return orderMapper.selectList(wrapper);
    }

    /**
     * 规范化查询数量限制，默认 {@value DEFAULT_LIMIT}，上限 {@value MAX_LIMIT}。
     */
    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    /** 补充归因回放的更新时间与展示名称；归因字段由路由统一写入。 */
    private void finalizeAttribution(ColonelsettlementOrder order) {
        order.setUpdateTime(LocalDateTime.now());
        fillUserNames(order);
    }

    /**
     * 填充订单上的渠道员和招商员用户名字段。
     */
    private void fillUserNames(ColonelsettlementOrder order) {
        order.setChannelUserName(resolveUserName(order.getChannelUserId()));
        order.setColonelUserName(resolveUserName(order.getColonelUserId()));
    }

    /**
     * 根据用户 ID 查询真实姓名，用于填充订单上的用户名字段。
     */
    private String resolveUserName(UUID userId) {
        return persistenceService.getUserName(userId);
    }

    /**
     * 判断归因结果是否安全应用于历史订单更新。
     *
     * <p>安全条件：归因成功且（无原生键匹配 或 原生键映射创建时间不晚于订单创建时间）。
     * 若映射创建时间晚于订单创建时间，则归因结果可能是后补的，不应覆盖历史数据。</p>
     *
     * @param order  订单实体
     * @param result 归因解析结果
     * @return true 表示可以安全更新，false 表示应跳过
     */
    private boolean isSafeForHistoricalUpdate(ColonelsettlementOrder order, AttributionService.AttributionResult result) {
        if (!AttributionService.STATUS_ATTRIBUTED.equals(result.attributionStatus())) {
            return false;
        }
        AttributionService.NativeMappingTrace nativeTrace = result.nativeTrace();
        if (nativeTrace == null || !nativeTrace.nativeKeyMatched()) {
            return true;
        }
        if (nativeTrace.mappingCreatedAt() == null || order.getCreateTime() == null) {
            return false;
        }
        return !nativeTrace.mappingCreatedAt().isAfter(order.getCreateTime());
    }

    /**
     * 归因回放结果记录，包含详细的统计数据。
     *
     * @param scanned                     扫描订单总数
     * @param attributed                  归因成功数
     * @param unattributed                未归因数
     * @param updated                     实际更新持久化的订单数
     * @param dryRun                      是否为试运行模式
     * @param nativeKeyMatched            原生键匹配数
     * @param safeToUpdate                安全可更新数
     * @param unsafeBecauseCreatedAfterOrder 因映射创建时间晚于订单而标记为不安全的数量
     * @param colonelBuyinIdMismatch      招商员 buyinId 不匹配数
     * @param ambiguousMapping            歧义映射数
     * @param stillUnattributed           回放后仍未归因的数量
     */
    public record ReplayResult(
            int scanned,
            int attributed,
            int unattributed,
            int updated,
            boolean dryRun,
            int nativeKeyMatched,
            int safeToUpdate,
            int unsafeBecauseCreatedAfterOrder,
            int colonelBuyinIdMismatch,
            int ambiguousMapping,
            int stillUnattributed) {
    }
}
