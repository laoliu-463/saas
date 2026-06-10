package com.colonel.saas.job;

import com.colonel.saas.domain.order.application.OrderSyncApplicationService;
import com.colonel.saas.domain.order.application.OrderSyncCommand;
import com.colonel.saas.domain.order.application.OrderSyncExecutionContext;
import com.colonel.saas.domain.order.application.OrderSyncResult;
import com.colonel.saas.service.OrderSyncService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderSyncJobTest {

    private com.colonel.saas.job.OrderSyncJob newJob() {
        lenient().when(orderSyncApplicationService.isRoutingEnabled()).thenReturn(false);
        return new com.colonel.saas.job.OrderSyncJob(orderSyncService, orderSyncApplicationService);
    }

    @Mock
    private OrderSyncService orderSyncService;

    @Mock
    private OrderSyncApplicationService orderSyncApplicationService;

    @Test
    void syncOrders_shouldSkipWhenLocked() {
        com.colonel.saas.job.OrderSyncJob job = newJob();
        when(orderSyncService.syncLatestWindow()).thenReturn(
                new OrderSyncService.SyncResult(0, 0, 0, 0, 0, true)
        );

        job.syncOrders();

        verify(orderSyncService).syncLatestWindow();
    }

    @Test
    void syncOrders_shouldLogResultWhenNotLocked() {
        com.colonel.saas.job.OrderSyncJob job = newJob();
        when(orderSyncService.syncLatestWindow()).thenReturn(
                new OrderSyncService.SyncResult(1000L, 2000L, 5, 10, 2, false)
        );

        job.syncOrders();

        verify(orderSyncService).syncLatestWindow();
    }

    @Test
    void syncOrders_shouldRethrowException() {
        com.colonel.saas.job.OrderSyncJob job = newJob();
        when(orderSyncService.syncLatestWindow()).thenThrow(new RuntimeException("sync failed"));

        assertThatThrownBy(job::syncOrders)
                .isInstanceOf(RuntimeException.class)
                .hasMessage("sync failed");

        verify(orderSyncService).syncLatestWindow();
    }

    @Test
    void syncOrders_shouldSkipWhenDisabled() {
        com.colonel.saas.job.OrderSyncJob job = newJob();
        ReflectionTestUtils.setField(job, "enabled", false);

        job.syncOrders();

        verifyNoInteractions(orderSyncService);
    }

    @Test
    void syncPayRecent_shouldInvokePayRecentWindowOnDedicatedMethod() {
        com.colonel.saas.job.OrderSyncJob job = newJob();
        when(orderSyncService.syncPayRecentWindow()).thenReturn(
                new OrderSyncService.SyncResult(1000L, 22600L, 1, 5, 2, 0, 3, 1, 0, false)
        );

        job.syncPayRecent();

        // PAY_RECENT delegates to syncPayRecentWindow, never the default syncLatestWindow.
        verify(orderSyncService).syncPayRecentWindow();
    }

    @Test
    void syncPayRecent_shouldSkipWhenDisabled() {
        com.colonel.saas.job.OrderSyncJob job = newJob();
        ReflectionTestUtils.setField(job, "payRecentEnabled", false);

        job.syncPayRecent();

        // No interaction with the underlying service when disabled.
        verifyNoInteractions(orderSyncService);
    }

    @Test
    void syncPayRecent_shouldSkipWhenLocked() {
        com.colonel.saas.job.OrderSyncJob job = newJob();
        when(orderSyncService.syncPayRecentWindow()).thenReturn(
                new OrderSyncService.SyncResult(0, 0, 0, 0, 0, true)
        );

        job.syncPayRecent();

        verify(orderSyncService).syncPayRecentWindow();
    }

    @Test
    void syncPayRecent_shouldRethrowException() {
        com.colonel.saas.job.OrderSyncJob job = newJob();
        when(orderSyncService.syncPayRecentWindow()).thenThrow(new RuntimeException("pay-recent failed"));

        assertThatThrownBy(job::syncPayRecent)
                .isInstanceOf(RuntimeException.class)
                .hasMessage("pay-recent failed");

        verify(orderSyncService).syncPayRecentWindow();
    }

    @Test
    void syncInstituteOrdersRecent_shouldInvokeInstituteRecentWindow() {
        com.colonel.saas.job.OrderSyncJob job = newJob();
        when(orderSyncService.syncInstituteOrdersRecentWindow()).thenReturn(
                new OrderSyncService.SyncResult(1000L, 22600L, 1, 5, 2, 0, 3, 1, 0, false)
        );

        job.syncInstituteOrdersRecent();

        verify(orderSyncService).syncInstituteOrdersRecentWindow();
    }

    @Test
    void syncInstituteOrdersRecent_shouldSkipWhenDisabled() {
        com.colonel.saas.job.OrderSyncJob job = newJob();
        ReflectionTestUtils.setField(job, "instituteRecentEnabled", false);

        job.syncInstituteOrdersRecent();

        verifyNoInteractions(orderSyncService);
    }

    @Test
    void syncInstituteOrdersRecent_shouldSkipWhenLocked() {
        com.colonel.saas.job.OrderSyncJob job = newJob();
        when(orderSyncService.syncInstituteOrdersRecentWindow()).thenReturn(
                new OrderSyncService.SyncResult(0, 0, 0, 0, 0, true)
        );

        job.syncInstituteOrdersRecent();

        verify(orderSyncService).syncInstituteOrdersRecentWindow();
    }

    @Test
    void syncInstituteOrdersRecent_shouldRethrowException() {
        com.colonel.saas.job.OrderSyncJob job = newJob();
        when(orderSyncService.syncInstituteOrdersRecentWindow()).thenThrow(new RuntimeException("institute failed"));

        assertThatThrownBy(job::syncInstituteOrdersRecent)
                .isInstanceOf(RuntimeException.class)
                .hasMessage("institute failed");

        verify(orderSyncService).syncInstituteOrdersRecentWindow();
    }

    @Test
    void syncInstituteFullBackfill_shouldInvokeFullBackfillWindow() {
        com.colonel.saas.job.OrderSyncJob job = newJob();
        ReflectionTestUtils.setField(job, "instituteBackfillEnabled", true);
        when(orderSyncService.syncInstituteFullBackfillWindow()).thenReturn(
                new OrderSyncService.SyncResult(1000L, 22600L, 1, 5, 2, 0, 3, 1, 0, false)
        );

        job.syncInstituteFullBackfill();

        verify(orderSyncService).syncInstituteFullBackfillWindow();
    }

    @Test
    void syncInstituteOrdersHot_shouldInvokeHotRecentMethod() {
        com.colonel.saas.job.OrderSyncJob job = newJob();
        when(orderSyncService.syncInstituteOrdersHotRecent()).thenReturn(
                new OrderSyncService.SyncResult(1000L, 22600L, 1, 5, 2, 0, 3, 1, 0, false)
        );

        job.syncInstituteOrdersHot();

        verify(orderSyncService).syncInstituteOrdersHotRecent();
    }

    @Test
    void syncInstituteOrdersHot_shouldSkipWhenDisabled() {
        com.colonel.saas.job.OrderSyncJob job = newJob();
        ReflectionTestUtils.setField(job, "instituteHotEnabled", false);

        job.syncInstituteOrdersHot();

        verifyNoInteractions(orderSyncService);
    }

    @Test
    void syncInstituteOrdersHot_shouldSkipWhenLocked() {
        com.colonel.saas.job.OrderSyncJob job = newJob();
        when(orderSyncService.syncInstituteOrdersHotRecent()).thenReturn(
                new OrderSyncService.SyncResult(0, 0, 0, 0, 0, true)
        );

        job.syncInstituteOrdersHot();

        verify(orderSyncService).syncInstituteOrdersHotRecent();
    }

    @Test
    void syncInstituteOrdersHot_shouldRethrowException() {
        com.colonel.saas.job.OrderSyncJob job = newJob();
        when(orderSyncService.syncInstituteOrdersHotRecent()).thenThrow(new RuntimeException("hot failed"));

        assertThatThrownBy(job::syncInstituteOrdersHot)
                .isInstanceOf(RuntimeException.class)
                .hasMessage("hot failed");

        verify(orderSyncService).syncInstituteOrdersHotRecent();
    }

    @Test
    void syncInstituteFullBackfill_shouldSkipWhenDisabled() {
        com.colonel.saas.job.OrderSyncJob job = newJob();
        ReflectionTestUtils.setField(job, "instituteBackfillEnabled", false);

        job.syncInstituteFullBackfill();

        verifyNoInteractions(orderSyncService);
    }

    @Test
    void syncSettlementSettle_shouldInvokeSettleWindow() {
        com.colonel.saas.job.OrderSyncJob job = newJob();
        when(orderSyncService.syncSettlementSettleWindow()).thenReturn(
                new OrderSyncService.SyncResult(1000L, 2000L, 0, 0, 0, 0, 0, 0, 0, false, 0, "EMPTY_PAGE")
        );

        job.syncSettlementSettle();

        verify(orderSyncService).syncSettlementSettleWindow();
    }

    @Test
    void syncSettlementSettle_shouldSkipWhenDisabled() {
        com.colonel.saas.job.OrderSyncJob job = newJob();
        ReflectionTestUtils.setField(job, "settleSyncEnabled", false);

        job.syncSettlementSettle();

        verifyNoInteractions(orderSyncService);
    }

    @Test
    void syncOrders_whenRoutingEnabled_shouldUseApplicationService() {
        when(orderSyncApplicationService.isRoutingEnabled()).thenReturn(true);
        OrderSyncJob job = new OrderSyncJob(orderSyncService, orderSyncApplicationService);
        OrderSyncResult applicationResult = OrderSyncResult.fromLegacy(
                new OrderSyncService.SyncResult(1000L, 2000L, 1, 5, 2, 0, 3, 1, 0, false),
                900L,
                2000L,
                50L);
        when(orderSyncApplicationService.execute(
                eq(OrderSyncCommand.scheduledIncremental()),
                eq(OrderSyncExecutionContext.scheduled(OrderSyncExecutionContext.TASK_INCREMENTAL))))
                .thenReturn(applicationResult);

        job.syncOrders();

        verify(orderSyncApplicationService).execute(any(), any());
        verify(orderSyncService, never()).syncLatestWindow();
    }
}
