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
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

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
import static org.mockito.Mockito.lenient;
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
    @Mock private DistributedJobLockService jobLockService;
    @Mock private ProductDisplayRuleService productDisplayRuleService;
    @Mock private DouyinProductGateway douyinProductGateway;
    @Mock private PlatformTransactionManager transactionManager;

    private ProductActivityBackfillService service;

    @BeforeEach
    void setUp() {
        // 任何 transactionTemplate.execute 调用都直接执行 callback（不开真实事务），
        // 保证单测不需要数据库事务基础设施。lenient 避免不必要的 stubbing 报警。
        org.mockito.Mockito.lenient().when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service = new ProductActivityBackfillService(
                dryRunProbeService,
                productService,
                activityMapper,
                snapshotMapper,
                jobLogMapper,
                syncStateMapper,
                jobLockService,
                productDisplayRuleService,
                douyinProductGateway,
                transactionManager);
    }

    @Test
    void backfill_dryRunShouldDelegateProbeAndWriteJobLogWithoutBusinessWrites() {
        when(dryRunProbeService.fullDryRun(any()))
                .thenReturn(fullDryRunResult());
        ArgumentCaptor<ProductSyncJobLog> jobLogCaptor = ArgumentCaptor.forClass(ProductSyncJobLog.class);

        ProductActivityBackfillService.BackfillResult result = service.backfill(
                new ProductActivityBackfillService.BackfillRequest(
                        "CUSTOM_ACTIVITY_IDS",
                        List.of("3859423"),
                        20,
                        50,
                        1000,
                        50_000,
                        true,
                        false,
                        "DEFERRED"),
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
        verify(jobLogMapper).updateById(jobLogCaptor.capture());
        assertThat(jobLogCaptor.getValue().getErrorMessage()).isNullOrEmpty();
    }

    @Test
    void backfill_dryRunFailedShouldPersistErrorMessage() {
        when(dryRunProbeService.fullDryRun(any()))
                .thenReturn(failedDryRunResult());
        ArgumentCaptor<ProductSyncJobLog> jobLogCaptor = ArgumentCaptor.forClass(ProductSyncJobLog.class);

        ProductActivityBackfillService.BackfillResult result = service.backfill(
                new ProductActivityBackfillService.BackfillRequest(
                        "CUSTOM_ACTIVITY_IDS",
                        List.of("3859423"),
                        20,
                        1,
                        1000,
                        50_000,
                        true,
                        false,
                        "DEFERRED"),
                UUID.randomUUID());

        assertThat(result.dryRun()).isTrue();
        assertThat(result.activitiesFailed()).isEqualTo(1);
        assertThat(result.stopReasonStats()).containsEntry("API_ERROR", 1L);
        verify(jobLogMapper).insert(any(ProductSyncJobLog.class));
        verify(jobLogMapper).updateById(jobLogCaptor.capture());
        assertThat(jobLogCaptor.getValue().getErrorMessage()).isNotBlank();
    }

    @Test
    void backfill_realRunFetchPageThrowsShouldPersistRawCauseAndFailureMessage() {
        when(activityMapper.selectActivityIdsForProductSyncProbe(eq("CUSTOM"), anyInt(), any(), eq(List.of("ACT-1"))))
                .thenReturn(List.of("ACT-1"));
        when(snapshotMapper.countActiveRowsByActivityIds(List.of("ACT-1"))).thenReturn(0L);
        when(jobLockService.tryAcquire(any(), any(), any())).thenReturn(true);
        when(jobLockService.tryAcquire(any(), any())).thenReturn(true);
        when(douyinProductGateway.queryActivityProducts(any()))
                .thenThrow(new RuntimeException("upstream 500"));

        ArgumentCaptor<ProductActivitySyncState> stateCaptor =
                ArgumentCaptor.forClass(ProductActivitySyncState.class);
        ArgumentCaptor<ProductSyncJobLog> jobLogCaptor = ArgumentCaptor.forClass(ProductSyncJobLog.class);

        ProductActivityBackfillService.BackfillResult result = service.backfill(
                realRequest(List.of("ACT-1")),
                UUID.randomUUID());

        assertThat(result.activitiesFailed()).isEqualTo(1);
        verify(syncStateMapper).upsert(stateCaptor.capture());
        ProductActivitySyncState state = stateCaptor.getValue();
        assertThat(state.getLastStatus()).isEqualTo("FAILED");
        assertThat(state.getLastErrorMessage()).isNotBlank();
        assertThat(state.getLastErrorMessage()).contains("rawCause=UPSTREAM_API_ERROR");
        verify(jobLogMapper).updateById(jobLogCaptor.capture());
        assertThat(jobLogCaptor.getValue().getErrorMessage()).isNotBlank();
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
                        false,
                        "DEFERRED"),
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
        // Phase 4-1.5：真实 backfill 改走 batched 路径，直接调 gateway + productService.upsertSnapshotsWithStats。
        // 这里只做最弱 mock 让两条活动至少跑到 page handler，验证锁 + sync state + job log 路径。
        org.mockito.Mockito.lenient().when(jobLockService.tryAcquire(any(), any(), any())).thenReturn(true);
        org.mockito.Mockito.lenient().when(jobLockService.tryAcquire(any(), any())).thenReturn(true);
        // gateway 返回空页（DONE_NO_MORE）让 runner 一次循环就退出。
        org.mockito.Mockito.lenient().when(douyinProductGateway.queryActivityProducts(any()))
                .thenReturn(new DouyinProductGateway.ActivityProductListResult(
                        false, 3859423L, 1L, null, null, java.util.List.of()));
        org.mockito.Mockito.lenient().when(productService.upsertSnapshotsWithStats(any(), any()))
                .thenReturn(new ProductService.ActivitySnapshotUpsertStats(0, 0, 0, 0));

        ProductActivityBackfillService.BackfillResult result = service.backfill(
                new ProductActivityBackfillService.BackfillRequest(
                        "CUSTOM_ACTIVITY_IDS",
                        List.of("ACT-1", "ACT-2"),
                        20,
                        50,
                        1000,
                        50_000,
                        false,
                        true,
                        "DEFERRED"),
                UUID.randomUUID());

        assertThat(result.dryRun()).isFalse();
        // gateway 返回空 page 会导致两个 activity 都判为 DONE_NO_MORE_SUCCESS。
        assertThat(result.activitiesSuccess()).isEqualTo(2);
        assertThat(result.activitiesFailed()).isZero();
        assertThat(result.dbRowsBefore()).isEqualTo(10L);

        ArgumentCaptor<ProductActivitySyncState> stateCaptor =
                ArgumentCaptor.forClass(ProductActivitySyncState.class);
        verify(syncStateMapper, times(2)).upsert(stateCaptor.capture());
        assertThat(stateCaptor.getAllValues())
                .extracting(ProductActivitySyncState::getLastStatus)
                .containsOnly("SUCCESS");
        verify(jobLogMapper).insert(any(ProductSyncJobLog.class));
        verify(jobLogMapper).updateById(any(ProductSyncJobLog.class));
    }

    @Test
    void backfill_realRunShouldSortBatchByProductIdBeforeUpsert() {
        when(activityMapper.selectActivityIdsForProductSyncProbe(eq("CUSTOM"), anyInt(), any(), eq(List.of("ACT-1"))))
                .thenReturn(List.of("ACT-1"));
        when(snapshotMapper.countActiveRowsByActivityIds(List.of("ACT-1"))).thenReturn(0L);
        when(jobLockService.tryAcquire(any(), any(), any())).thenReturn(true);
        when(jobLockService.tryAcquire(any(), any())).thenReturn(true);
        when(douyinProductGateway.queryActivityProducts(any()))
                .thenReturn(new DouyinProductGateway.ActivityProductListResult(
                        false,
                        3859423L,
                        1L,
                        null,
                        null,
                        List.of(productItem(30L), productItem(2L), productItem(10L))));
        when(productService.upsertSnapshotsWithStats(any(), any()))
                .thenReturn(new ProductService.ActivitySnapshotUpsertStats(3, 0, 0, 0));

        service.backfill(realRequest(List.of("ACT-1")), UUID.randomUUID());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DouyinProductGateway.ActivityProductItem>> batchCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(productService).upsertSnapshotsWithStats(eq("ACT-1"), batchCaptor.capture());
        assertThat(batchCaptor.getValue())
                .extracting(DouyinProductGateway.ActivityProductItem::productId)
                .containsExactly(2L, 10L, 30L);
    }

    @Test
    void backfill_realRunShouldRetryDeadlockAtBatchLevelWithoutRefetchingPage() {
        when(activityMapper.selectActivityIdsForProductSyncProbe(eq("CUSTOM"), anyInt(), any(), eq(List.of("ACT-1"))))
                .thenReturn(List.of("ACT-1"));
        when(snapshotMapper.countActiveRowsByActivityIds(List.of("ACT-1"))).thenReturn(0L);
        when(jobLockService.tryAcquire(any(), any(), any())).thenReturn(true);
        when(jobLockService.tryAcquire(any(), any())).thenReturn(true);
        when(douyinProductGateway.queryActivityProducts(any()))
                .thenReturn(new DouyinProductGateway.ActivityProductListResult(
                        false,
                        3859423L,
                        1L,
                        null,
                        null,
                        List.of(productItem(10L))));
        when(productService.upsertSnapshotsWithStats(any(), any()))
                .thenThrow(new DeadlockLoserDataAccessException("deadlock detected 40P01", null))
                .thenReturn(new ProductService.ActivitySnapshotUpsertStats(1, 0, 0, 0));

        ProductActivityBackfillService.BackfillResult result =
                service.backfill(realRequest(List.of("ACT-1")), UUID.randomUUID());

        assertThat(result.activitiesSuccess()).isEqualTo(1);
        assertThat(result.deadlockRetryCount()).isEqualTo(1L);
        verify(douyinProductGateway, times(1)).queryActivityProducts(any());
        verify(productService, times(2)).upsertSnapshotsWithStats(eq("ACT-1"), any());
    }

    private ProductActivityBackfillService.BackfillRequest realRequest(List<String> activityIds) {
        return new ProductActivityBackfillService.BackfillRequest(
                "CUSTOM_ACTIVITY_IDS",
                activityIds,
                20,
                50,
                1000,
                50_000,
                false,
                true,
                "DEFERRED");
    }

    private DouyinProductGateway.ActivityProductItem productItem(long productId) {
        return new DouyinProductGateway.ActivityProductItem(
                productId,
                "product-" + productId,
                null,
                0L,
                null,
                0L,
                0L,
                0L,
                null,
                0,
                null,
                null,
                null,
                false,
                false,
                0L,
                0L,
                null,
                null,
                1,
                "推广中",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                Map.of());
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
                        List.<ActivityProductPaginationRunner.PageSummary>of(),
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

    private ProductSyncDryRunProbeService.FullDryRunResult failedDryRunResult() {
        ProductSyncDryRunProbeService.ActivityDryRunResult activity =
                new ProductSyncDryRunProbeService.ActivityDryRunResult(
                        "3859423",
                        true,
                        20,
                        1000,
                        1,
                        0,
                        0,
                        0,
                        1,
                        0,
                        20,
                        "API_ERROR",
                        true,
                        List.<ActivityProductPaginationRunner.PageSummary>of(),
                        List.of("gateway timeout"));
        return new ProductSyncDryRunProbeService.FullDryRunResult(
                true,
                "CUSTOM_ACTIVITY_IDS",
                1,
                0,
                0,
                0,
                0,
                0,
                1,
                20,
                0,
                0,
                1,
                Map.of("API_ERROR", 1L),
                List.of(),
                List.of(),
                List.of(new ProductSyncDryRunProbeService.ApiError("3859423", "API_ERROR", "gateway timeout")),
                List.of(activity));
    }
}
