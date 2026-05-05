package com.colonel.saas.job;

import com.colonel.saas.service.OrderSyncService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderSyncJobTest {

    private com.colonel.saas.job.OrderSyncJob newJob() {
        return new com.colonel.saas.job.OrderSyncJob(orderSyncService);
    }

    @Mock
    private OrderSyncService orderSyncService;

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
}
