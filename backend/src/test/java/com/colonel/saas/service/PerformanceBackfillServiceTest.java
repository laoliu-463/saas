package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.entity.PerformanceRecord;
import com.colonel.saas.mapper.ColonelsettlementOrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PerformanceBackfillServiceTest {

    @Mock
    private ColonelsettlementOrderMapper orderMapper;
    @Mock
    private PerformanceCalculationService performanceCalculationService;

    private PerformanceBackfillService service;

    @BeforeEach
    void setUp() {
        service = new PerformanceBackfillService(orderMapper, performanceCalculationService);
    }

    @Test
    void backfill_shouldUpsertByOrderIds() {
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setOrderId("order-1");
        when(orderMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(order));
        when(performanceCalculationService.upsertFromOrder(order)).thenReturn(new PerformanceRecord());

        PerformanceBackfillService.BackfillResult result = service.backfill(
                List.of("order-1"),
                null,
                null,
                null,
                true);

        assertThat(result.scanned()).isEqualTo(1);
        assertThat(result.upserted()).isEqualTo(1);
        assertThat(result.failed()).isZero();
        verify(performanceCalculationService).upsertFromOrder(order);
    }

    @Test
    void backfill_shouldScanMissingOrdersWhenOrderIdsEmpty() {
        when(orderMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        PerformanceBackfillService.BackfillResult result = service.backfill(
                null,
                null,
                null,
                100,
                true);

        assertThat(result.scanned()).isZero();
        verify(orderMapper).selectList(any(LambdaQueryWrapper.class));
        verify(performanceCalculationService, never()).upsertFromOrder(any());
    }
}
