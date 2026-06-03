package com.colonel.saas.service;

import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * 订单双轨金额解析器，从抖店订单原始载荷（rawPayload）中解析预估轨与结算轨金额。
 *
 * <ul>
 *   <li>解析双轨服务费：预估服务费（estimateServiceFee）与结算服务费（effectiveServiceFee）</li>
 *   <li>解析双轨技术服务费：预估技术服务费（estimateTechServiceFee）与结算技术服务费（effectiveTechServiceFee）</li>
 *   <li>解析订单实付金额（payAmount）和结算金额（settleAmount）</li>
 *   <li>支持多字段名别名自动匹配，兼容抖店 API 不同版本的字段命名</li>
 *   <li>提供预估轨快照保留机制（mergeEstimateSnapshot），避免重复同步覆盖首次预估值</li>
 *   <li>所有金额单位为分（人民币最小单位），避免浮点精度问题</li>
 * </ul>
 *
 * <p>在架构中属于订单域（Order Domain）的核心金额计算工具类，
 * 被 {@link OrderSyncService} 和 {@link OrderSyncPersistenceService} 调用，
 * 对应订单域 V1.6 §2.4.2 双轨金额规范。</p>
 *
 * @see OrderSyncService
 * @see OrderSyncPersistenceService
 */
public final class OrderDualTrackAmountResolver {

    /** 工具类，禁止实例化 */
    private OrderDualTrackAmountResolver() {
    }

    /**
     * 双轨金额结果值对象，包含订单全部六个金额字段。
     *
     * @param payAmount              订单实付金额（分）
     * @param settleAmount           结算金额（分）
     * @param estimateServiceFee     预估服务费（分），订单创建时即确定
     * @param effectiveServiceFee    结算服务费（分），订单结算后由抖店回填
     * @param estimateTechServiceFee 预估技术服务费（分）
     * @param effectiveTechServiceFee 结算技术服务费（分）
     */
    public record DualTrackAmounts(
            long payAmount,
            long settleAmount,
            long estimateServiceFee,
            long effectiveServiceFee,
            long estimateTechServiceFee,
            long effectiveTechServiceFee) {
    }

    /**
     * 从原始载荷解析双轨金额，支持多别名自动匹配和 fallback 兜底。
     *
     * <ol>
     *   <li>第一步：从 rawPayload 中按优先级匹配实付金额字段（payGoodsAmount > orderAmount > ...），
     *       若均无则使用 fallbackPayAmount 兜底</li>
     *   <li>第二步：匹配结算金额字段（settledGoodsAmount > settleAmount > actualAmount），
     *       若均无则回退到 payAmount</li>
     *   <li>第三步：匹配预估服务费与结算服务费，优先从 colonelOrderInfo 嵌套层提取</li>
     *   <li>第四步：执行服务费 fallback 链 —— 预估缺失时用结算值填充，结算缺失时用 fallbackServiceFee</li>
     *   <li>第五步：匹配预估/结算技术服务费，逻辑同上</li>
     *   <li>第六步：组装 DualTrackAmounts 结果并返回</li>
     * </ol>
     *
     * @param rawPayload       抖店订单原始 JSON 载荷（扁平化 Map）
     * @param fallbackPayAmount 实付金额兜底值（来自已有记录），可为 null
     * @param fallbackServiceFee 服务费兜底值（来自已有记录），可为 null
     * @return 解析后的双轨金额对象，所有字段已确保非负
     */
    public static DualTrackAmounts resolve(
            Map<String, Object> rawPayload,
            Long fallbackPayAmount,
            Long fallbackServiceFee) {
        // 第一步：解析实付金额，按别名优先级依次尝试
        long payAmount = firstPositive(
                asLong(pick(rawPayload, "pay_goods_amount", "payGoodsAmount", "order_amount", "orderAmount",
                        "total_pay_amount", "totalPayAmount", "pay_amount", "payAmount")),
                fallbackPayAmount);
        // 第二步：解析结算金额，缺失时回退到 payAmount
        long settleAmount = firstPositive(
                asLong(pick(rawPayload, "settled_goods_amount", "settledGoodsAmount", "settle_goods_amount",
                        "settleGoodsAmount", "settle_amount", "settleAmount", "actual_amount", "actualAmount")),
                payAmount);

        // 第三步：解析预估服务费与结算服务费（优先从嵌套层 colonelOrderInfo 提取）
        long estimateServiceFee = firstNonNegative(asLong(pickNested(rawPayload,
                "estimated_commission", "estimatedCommission", "estimated_service_fee", "estimatedServiceFee",
                "estimate_institution_commission", "estimateInstitutionCommission")));
        long effectiveServiceFee = firstNonNegative(asLong(pickNested(rawPayload,
                "settle_colonel_commission", "settleColonelCommission", "commission", "real_commission",
                "realCommission", "settled_commission", "settledCommission", "institution_commission",
                "institutionCommission", "colonel_commission", "colonelCommission", "service_fee", "serviceFee")));

        // 第四步：服务费 fallback 链 —— 预估缺失时用结算值填充
        if (estimateServiceFee <= 0 && effectiveServiceFee > 0) {
            estimateServiceFee = effectiveServiceFee;
        }
        // 预估仍缺失时用兜底值填充
        if (estimateServiceFee <= 0 && fallbackServiceFee != null && fallbackServiceFee > 0) {
            estimateServiceFee = fallbackServiceFee;
        }
        if (effectiveServiceFee <= 0 && estimateServiceFee > 0) {
            // 待结算单：结算轨常为 0，保留预估值不覆盖
        } else if (effectiveServiceFee <= 0 && fallbackServiceFee != null && fallbackServiceFee > 0) {
            // 结算轨缺失且有兜底值时，使用兜底值
            effectiveServiceFee = fallbackServiceFee;
        }

        // 第五步：解析预估/结算技术服务费
        long estimateTechServiceFee = firstNonNegative(asLong(pickNested(rawPayload,
                "estimated_tech_service_fee", "estimatedTechServiceFee", "estimate_platform_service_fee",
                "estimatePlatformServiceFee", "settle_colonel_tech_service_fee", "settleColonelTechServiceFee")));
        long effectiveTechServiceFee = firstNonNegative(asLong(pickNested(rawPayload,
                "tech_service_fee", "techServiceFee", "settled_tech_service_fee", "settledTechServiceFee",
                "platform_service_fee", "platformServiceFee", "settle_colonel_tech_service_fee",
                "settleColonelTechServiceFee")));
        // 预估技术服务费缺失时，用结算值填充
        if (estimateTechServiceFee <= 0) {
            estimateTechServiceFee = effectiveTechServiceFee;
        }

        // 第六步：组装结果
        return new DualTrackAmounts(
                payAmount,
                settleAmount,
                estimateServiceFee,
                effectiveServiceFee,
                estimateTechServiceFee,
                effectiveTechServiceFee);
    }

    /**
     * 再次同步时保留已有预估轨快照（§2.4.2：更新 effective_*，保留 estimate_*）。
     */
    public static void mergeEstimateSnapshot(
            com.colonel.saas.entity.ColonelsettlementOrder existing,
            com.colonel.saas.entity.ColonelsettlementOrder incoming) {
        if (existing == null || incoming == null) {
            return;
        }
        if (hasValue(existing.getEstimateServiceFee())) {
            incoming.setEstimateServiceFee(existing.getEstimateServiceFee());
        }
        if (hasValue(existing.getEstimateTechServiceFee())) {
            incoming.setEstimateTechServiceFee(existing.getEstimateTechServiceFee());
        }
        if (hasValue(existing.getOrderAmount())) {
            incoming.setOrderAmount(existing.getOrderAmount());
        }
    }

    /**
     * 事实源再次同步时保留已有结算轨快照。
     * <p>
     * 6468 只负责事实/预估轨；当该来源更新已存在订单时，不能清空 2704 已补充的
     * settle/effective 字段。
     * </p>
     */
    public static void mergeSettlementSnapshot(
            com.colonel.saas.entity.ColonelsettlementOrder existing,
            com.colonel.saas.entity.ColonelsettlementOrder incoming) {
        if (existing == null || incoming == null) {
            return;
        }
        if (isEmpty(incoming.getSettleAmount()) && hasValue(existing.getSettleAmount())) {
            incoming.setSettleAmount(existing.getSettleAmount());
        }
        if (isEmpty(incoming.getEffectiveServiceFee()) && hasValue(existing.getEffectiveServiceFee())) {
            incoming.setEffectiveServiceFee(existing.getEffectiveServiceFee());
        }
        if (isEmpty(incoming.getEffectiveTechServiceFee()) && hasValue(existing.getEffectiveTechServiceFee())) {
            incoming.setEffectiveTechServiceFee(existing.getEffectiveTechServiceFee());
        }
        if (isEmpty(incoming.getSettleColonelCommission()) && hasValue(existing.getSettleColonelCommission())) {
            incoming.setSettleColonelCommission(existing.getSettleColonelCommission());
        }
        if (isEmpty(incoming.getSettleColonelTechServiceFee()) && hasValue(existing.getSettleColonelTechServiceFee())) {
            incoming.setSettleColonelTechServiceFee(existing.getSettleColonelTechServiceFee());
        }
        if (isEmpty(incoming.getSettleSecondColonelCommission()) && hasValue(existing.getSettleSecondColonelCommission())) {
            incoming.setSettleSecondColonelCommission(existing.getSettleSecondColonelCommission());
        }
        if (incoming.getSettleTime() == null && existing.getSettleTime() != null) {
            incoming.setSettleTime(existing.getSettleTime());
        }
    }

    /**
     * 将 6468 事实源金额写入订单实体。
     * <p>
     * 6468 只负责事实/预估轨：写入实付金额、actualAmount 兼容字段和预估费用；
     * 不写 settle/effective/settleColonel* 字段，避免污染 2704 结算轨。
     * </p>
     */
    public static void applyInstituteFactToOrder(
            com.colonel.saas.entity.ColonelsettlementOrder order,
            DualTrackAmounts amounts) {
        if (order == null || amounts == null) {
            return;
        }
        order.setOrderAmount(amounts.payAmount());
        order.setActualAmount(amounts.payAmount());
        order.setEstimateServiceFee(amounts.estimateServiceFee());
        order.setEstimateTechServiceFee(amounts.estimateTechServiceFee());
    }

    /**
     * 将解析后的双轨金额写入订单实体，同时兼容旧字段映射。
     *
     * <ol>
     *   <li>第一步：写入基础金额字段（订单实付、结算金额）</li>
     *   <li>第二步：写入双轨服务费与技术服务费（预估轨 + 结算轨共四个字段）</li>
     *   <li>第三步：兼容旧字段 settleColonelCommission / settleColonelTechServiceFee，
     *       结算轨优先，结算轨为 0 时回退到预估轨</li>
     * </ol>
     *
     * @param order   目标订单实体，不能为 null
     * @param amounts 解析后的双轨金额，不能为 null
     */
    public static void applyToOrder(
            com.colonel.saas.entity.ColonelsettlementOrder order,
            DualTrackAmounts amounts) {
        if (order == null || amounts == null) {
            return;
        }
        // 第一步：写入基础金额
        order.setOrderAmount(amounts.payAmount());
        order.setActualAmount(amounts.settleAmount());
        order.setSettleAmount(amounts.settleAmount());
        // 第二步：写入双轨服务费与技术服务费
        order.setEstimateServiceFee(amounts.estimateServiceFee());
        order.setEffectiveServiceFee(amounts.effectiveServiceFee());
        order.setEstimateTechServiceFee(amounts.estimateTechServiceFee());
        order.setEffectiveTechServiceFee(amounts.effectiveTechServiceFee());
        // 第三步：兼容旧字段 —— 结算轨优先，为 0 时回退预估轨
        order.setSettleColonelCommission(amounts.effectiveServiceFee() > 0
                ? amounts.effectiveServiceFee()
                : amounts.estimateServiceFee());
        order.setSettleColonelTechServiceFee(amounts.effectiveTechServiceFee() > 0
                ? amounts.effectiveTechServiceFee()
                : amounts.estimateTechServiceFee());
    }

    /**
     * 判断金额值是否为"空"（null 或 <= 0），用于预估轨快照保留判断。
     *
     * @param value 待判断的金额值
     * @return true 表示空值
     */
    private static boolean isEmpty(Long value) {
        return value == null || value <= 0;
    }

    /**
     * 判断金额值是否有效（非 null 且 > 0）。
     *
     * @param value 待判断的金额值
     * @return true 表示有效值
     */
    private static boolean hasValue(Long value) {
        return value != null && value > 0;
    }

    /**
     * 优先返回主值（必须 > 0），否则返回兜底值（确保非负）。
     *
     * @param primary  主值
     * @param fallback 兜底值，可为 null
     * @return 非负的金额值
     */
    private static long firstPositive(Long primary, Long fallback) {
        if (primary != null && primary > 0) {
            return primary;
        }
        return fallback == null ? 0L : Math.max(fallback, 0L);
    }

    /**
     * 将可能为 null 的值转为非负 long，null 返回 0。
     *
     * @param value 可能为 null 的值
     * @return 非负的 long 值
     */
    private static long firstNonNegative(Long value) {
        return value == null ? 0L : Math.max(value, 0L);
    }

    /**
     * 从 Map 中按优先级依次查找第一个存在的 key 对应的值。
     *
     * <ol>
     *   <li>遍历 keys 数组，按顺序检查 source 中是否存在该 key</li>
     *   <li>返回第一个存在的 key 对应的值，均不存在时返回 null</li>
     * </ol>
     *
     * @param source 数据源 Map
     * @param keys   候选 key 列表（按优先级排列）
     * @return 第一个匹配的值，或 null
     */
    private static Object pick(Map<String, Object> source, String... keys) {
        if (source == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (source.containsKey(key)) {
                return source.get(key);
            }
        }
        return null;
    }

    /**
     * 支持嵌套层的字段查找：先在顶层查找，未找到则在 colonelOrderInfo 子对象中查找。
     *
     * <ol>
     *   <li>第一步：在 rawPayload 顶层按 keys 查找</li>
     *   <li>第二步：顶层未找到时，提取 colonel_order_info / colonelOrderInfo 嵌套 Map</li>
     *   <li>第三步：在嵌套 Map 中按 keys 再次查找</li>
     * </ol>
     *
     * @param rawPayload 抖店订单原始载荷
     * @param keys       候选 key 列表
     * @return 匹配到的值，或 null
     */
    @SuppressWarnings("unchecked")
    private static Object pickNested(Map<String, Object> rawPayload, String... keys) {
        // 第一步：在顶层查找
        Object direct = pick(rawPayload, keys);
        if (direct != null) {
            return direct;
        }
        // 第二步：提取嵌套层
        Object nested = pick(rawPayload, "colonel_order_info", "colonelOrderInfo");
        if (nested instanceof Map<?, ?> map) {
            // 第三步：在嵌套层中查找
            return pick((Map<String, Object>) map, keys);
        }
        return null;
    }

    /**
     * 将任意对象安全转换为 Long，支持 Number 类型和字符串解析。
     *
     * @param value 待转换的对象（Number、String 或 null）
     * @return 转换后的 Long 值，转换失败返回 null
     */
    private static Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = String.valueOf(value).trim();
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
