package com.colonel.saas.service;

import com.colonel.saas.entity.ProductActivitySyncState;
import com.colonel.saas.entity.ProductSyncJobLog;
import com.colonel.saas.gateway.douyin.DouyinProductGateway;
import com.colonel.saas.job.JobLockKeys;
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
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 4-1.5 deadlock 修复：backfill 并发锁与 deadlock retry 测试。
 */
@ExtendWith(MockitoExtension.class)
class ProductBackfillConcurrencyAndDeadlockTest {

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
    void backfill_lockNotAcquired_shouldReturnFailedLockedAndNotWriteBusiness() {
        // 全局锁被占，backfill 必须立刻放弃、不进入真实写库、status=FAILED_LOCKED。
        when(jobLockService.tryAcquire(eq(JobLockKeys.PRODUCT_BACKFILL_GLOBAL), any(Duration.class))).thenReturn(false);

        ProductActivityBackfillService.BackfillResult result = service.backfill(
                new ProductActivityBackfillService.BackfillRequest(
                        "CUSTOM_ACTIVITY_IDS",
                        List.of("3859423"),
                        20,
                        50,
                        1000,
                        50_000,
                        false,
                        true,
                        "DEFERRED"),
                UUID.randomUUID());

        assertThat(result.activitiesFailed()).isEqualTo(1);
        assertThat(result.stopReasonStats()).containsEntry("FAILED_LOCKED", 1L);
        // 没有进入 activity 写库。
        verify(productService, never()).upsertSnapshotsWithStats(any(), any());
        verify(syncStateMapper, never()).upsert(any());
        // job log 一定写了 status = FAILED_LOCKED。
        verify(jobLogMapper, times(1)).insert(any());
        verify(jobLogMapper, times(1)).updateById(any());
    }

    @Test
    void backfill_activityLockHeld_shouldSkipActivityAndContinueOthers() {
        // 全局锁能拿，activity 锁一个能拿一个不能拿。
        when(jobLockService.tryAcquire(eq(JobLockKeys.PRODUCT_BACKFILL_GLOBAL), any(Duration.class))).thenReturn(true);
        when(jobLockService.tryAcquire(eq(JobLockKeys.productBackfillActivityLock("ACT-1")), any(Duration.class))).thenReturn(false);
        when(jobLockService.tryAcquire(eq(JobLockKeys.productBackfillActivityLock("ACT-2")), any(Duration.class))).thenReturn(true);
        when(activityMapper.selectActivityIdsForProductSyncProbe(any(), anyInt(), any(), any()))
                .thenReturn(List.of("ACT-1", "ACT-2"));
        when(snapshotMapper.countActiveRowsByActivityIds(List.of("ACT-1", "ACT-2"))).thenReturn(0L);
        org.mockito.Mockito.lenient().when(douyinProductGateway.queryActivityProducts(any()))
                .thenReturn(new DouyinProductGateway.ActivityProductListResult(
                        false, 1L, 1L, null, null, List.of()));
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

        // ACT-1 锁被占，被 skip 计入 lockWaitCount；ACT-2 成功。
        assertThat(result.lockWaitCount()).isEqualTo(1L);
        assertThat(result.stopReasonStats()).containsEntry("FAILED_LOCKED", 1L);
        // ACT-1 的失败状态被记入 sync state（FAILED）。
        ArgumentCaptor<ProductActivitySyncState> stateCaptor =
                ArgumentCaptor.forClass(ProductActivitySyncState.class);
        verify(syncStateMapper, times(2)).upsert(stateCaptor.capture());
        assertThat(stateCaptor.getAllValues())
                .filteredOn(state -> "ACT-1".equals(state.getActivityId()))
                .singleElement()
                .satisfies(state -> {
                    assertThat(state.getLastStatus()).isEqualTo("FAILED");
                    assertThat(state.getLastStopReason()).isEqualTo("FAILED_LOCKED");
                });
    }

    @Test
    void backfill_deadlockRetry_shouldRetryUpToMaxThenSucceed() {
        DouyinProductGateway.ActivityProductItem item = newItem(3859423L);
        when(jobLockService.tryAcquire(any(), any(Duration.class))).thenReturn(true);
        when(activityMapper.selectActivityIdsForProductSyncProbe(any(), anyInt(), any(), any()))
                .thenReturn(List.of("ACT-1"));
        when(snapshotMapper.countActiveRowsByActivityIds(List.of("ACT-1"))).thenReturn(0L);
        // 第一次抛 deadlock，第二次成功。
        DeadlockLoserDataAccessException ddl = new DeadlockLoserDataAccessException(
                "deadlock detected SQLSTATE 40P01", new RuntimeException("SQLSTATE 40P01"));
        when(productService.upsertSnapshotsWithStats(any(), any()))
                .thenThrow(ddl)
                .thenReturn(new ProductService.ActivitySnapshotUpsertStats(1, 0, 0, 0));
        when(douyinProductGateway.queryActivityProducts(any()))
                .thenReturn(new DouyinProductGateway.ActivityProductListResult(
                        false, 1L, 1L, 1L, null, List.of(item)));

        ProductActivityBackfillService.BackfillResult result = service.backfill(
                new ProductActivityBackfillService.BackfillRequest(
                        "CUSTOM_ACTIVITY_IDS",
                        List.of("ACT-1"),
                        20,
                        50,
                        1000,
                        50_000,
                        false,
                        true,
                        "DEFERRED"),
                UUID.randomUUID());

        // 重试 1 次后成功：deadlockRetryCount>=1，且 productService.upsertSnapshotsWithStats 至少被调 2 次。
        assertThat(result.deadlockRetryCount()).isGreaterThanOrEqualTo(1L);
        verify(productService, org.mockito.Mockito.atLeast(2)).upsertSnapshotsWithStats(any(), any());
    }

    @Test
    void backfill_lockNotAvailableSqlState_shouldAlsoRetry() {
        DouyinProductGateway.ActivityProductItem item = newItem(3859423L);
        when(jobLockService.tryAcquire(any(), any(Duration.class))).thenReturn(true);
        when(activityMapper.selectActivityIdsForProductSyncProbe(any(), anyInt(), any(), any()))
                .thenReturn(List.of("ACT-1"));
        when(snapshotMapper.countActiveRowsByActivityIds(List.of("ACT-1"))).thenReturn(0L);
        CannotAcquireLockException lna = new CannotAcquireLockException("lock not available 55P03");
        when(productService.upsertSnapshotsWithStats(any(), any()))
                .thenThrow(lna)
                .thenReturn(new ProductService.ActivitySnapshotUpsertStats(0, 0, 0, 0));
        when(douyinProductGateway.queryActivityProducts(any()))
                .thenReturn(new DouyinProductGateway.ActivityProductListResult(
                        false, 1L, 1L, 1L, null, List.of(item)));

        ProductActivityBackfillService.BackfillResult result = service.backfill(
                realRequest("ACT-1", "DEFERRED"),
                UUID.randomUUID());

        assertThat(result.deadlockRetryCount()).isGreaterThanOrEqualTo(1L);
        verify(productService, org.mockito.Mockito.atLeast(2)).upsertSnapshotsWithStats(any(), any());
    }

    @Test
    void backfill_deadlockRetryExhausted_shouldFailWithoutRefetchingPage() {
        ReflectionTestUtils.setField(service, "deadlockRetryMax", 1);
        DouyinProductGateway.ActivityProductItem item = newItem(3859423L);
        when(jobLockService.tryAcquire(any(), any(Duration.class))).thenReturn(true);
        when(activityMapper.selectActivityIdsForProductSyncProbe(any(), anyInt(), any(), any()))
                .thenReturn(List.of("ACT-1"));
        when(snapshotMapper.countActiveRowsByActivityIds(List.of("ACT-1"))).thenReturn(0L);
        DeadlockLoserDataAccessException ddl = new DeadlockLoserDataAccessException(
                "deadlock detected SQLSTATE 40P01", new RuntimeException("SQLSTATE 40P01"));
        when(productService.upsertSnapshotsWithStats(any(), any()))
                .thenThrow(ddl)
                .thenThrow(ddl);
        when(douyinProductGateway.queryActivityProducts(any()))
                .thenReturn(new DouyinProductGateway.ActivityProductListResult(
                        false, 1L, 1L, 1L, null, List.of(item)));

        ProductActivityBackfillService.BackfillResult result = service.backfill(
                realRequest("ACT-1", "NONE"),
                UUID.randomUUID());

        assertThat(result.activitiesFailed()).isEqualTo(1);
        assertThat(result.stopReasonStats()).containsEntry("DEADLOCK_RETRY_EXHAUSTED", 1L);
        assertThat(result.deadlockRetryCount()).isEqualTo(1L);
        verify(douyinProductGateway, times(1)).queryActivityProducts(any());
        verify(productService, times(2)).upsertSnapshotsWithStats(any(), any());

        ArgumentCaptor<ProductSyncJobLog> jobLogCaptor = ArgumentCaptor.forClass(ProductSyncJobLog.class);
        verify(jobLogMapper).updateById(jobLogCaptor.capture());
        assertThat(jobLogCaptor.getValue().getStatus()).isEqualTo("FAILED");
        assertThat(jobLogCaptor.getValue().getFinishedAt()).isNotNull();

        ArgumentCaptor<ProductActivitySyncState> stateCaptor =
                ArgumentCaptor.forClass(ProductActivitySyncState.class);
        verify(syncStateMapper).upsert(stateCaptor.capture());
        assertThat(stateCaptor.getValue().getLastStatus()).isEqualTo("FAILED");
        assertThat(stateCaptor.getValue().getLastStopReason()).isEqualTo("DEADLOCK_RETRY_EXHAUSTED");
    }

    private ProductActivityBackfillService.BackfillRequest realRequest(String activityId, String displayRefreshMode) {
        return new ProductActivityBackfillService.BackfillRequest(
                "CUSTOM_ACTIVITY_IDS",
                List.of(activityId),
                20,
                50,
                1000,
                50_000,
                false,
                true,
                displayRefreshMode);
    }

    private static DouyinProductGateway.ActivityProductItem newItem(long productId) {
        return new DouyinProductGateway.ActivityProductItem(
                productId,
                "title-" + productId,
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
                0,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

}
