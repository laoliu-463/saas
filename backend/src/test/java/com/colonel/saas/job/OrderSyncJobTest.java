package com.colonel.saas.job;

import com.colonel.saas.service.OrderSyncService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderSyncJobTest {

    @Mock
    private OrderSyncService orderSyncService;

    @Test
    void syncOrders_shouldSkipWhenLocked() {
        OrderSyncJob job = new OrderSyncJob(orderSyncService);
        when(orderSyncService.syncLatestWindow()).thenReturn(
                new OrderSyncService.SyncResult(0, 0, 0, 0, 0, true)
        );

        job.syncOrders();

        verify(orderSyncService).syncLatestWindow();
    }

    @Test
    void syncOrders_shouldLogResultWhenNotLocked() {
        OrderSyncJob job = new OrderSyncJob(orderSyncService);
        when(orderSyncService.syncLatestWindow()).thenReturn(
                new OrderSyncService.SyncResult(1000L, 2000L, 5, 10, 2, false)
        );

        job.syncOrders();

        verify(orderSyncService).syncLatestWindow();
    }

    @Test
    void syncOrders_shouldCatchException() {
        OrderSyncJob job = new OrderSyncJob(orderSyncService);
        when(orderSyncService.syncLatestWindow()).thenThrow(new RuntimeException("sync failed"));

        job.syncOrders();

        verify(orderSyncService).syncLatestWindow();
    }

    @Test
    void syncOrders_shouldSkipWhenDisabled() {
        OrderSyncJob job = new OrderSyncJob(orderSyncService);
        ReflectionTestUtils.setField(job, "enabled", false);

        job.syncOrders();

        verifyNoInteractions(orderSyncService);
    }
}
