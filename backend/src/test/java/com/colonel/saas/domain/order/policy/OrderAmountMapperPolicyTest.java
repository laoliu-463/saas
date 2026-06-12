package com.colonel.saas.domain.order.policy;

import com.colonel.saas.domain.order.policy.OrderAmountMapperPolicy.AmountWarning;
import com.colonel.saas.domain.order.policy.OrderAmountMapperPolicy.MappedAmounts;
import com.colonel.saas.domain.order.policy.OrderAmountMapperPolicy.OutputField;
import com.colonel.saas.domain.order.policy.OrderAmountMapperPolicy.Track;
import com.colonel.saas.entity.ColonelsettlementOrder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link OrderAmountMapperPolicy} 纯单元测试。
 *
 * <p>覆盖：pending-settlement（estimate>0, effective=0）、settled（effective>0）、
 * 字段缺失、fen 精度、decimal 精度、字段别名、COI/COI2 优先、SETTLEMENT_STRICT 模式、
 * 服务费率 fallback（INSTITUTE only），以及 merge/apply 行为。</p>
 */
class OrderAmountMapperPolicyTest {

    @Nested
    @DisplayName("map() — pending settlement (estimate 轨有值，effective 全 0)")
    class PendingSettlement {

        @Test
        @DisplayName("raw payload 给出 预估服务费 时，estimate_* > 0 且 effective_* = 0")
        void shouldKeepEstimateAndZeroEffective() {
            Map<String, Object> raw = new LinkedHashMap<>();
            raw.put("order_id", "OID-001");
            raw.put("pay_goods_amount", 19900L);
            raw.put("estimated_commission", 1990L);
            raw.put("estimated_tech_service_fee", 100L);

            MappedAmounts result = OrderAmountMapperPolicy.map(raw, null, null, Track.INSTITUTE);

            assertThat(result.payAmount()).isEqualTo(19_900L);
            assertThat(result.estimateServiceFee()).isEqualTo(1_990L);
            assertThat(result.effectiveServiceFee()).isEqualTo(0L);
            assertThat(result.estimateTechServiceFee()).isEqualTo(100L);
            assertThat(result.effectiveTechServiceFee()).isEqualTo(0L);
            assertThat(result.settleAmount()).isEqualTo(19_900L);
            // raw 未提供 settleAmount 别名，INSTITUTE 模式回退到 payAmount，发出 FALLBACK 警告
            assertThat(result.amountWarnings())
                    .filteredOn(w -> w.field() == OutputField.SETTLE_AMOUNT)
                    .extracting(AmountWarning::code)
                    .contains("FALLBACK");
        }

        @Test
        @DisplayName("raw 给出 serviceFeeRate 但未给 commission 时，用费率回填 estimateServiceFee")
        void shouldBackfillEstimateFromRate() {
            Map<String, Object> raw = new LinkedHashMap<>();
            raw.put("order_id", "OID-002");
            raw.put("pay_goods_amount", 20_000L);
            raw.put("service_fee_rate", "0.10");

            MappedAmounts result = OrderAmountMapperPolicy.map(raw, null, null, Track.INSTITUTE);

            assertThat(result.serviceFeeRate()).isEqualByComparingTo(new BigDecimal("0.10"));
            // 20000 * 0.10 = 2000, techServiceFee 缺失视为 0
            assertThat(result.estimateServiceFee()).isEqualTo(2_000L);
            assertThat(result.effectiveServiceFee()).isEqualTo(2_000L); // INSTITUTE 模式下回填
            assertThat(result.rawFieldUsedMap()).containsEntry(OutputField.SERVICE_FEE_RATE, "service_fee_rate");
        }
    }

    @Nested
    @DisplayName("map() — settled (结算轨有 effective 值)")
    class Settled {

        @Test
        @DisplayName("raw 给出 结算服务费 时，effective_* > 0 且 estimate_* 沿用或保留")
        void shouldSetBothEstimateAndEffectiveOnSettledOrder() {
            Map<String, Object> raw = new LinkedHashMap<>();
            raw.put("order_id", "OID-100");
            raw.put("pay_goods_amount", 30_000L);
            raw.put("settled_goods_amount", 28_000L);
            raw.put("settle_colonel_commission", 2_800L);
            raw.put("settle_colonel_tech_service_fee", 80L);

            MappedAmounts result = OrderAmountMapperPolicy.map(raw, null, null, Track.INSTITUTE);

            assertThat(result.payAmount()).isEqualTo(30_000L);
            assertThat(result.settleAmount()).isEqualTo(28_000L);
            assertThat(result.effectiveServiceFee()).isEqualTo(2_800L);
            assertThat(result.estimateServiceFee()).isEqualTo(2_800L); // estimate 缺失时沿用 effective
            assertThat(result.effectiveTechServiceFee()).isEqualTo(80L);
        }

        @Test
        @DisplayName("raw 未给 effective 时，INSTITUTE 模式用 serviceFeeRate 兜底")
        void shouldFallbackEffectiveFromRateWhenMissing() {
            Map<String, Object> raw = new LinkedHashMap<>();
            raw.put("order_id", "OID-101");
            raw.put("pay_goods_amount", 50_000L);
            raw.put("settled_goods_amount", 45_000L);
            raw.put("service_fee_rate", "10");

            MappedAmounts result = OrderAmountMapperPolicy.map(raw, null, null, Track.INSTITUTE);

            assertThat(result.serviceFeeRate()).isEqualByComparingTo(new BigDecimal("0.10"));
            // INSTITUTE: 45000 * 0.10 = 4500, - 0 = 4500
            assertThat(result.effectiveServiceFee()).isEqualTo(4_500L);
        }

        @Test
        @DisplayName("SETTLEMENT_STRICT 模式下 raw 缺 settle 字段时保持 0 并产生警告")
        void strictModeMissingSettleShouldWarn() {
            Map<String, Object> raw = new LinkedHashMap<>();
            raw.put("order_id", "OID-200");
            raw.put("pay_goods_amount", 10_000L);

            MappedAmounts result = OrderAmountMapperPolicy.mapStrictSettlement(raw, null);

            assertThat(result.settleAmount()).isEqualTo(0L);
            assertThat(result.effectiveServiceFee()).isEqualTo(0L);
            assertThat(result.amountWarnings())
                    .extracting(AmountWarning::code)
                    .contains("MISSING");
        }

        @Test
        @DisplayName("SETTLEMENT_STRICT 模式下 raw 给 settle 字段时不回退到 payAmount")
        void strictModeShouldNotFallbackToPay() {
            Map<String, Object> raw = new LinkedHashMap<>();
            raw.put("order_id", "OID-201");
            raw.put("pay_goods_amount", 10_000L);
            raw.put("settled_goods_amount", 9_500L);

            MappedAmounts result = OrderAmountMapperPolicy.mapStrictSettlement(raw, null);

            assertThat(result.settleAmount()).isEqualTo(9_500L);
            assertThat(result.settleAmount()).isNotEqualTo(result.payAmount());
        }
    }

    @Nested
    @DisplayName("map() — 字段缺失 / 异常输入")
    class MissingFields {

        @Test
        @DisplayName("raw 为空 Map：payAmount 兜底为 0，不抛异常")
        void emptyRawShouldReturnZeros() {
            MappedAmounts result = OrderAmountMapperPolicy.map(new HashMap<>(), null);

            assertThat(result.payAmount()).isEqualTo(0L);
            assertThat(result.settleAmount()).isEqualTo(0L);
            assertThat(result.estimateServiceFee()).isEqualTo(0L);
            assertThat(result.effectiveServiceFee()).isEqualTo(0L);
            assertThat(result.estimateTechServiceFee()).isEqualTo(0L);
            assertThat(result.effectiveTechServiceFee()).isEqualTo(0L);
            assertThat(result.serviceFeeRate()).isNull();
            assertThat(result.commissionRate()).isNull();
        }

        @Test
        @DisplayName("raw 为 null：不抛异常，payAmount=0")
        void nullRawShouldReturnZeros() {
            MappedAmounts result = OrderAmountMapperPolicy.map(null, null);

            assertThat(result.payAmount()).isEqualTo(0L);
            assertThat(result.settleAmount()).isEqualTo(0L);
            assertThat(result.amountWarnings()).isNotNull();
        }

        @Test
        @DisplayName("existing 兜底：raw 缺 payAmount 时沿用 existing.orderAmount")
        void shouldFallbackToExistingPayAmount() {
            ColonelsettlementOrder existing = new ColonelsettlementOrder();
            existing.setOrderAmount(8_888L);

            Map<String, Object> raw = new LinkedHashMap<>();
            raw.put("order_id", "OID-300");
            // 没有 payAmount 任何别名

            MappedAmounts result = OrderAmountMapperPolicy.map(raw, existing);

            assertThat(result.payAmount()).isEqualTo(8_888L);
            assertThat(result.amountWarnings())
                    .extracting(AmountWarning::code)
                    .contains("MISSING");
        }
    }

    @Nested
    @DisplayName("map() — 精度 / 单位")
    class Precision {

        @Test
        @DisplayName("raw 字段为分（整数），输出 long 不丢精度")
        void fenPrecisionShouldBePreserved() {
            Map<String, Object> raw = new LinkedHashMap<>();
            raw.put("order_id", "OID-400");
            raw.put("pay_goods_amount", 123_456_789L);
            raw.put("settled_goods_amount", 123_456_000L);

            MappedAmounts result = OrderAmountMapperPolicy.map(raw, null);

            assertThat(result.payAmount()).isEqualTo(123_456_789L);
            assertThat(result.settleAmount()).isEqualTo(123_456_000L);
        }

        @Test
        @DisplayName("serviceFeeRate 接受百分制（≤100）和万分制（≤10000），结果归一化到 0~1")
        void shouldNormalizeRateUnits() {
            Map<String, Object> rawPercent = new LinkedHashMap<>();
            rawPercent.put("pay_goods_amount", 10_000L);
            rawPercent.put("service_fee_rate", "10");
            MappedAmounts percentResult = OrderAmountMapperPolicy.map(rawPercent, null);
            assertThat(percentResult.serviceFeeRate()).isEqualByComparingTo(new BigDecimal("0.10"));

            Map<String, Object> rawBasisPoints = new LinkedHashMap<>();
            rawBasisPoints.put("pay_goods_amount", 10_000L);
            rawBasisPoints.put("service_fee_rate", "1000");
            MappedAmounts bpResult = OrderAmountMapperPolicy.map(rawBasisPoints, null);
            assertThat(bpResult.serviceFeeRate()).isEqualByComparingTo(new BigDecimal("0.10"));
        }

        @Test
        @DisplayName("serviceFeeRate 为 0 或负数时归一化为 null（不参与反推）")
        void shouldReturnNullRateForZeroOrNegative() {
            Map<String, Object> rawZero = new LinkedHashMap<>();
            rawZero.put("pay_goods_amount", 10_000L);
            rawZero.put("service_fee_rate", "0");
            assertThat(OrderAmountMapperPolicy.map(rawZero, null).serviceFeeRate()).isNull();

            Map<String, Object> rawNeg = new LinkedHashMap<>();
            rawNeg.put("pay_goods_amount", 10_000L);
            rawNeg.put("service_fee_rate", "-0.1");
            assertThat(OrderAmountMapperPolicy.map(rawNeg, null).serviceFeeRate()).isNull();
        }
    }

    @Nested
    @DisplayName("map() — 字段别名兼容")
    class FieldAliases {

        @Test
        @DisplayName("payAmount 同时提供多个别名时按顺序命中第一个")
        void shouldPickFirstAvailableAlias() {
            Map<String, Object> raw = new LinkedHashMap<>();
            raw.put("order_id", "OID-500");
            raw.put("orderAmount", 5_000L); // 第二顺位
            raw.put("pay_goods_amount", 6_000L); // 第一顺位

            MappedAmounts result = OrderAmountMapperPolicy.map(raw, null);

            assertThat(result.payAmount()).isEqualTo(6_000L);
            assertThat(result.rawFieldUsedMap())
                    .containsEntry(OutputField.PAY_AMOUNT, "pay_goods_amount");
        }

        @Test
        @DisplayName("payAmount 缺主名但有 orderAmount 别名时命中别名")
        void shouldFallbackToOrderAmountAlias() {
            Map<String, Object> raw = new LinkedHashMap<>();
            raw.put("orderAmount", 7_777L);

            MappedAmounts result = OrderAmountMapperPolicy.map(raw, null);

            assertThat(result.payAmount()).isEqualTo(7_777L);
            assertThat(result.rawFieldUsedMap())
                    .containsEntry(OutputField.PAY_AMOUNT, "orderAmount");
        }

        @Test
        @DisplayName("estimate_service_fee 给字符串数字时仍能解析")
        void shouldParseStringNumerics() {
            Map<String, Object> raw = new LinkedHashMap<>();
            raw.put("pay_goods_amount", "10000");
            raw.put("estimated_commission", "500");

            MappedAmounts result = OrderAmountMapperPolicy.map(raw, null);

            assertThat(result.payAmount()).isEqualTo(10_000L);
            assertThat(result.estimateServiceFee()).isEqualTo(500L);
        }
    }

    @Nested
    @DisplayName("map() — COI / COI2 优先级")
    class InstitutionsPriority {

        @Test
        @DisplayName("顶层 estimate 缺失、colonel_order_info 命中时，estimateServiceFee 来自 COI")
        void shouldPickFromColonelOrderInfo() {
            Map<String, Object> coi = new LinkedHashMap<>();
            coi.put("estimated_commission", 999L);

            Map<String, Object> raw = new LinkedHashMap<>();
            raw.put("pay_goods_amount", 10_000L);
            raw.put("colonel_order_info", coi);

            MappedAmounts result = OrderAmountMapperPolicy.map(raw, null);

            assertThat(result.estimateServiceFee()).isEqualTo(999L);
        }

        @Test
        @DisplayName("顶层与 COI 都缺失、COI2 命中时，effective 来自 COI2")
        void shouldFallbackToColonelOrderInfoSecond() {
            Map<String, Object> coi2 = new LinkedHashMap<>();
            coi2.put("settle_colonel_commission", 1_234L);

            Map<String, Object> raw = new LinkedHashMap<>();
            raw.put("pay_goods_amount", 10_000L);
            raw.put("colonel_order_info_second", coi2);

            MappedAmounts result = OrderAmountMapperPolicy.map(raw, null);

            assertThat(result.effectiveServiceFee()).isEqualTo(1_234L);
        }

        @Test
        @DisplayName("顶层优先级 > COI > COI2")
        void topLevelBeatsInstitutions() {
            Map<String, Object> coi = new LinkedHashMap<>();
            coi.put("estimated_commission", 999L);

            Map<String, Object> coi2 = new LinkedHashMap<>();
            coi2.put("estimated_commission", 555L);

            Map<String, Object> raw = new LinkedHashMap<>();
            raw.put("pay_goods_amount", 10_000L);
            raw.put("estimated_commission", 100L);
            raw.put("colonel_order_info", coi);
            raw.put("colonel_order_info_second", coi2);

            MappedAmounts result = OrderAmountMapperPolicy.map(raw, null);

            assertThat(result.estimateServiceFee()).isEqualTo(100L);
        }
    }

    @Nested
    @DisplayName("merge / apply 行为")
    class MergeAndApply {

        @Test
        @DisplayName("mergeEstimateSnapshot：保留 existing.estimateServiceFee 到 incoming")
        void shouldPreserveEstimateSnapshot() {
            ColonelsettlementOrder existing = new ColonelsettlementOrder();
            existing.setEstimateServiceFee(2_000L);
            existing.setEstimateTechServiceFee(80L);
            existing.setOrderAmount(10_000L);

            ColonelsettlementOrder incoming = new ColonelsettlementOrder();
            incoming.setEstimateServiceFee(0L);
            incoming.setEstimateTechServiceFee(0L);
            incoming.setOrderAmount(0L);

            OrderAmountMapperPolicy.mergeEstimateSnapshot(existing, incoming);

            assertThat(incoming.getEstimateServiceFee()).isEqualTo(2_000L);
            assertThat(incoming.getEstimateTechServiceFee()).isEqualTo(80L);
            assertThat(incoming.getOrderAmount()).isEqualTo(10_000L);
        }

        @Test
        @DisplayName("mergeSettlementSnapshot：incoming 空缺字段沿用 existing 结算轨")
        void shouldPreserveSettlementSnapshot() {
            ColonelsettlementOrder existing = new ColonelsettlementOrder();
            existing.setSettleAmount(9_000L);
            existing.setEffectiveServiceFee(900L);
            existing.setEffectiveTechServiceFee(50L);
            existing.setSettleColonelCommission(900L);
            existing.setSettleColonelTechServiceFee(50L);
            existing.setSettleSecondColonelCommission(200L);

            ColonelsettlementOrder incoming = new ColonelsettlementOrder();
            incoming.setSettleAmount(0L);
            incoming.setEffectiveServiceFee(0L);
            incoming.setEffectiveTechServiceFee(0L);
            incoming.setSettleColonelCommission(0L);
            incoming.setSettleColonelTechServiceFee(0L);
            incoming.setSettleSecondColonelCommission(0L);

            OrderAmountMapperPolicy.mergeSettlementSnapshot(existing, incoming);

            assertThat(incoming.getSettleAmount()).isEqualTo(9_000L);
            assertThat(incoming.getEffectiveServiceFee()).isEqualTo(900L);
            assertThat(incoming.getEffectiveTechServiceFee()).isEqualTo(50L);
            assertThat(incoming.getSettleColonelCommission()).isEqualTo(900L);
            assertThat(incoming.getSettleColonelTechServiceFee()).isEqualTo(50L);
            assertThat(incoming.getSettleSecondColonelCommission()).isEqualTo(200L);
        }

        @Test
        @DisplayName("applyInstituteFactToOrder：PAY_SUCC 待结算不写 settle/effective")
        void applyInstituteFactShouldNotTouchSettleForPaySucc() {
            ColonelsettlementOrder order = new ColonelsettlementOrder();
            order.setSettleAmount(0L);
            order.setEffectiveServiceFee(0L);

            MappedAmounts amounts = new MappedAmounts(
                    10_000L, 0L, 1_000L, 0L, 50L, 0L,
                    new BigDecimal("0.10"), null,
                    List.of(), Map.of());
            Map<String, Object> raw = new LinkedHashMap<>();
            raw.put("flow_point", "PAY_SUCC");
            raw.put("settled_goods_amount", 0L);
            raw.put("colonel_order_info", Map.of("real_commission", 0L, "tech_service_fee", 3L));

            OrderAmountMapperPolicy.applyInstituteFactToOrder(order, amounts, raw);

            assertThat(order.getOrderAmount()).isEqualTo(10_000L);
            assertThat(order.getActualAmount()).isEqualTo(10_000L);
            assertThat(order.getEstimateServiceFee()).isEqualTo(1_000L);
            assertThat(order.getEstimateTechServiceFee()).isEqualTo(50L);
            assertThat(order.getSettleAmount()).isEqualTo(0L);
            assertThat(order.getEffectiveServiceFee()).isEqualTo(0L);
            assertThat(order.getSettleTime()).isNull();
        }

        @Test
        @DisplayName("applyInstituteFactToOrder：6468 SETTLE 已结算写入结算轨")
        void applyInstituteFactShouldWriteSettlementWhenSettled() {
            ColonelsettlementOrder order = new ColonelsettlementOrder();
            MappedAmounts amounts = new MappedAmounts(
                    16_80L, 16_80L, 34L, 30L, 3L, 2L,
                    null, null,
                    List.of(), Map.of());
            Map<String, Object> raw = new LinkedHashMap<>();
            raw.put("flow_point", "SETTLE");
            raw.put("settled_goods_amount", 1680L);
            raw.put("settle_time", "2026-06-10 12:00:00");
            raw.put("colonel_order_info", Map.of(
                    "real_commission", 30L,
                    "settled_tech_service_fee", 2L
            ));

            OrderAmountMapperPolicy.applyInstituteFactToOrder(order, amounts, raw);
            OrderAmountMapperPolicy.applyInstituteSettleTime(order,
                    java.time.LocalDateTime.of(2026, 6, 10, 12, 0));

            assertThat(order.getSettleAmount()).isEqualTo(1680L);
            assertThat(order.getEffectiveServiceFee()).isEqualTo(30L);
            assertThat(order.getEffectiveTechServiceFee()).isEqualTo(2L);
            assertThat(order.getSettleColonelCommission()).isEqualTo(30L);
            assertThat(order.getSettleColonelTechServiceFee()).isEqualTo(2L);
            assertThat(order.getSettleTime()).isNotNull();
        }

        @Test
        @DisplayName("applyInstituteSettlementFromRaw：6468 返回 0/null 不覆盖 DB 已有结算轨")
        void applyInstituteSettlementShouldNotOverwriteExistingWithZeros() {
            ColonelsettlementOrder order = new ColonelsettlementOrder();
            order.setSettleAmount(9000L);
            order.setEffectiveServiceFee(900L);
            order.setSettleTime(java.time.LocalDateTime.of(2026, 5, 1, 10, 0));

            Map<String, Object> raw = new LinkedHashMap<>();
            raw.put("flow_point", "PAY_SUCC");
            raw.put("settled_goods_amount", 0L);
            raw.put("colonel_order_info", Map.of("real_commission", 0L));

            OrderAmountMapperPolicy.applyInstituteFactToOrder(order,
                    new MappedAmounts(10_000L, 0L, 1_000L, 0L, 50L, 0L, null, null, List.of(), Map.of()),
                    raw);

            assertThat(order.getSettleAmount()).isEqualTo(9000L);
            assertThat(order.getEffectiveServiceFee()).isEqualTo(900L);
            assertThat(order.getSettleTime()).isEqualTo(java.time.LocalDateTime.of(2026, 5, 1, 10, 0));
        }

        @Test
        @DisplayName("applyInstituteSettlementFromRaw：DB 结算轨为空时补齐非零结算字段")
        void applyInstituteSettlementShouldFillEmptySettlementTrack() {
            ColonelsettlementOrder order = new ColonelsettlementOrder();
            order.setOrderAmount(1680L);
            order.setEstimateServiceFee(34L);

            Map<String, Object> raw = new LinkedHashMap<>();
            raw.put("settled_goods_amount", 1680L);
            raw.put("colonel_order_info", Map.of("real_commission", 30L));

            OrderAmountMapperPolicy.applyInstituteSettlementFromRaw(order, raw);

            assertThat(order.getSettleAmount()).isEqualTo(1680L);
            assertThat(order.getEffectiveServiceFee()).isEqualTo(30L);
        }

        @Test
        @DisplayName("applyToOrder：写完整双轨 + 旧字段 settleColonelCommission/settleColonelTechServiceFee")
        void applyToOrderShouldWriteFullTrackAndLegacyFields() {
            ColonelsettlementOrder order = new ColonelsettlementOrder();

            MappedAmounts amounts = new MappedAmounts(
                    10_000L, 9_500L, 1_000L, 950L, 50L, 40L,
                    new BigDecimal("0.10"), null,
                    List.of(), Map.of());

            OrderAmountMapperPolicy.applyToOrder(order, amounts);

            assertThat(order.getOrderAmount()).isEqualTo(10_000L);
            assertThat(order.getActualAmount()).isEqualTo(9_500L);
            assertThat(order.getSettleAmount()).isEqualTo(9_500L);
            assertThat(order.getEstimateServiceFee()).isEqualTo(1_000L);
            assertThat(order.getEffectiveServiceFee()).isEqualTo(950L);
            assertThat(order.getEstimateTechServiceFee()).isEqualTo(50L);
            assertThat(order.getEffectiveTechServiceFee()).isEqualTo(40L);
            assertThat(order.getSettleColonelCommission()).isEqualTo(950L);
            assertThat(order.getSettleColonelTechServiceFee()).isEqualTo(40L);
        }
    }

    @Nested
    @DisplayName("输出契约：amountWarnings 与 rawFieldUsedMap")
    class OutputContract {

        @Test
        @DisplayName("MISSING 警告：raw 缺关键字段时进入 amountWarnings")
        void shouldWarnOnMissingPay() {
            Map<String, Object> raw = new LinkedHashMap<>();
            raw.put("order_id", "OID-900");

            MappedAmounts result = OrderAmountMapperPolicy.map(raw, null);

            assertThat(result.amountWarnings())
                    .filteredOn(w -> w.field() == OutputField.PAY_AMOUNT)
                    .extracting(AmountWarning::code)
                    .contains("MISSING");
        }

        @Test
        @DisplayName("rawFieldUsedMap：命中的字段进入 Map，未命中则不在")
        void rawFieldUsedMapShouldReflectWins() {
            Map<String, Object> raw = new LinkedHashMap<>();
            raw.put("pay_goods_amount", 10_000L);
            raw.put("estimated_commission", 500L);

            MappedAmounts result = OrderAmountMapperPolicy.map(raw, null);

            assertThat(result.rawFieldUsedMap())
                    .containsEntry(OutputField.PAY_AMOUNT, "pay_goods_amount");
            assertThat(result.rawFieldUsedMap())
                    .doesNotContainKey(OutputField.COMMISSION_RATE);
        }
    }

    @Nested
    @DisplayName("BANS：禁止业务推导")
    class BusinessDerivationBans {

        @Test
        @DisplayName("Policy 不计算 serviceFeeExpense / 招商提成 / 渠道提成 / 毛利")
        void shouldNotComputeDerivedBusiness() {
            Map<String, Object> raw = new LinkedHashMap<>();
            raw.put("pay_goods_amount", 10_000L);
            raw.put("service_fee_rate", "0.10");

            MappedAmounts result = OrderAmountMapperPolicy.map(raw, null);

            // 只输出 10 个任务清单字段，serviceFeeExpense 不在产物中
            // 验证：MappedAmounts 记录字段数 = 10
            assertThat(MappedAmounts.class.getRecordComponents()).hasSize(10);
            // serviceFeeRate 归一化
            assertThat(result.serviceFeeRate()).isEqualByComparingTo(new BigDecimal("0.10"));
        }
    }
}
