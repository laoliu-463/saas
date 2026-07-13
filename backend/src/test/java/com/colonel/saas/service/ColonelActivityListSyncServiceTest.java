package com.colonel.saas.service;

import com.colonel.saas.entity.ColonelActivitySyncJobLog;
import com.colonel.saas.gateway.douyin.DouyinActivityGateway;
import com.colonel.saas.job.JobLockKeys;
import com.colonel.saas.mapper.ColonelActivitySyncJobLogMapper;
import com.colonel.saas.mapper.ColonelsettlementActivityMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ColonelActivityListSyncServiceTest {

    @Mock
    private DouyinActivityGateway douyinActivityGateway;
    @Mock
    private ColonelsettlementActivityMapper activityMapper;
    @Mock
    private ColonelActivitySyncJobLogMapper jobLogMapper;
    @Mock
    private ColonelsettlementActivityService activityService;
    @Mock
    private DistributedJobLockService jobLockService;

    private ColonelActivityListSyncService service;

    @BeforeEach
    void setUp() {
        service = new ColonelActivityListSyncService(
                douyinActivityGateway,
                activityMapper,
                jobLogMapper,
                activityService,
                jobLockService
        );
        // 使用反射设置属性以避免配置文件依赖
        org.springframework.test.util.ReflectionTestUtils.setField(service, "enabled", true);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "maxPages", 2);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "pageSize", 5);

        // 手动将异步执行替换为同步执行，以便单线程测试
        org.springframework.test.util.ReflectionTestUtils.setField(service, "syncExecutor",
                new java.util.concurrent.AbstractExecutorService() {
                    @Override
                    public void shutdown() {}
                    @Override
                    public List<Runnable> shutdownNow() { return List.of(); }
                    @Override
                    public boolean isShutdown() { return false; }
                    @Override
                    public boolean isTerminated() { return false; }
                    @Override
                    public boolean awaitTermination(long timeout, java.util.concurrent.TimeUnit unit) { return true; }
                    @Override
                    public void execute(Runnable command) { command.run(); }
                });
    }

    @Test
    void triggerSync_shouldAcquireLockAndSyncSuccessfully() {
        UUID userId = UUID.randomUUID();
        when(jobLockService.tryAcquire(eq(JobLockKeys.COLONEL_ACTIVITY_LIST_SYNC), any(Duration.class), anyString()))
                .thenReturn(true);
        when(jobLogMapper.tryClaimActiveJob(any(), any(), any(), any(), any())).thenReturn(true);

        DouyinActivityGateway.ActivityItem item = new DouyinActivityGateway.ActivityItem(
                12345L, "Test Activity", "2026-07-01", "2026-07-31", 5, "推广中", "2026-07-01", "2026-07-10", null, 9999L
        );
        DouyinActivityGateway.ActivityListResult mockResult = new DouyinActivityGateway.ActivityListResult(
                false, 9999L, 1L, List.of(item)
        );
        when(douyinActivityGateway.listActivities(any())).thenReturn(mockResult);

        ColonelActivityListSyncService.SyncTriggerResult result = service.triggerSync(userId);

        assertThat(result.jobId()).isNotNull();
        assertThat(result.status()).isEqualTo("QUEUED");

        // 验证 P8.4 原子 claim 活跃任务
        verify(jobLogMapper).tryClaimActiveJob(
                eq(result.jobId()), eq("ACTIVITY_LIST"), eq("ACTIVITY_LIST_GLOBAL"), eq(userId), any());

        // 验证同步状态更新为 RUNNING，然后更新为 SUCCESS
        verify(jobLogMapper).updateRunning(eq(result.jobId()), any());
        verify(jobLogMapper).updateStatus(eq(result.jobId()), eq("SUCCESS"), any(), eq(1), eq(1), eq(0), any(), any());

        // 验证调用了 activityService.syncFromGatewayItem
        verify(activityService).syncFromGatewayItem(item);

        // 验证释放锁
        verify(jobLockService).releaseWithOwner(eq(JobLockKeys.COLONEL_ACTIVITY_LIST_SYNC), anyString());
    }

    @Test
    void triggerSync_shouldFailWhenLockIsHeld() {
        when(jobLockService.tryAcquire(eq(JobLockKeys.COLONEL_ACTIVITY_LIST_SYNC), any(Duration.class), anyString()))
                .thenReturn(false);
        when(jobLogMapper.tryClaimActiveJob(any(), any(), any(), any(), any())).thenReturn(true);

        ColonelActivityListSyncService.SyncTriggerResult result = service.triggerSync(null);

        assertThat(result.jobId()).isNotNull();
        verify(jobLogMapper).updateStatus(eq(result.jobId()), eq("FAILED_LOCKED"), any(), eq(0), eq(0), eq(0), any(), any());
        verify(douyinActivityGateway, never()).listActivities(any());
    }

    /**
     * P8.4 修复验证: 同 scope 已有活跃任务时, ON CONFLICT 失败, 复用现有任务.
     */
    @Test
    void triggerSync_shouldReuseExistingActiveJobWhenConflict() {
        // P8.4 修复验证: 同 scope 已有活跃任务时, ON CONFLICT 失败, 复用现有任务.
        // 注意: 本测试不调 lock (ON CONFLICT 失败在 lock 之前发生)
        org.mockito.Mockito.lenient().when(jobLockService.tryAcquire(any(), any(Duration.class), anyString()))
                .thenReturn(true);
        // tryClaimActiveJob 返回 false = ON CONFLICT 触发, 同 scope 已有活跃任务
        when(jobLogMapper.tryClaimActiveJob(any(), any(), any(), any(), any())).thenReturn(false);

        ColonelActivityListSyncService.SyncTriggerResult result = service.triggerSync(null);

        // 期望返回 RUNNING (前端应轮询), 不是 QUEUED
        assertThat(result.status()).isEqualTo("RUNNING");
        assertThat(result.reused()).isTrue();
        // 不应调 listActivities (无新 job 提交)
        verify(douyinActivityGateway, never()).listActivities(any());
    }

    @Test
    void triggerSync_shouldFetchSubsequentPagesConcurrentlyWithinBoundedWindow() throws Exception {
        org.springframework.test.util.ReflectionTestUtils.setField(service, "maxPages", 5);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "pageSize", 2);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "pageParallelism", 2);
        when(jobLockService.tryAcquire(eq(JobLockKeys.COLONEL_ACTIVITY_LIST_SYNC), any(Duration.class), anyString()))
                .thenReturn(true);
        when(jobLogMapper.tryClaimActiveJob(any(), any(), any(), any(), any())).thenReturn(true);

        AtomicInteger activePageFetches = new AtomicInteger();
        AtomicInteger maxConcurrentPageFetches = new AtomicInteger();
        CountDownLatch subsequentPagesStarted = new CountDownLatch(2);

        when(douyinActivityGateway.listActivities(any())).thenAnswer(invocation -> {
            DouyinActivityGateway.ActivityListQuery query = invocation.getArgument(0);
            long page = query.page();
            if (page > 1) {
                int active = activePageFetches.incrementAndGet();
                maxConcurrentPageFetches.accumulateAndGet(active, Math::max);
                subsequentPagesStarted.countDown();
                subsequentPagesStarted.await(250, TimeUnit.MILLISECONDS);
                activePageFetches.decrementAndGet();
            }
            return new DouyinActivityGateway.ActivityListResult(
                    false,
                    9999L,
                    9999L,  // P8.3: 上游 total 不可信 (本测试模拟), maxPages 兜底
                    List.of(
                            activityItem(page * 10 + 1),
                            activityItem(page * 10 + 2)
                    )
            );
        });

        ColonelActivityListSyncService.SyncTriggerResult result = service.triggerSync(null);

        assertThat(result.status()).isEqualTo("QUEUED");
        assertThat(maxConcurrentPageFetches.get()).isGreaterThanOrEqualTo(2);
        assertThat(maxConcurrentPageFetches.get()).isLessThanOrEqualTo(2);
        verify(activityService, times(10)).syncFromGatewayItem(any());
    }

    /**
     * P8.3 修复验证: 上游 total 一致且累计达到时, 应立即 complete (而非 maxPages 耗尽).
     */
    @Test
    void triggerSync_shouldStopAtUpstreamTotalWhenReached() throws Exception {
        org.springframework.test.util.ReflectionTestUtils.setField(service, "maxPages", 10);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "pageSize", 2);
        when(jobLockService.tryAcquire(eq(JobLockKeys.COLONEL_ACTIVITY_LIST_SYNC), any(Duration.class), anyString()))
                .thenReturn(true);
        when(jobLogMapper.tryClaimActiveJob(any(), any(), any(), any(), any())).thenReturn(true);
        // total=4 应在 2 页后停 (2 条/页 × 2 = 4)
        when(douyinActivityGateway.listActivities(any())).thenAnswer(invocation -> {
            DouyinActivityGateway.ActivityListQuery query = invocation.getArgument(0);
            long page = query.page();
            return new DouyinActivityGateway.ActivityListResult(
                    false, 9999L, 4L,
                    List.of(activityItem(page * 10 + 1), activityItem(page * 10 + 2)));
        });

        ColonelActivityListSyncService.SyncTriggerResult result = service.triggerSync(null);

        assertThat(result.status()).isEqualTo("QUEUED");
        // 期望只调 4 次 (2 页 × 2 条), 不调 10 次 (maxPages 兜底)
        verify(activityService, times(4)).syncFromGatewayItem(any());
    }

    private DouyinActivityGateway.ActivityItem activityItem(long activityId) {
        return new DouyinActivityGateway.ActivityItem(
                activityId,
                "Activity " + activityId,
                "2026-07-01",
                "2026-07-31",
                5,
                "推广中",
                "2026-07-01",
                "2026-07-10",
                null,
                9999L
        );
    }
}
