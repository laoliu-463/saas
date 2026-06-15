package com.colonel.saas.service;

import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.entity.ProductActivitySyncState;
import com.colonel.saas.entity.ProductSyncJobLog;
import com.colonel.saas.gateway.douyin.DouyinProductGateway;
import com.colonel.saas.mapper.ColonelsettlementActivityMapper;
import com.colonel.saas.mapper.ProductActivitySyncStateMapper;
import com.colonel.saas.mapper.ProductSnapshotMapper;
import com.colonel.saas.mapper.ProductSyncJobLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductActivityBackfillServiceTest {

    @Mock private ProductSyncDryRunProbeService dryRunProbeService;
    @Mock private ProductService productService;
    @Mock private ColonelsettlementActivityMapper activityMapper;
    @Mock private ProductSnapshotMapper snapshotMapper;
    @Mock private ProductSyncJobLogMapper jobLogMapper;
    @Mock private ProductActivitySyncStateMapper syncStateMapper;

    private ProductActivityBackfillService service;

    @BeforeEach
    void setUp() {
        service = new ProductActivityBackfillService(
                dryRunProbeService,
                productService,
                activityMapper,
                snapshotMapper,
                jobLogMapper,
                syncStateMapper);
    }

    @Test
    void backfill_dryRunShouldDelegateProbeAndWriteJobLogWithoutBusinessWrites() {
        when(dryRunProbeService.fullDryRun(any()))
                .thenReturn(fullDryRunResult());

        ProductActivityBackfillService.BackfillResult result = service.backfill(
                new ProductActivityBackfillService.BackfillRequest(
                        "CUSTOM_ACTIVITY_IDS",
                        List.of("3859423"),
                        20,
                        50,
                        1000,
                        50_000,
                        true,
                        false),
                UUID.randomUUID());

        assertThat(result.jobId()).isNotBlank();
        assertThat(result.dryRun()).isTrue();
        assertThat(result.scope()).isEqualTo("CUSTOM_ACTIVITY_IDS");
        assertThat(result.activitiesScanned()).isEqualTo(1);
        assertThat(result.apiFetchedRows()).isEqualTo(120L);
        assertThat(result.estimatedGapRows()).isEqualTo(100L);
        assertThat(result.inserted()).isZero();
        assertThat(result.stopReasonStats()).containsEntry("DONE_NO_MORE", 1L);
        verify(productService, never()).refreshActivitySnapshots(any(), anyInt(), anyInt());
        verify(snapshotMapper, never()).upsert(any());
        verify(jobLogMapper).insert(any(ProductSyncJobLog.class));
        verify(jobLogMapper).updateById(any(ProductSyncJobLog.class));
    }

    @Test
    void backfill_realRunShouldRequireConfirm() {
        assertThatThrownBy(() -> service.backfill(
                new ProductActivityBackfillService.BackfillRequest(
                        "CUSTOM_ACTIVITY_IDS",
                        List.of("3859423"),
                        20,
                        50,
                        1000,
                        50_000,
                        false,
                        false),
                UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("confirm=true");

        verify(productService, never()).refreshActivitySnapshots(any(), anyInt(), anyInt());
        verify(jobLogMapper, never()).insert(any());
    }

    @Test
    void backfill_realRunShouldRefreshActivitiesAndPersistActivitySyncState() {
        when(activityMapper.selectActivityIdsForProductSyncProbe(eq("CUSTOM"), anyInt(), any(), eq(List.of("ACT-1", "ACT-2"))))
                .thenReturn(List.of("ACT-1", "ACT-2"));
        when(snapshotMapper.countActiveRowsByActivityIds(List.of("ACT-1", "ACT-2"))).thenReturn(10L);
        when(productService.refreshActivitySnapshots(any(), anyInt(), anyInt()))
                .thenReturn(new ProductService.ActivityProductRefreshResult(
                        25,
                        2,
                        5,
                        20,
                        0,
                        2,
                        25,
                        25,
                        0,
                        "DONE_NO_MORE",
                        false,
                        true))
                .thenReturn(new ProductService.ActivityProductRefreshResult(
                        2_000,
                        0,
                        100,
                        1_900,
                        0,
                        100,
                        2_000,
                        1_980,
                        20,
                        "MAX_PAGES_REACHED",
                        true,
                        false));

        ProductActivityBackfillService.BackfillResult result = service.backfill(
                new ProductActivityBackfillService.BackfillRequest(
                        "CUSTOM_ACTIVITY_IDS",
                        List.of("ACT-1", "ACT-2"),
                        20,
                        50,
                        1000,
                        50_000,
                        false,
                        true),
                UUID.randomUUID());

        assertThat(result.dryRun()).isFalse();
        assertThat(result.activitiesSuccess()).isEqualTo(1);
        assertThat(result.activitiesIncomplete()).isEqualTo(1);
        assertThat(result.activitiesFailed()).isZero();
        assertThat(result.inserted()).isEqualTo(105);
        assertThat(result.updated()).isEqualTo(1_920);
        assertThat(result.dbRowsBefore()).isEqualTo(10L);
        assertThat(result.stopReasonStats()).containsEntry("MAX_PAGES_REACHED", 1L);

        ArgumentCaptor<ProductActivitySyncState> stateCaptor =
                ArgumentCaptor.forClass(ProductActivitySyncState.class);
        verify(syncStateMapper, times(2)).upsert(stateCaptor.capture());
        assertThat(stateCaptor.getAllValues())
                .extracting(ProductActivitySyncState::getLastStatus)
                .containsExactly("SUCCESS", "INCOMPLETE_MAX_PAGES");
        assertThat(stateCaptor.getAllValues().get(0).getLastSuccessAt()).isNotNull();
        assertThat(stateCaptor.getAllValues().get(1).getLastSuccessAt()).isNull();
        verify(jobLogMapper).insert(any(ProductSyncJobLog.class));
        verify(jobLogMapper).updateById(any(ProductSyncJobLog.class));
    }

    private ProductSyncDryRunProbeService.FullDryRunResult fullDryRunResult() {
        ProductSyncDryRunProbeService.ActivityDryRunResult activity =
                new ProductSyncDryRunProbeService.ActivityDryRunResult(
                        "3859423",
                        true,
                        20,
                        1000,
                        1,
                        120,
                        100,
                        20,
                        20,
                        100,
                        0,
                        "DONE_NO_MORE",
                        false,
                        List.of(),
                        List.of());
        return new ProductSyncDryRunProbeService.FullDryRunResult(
                true,
                "CUSTOM_ACTIVITY_IDS",
                1,
                1,
                0,
                0,
                120,
                100,
                20,
                100,
                1,
                0,
                0,
                Map.of("DONE_NO_MORE", 1L),
                List.of(activity),
                List.of(activity),
                List.of(),
                List.of(activity));
    }
}
