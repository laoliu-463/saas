package com.colonel.saas.service;

import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.gateway.douyin.DouyinOrderGateway;
import com.colonel.saas.mapper.ColonelsettlementOrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class Order6468PaginationDryRunServiceTest {

    @Mock
    private DouyinOrderGateway douyinOrderGateway;

    @Mock
    private ColonelsettlementOrderMapper orderMapper;

    private Order6468PaginationDryRunService service;

    @BeforeEach
    void setUp() {
        service = new Order6468PaginationDryRunService(douyinOrderGateway, orderMapper);
    }

    @Test
    void dryRun_shouldContinueWhenHasMoreFalseBut6468CursorExists() {
        when(douyinOrderGateway.listInstituteOrders(any()))
                .thenReturn(
                        page(List.of(order("ORDER-1", "PAY_SUCC", 1000L, 20L, 2L, "2026-06-03 12:00:00")), false, "cursor-2"),
                        page(List.of(order("ORDER-2", "REFUND", 2000L, 40L, 4L, "2026-06-03 13:00:00")), false, "0")
                );
        when(orderMapper.selectList(any())).thenReturn(List.of());

        Order6468PaginationDryRunService.DryRunResult result = service.dryRun(request());

        assertThat(result.readOnly()).isTrue();
        assertThat(result.pagesFetched()).isEqualTo(2);
        assertThat(result.rawOrderRows()).isEqualTo(2);
        assertThat(result.uniqueOrders()).isEqualTo(2);
        assertThat(result.stopReason()).isEqualTo("NO_NEXT_CURSOR");
        assertThat(result.candidates().get("ALL_RAW").orderCount()).isEqualTo(2);
        assertThat(result.candidates().get("PAY_SUCC").orderCount()).isEqualTo(1);
        assertThat(result.candidates().get("NON_REFUND_NON_CANCELLED").orderCount()).isEqualTo(1);
        assertThat(result.candidates().get("PAY_TIME_RANGE").orderAmountCent()).isEqualTo(3000L);
        assertThat(result.candidates().get("ALL_RAW").serviceFeeIncomeCent()).isEqualTo(60L);
        assertThat(result.candidates().get("ALL_RAW").techServiceFeeCent()).isEqualTo(6L);

        ArgumentCaptor<DouyinOrderGateway.DouyinOrderQueryRequest> requestCaptor =
                ArgumentCaptor.forClass(DouyinOrderGateway.DouyinOrderQueryRequest.class);
        verify(douyinOrderGateway, org.mockito.Mockito.times(2)).listInstituteOrders(requestCaptor.capture());
        assertThat(requestCaptor.getAllValues().get(0).cursor()).isEqualTo("0");
        assertThat(requestCaptor.getAllValues().get(1).cursor()).isEqualTo("cursor-2");
        verify(orderMapper, never()).insert(any());
        verify(orderMapper, never()).insertIgnoreByOrderId(any());
        verify(orderMapper, never()).updateSyncedById(any());
    }

    @Test
    void dryRun_shouldStopWhenCursorRepeatsAndReportNewCandidatesAfterLocalDedup() {
        when(douyinOrderGateway.listInstituteOrders(any()))
                .thenReturn(
                        page(List.of(order("ORDER-1", "PAY_SUCC", 1000L, 20L, 2L, "2026-06-03 12:00:00")), false, "cursor-2"),
                        page(List.of(order("ORDER-EXISTING", "PAY_SUCC", 3000L, 60L, 6L, "2026-06-03 13:00:00")), false, "cursor-2")
                );
        ColonelsettlementOrder existing = new ColonelsettlementOrder();
        existing.setOrderId("ORDER-EXISTING");
        when(orderMapper.selectList(any())).thenReturn(List.of(existing));

        Order6468PaginationDryRunService.DryRunResult result = service.dryRun(request());

        assertThat(result.pagesFetched()).isEqualTo(2);
        assertThat(result.stopReason()).isEqualTo("REPEATED_CURSOR");
        assertThat(result.warnings()).anyMatch(warning -> warning.contains("cursor-2"));
        assertThat(result.candidates().get("ALL_RAW").orderCount()).isEqualTo(2);
        assertThat(result.candidates().get("NEW_AFTER_LOCAL_DEDUP").orderCount()).isEqualTo(1);
        assertThat(result.candidates().get("NEW_AFTER_LOCAL_DEDUP").orderAmountCent()).isEqualTo(1000L);
    }

    private Order6468PaginationDryRunService.DryRunRequest request() {
        return new Order6468PaginationDryRunService.DryRunRequest(
                1780459200L,
                1780549200L,
                100,
                10,
                50000,
                1780459200L,
                1780549200L
        );
    }

    private DouyinOrderGateway.OrderListResult page(
            List<DouyinOrderGateway.DouyinOrderItem> orders,
            boolean hasMore,
            String nextCursor) {
        return new DouyinOrderGateway.OrderListResult(orders, hasMore, nextCursor, Map.of());
    }

    private DouyinOrderGateway.DouyinOrderItem order(
            String orderId,
            String flowPoint,
            long amountCent,
            long serviceFeeCent,
            long techFeeCent,
            String payTime) {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("order_id", orderId);
        raw.put("flow_point", flowPoint);
        raw.put("pay_goods_amount", amountCent);
        raw.put("pay_success_time", payTime);
        raw.put("create_time", payTime);
        raw.put("update_time", payTime);
        raw.put("colonel_order_info", Map.of(
                "estimated_commission", serviceFeeCent,
                "tech_service_fee", techFeeCent
        ));
        return new DouyinOrderGateway.DouyinOrderItem(
                orderId,
                "PRODUCT-" + orderId,
                "PRODUCT-" + orderId,
                "SHOP-1",
                "shop",
                "TALENT-1",
                "talent",
                null,
                amountCent,
                serviceFeeCent,
                null,
                1780473600L,
                null,
                raw
        );
    }
}
