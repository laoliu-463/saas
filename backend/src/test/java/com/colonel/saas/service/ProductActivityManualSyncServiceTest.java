package com.colonel.saas.service;

import com.colonel.saas.gateway.douyin.DouyinProductGateway;
import com.colonel.saas.mapper.ColonelsettlementActivityMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductActivityManualSyncServiceTest {

    @Mock
    private ProductService productService;
    @Mock
    private ColonelsettlementActivityService colonelActivityService;
    @Mock
    private ColonelsettlementActivityMapper activityMapper;

    private void stubCompleteRefresh() {
        when(productService.refreshActivitySnapshots(any()))
                .thenReturn(new ProductService.ActivityProductRefreshResult(3, 1, 1, 2, 0));
    }

    @Test
    void trigger_shouldReturnAcceptedAndRunRefreshInBackgroundExecutor() {
        stubCompleteRefresh();
        ProductActivityManualSyncService service = new ProductActivityManualSyncService(
                productService,
                colonelActivityService,
                activityMapper,
                Runnable::run);

        ProductActivityManualSyncService.SyncTriggerResult result = service.trigger(" ACT-1 ", null);

        assertThat(result.activityId()).isEqualTo("ACT-1");
        assertThat(result.syncStatus()).isEqualTo("ACCEPTED");
        verify(colonelActivityService).syncActivitySummaryFromUpstream("ACT-1", null);
        ArgumentCaptor<DouyinProductGateway.ActivityProductQueryRequest> captor =
                ArgumentCaptor.forClass(DouyinProductGateway.ActivityProductQueryRequest.class);
        verify(productService).refreshActivitySnapshots(captor.capture());
        assertThat(captor.getValue().activityId()).isEqualTo("ACT-1");
        assertThat(captor.getValue().count()).isEqualTo(20);
        verify(activityMapper).touchLastSyncAt(eq("ACT-1"), any(LocalDateTime.class));
    }

    @Test
    void trigger_shouldReturnRunningWhenSameActivityAlreadyQueued() {
        stubCompleteRefresh();
        List<Runnable> queuedTasks = new ArrayList<>();
        Executor queuedExecutor = queuedTasks::add;
        ProductActivityManualSyncService service = new ProductActivityManualSyncService(
                productService,
                colonelActivityService,
                activityMapper,
                queuedExecutor);

        ProductActivityManualSyncService.SyncTriggerResult first = service.trigger("ACT-1", null);
        ProductActivityManualSyncService.SyncTriggerResult second = service.trigger("ACT-1", null);

        assertThat(first.syncStatus()).isEqualTo("ACCEPTED");
        assertThat(second.syncStatus()).isEqualTo("RUNNING");
        assertThat(queuedTasks).hasSize(1);

        queuedTasks.get(0).run();
        ProductActivityManualSyncService.SyncTriggerResult third = service.trigger("ACT-1", null);
        assertThat(third.syncStatus()).isEqualTo("ACCEPTED");
        assertThat(queuedTasks).hasSize(2);
    }

    @Test
    void trigger_shouldReturnBusyWithoutCallingUpstreamWhenExecutorRejects() {
        AtomicBoolean reject = new AtomicBoolean(true);
        List<Runnable> queuedTasks = new ArrayList<>();
        Executor executor = task -> {
            if (reject.get()) {
                throw new RejectedExecutionException("queue full");
            }
            queuedTasks.add(task);
        };
        ProductActivityManualSyncService service = new ProductActivityManualSyncService(
                productService,
                colonelActivityService,
                activityMapper,
                executor);

        ProductActivityManualSyncService.SyncTriggerResult busy = service.trigger("ACT-1", null);

        assertThat(busy.activityId()).isEqualTo("ACT-1");
        assertThat(busy.syncStatus()).isEqualTo("BUSY");
        verify(colonelActivityService, never()).syncActivitySummaryFromUpstream(any(), any());
        verify(productService, never()).refreshActivitySnapshots(any());

        reject.set(false);
        ProductActivityManualSyncService.SyncTriggerResult accepted = service.trigger("ACT-1", null);
        assertThat(accepted.syncStatus()).isEqualTo("ACCEPTED");
        assertThat(queuedTasks).hasSize(1);
    }

    @Test
    void trigger_shouldNotTouchLastSyncAtWhenRefreshIsIncomplete() {
        when(productService.refreshActivitySnapshots(any()))
                .thenReturn(new ProductService.ActivityProductRefreshResult(
                        2_000,
                        1,
                        100,
                        1_900,
                        0,
                        100,
                        2_000,
                        2_000,
                        0,
                        "MAX_PAGES_REACHED",
                        true,
                        false));
        ProductActivityManualSyncService service = new ProductActivityManualSyncService(
                productService,
                colonelActivityService,
                activityMapper,
                Runnable::run);

        ProductActivityManualSyncService.SyncTriggerResult result = service.trigger("ACT-1", null);

        assertThat(result.syncStatus()).isEqualTo("ACCEPTED");
        verify(activityMapper, never()).touchLastSyncAt(eq("ACT-1"), any(LocalDateTime.class));
    }
}
