package com.colonel.saas.domain.order.policy;

import com.colonel.saas.common.time.AppZone;
import com.colonel.saas.domain.shared.policy.DomainText;
import com.colonel.saas.entity.ColonelsettlementOrder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongConsumer;

/**
 * 订单金额映射 Policy：把抖音订单 raw payload 集中映射到本地双轨金额字段。
 *
 * <p>本 Policy 隶属订单域（Order Domain），是 DDD-ORDER-002 的迁移目标。
 * 订单域只落库接口返回的事实金额，不做业务推导（不计算服务费收益 / 提成 / 毛利）。
 * 对应订单域 V1.6 §2.4.2 双轨金额规范。</p>
 *
 * <p>迁移自 {@code com.colonel.saas.domain.order.policy.OrderDualTrackAmountResolver}。</p>
 */
public final class OrderAmountMapperPolicy {

    private static final DateTimeFormatter RAW_DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private OrderAmountMapperPolicy() {
    }

    /** 金额解析轨道：INSTITUTE 允许预估轨兜底；SETTLEMENT_STRICT 禁止结算轨回退预估/实付。 */
    public enum Track {
        INSTITUTE,
        SETTLEMENT_STRICT
    }

    /** 映射结果中支持的输出字段。 */
    public enum OutputField {
        PAY_AMOUNT,
        SETTLE_AMOUNT,
        ESTIMATE_SERVICE_FEE,
        EFFECTIVE_SERVICE_FEE,
        ESTIMATE_TECH_SERVICE_FEE,
        EFFECTIVE_TECH_SERVICE_FEE,
        SERVICE_FEE_RATE,
        COMMISSION_RATE
    }

    /** 单条警告：指出 raw 字段缺失、回退、归一化等。 */
    public record AmountWarning(OutputField field, String code, String message) {
    }

    /**
     * 映射结果值对象，对应任务清单中 10 个输出字段。
     *
     * @param payAmount                订单实付金额（分）
     * @param settleAmount             结算金额（分）
     * @param estimateServiceFee       预估服务费收入（分）
     * @param effectiveServiceFee      结算服务费收入（分）
     * @param estimateTechServiceFee   预估技术服务费（分）
     * @param effectiveTechServiceFee  结算技术服务费（分）
     * @param estimateServiceFeeExpense 预估服务费支出（分）
     * @param effectiveServiceFeeExpense 结算服务费支出（分）
     * @param serviceFeeRate           归一化后的服务费率（0~1 之间）；缺失时为 null
     * @param commissionRate           归一化后的招商提成率（0~1 之间）；缺失时为 null
     * @param amountWarnings           映射过程产生的警告（只读视图）
     * @param rawFieldUsedMap          实际命中的 raw key（按 OutputField 索引）；未命中时键不存在
     */
    public record MappedAmounts(
            long payAmount,
            long settleAmount,
            long estimateServiceFee,
            long effectiveServiceFee,
            long estimateTechServiceFee,
            long effectiveTechServiceFee,
            long estimateServiceFeeExpense,
            long effectiveServiceFeeExpense,
            BigDecimal serviceFeeRate,
            BigDecimal commissionRate,
            List<AmountWarning> amountWarnings,
            Map<OutputField, String> rawFieldUsedMap) {
    }

    /** 同步来源：6468 事实/预估轨；2704 结算事实轨。 */
    public enum SyncTrack {
        INSTITUTE,
        SETTLEMENT
    }

    /**
     * 把 raw payload 映射为双轨金额。默认使用 INSTITUTE 轨道。
     */
    public static MappedAmounts map(Map<String, Object> rawPayload,
                                    ColonelsettlementOrder existing) {
        return map(rawPayload, existing, null, Track.INSTITUTE);
    }

    /**
     * 2704 结算事实源：结算轨字段缺失时不回退实付/预估。
     */
    public static MappedAmounts mapStrictSettlement(Map<String, Object> rawPayload,
                                                    ColonelsettlementOrder existing) {
        return map(rawPayload, existing, null, Track.SETTLEMENT_STRICT);
    }

    /**
     * 通用映射入口。
     *
     * @param rawPayload   抖店订单原始 JSON 载荷（扁平化 Map）
     * @param existing     已有订单（用于保留 estimate 快照与 fallback），可为 null
     * @param aliasConfig  字段别名配置（按 OutputField 索引），可为 null
     * @param track        解析轨道
     */
    public static MappedAmounts map(Map<String, Object> rawPayload,
                                    ColonelsettlementOrder existing,
                                    Map<OutputField, List<String>> aliasConfig,
                                    Track track) {
        boolean settlementStrict = track == Track.SETTLEMENT_STRICT;
        Map<String, Object> safe = rawPayload == null ? Map.of() : rawPayload;
        List<AmountWarning> warnings = new ArrayList<>();
        Map<OutputField, String> used = new LinkedHashMap<>();

        // 第一步：解析实付金额
        Long payRaw = pickLong(safe, aliasConfig, OutputField.PAY_AMOUNT,
                "pay_goods_amount", "payGoodsAmount", "order_amount", "orderAmount",
                "total_pay_amount", "totalPayAmount", "pay_amount", "payAmount");
        recordKey(used, OutputField.PAY_AMOUNT, payRaw == null ? null : aliasConfig, safe,
                "pay_goods_amount", "payGoodsAmount", "order_amount", "orderAmount",
                "total_pay_amount", "totalPayAmount", "pay_amount", "payAmount");
        long payAmount = firstPositive(payRaw, fallbackPayAmount(existing));
        if (payRaw == null) {
            warnings.add(new AmountWarning(OutputField.PAY_AMOUNT, "MISSING",
                    "raw payload 未提供任何 payAmount 别名字段；已使用兜底值"));
        }

        // 第二步：解析结算金额
        Long settledRaw = pickLong(safe, aliasConfig, OutputField.SETTLE_AMOUNT,
                "settled_goods_amount", "settledGoodsAmount", "settle_goods_amount",
                "settleGoodsAmount", "settle_amount", "settleAmount", "actual_amount", "actualAmount");
        recordKey(used, OutputField.SETTLE_AMOUNT, settledRaw == null ? null : aliasConfig, safe,
                "settled_goods_amount", "settledGoodsAmount", "settle_goods_amount",
                "settleGoodsAmount", "settle_amount", "settleAmount", "actual_amount", "actualAmount");
        long settleAmount;
        if (settlementStrict) {
            settleAmount = firstNonNegative(settledRaw);
            if (settledRaw == null) {
                warnings.add(new AmountWarning(OutputField.SETTLE_AMOUNT, "MISSING",
                        "SETTLEMENT_STRICT 模式下 raw settleAmount 缺失；保持 0"));
            }
        } else {
            settleAmount = firstPositive(settledRaw, payAmount);
            if (settledRaw == null) {
                warnings.add(new AmountWarning(OutputField.SETTLE_AMOUNT, "FALLBACK",
                        "raw payload 未提供 settleAmount 别名；回退到 payAmount"));
            }
        }

        // 第三步：解析预估/结算技术服务费。tech_service_fee 在部分待结算样本中是预估字段，
        // 非 strict 轨道不写入 effective；结算轨只使用明确结算别名。
        Long estimateTechRaw = pickNestedLong(safe,
                "estimated_tech_service_fee", "estimatedTechServiceFee", "estimate_platform_service_fee",
                "estimatePlatformServiceFee");
        Long effectiveTechRaw = pickNestedLong(safe,
                "effective_tech_service_fee", "effectiveTechServiceFee", "settled_tech_service_fee",
                "settledTechServiceFee", "real_tech_service_fee", "realTechServiceFee",
                "settle_colonel_tech_service_fee", "settleColonelTechServiceFee");
        Long ambiguousTechRaw = pickNestedLong(safe,
                "tech_service_fee", "techServiceFee", "platform_service_fee", "platformServiceFee");
        long estimateTechServiceFee = firstNonNegative(estimateTechRaw);
        long effectiveTechServiceFee = firstNonNegative(effectiveTechRaw);
        if (effectiveTechServiceFee <= 0 && settlementStrict) {
            effectiveTechServiceFee = firstNonNegative(ambiguousTechRaw);
        }
        if (estimateTechServiceFee <= 0 && !settlementStrict) {
            estimateTechServiceFee = firstNonNegative(ambiguousTechRaw);
        }
        if (estimateTechServiceFee <= 0) {
            estimateTechServiceFee = effectiveTechServiceFee;
        }

        // 第四步：解析预估/结算服务费收入
        long estimateServiceFee = firstFromInstitutions(safe,
                "estimated_commission", "estimatedCommission", "estimated_service_fee",
                "estimatedServiceFee", "estimate_institution_commission", "estimateInstitutionCommission");
        long effectiveServiceFee = firstFromInstitutions(safe,
                "effective_service_fee", "effectiveServiceFee", "settle_colonel_commission",
                "settleColonelCommission", "real_commission", "realCommission",
                "settled_commission", "settledCommission");

        BigDecimal serviceFeeRate = resolveServiceFeeRate(safe);
        if (serviceFeeRate != null) {
            used.put(OutputField.SERVICE_FEE_RATE, firstKeyPresent(safe,
                    "service_fee_rate", "serviceFeeRate", "service_rate", "serviceRate",
                    "ad_service_ratio", "adServiceRatio"));
        }
        if (estimateServiceFee <= 0 && serviceFeeRate != null) {
            estimateServiceFee = calculateServiceFeeIncome(payAmount, serviceFeeRate, 0L);
        }

        // 第五步：服务费 fallback 链
        if (estimateServiceFee <= 0 && effectiveServiceFee > 0) {
            estimateServiceFee = effectiveServiceFee;
        }
        Long existingEstimate = existing == null ? null : existing.getEstimateServiceFee();
        if (estimateServiceFee <= 0 && existingEstimate != null && existingEstimate > 0) {
            estimateServiceFee = existingEstimate;
            warnings.add(new AmountWarning(OutputField.ESTIMATE_SERVICE_FEE, "FALLBACK",
                    "estimateServiceFee 缺失；沿用已有订单的预估值"));
        }
        if (!settlementStrict) {
            // 待结算单：结算轨常为 0，保留预估值不覆盖（与旧逻辑保持一致）
        } else if (settlementStrict && effectiveServiceFee <= 0) {
            // 结算轨严格：不回退
        }
        // Policy 不推导净服务费收益：effectiveServiceFee / effectiveTechServiceFee
        // 各自作为独立事实字段落库。两者相减属于"服务费收益"业务推导，
        // 由业绩/提成域按需计算（参见任务简报 BANS）。

        // commissionRate 当前 raw payload 无标准字段，先以 null 暴露
        BigDecimal commissionRate = resolveCommissionRate(safe);
        if (commissionRate != null) {
            used.put(OutputField.COMMISSION_RATE, firstKeyPresent(safe,
                    "commission_rate", "commissionRate", "招商_提成率"));
        }

        // 第六步：计算服务费支出（二级机构分佣场景）
        long estimateServiceFeeExpense = computeEstimateServiceFeeExpense(safe);
        long effectiveServiceFeeExpense = computeEffectiveServiceFeeExpense(safe);

        return new MappedAmounts(
                payAmount,
                settleAmount,
                estimateServiceFee,
                effectiveServiceFee,
                estimateTechServiceFee,
                effectiveTechServiceFee,
                estimateServiceFeeExpense,
                effectiveServiceFeeExpense,
                serviceFeeRate,
                commissionRate,
                List.copyOf(warnings),
                Map.copyOf(used));
    }

    /**
     * 再次同步时保留已有预估轨快照（§2.4.2：更新 effective_*，保留 estimate_*）。
     */
    public static void mergeEstimateSnapshot(ColonelsettlementOrder existing, ColonelsettlementOrder incoming) {
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
     * 6468 在已结算场景可写入普通订单结算轨；当该来源再次同步且未返回有效结算字段时，
     * 不能清空 2704 或历史 6468 已补充的 settle/effective 字段。
     * </p>
     */
    public static void mergeSettlementSnapshot(ColonelsettlementOrder existing, ColonelsettlementOrder incoming) {
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
     * 把映射结果写入订单实体（6468 事实源）。
     */
    public static void applyInstituteFactToOrder(ColonelsettlementOrder order, MappedAmounts amounts) {
        applyInstituteFactToOrder(order, amounts, null);
    }

    /**
     * 把映射结果写入订单实体（6468 事实源）。
     * <p>
     * 6468 是主订单事实源：写入实付金额、actualAmount 兼容字段和预估费用。
     * 当 raw 响应明确表示已结算时，保守写入普通订单结算轨（settle_* / effective_*）；
     * PAY_SUCC 待结算或结算字段为 0/null 时不写结算轨，也不覆盖已有非空结算字段。
     * </p>
     */
    public static void applyInstituteFactToOrder(ColonelsettlementOrder order,
                                                 MappedAmounts amounts,
                                                 Map<String, Object> rawPayload) {
        if (order == null || amounts == null) {
            return;
        }
        order.setOrderAmount(amounts.payAmount());
        order.setActualAmount(amounts.payAmount());
        order.setEstimateServiceFee(amounts.estimateServiceFee());
        order.setEstimateTechServiceFee(amounts.estimateTechServiceFee());
        order.setEstimateServiceFeeExpense(amounts.estimateServiceFeeExpense());
        if (rawPayload != null && hasInstituteSettlementSignal(rawPayload)) {
            applyInstituteSettlementFromRaw(order, rawPayload);
        }
    }

    /**
     * 判断 6468 raw 是否携带已结算信号（保守：任一条件满足即可）。
     */
    public static boolean hasInstituteSettlementSignal(Map<String, Object> rawPayload) {
        if (rawPayload == null || rawPayload.isEmpty()) {
            return false;
        }
        String flowPoint = asString(pick(rawPayload, "flow_point", "flowPoint"));
        if ("SETTLE".equalsIgnoreCase(flowPoint)) {
            return true;
        }
        Long settledGoods = pickLongWithNested(rawPayload,
                "settled_goods_amount", "settledGoodsAmount", "settle_goods_amount", "settleGoodsAmount");
        if (settledGoods != null && settledGoods > 0) {
            return true;
        }
        Long realCommission = pickLongFromInstitutions(rawPayload, "real_commission", "realCommission");
        if (realCommission != null && realCommission > 0) {
            return true;
        }
        Long settledTech = pickLongWithNested(rawPayload,
                "settled_tech_service_fee", "settledTechServiceFee");
        if (settledTech != null && settledTech > 0) {
            return true;
        }
        Object settleTime = pick(rawPayload, "settle_time", "settleTime", "settled_time", "settledTime");
        return settleTime != null && DomainText.hasText(String.valueOf(settleTime).trim())
                && !"null".equalsIgnoreCase(String.valueOf(settleTime).trim());
    }

    /**
     * 从 6468 raw 保守写入普通订单结算轨；仅非空非零值落库，不覆盖为 0/null。
     */
    public static void applyInstituteSettlementFromRaw(ColonelsettlementOrder order, Map<String, Object> rawPayload) {
        if (order == null || rawPayload == null || !hasInstituteSettlementSignal(rawPayload)) {
            return;
        }
        writePositiveLongIfAllowed(order.getSettleAmount(),
                pickLongWithNested(rawPayload,
                        "settled_goods_amount", "settledGoodsAmount", "settle_goods_amount", "settleGoodsAmount",
                        "settle_amount", "settleAmount", "real_goods_amount", "realGoodsAmount"),
                order::setSettleAmount);
        writePositiveLongIfAllowed(order.getEffectiveServiceFee(),
                pickLongFromInstitutions(rawPayload,
                        "real_commission", "realCommission", "commission",
                        "settled_commission", "settledCommission", "effective_service_fee", "effectiveServiceFee"),
                order::setEffectiveServiceFee);
        Long effectiveTech = pickLongWithNested(rawPayload,
                "tech_service_fee", "techServiceFee", "settled_tech_service_fee", "settledTechServiceFee",
                "real_tech_service_fee", "realTechServiceFee", "effective_tech_service_fee", "effectiveTechServiceFee");
        if (effectiveTech != null && effectiveTech > 0) {
            writePositiveLongIfAllowed(order.getEffectiveTechServiceFee(), effectiveTech, order::setEffectiveTechServiceFee);
        }
        Long effectiveServiceFee = order.getEffectiveServiceFee();
        if (effectiveServiceFee != null && effectiveServiceFee > 0) {
            writePositiveLongIfAllowed(order.getSettleColonelCommission(), effectiveServiceFee,
                    order::setSettleColonelCommission);
        }
        Long effectiveTechServiceFee = order.getEffectiveTechServiceFee();
        if (effectiveTechServiceFee != null && effectiveTechServiceFee > 0) {
            writePositiveLongIfAllowed(order.getSettleColonelTechServiceFee(), effectiveTechServiceFee,
                    order::setSettleColonelTechServiceFee);
        }
    }

    /**
     * 保守写入结算时间：仅在新值非空时写入；已有值时允许用新的非空值更新。
     */
    public static void applyInstituteSettleTime(ColonelsettlementOrder order, LocalDateTime settleTime) {
        if (order == null || settleTime == null) {
            return;
        }
        order.setSettleTime(settleTime);
    }

    /**
     * 从 6468 raw 解析结算时间；缺失时使用网关 item 提供的 fallback。
     */
    public static LocalDateTime resolveInstituteSettleTime(
            Map<String, Object> rawPayload,
            LocalDateTime fallback) {
        if (rawPayload == null || rawPayload.isEmpty() || !hasInstituteSettlementSignal(rawPayload)) {
            return null;
        }
        LocalDateTime parsed = parseRawDateTime(rawPayload,
                "settle_time", "settleTime", "settled_time", "settledTime");
        return parsed != null ? parsed : fallback;
    }

    /**
     * 把映射结果写入订单实体（2704 分次结算补充源）。
     * <p>
     * 2704 补充分次结算与普通订单结算轨；非普通订单主入库源，也不是结算轨唯一来源。
     * 包含基础金额、双轨服务费、双轨技术服务费与旧字段兼容（settleColonelCommission 等）。
     * </p>
     */
    public static void applyToOrder(ColonelsettlementOrder order, MappedAmounts amounts) {
        if (order == null || amounts == null) {
            return;
        }
        order.setOrderAmount(amounts.payAmount());
        order.setActualAmount(amounts.settleAmount());
        order.setSettleAmount(amounts.settleAmount());
        order.setEstimateServiceFee(amounts.estimateServiceFee());
        order.setEffectiveServiceFee(amounts.effectiveServiceFee());
        order.setEstimateTechServiceFee(amounts.estimateTechServiceFee());
        order.setEffectiveTechServiceFee(amounts.effectiveTechServiceFee());
        order.setEstimateServiceFeeExpense(amounts.estimateServiceFeeExpense());
        order.setEffectiveServiceFeeExpense(amounts.effectiveServiceFeeExpense());
        order.setSettleColonelCommission(amounts.effectiveServiceFee() > 0 ? amounts.effectiveServiceFee() : null);
        order.setSettleColonelTechServiceFee(amounts.effectiveTechServiceFee() > 0 ? amounts.effectiveTechServiceFee() : null);
    }

    /**
     * 将 1603 结算口径金额写入订单实体；结算轨字段没有值时保持 0/null，不使用预估轨兜底。
     */
    public static void applyInstituteSettlementToOrder(ColonelsettlementOrder order, MappedAmounts amounts) {
        if (order == null || amounts == null) {
            return;
        }
        order.setOrderAmount(amounts.payAmount());
        order.setActualAmount(amounts.payAmount());
        order.setSettleAmount(amounts.settleAmount());
        order.setEstimateServiceFee(amounts.estimateServiceFee());
        order.setEffectiveServiceFee(amounts.effectiveServiceFee());
        order.setEstimateTechServiceFee(amounts.estimateTechServiceFee());
        order.setEffectiveTechServiceFee(amounts.effectiveTechServiceFee());
        order.setEstimateServiceFeeExpense(amounts.estimateServiceFeeExpense());
        order.setEffectiveServiceFeeExpense(amounts.effectiveServiceFeeExpense());
        order.setSettleColonelCommission(amounts.effectiveServiceFee() > 0 ? amounts.effectiveServiceFee() : null);
        order.setSettleColonelTechServiceFee(amounts.effectiveTechServiceFee() > 0 ? amounts.effectiveTechServiceFee() : null);
    }

    /**
     * 1603 结算口径解析：只读取上游明确返回的结算字段，不做实付/预估硬兜底。
     */
    public static MappedAmounts mapInstituteSettlement(Map<String, Object> rawPayload) {
        Map<String, Object> safe = rawPayload == null ? Map.of() : rawPayload;
        long payAmount = firstNonNegative(asLong(pick(safe,
                "pay_goods_amount", "payGoodsAmount", "order_amount", "orderAmount",
                "total_pay_amount", "totalPayAmount", "pay_amount", "payAmount")));
        long settleAmount = firstNonNegative(asLong(pick(safe,
                "settled_goods_amount", "settledGoodsAmount", "settle_goods_amount", "settleGoodsAmount",
                "settle_amount", "settleAmount", "real_goods_amount", "realGoodsAmount",
                "actual_amount", "actualAmount")));
        long estimateServiceFee = firstFromInstitutions(safe,
                "estimated_commission", "estimatedCommission", "estimated_service_fee", "estimatedServiceFee",
                "estimate_institution_commission", "estimateInstitutionCommission",
                "estimate_commission", "estimateCommission");
        long effectiveServiceFee = firstFromInstitutions(safe,
                "real_commission", "realCommission", "settled_commission", "settledCommission",
                "commission", "institution_commission", "institutionCommission",
                "colonel_commission", "colonelCommission", "service_fee", "serviceFee");
        long estimateTechServiceFee = firstNonNegative(asLong(pickNestedLong(safe,
                "estimated_tech_service_fee", "estimatedTechServiceFee",
                "estimate_platform_service_fee", "estimatePlatformServiceFee")));
        long effectiveTechServiceFee = firstNonNegative(asLong(pickNestedLong(safe,
                "settled_tech_service_fee", "settledTechServiceFee",
                "real_tech_service_fee", "realTechServiceFee")));
        long estimateServiceFeeExpense = computeEstimateServiceFeeExpense(safe);
        long effectiveServiceFeeExpense = computeEffectiveServiceFeeExpense(safe);
        return new MappedAmounts(
                payAmount, settleAmount,
                estimateServiceFee, effectiveServiceFee,
                estimateTechServiceFee, effectiveTechServiceFee,
                estimateServiceFeeExpense, effectiveServiceFeeExpense,
                null, null,
                List.of(), Map.of());
    }

    // ============ 服务费支出计算 ============

    /**
     * 计算预估服务费支出：
     * 当且仅当一级机构与二级机构同时存在预估服务费时，二级机构的预估服务费计入支出。
     */
    static long computeEstimateServiceFeeExpense(Map<String, Object> rawPayload) {
        if (rawPayload == null || rawPayload.isEmpty()) {
            return 0L;
        }
        String[] estKeys = {
                "estimated_commission", "estimatedCommission", "estimated_service_fee", "estimatedServiceFee",
                "estimate_institution_commission", "estimateInstitutionCommission",
                "estimate_commission", "estimateCommission"
        };
        long primaryFee = firstFromPrimaryInstitution(rawPayload, estKeys);
        long secondFee = fromSecondInstitution(rawPayload, estKeys);
        return primaryFee > 0L && secondFee > 0L ? secondFee : 0L;
    }

    /**
     * 计算结算服务费支出：
     * 当且仅当一级机构与二级机构同时存在结算服务费时，二级机构的结算服务费计入支出。
     */
    static long computeEffectiveServiceFeeExpense(Map<String, Object> rawPayload) {
        if (rawPayload == null || rawPayload.isEmpty()) {
            return 0L;
        }
        String[] effKeys = {
                "real_commission", "realCommission", "settled_commission", "settledCommission",
                "commission", "institution_commission", "institutionCommission",
                "colonel_commission", "colonelCommission", "service_fee", "serviceFee"
        };
        long primaryFee = firstFromPrimaryInstitution(rawPayload, effKeys);
        long secondFee = fromSecondInstitution(rawPayload, effKeys);
        return primaryFee > 0L && secondFee > 0L ? secondFee : 0L;
    }

    @SuppressWarnings("unchecked")
    private static long firstFromPrimaryInstitution(Map<String, Object> rawPayload, String... keys) {
        Object direct = pick(rawPayload, keys);
        if (direct != null) {
            long value = firstNonNegative(asLong(direct));
            if (value > 0L) {
                return value;
            }
        }
        Object nested1 = pick(rawPayload, "colonel_order_info", "colonelOrderInfo");
        if (nested1 instanceof Map<?, ?> map1) {
            Object val = pick((Map<String, Object>) map1, keys);
            if (val != null) {
                long value = firstNonNegative(asLong(val));
                if (value > 0L) {
                    return value;
                }
            }
        }
        return 0L;
    }

    @SuppressWarnings("unchecked")
    private static long fromSecondInstitution(Map<String, Object> rawPayload, String... keys) {
        Object nested2 = pick(rawPayload, "colonel_order_info_second", "colonelOrderInfoSecond");
        if (nested2 instanceof Map<?, ?> map2) {
            Object val = pick((Map<String, Object>) map2, keys);
            if (val != null) {
                return firstNonNegative(asLong(val));
            }
        }
        return 0L;
    }

    // ============ 内部辅助方法（private static） ============

    private static Long fallbackPayAmount(ColonelsettlementOrder existing) {
        if (existing == null) {
            return null;
        }
        if (existing.getOrderAmount() != null && existing.getOrderAmount() > 0) {
            return existing.getOrderAmount();
        }
        return null;
    }

    private static boolean isEmpty(Long value) {
        return value == null || value <= 0;
    }

    private static boolean hasValue(Long value) {
        return value != null && value > 0;
    }

    private static void writePositiveLongIfAllowed(Long existing, Long incoming, LongConsumer setter) {
        if (incoming == null || incoming <= 0) {
            return;
        }
        if (existing == null || existing <= 0) {
            setter.accept(incoming);
            return;
        }
        setter.accept(incoming);
    }

    private static Long pickLongWithNested(Map<String, Object> rawPayload, String... keys) {
        Long direct = pickLong(rawPayload, keys);
        if (direct != null) {
            return direct;
        }
        return pickNestedLong(rawPayload, keys);
    }

    @SuppressWarnings("unchecked")
    private static Long pickLongFromInstitutions(Map<String, Object> rawPayload, String... keys) {
        Object direct = pick(rawPayload, keys);
        if (direct != null) {
            return asLong(direct);
        }
        Object nested1 = pick(rawPayload, "colonel_order_info", "colonelOrderInfo");
        if (nested1 instanceof Map<?, ?> map1) {
            Object val = pick((Map<String, Object>) map1, keys);
            if (val != null) {
                return asLong(val);
            }
        }
        Object nested2 = pick(rawPayload, "colonel_order_info_second", "colonelOrderInfoSecond");
        if (nested2 instanceof Map<?, ?> map2) {
            Object val = pick((Map<String, Object>) map2, keys);
            if (val != null) {
                return asLong(val);
            }
        }
        return null;
    }

    private static long firstPositive(Long primary, Long fallback) {
        if (primary != null && primary > 0) {
            return primary;
        }
        return fallback == null ? 0L : Math.max(fallback, 0L);
    }

    private static long firstNonNegative(Long value) {
        return value == null ? 0L : Math.max(value, 0L);
    }

    private static Long pickLong(Map<String, Object> source, String... keys) {
        Object value = pick(source, keys);
        return asLong(value);
    }

    private static Long pickLong(Map<String, Object> source,
                                 Map<OutputField, List<String>> aliasConfig,
                                 OutputField field, String... defaults) {
        List<String> keys = aliasConfig == null ? null : aliasConfig.get(field);
        if (keys == null || keys.isEmpty()) {
            return pickLong(source, defaults);
        }
        return pickLong(source, keys.toArray(new String[0]));
    }

    private static void recordKey(Map<OutputField, String> used, OutputField field,
                                  Map<OutputField, List<String>> aliasConfig,
                                  Map<String, Object> source, String... defaults) {
        if (used.containsKey(field)) {
            return;
        }
        String key = firstKeyPresentWithConfig(source, aliasConfig, field, defaults);
        if (key != null) {
            used.put(field, key);
        }
    }

    private static String firstKeyPresentWithConfig(Map<String, Object> source,
                                                     Map<OutputField, List<String>> aliasConfig,
                                                     OutputField field, String... defaults) {
        List<String> keys = aliasConfig == null ? null : aliasConfig.get(field);
        if (keys == null || keys.isEmpty()) {
            return firstKeyPresent(source, defaults);
        }
        return firstKeyPresent(source, keys.toArray(new String[0]));
    }

    private static String firstKeyPresent(Map<String, Object> source, String... keys) {
        if (source == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (source.containsKey(key)) {
                return key;
            }
        }
        return null;
    }

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

    @SuppressWarnings("unchecked")
    private static Long pickNestedLong(Map<String, Object> rawPayload, String... keys) {
        Object direct = pick(rawPayload, keys);
        if (direct != null) {
            return asLong(direct);
        }
        Object nested = pick(rawPayload, "colonel_order_info", "colonelOrderInfo");
        if (nested instanceof Map<?, ?> map) {
            return asLong(pick((Map<String, Object>) map, keys));
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static long firstFromInstitutions(Map<String, Object> rawPayload, String... keys) {
        Object direct = pick(rawPayload, keys);
        if (direct != null) {
            long value = firstNonNegative(asLong(direct));
            if (value > 0L) {
                return value;
            }
        }
        Object nested1 = pick(rawPayload, "colonel_order_info", "colonelOrderInfo");
        if (nested1 instanceof Map<?, ?> map1) {
            Object val = pick((Map<String, Object>) map1, keys);
            if (val != null) {
                long value = firstNonNegative(asLong(val));
                if (value > 0L) {
                    return value;
                }
            }
        }
        Object nested2 = pick(rawPayload, "colonel_order_info_second", "colonelOrderInfoSecond");
        if (nested2 instanceof Map<?, ?> map2) {
            Object val = pick((Map<String, Object>) map2, keys);
            if (val != null) {
                long value = firstNonNegative(asLong(val));
                if (value > 0L) {
                    return value;
                }
            }
        }
        return 0L;
    }

    private static long calculateServiceFeeIncome(long orderAmount, BigDecimal serviceFeeRate, long techServiceFee) {
        if (orderAmount <= 0 || serviceFeeRate == null || serviceFeeRate.compareTo(BigDecimal.ZERO) <= 0) {
            return 0L;
        }
        long grossIncome = BigDecimal.valueOf(orderAmount)
                .multiply(serviceFeeRate)
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();
        return Math.max(grossIncome - Math.max(techServiceFee, 0L), 0L);
    }

    private static BigDecimal resolveServiceFeeRate(Map<String, Object> rawPayload) {
        return normalizeRate(asBigDecimal(pickNestedBigDecimalSource(rawPayload,
                "service_fee_rate", "serviceFeeRate", "service_rate", "serviceRate",
                "ad_service_ratio", "adServiceRatio")));
    }

    private static BigDecimal resolveCommissionRate(Map<String, Object> rawPayload) {
        return normalizeRate(asBigDecimal(pickNestedBigDecimalSource(rawPayload,
                "commission_rate", "commissionRate", "招商_提成率", "招商提成率")));
    }

    private static Object pickNestedBigDecimalSource(Map<String, Object> rawPayload, String... keys) {
        Object direct = pick(rawPayload, keys);
        if (direct != null) {
            return direct;
        }
        Object nested = pick(rawPayload, "colonel_order_info", "colonelOrderInfo");
        if (nested instanceof Map<?, ?> map) {
            return pick((Map<String, Object>) map, keys);
        }
        return null;
    }

    private static BigDecimal normalizeRate(BigDecimal raw) {
        if (raw == null || raw.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        BigDecimal abs = raw.abs();
        if (abs.compareTo(BigDecimal.ONE) <= 0) {
            return raw;
        }
        if (abs.compareTo(BigDecimal.valueOf(100)) <= 0) {
            return raw.divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);
        }
        return raw.divide(BigDecimal.valueOf(10000), 8, RoundingMode.HALF_UP);
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static LocalDateTime parseRawDateTime(Map<String, Object> rawPayload, String... keys) {
        Object value = pick(rawPayload, keys);
        return asDateTime(value);
    }

    private static LocalDateTime asDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            long raw = number.longValue();
            return raw > 9_999_999_999L ? AppZone.fromEpochMilli(raw) : AppZone.fromEpochSecond(raw);
        }
        String text = String.valueOf(value).trim();
        if (!DomainText.hasText(text) || "null".equalsIgnoreCase(text)) {
            return null;
        }
        try {
            long raw = Long.parseLong(text);
            return raw > 9_999_999_999L ? AppZone.fromEpochMilli(raw) : AppZone.fromEpochSecond(raw);
        } catch (NumberFormatException ignore) {
            // Fall through to formatted date parsing.
        }
        try {
            return LocalDateTime.parse(text, RAW_DATE_TIME_FORMATTER);
        } catch (DateTimeParseException ignore) {
            return null;
        }
    }

    private static Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = String.valueOf(value).trim();
        if (!DomainText.hasText(text)) {
            return null;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static BigDecimal asBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        String text = String.valueOf(value).trim()
                .replace("%", "")
                .replace("％", "")
                .replace(",", "")
                .replace(" ", "");
        if (!DomainText.hasText(text)) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    static Map<OutputField, List<String>> defaultsAliasConfig() {
        return Collections.emptyMap();
    }
}
