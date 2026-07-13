package com.colonel.saas.service;

import com.colonel.saas.gateway.douyin.DouyinProductGateway;
import com.colonel.saas.job.JobLockKeys;
import com.colonel.saas.mapper.ColonelsettlementActivityMapper;
import com.colonel.saas.mapper.ProductActivitySyncStateMapper;
import com.colonel.saas.mapper.ProductSnapshotMapper;
import com.colonel.saas.mapper.ProductSyncJobLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 4-1.5 商品展示口径回归：displayRefreshMode 行为。
 * NONE / SKIPPED 模式不应触发展示规则刷新；IMMEDIATE 模式触发；DEFERRED 模式由 backfill 任务尾部触发。
 */
@ExtendWith(MockitoExtension.class)
class ProductLibraryDisplayRegressionTest {

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
                Runnable::run,
                transactionManager);
    }

    @Test
    void backfill_displayRefreshNone_shouldNotTriggerDisplayRuleService() {
        org.mockito.Mockito.lenient().when(jobLockService.tryAcquire(
                eq(JobLockKeys.PRODUCT_BACKFILL_GLOBAL), any(Duration.class), anyString())).thenReturn(true);
        org.mockito.Mockito.lenient().when(jobLockService.tryAcquire(any(), any(Duration.class), anyString())).thenReturn(true);
        org.mockito.Mockito.lenient().when(activityMapper.selectActivityIdsForProductSyncProbe(any(), anyInt(), any(), any()))
                .thenReturn(List.of("ACT-1"));
        org.mockito.Mockito.lenient().when(snapshotMapper.countActiveRowsByActivityIds(List.of("ACT-1"))).thenReturn(0L);
        org.mockito.Mockito.lenient().when(douyinProductGateway.queryActivityProducts(any()))
                .thenReturn(new DouyinProductGateway.ActivityProductListResult(
                        false, 1L, 1L, null, null, List.of()));
        org.mockito.Mockito.lenient().when(productService.upsertSnapshotsWithStats(any(), any()))
                .thenReturn(new ProductService.ActivitySnapshotUpsertStats(0, 0, 0, 0));

        service.backfill(
                new ProductActivityBackfillService.BackfillRequest(
                        "CUSTOM_ACTIVITY_IDS",
                        List.of("ACT-1"),
                        20,
                        50,
                        1000,
                        50_000,
                        false,
                        true,
                        "NONE"),
                UUID.randomUUID());

        verify(productDisplayRuleService, never()).repairLibraryStateForActivity(any(), org.mockito.ArgumentMatchers.anyBoolean(), anyInt());
        verify(productDisplayRuleService, never()).applyForActivityId(any());
    }

    @Test
    void backfill_displayRefreshDeferred_shouldCallDisplayRuleAfterBatch() {
        org.mockito.Mockito.lenient().when(jobLockService.tryAcquire(
                eq(JobLockKeys.PRODUCT_BACKFILL_GLOBAL), any(Duration.class), anyString())).thenReturn(true);
        org.mockito.Mockito.lenient().when(jobLockService.tryAcquire(any(), any(Duration.class), anyString())).thenReturn(true);
        org.mockito.Mockito.lenient().when(activityMapper.selectActivityIdsForProductSyncProbe(any(), anyInt(), any(), any()))
                .thenReturn(List.of("ACT-1"));
        org.mockito.Mockito.lenient().when(snapshotMapper.countActiveRowsByActivityIds(List.of("ACT-1"))).thenReturn(0L);
        org.mockito.Mockito.lenient().when(douyinProductGateway.queryActivityProducts(any()))
                .thenReturn(new DouyinProductGateway.ActivityProductListResult(
                        false, 1L, 1L, null, null, List.of()));
        org.mockito.Mockito.lenient().when(productService.upsertSnapshotsWithStats(any(), any()))
                .thenReturn(new ProductService.ActivitySnapshotUpsertStats(0, 0, 0, 0));

        service.backfill(
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

        // DEFERRED 模式：事实层写完后，会用 PRODUCT_DISPLAY_REFRESH 锁触发展示规则刷新。
        verify(productDisplayRuleService).repairLibraryStateForActivity(eq("ACT-1"), eq(false), anyInt());
        verify(productDisplayRuleService).applyForActivityId(eq("ACT-1"));
    }

    @Test
    void backfill_displayRefreshImmediate_shouldCallDisplayRuleDuringActivityLoop() {
        org.mockito.Mockito.lenient().when(jobLockService.tryAcquire(
                eq(JobLockKeys.PRODUCT_BACKFILL_GLOBAL), any(Duration.class), anyString())).thenReturn(true);
        org.mockito.Mockito.lenient().when(jobLockService.tryAcquire(any(), any(Duration.class), anyString())).thenReturn(true);
        org.mockito.Mockito.lenient().when(activityMapper.selectActivityIdsForProductSyncProbe(any(), anyInt(), any(), any()))
                .thenReturn(List.of("ACT-1"));
        org.mockito.Mockito.lenient().when(snapshotMapper.countActiveRowsByActivityIds(List.of("ACT-1"))).thenReturn(0L);
        org.mockito.Mockito.lenient().when(douyinProductGateway.queryActivityProducts(any()))
                .thenReturn(new DouyinProductGateway.ActivityProductListResult(
                        false, 1L, 1L, null, null, List.of()));
        org.mockito.Mockito.lenient().when(productService.upsertSnapshotsWithStats(any(), any()))
                .thenReturn(new ProductService.ActivitySnapshotUpsertStats(0, 0, 0, 0));

        service.backfill(
                new ProductActivityBackfillService.BackfillRequest(
                        "CUSTOM_ACTIVITY_IDS",
                        List.of("ACT-1"),
                        20,
                        50,
                        1000,
                        50_000,
                        false,
                        true,
                        "IMMEDIATE"),
                UUID.randomUUID());

        // IMMEDIATE 模式：在 batch loop 内部就触发，且额外跑一次 DEFERRED 总尾触发。
        verify(productDisplayRuleService, org.mockito.Mockito.atLeastOnce()).repairLibraryStateForActivity(eq("ACT-1"), eq(false), anyInt());
        verify(productDisplayRuleService, org.mockito.Mockito.atLeastOnce()).applyForActivityId(eq("ACT-1"));
        assertThat(true).isTrue();
    }
}
