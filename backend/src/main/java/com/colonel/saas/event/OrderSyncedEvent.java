package com.colonel.saas.event;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 订单同步完成事件。
 * <p>
 * 当订单数据从抖店 API 同步并成功落库后，由 {@code OrderSyncJob} 发布此事件。
 * 该事件是系统中最核心的跨域事件之一，多个监听方依赖此事件进行后续处理：
 * </p>
 * <ul>
 *   <li>业绩域 — 触发业绩归属和提成计算</li>
 *   <li>看板汇总 — 更新实时数据统计</li>
 *   <li>寄样域 — 判断达人的"交作业"是否完成</li>
 *   <li>达人保护期 — 根据 talentUid 重置保护期计时器（T-03）</li>
 * </ul>
 * <p>
 * 关键字段说明：
 * <ul>
 *   <li>{@code newlyInserted} — 是否为新插入记录（区别于更新已有记录）</li>
 *   <li>{@code attributionStatus} — 归属状态，标识订单是否已完成业绩归属</li>
 *   <li>{@code orderAmount / payAmount / settleAmount} — 订单金额、支付金额、结算金额，单位：分</li>
 *   <li>{@code estimate*Fee / effective*Fee} — 预估/实际服务费、服务费支出和技服费，单位：分</li>
 *   <li>{@code settleColonel*} — 结算维度的团长佣金和技服费，单位：分</li>
 *   <li>{@code productId / activityId / partnerId} — 订单事实的商品、活动和合作方标识</li>
 *   <li>{@code defaultChannelId / defaultRecruiterId} — 订单域默认归因结果，不代表最终业绩归属</li>
 *   <li>{@code extraData} — 扩展数据，用于传递额外的业务信息（如达人保护期参数）</li>
 * </ul>
 * </p>
 *
 * @param orderId 抖店订单号
 * @param orderRowId 本地订单记录的 UUID 主键
 * @param newlyInserted 是否为新插入记录
 * @param attributionStatus 归属状态
 * @param orderAmount 订单金额，单位：分
 * @param payAmount 支付金额，单位：分
 * @param settleAmount 结算金额，单位：分
 * @param estimateServiceFee 预估服务费，单位：分
 * @param effectiveServiceFee 实际服务费，单位：分
 * @param estimateServiceFeeExpense 预估服务费支出，单位：分
 * @param effectiveServiceFeeExpense 实际服务费支出，单位：分
 * @param estimateTechServiceFee 预估技服费，单位：分
 * @param effectiveTechServiceFee 实际技服费，单位：分
 * @param settleColonelCommission 结算团长佣金，单位：分
 * @param settleColonelTechServiceFee 结算团长技服费，单位：分
 * @param settleSecondColonelCommission 结算二级团长佣金，单位：分
 * @param orderStatus 订单状态码
 * @param orderCreateTime 订单创建时间
 * @param talentUid 达人 UID，用于达人保护期重置等场景
 * @param extraData 扩展数据，用于传递达人保护期参数等额外业务信息
 * @param productId 商品 ID
 * @param activityId 活动 ID
 * @param partnerId 合作方 ID，当前来自订单 shopId
 * @param talentId 达人本地 ID
 * @param defaultChannelId 默认渠道用户 ID
 * @param defaultRecruiterId 默认招商用户 ID
 * @param recruiterAttribution 默认招商归因类型
 * @param pickSource 推广来源
 * @param payTime 支付时间
 * @param settleTime 结算时间
 * @param isUpdate 是否为更新已有订单
 * @param occurredAt 事件发生时间
 *
 * @see com.colonel.saas.job.OrderSyncJob
 * @see com.colonel.saas.listener.OrderSyncedEventListener
 */
public record OrderSyncedEvent(
        /** 抖店订单号，全局唯一标识 */
        String orderId,
        /** 本地订单记录的 UUID 主键 */
        UUID orderRowId,
        /** 是否为新插入记录，{@code true} 表示首次同步，{@code false} 表示更新已有记录 */
        boolean newlyInserted,
        /** 归属状态，标识订单是否已完成业绩归属 */
        String attributionStatus,
        /** 订单金额，单位：分 */
        long orderAmount,
        /** 支付金额，单位：分 */
        long payAmount,
        /** 结算金额，单位：分 */
        long settleAmount,
        /** 预估服务费，单位：分 */
        long estimateServiceFee,
        /** 实际服务费，单位：分 */
        long effectiveServiceFee,
        /** 预估服务费支出，单位：分 */
        long estimateServiceFeeExpense,
        /** 实际服务费支出，单位：分 */
        long effectiveServiceFeeExpense,
        /** 预估技服费，单位：分 */
        long estimateTechServiceFee,
        /** 实际技服费，单位：分 */
        long effectiveTechServiceFee,
        /** 结算团长佣金，单位：分 */
        long settleColonelCommission,
        /** 结算团长技服费，单位：分 */
        long settleColonelTechServiceFee,
        /** 结算二级团长佣金，单位：分 */
        long settleSecondColonelCommission,
        /** 订单状态码 */
        Integer orderStatus,
        /** 订单创建时间 */
        LocalDateTime orderCreateTime,
        /** 达人 UID，用于达人保护期重置（T-03） */
        String talentUid,
        /** 扩展数据，用于传递达人保护期参数等额外业务信息 */
        Map<String, Object> extraData,
        /** 商品 ID */
        String productId,
        /** 活动 ID */
        String activityId,
        /** 合作方 ID，当前来自订单 shopId */
        String partnerId,
        /** 达人本地 ID */
        UUID talentId,
        /** 默认渠道用户 ID */
        UUID defaultChannelId,
        /** 默认招商用户 ID */
        UUID defaultRecruiterId,
        /** 默认招商归因类型 */
        String recruiterAttribution,
        /** 推广来源 */
        String pickSource,
        /** 支付时间 */
        LocalDateTime payTime,
        /** 结算时间 */
        LocalDateTime settleTime,
        /** 是否为更新已有订单 */
        boolean isUpdate,
        /** 事件发生时间 */
        LocalDateTime occurredAt,
        /** 订单事实版本；同一订单版本变更必须形成新的可消费事件。 */
        int orderVersion) {

    /** 兼容既有事件调用；未提供版本的历史调用按版本 0 处理。 */
    public OrderSyncedEvent(
            String orderId, UUID orderRowId, boolean newlyInserted, String attributionStatus,
            long orderAmount, long payAmount, long settleAmount, long estimateServiceFee,
            long effectiveServiceFee, long estimateServiceFeeExpense, long effectiveServiceFeeExpense,
            long estimateTechServiceFee, long effectiveTechServiceFee, long settleColonelCommission,
            long settleColonelTechServiceFee, long settleSecondColonelCommission, Integer orderStatus,
            LocalDateTime orderCreateTime, String talentUid, Map<String, Object> extraData, String productId,
            String activityId, String partnerId, UUID talentId, UUID defaultChannelId, UUID defaultRecruiterId,
            String recruiterAttribution, String pickSource, LocalDateTime payTime, LocalDateTime settleTime,
            boolean isUpdate, LocalDateTime occurredAt) {
        this(orderId, orderRowId, newlyInserted, attributionStatus, orderAmount, payAmount, settleAmount,
                estimateServiceFee, effectiveServiceFee, estimateServiceFeeExpense, effectiveServiceFeeExpense,
                estimateTechServiceFee, effectiveTechServiceFee, settleColonelCommission, settleColonelTechServiceFee,
                settleSecondColonelCommission, orderStatus, orderCreateTime, talentUid, extraData, productId,
                activityId, partnerId, talentId, defaultChannelId, defaultRecruiterId, recruiterAttribution,
                pickSource, payTime, settleTime, isUpdate, occurredAt, 0);
    }

    /** 用于版本化 Outbox 契约的最小事件工厂。 */
    public static OrderSyncedEvent versioned(String orderId, UUID orderRowId, int orderVersion) {
        return new OrderSyncedEvent(orderId, orderRowId, false, null,
                0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L,
                null, null, null, Map.of(), null, null, null, null, null, null,
                null, null, null, null, false, LocalDateTime.now(), orderVersion);
    }

    public OrderSyncedEvent(
            String orderId,
            UUID orderRowId,
            boolean newlyInserted,
            String attributionStatus,
            long orderAmount,
            long payAmount,
            long settleAmount,
            long estimateServiceFee,
            long effectiveServiceFee,
            long estimateTechServiceFee,
            long effectiveTechServiceFee,
            long settleColonelCommission,
            long settleColonelTechServiceFee,
            long settleSecondColonelCommission,
            Integer orderStatus,
            LocalDateTime orderCreateTime,
            String talentUid,
            Map<String, Object> extraData) {
        this(
                orderId,
                orderRowId,
                newlyInserted,
                attributionStatus,
                orderAmount,
                payAmount,
                settleAmount,
                estimateServiceFee,
                effectiveServiceFee,
                0L,
                0L,
                estimateTechServiceFee,
                effectiveTechServiceFee,
                settleColonelCommission,
                settleColonelTechServiceFee,
                settleSecondColonelCommission,
                orderStatus,
                orderCreateTime,
                talentUid,
                extraData,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                !newlyInserted,
                null,
                0);
    }
}
