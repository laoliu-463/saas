package com.colonel.saas.service;

import com.colonel.saas.domain.product.application.ProductActivitySyncApplicationService;
import com.colonel.saas.mapper.ColonelsettlementActivityMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductActivityManualSyncServiceTest {

    @Mock
    private ProductActivitySyncApplicationService productActivitySyncApplicationService;
    @Mock
    private ColonelsettlementActivityService colonelActivityService;
    @Mock
    private ColonelsettlementActivityMapper activityMapper;

    @BeforeEach
    void setUp() {
        when(productActivitySyncApplicationService.refreshManualActivitySnapshots(any(), any(), any()))
                .thenReturn(new ProductActivitySyncApplicationService.ActivityProductRefreshResult(3, 1, 1, 2, 0));
    }

    @Test
    void trigger_shouldReturnAcceptedAndRunRefreshInBackgroundExecutor() {
        ProductActivityManualSyncService service = new ProductActivityManualSyncService(
                productActivitySyncApplicationService,
                colonelActivityService,
                activityMapper,
                Runnable::run);

        ProductActivityManualSyncService.SyncTriggerResult result = service.trigger(" ACT-1 ", null);

        assertThat(result.activityId()).isEqualTo("ACT-1");
        assertThat(result.syncStatus()).isEqualTo("ACCEPTED");
        verify(colonelActivityService).syncActivitySummaryFromUpstream("ACT-1", null);
        verify(productActivitySyncApplicationService).refreshManualActivitySnapshots("ACT-1", null, 20);
        verify(activityMapper).touchLastSyncAt(eq("ACT-1"), any(LocalDateTime.class));
    }

    @Test
    void trigger_shouldReturnRunningWhenSameActivityAlreadyQueued() {
        List<Runnable> queuedTasks = new ArrayList<>();
        Executor queuedExecutor = queuedTasks::add;
        ProductActivityManualSyncService service = new ProductActivityManualSyncService(
                productActivitySyncApplicationService,
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
    void trigger_shouldNotTouchLastSyncAtWhenRefreshIsIncomplete() {
        when(productActivitySyncApplicationService.refreshManualActivitySnapshots(any(), any(), any()))
                .thenReturn(new ProductActivitySyncApplicationService.ActivityProductRefreshResult(
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
                productActivitySyncApplicationService,
                colonelActivityService,
                activityMapper,
                Runnable::run);

        ProductActivityManualSyncService.SyncTriggerResult result = service.trigger("ACT-1", null);

        assertThat(result.syncStatus()).isEqualTo("ACCEPTED");
        verify(activityMapper, never()).touchLastSyncAt(eq("ACT-1"), any(LocalDateTime.class));
    }
}
