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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
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

        // 验证 Job 日志入库
        ArgumentCaptor<ColonelActivitySyncJobLog> jobLogCaptor = ArgumentCaptor.forClass(ColonelActivitySyncJobLog.class);
        verify(jobLogMapper).insert(jobLogCaptor.capture());
        assertThat(jobLogCaptor.getValue().getJobId()).isEqualTo(result.jobId());
        assertThat(jobLogCaptor.getValue().getTriggeredBy()).isEqualTo(userId);

        // 验证同步状态更新为 RUNNING，然后更新为 SUCCESS
        verify(jobLogMapper).updateRunning(eq(result.jobId()), any());
        verify(jobLogMapper).updateStatus(eq(result.jobId()), eq("SUCCESS"), any(), eq(1), eq(0), any(), any());

        // 验证调用了 activityService.syncFromGatewayItem
        verify(activityService).syncFromGatewayItem(item);

        // 验证释放锁
        verify(jobLockService).releaseWithOwner(eq(JobLockKeys.COLONEL_ACTIVITY_LIST_SYNC), anyString());
    }

    @Test
    void triggerSync_shouldFailWhenLockIsHeld() {
        when(jobLockService.tryAcquire(eq(JobLockKeys.COLONEL_ACTIVITY_LIST_SYNC), any(Duration.class), anyString()))
                .thenReturn(false);

        ColonelActivityListSyncService.SyncTriggerResult result = service.triggerSync(null);

        assertThat(result.jobId()).isNotNull();
        verify(jobLogMapper).updateStatus(eq(result.jobId()), eq("FAILED_LOCKED"), any(), eq(0), eq(0), any(), any());
        verify(douyinActivityGateway, never()).listActivities(any());
    }
}
