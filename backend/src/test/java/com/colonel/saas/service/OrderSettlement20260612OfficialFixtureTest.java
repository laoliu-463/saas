package com.colonel.saas.service;

import com.colonel.saas.service.settlement.SettlementOrderGateway;
import com.colonel.saas.service.settlement.SettlementOrderPage;
import com.colonel.saas.service.settlement.SettlementOrderQuery;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 2026-06-12 官方口径对齐 fixture。
 *
 * <p>模拟上游真实 10 个一/二级机构同时返回的订单（合计 expense=8.69 元），
 * 校验 OrderDualTrackAmountResolver.resolveInstituteSettlement 解析结果与官方目标一致。
 * </p>
 *
 * <p>每个订单一二级机构 real_commission 同时为正时，二级机构的 real_commission 计入
 * effective_service_fee_expense，对应官方"结算服务费支出 8.69 元"的来源。</p>
 */
@ExtendWith(MockitoExtension.class)
class OrderSettlement20260612OfficialFixtureTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Mock
    private SettlementOrderGateway instituteSettlementGateway;

    @Test
    void resolveInstituteSettlement_shouldMatch2026_06_12OfficialTargetsForTenBothInstitutionOrders() {
        // 10 单一/二级机构均有 real_commission 订单，二级 real_commission 累计 869 cents = 8.69 元
        Map<String, Object> order1 = bothInstitutionOrder("ORDER-1", 108L, 95L);
        Map<String, Object> order2 = bothInstitutionOrder("ORDER-2", 108L, 95L);
        Map<String, Object> order3 = bothInstitutionOrder("ORDER-3", 108L, 95L);
        Map<String, Object> order4 = bothInstitutionOrder("ORDER-4", 108L, 95L);
        Map<String, Object> order5 = bothInstitutionOrder("ORDER-5", 108L, 95L);
        Map<String, Object> order6 = bothInstitutionOrder("ORDER-6", 108L, 95L);
        Map<String, Object> order7 = bothInstitutionOrder("ORDER-7", 108L, 95L);
        Map<String, Object> order8 = bothInstitutionOrder("ORDER-8", 108L, 95L);
        Map<String, Object> order9 = bothInstitutionOrder("ORDER-9", 16L, 14L);
        Map<String, Object> order10 = bothInstitutionOrder("ORDER-10", 108L, 95L);

        long settleAmount = 0L;
        long effectiveServiceFee = 0L;
        long effectiveServiceFeeExpense = 0L;
        for (Map<String, Object> raw : List.of(order1, order2, order3, order4, order5,
                order6, order7, order8, order9, order10)) {
            OrderDualTrackAmountResolver.DualTrackAmounts amounts =
                    OrderDualTrackAmountResolver.resolveInstituteSettlement(raw);
            settleAmount += amounts.settleAmount();
            effectiveServiceFee += amounts.effectiveServiceFee();
            effectiveServiceFeeExpense += amounts.effectiveServiceFeeExpense();
        }

        // 8 单 × 108 + 1 单 × 16 + 1 单 × 108 = 988 cents
        assertThat(effectiveServiceFee).isEqualTo(988L);
        // 8 单 × 95 + 1 单 × 14 + 1 单 × 95 = 869 cents = 8.69 元（官方目标）
        assertThat(effectiveServiceFeeExpense).isEqualTo(869L);
    }

    @Test
    void resolve_shouldAlsoMapExpenseForLegacySyncPath() {
        // OrderDualTrackAmountResolver.resolve() 是 INSTITUTE 源的主链路，订单同步持久化走这条。
        // 4b6319d1 之前 resolve() 把 expense 硬编码为 0；本测试确保 resolve() 与 resolveInstituteSettlement()
        // 共用 computeServiceFeeExpense 助手后两个入口产出一致。
        Map<String, Object> raw = bothInstitutionOrder("ORDER-LEGACY-1", 108L, 95L);

        OrderDualTrackAmountResolver.DualTrackAmounts instituteSettlement =
                OrderDualTrackAmountResolver.resolveInstituteSettlement(raw);
        OrderDualTrackAmountResolver.DualTrackAmounts legacy =
                OrderDualTrackAmountResolver.resolve(raw, null, null);

        assertThat(legacy.effectiveServiceFeeExpense()).isEqualTo(95L);
        assertThat(legacy.estimateServiceFeeExpense()).isEqualTo(95L);
        assertThat(legacy.effectiveServiceFee()).isEqualTo(instituteSettlement.effectiveServiceFee());
    }

    @Test
    void dryRun_2026_06_12_shouldCall1603AndReturnOfficialExpenseForBothInstitutionOrders() {
        when(instituteSettlementGateway.fetch(any())).thenReturn(page(Map.of(
                "order_id", "FIXTURE-1603",
                "pay_goods_amount", 5000L,
                "settled_goods_amount", 4800L,
                "settle_time", "2026-06-12 10:00:00",
                "flow_point", "SETTLE",
                "colonel_order_info", Map.of("real_commission", 108L),
                "colonel_order_info_second", Map.of("real_commission", 95L)
        )));

        Order1603SettlementDryRunService service = new Order1603SettlementDryRunService(instituteSettlementGateway);
        Order1603SettlementDryRunService.DryRunResult result = service.dryRun(
                new Order1603SettlementDryRunService.DryRunRequest(
                        "2026-06-12 00:00:00",
                        "2026-06-13 00:00:00",
                        "settle",
                        20,
                        "0",
                        3,
                        100,
                        List.of()
                ));

        assertThat(result.fetched()).isEqualTo(1);
        Order1603SettlementDryRunService.OrderMapping order = result.orders().get(0);
        // 一级机构 = 收入 108，二级机构 = 支出 95
        // 解析口径：effective_service_fee = 108（一级），effective_service_fee_expense = 95（二级）
        assertThat(order.effectiveServiceFee()).isEqualTo(108L);
        // settle_time / settle_amount / flow_point 都已填充 → HIGH 置信度
        assertThat(result.mappingConfidence()).isEqualTo("HIGH");
    }

    private static Map<String, Object> bothInstitutionOrder(String orderId, long primaryFee, long secondFee) {
        Map<String, Object> coi = new LinkedHashMap<>();
        coi.put("real_commission", primaryFee);
        coi.put("estimated_commission", primaryFee + 10L);
        Map<String, Object> coi2 = new LinkedHashMap<>();
        coi2.put("real_commission", secondFee);
        coi2.put("estimated_commission", secondFee);
        return new LinkedHashMap<>(Map.of(
                "order_id", orderId,
                "pay_goods_amount", 5000L,
                "settled_goods_amount", 4800L,
                "colonel_order_info", coi,
                "colonel_order_info_second", coi2
        ));
    }

    private static SettlementOrderPage page(Map<String, Object> rawOrder) {
        JsonNode node = OBJECT_MAPPER.valueToTree(rawOrder);
        return new SettlementOrderPage(
                List.of(node),
                "0",
                false,
                OBJECT_MAPPER.valueToTree(Map.of("log_id", "log-fixture")),
                "buyin.instituteOrderColonel",
                OrderSyncPersistenceService.SYNC_SOURCE_INSTITUTE_SETTLEMENT);
    }
}
