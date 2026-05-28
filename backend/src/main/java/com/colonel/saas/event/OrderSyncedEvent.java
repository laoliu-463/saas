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
 *   <li>{@code estimate*Fee / effective*Fee} — 预估/实际服务费和技服费，单位：分</li>
 *   <li>{@code settleColonel*} — 结算维度的团长佣金和技服费，单位：分</li>
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
 * @param estimateTechServiceFee 预估技服费，单位：分
 * @param effectiveTechServiceFee 实际技服费，单位：分
 * @param settleColonelCommission 结算团长佣金，单位：分
 * @param settleColonelTechServiceFee 结算团长技服费，单位：分
 * @param settleSecondColonelCommission 结算二级团长佣金，单位：分
 * @param orderStatus 订单状态码
 * @param orderCreateTime 订单创建时间
 * @param talentUid 达人 UID，用于达人保护期重置等场景
 * @param extraData 扩展数据，用于传递达人保护期参数等额外业务信息
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
        Map<String, Object> extraData) {
}
