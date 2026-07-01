package com.colonel.saas.domain.performance.application;

import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.domain.order.facade.OrderReadFacade;
import com.colonel.saas.dto.performance.PerformanceRecalculateMonthResponse;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.entity.PerformanceRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * PerformanceMonthRecalculationApplicationService 直接行为验证（DDD-PERFORMANCE Slice 5）。
 *
 * <p>核心目标：验证 Application 层独立持有完整的月度重算逻辑，
 * 不依赖 Service 中转。Service 层委派是 thin shell，
 * 真实业务逻辑（月份解析 / 时间区间 / 逐笔重算 / 容错 / 响应组装）必须由本测试覆盖。</p>
 */
@ExtendWith(MockitoExtension.class)
class PerformanceMonthRecalculationApplicationServiceTest {

    @Mock
    private OrderReadFacade orderReadFacade;

    @Mock
    private PerformanceCalculationApplicationService performanceCalculationApplicationService;

    private PerformanceMonthRecalculationApplicationService applicationService;

    @BeforeEach
    void setUp() {
        applicationService = new PerformanceMonthRecalculationApplicationService(
                orderReadFacade, performanceCalculationApplicationService);
    }

    @Test
    void recalculateMonth_shouldRejectBlankMonth() {
        assertThatThrownBy(() -> applicationService.recalculateMonth(" ", "重算五月"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("month 不能为空");

        verifyNoInteractions(orderReadFacade, performanceCalculationApplicationService);
    }

    @Test
    void recalculateMonth_shouldRejectBlankReason() {
        assertThatThrownBy(() -> applicationService.recalculateMonth("2026-05", " "))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("reason 不能为空");

        verifyNoInteractions(orderReadFacade, performanceCalculationApplicationService);
    }

    @Test
    void recalculateMonth_shouldRejectInvalidMonthFormat() {
        assertThatThrownBy(() -> applicationService.recalculateMonth("2026/05", "重算五月"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("month 格式应为 yyyy-MM");

        verifyNoInteractions(orderReadFacade, performanceCalculationApplicationService);
    }

    @Test
    void recalculateMonth_shouldCountUpsertedSkippedAndContinueAfterSingleOrderFailure() {
        ColonelsettlementOrder upserted = order("O-1", null);
        ColonelsettlementOrder settled = order("O-2", LocalDateTime.of(2026, 5, 6, 10, 0));
        ColonelsettlementOrder failed = order("O-3", null);
        ColonelsettlementOrder ignored = order("O-4", null);
        when(orderReadFacade.findUnsettledOrdersByCreateTimeRange(any(), any(), eq(2000)))
                .thenReturn(List.of(upserted, settled, failed, ignored));
        when(performanceCalculationApplicationService.upsertFromOrder(upserted)).thenReturn(new PerformanceRecord());
        when(performanceCalculationApplicationService.upsertFromOrder(failed)).thenThrow(new IllegalStateException("boom"));
        when(performanceCalculationApplicationService.upsertFromOrder(ignored)).thenReturn(null);

        PerformanceRecalculateMonthResponse response = applicationService.recalculateMonth(" 2026-05 ", "重算五月");

        assertThat(response.getJobId()).isNotBlank();
        assertThat(response.getStatus()).isEqualTo("SUBMITTED");
        assertThat(response.getMonth()).isEqualTo("2026-05");
        assertThat(response.getScanned()).isEqualTo(4);
        assertThat(response.getUpserted()).isEqualTo(1);
        assertThat(response.getSkippedSettled()).isEqualTo(1);
        verify(performanceCalculationApplicationService).upsertFromOrder(upserted);
        verify(performanceCalculationApplicationService, never()).upsertFromOrder(settled);
        verify(performanceCalculationApplicationService).upsertFromOrder(failed);
        verify(performanceCalculationApplicationService).upsertFromOrder(ignored);
    }

    private static ColonelsettlementOrder order(String orderId, LocalDateTime settleTime) {
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setOrderId(orderId);
        order.setSettleTime(settleTime);
        return order;
    }
}