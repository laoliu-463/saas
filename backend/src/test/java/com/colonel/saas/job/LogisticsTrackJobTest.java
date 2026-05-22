package com.colonel.saas.job;

import com.colonel.saas.entity.SampleRequest;
import com.colonel.saas.mapper.SampleRequestMapper;
import com.colonel.saas.service.DistributedJobLockService;
import com.colonel.saas.service.LogisticsTrackService;
import io.lettuce.core.RedisCommandExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LogisticsTrackJobTest {

    @Mock
    private LogisticsTrackService logisticsTrackService;
    @Mock
    private SampleRequestMapper sampleRequestMapper;
    @Mock
    private DistributedJobLockService jobLockService;
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ValueOperations<String, Object> valueOperations;

    @BeforeEach
    void grantLock() {
        lenient().when(jobLockService.tryAcquire(eq(JobLockKeys.LOGISTICS_TRACK), any(Duration.class))).thenReturn(true);
    }

    private LogisticsTrackJob newJob() {
        return new LogisticsTrackJob(logisticsTrackService, sampleRequestMapper, jobLockService, true);
    }

    private LogisticsTrackJob newDisabledJob() {
        return new LogisticsTrackJob(logisticsTrackService, sampleRequestMapper, jobLockService, false);
    }

    @Test
    @DisplayName("获取锁后处理所有 SHIPPING 寄样单")
    void refreshShippingSamples_shouldProcessAllShippingSamples() {
        LogisticsTrackJob job = newJob();
        SampleRequest sample1 = new SampleRequest();
        sample1.setRequestNo("SR-001");
        SampleRequest sample2 = new SampleRequest();
        sample2.setRequestNo("SR-002");
        when(sampleRequestMapper.selectList(any())).thenReturn(List.of(sample1, sample2));

        job.refreshShippingSamples();

        verify(logisticsTrackService).refreshAndProgress(sample1);
        verify(logisticsTrackService).refreshAndProgress(sample2);
        verify(jobLockService).release(JobLockKeys.LOGISTICS_TRACK);
    }

    @Test
    @DisplayName("单条失败不中断批量处理")
    void refreshShippingSamples_singleFailureDoesNotStopBatch() {
        LogisticsTrackJob job = newJob();
        SampleRequest sample1 = new SampleRequest();
        sample1.setRequestNo("SR-001");
        SampleRequest sample2 = new SampleRequest();
        sample2.setRequestNo("SR-002");
        when(sampleRequestMapper.selectList(any())).thenReturn(List.of(sample1, sample2));
        doThrow(new RuntimeException("查询失败")).when(logisticsTrackService).refreshAndProgress(sample1);

        job.refreshShippingSamples();

        verify(logisticsTrackService).refreshAndProgress(sample1);
        verify(logisticsTrackService).refreshAndProgress(sample2);
        verify(jobLockService).release(JobLockKeys.LOGISTICS_TRACK);
    }

    @Test
    @DisplayName("未获取锁时跳过执行")
    void refreshShippingSamples_shouldSkipWhenLockNotAcquired() {
        LogisticsTrackJob job = newJob();
        when(jobLockService.tryAcquire(eq(JobLockKeys.LOGISTICS_TRACK), any(Duration.class))).thenReturn(false);

        job.refreshShippingSamples();

        verify(sampleRequestMapper, never()).selectList(any());
        verify(logisticsTrackService, never()).refreshAndProgress(any());
        verify(jobLockService, never()).release(JobLockKeys.LOGISTICS_TRACK);
    }

    @Test
    @DisplayName("Job 关闭时跳过执行")
    void refreshShippingSamples_shouldSkipWhenDisabled() {
        LogisticsTrackJob job = newDisabledJob();

        job.refreshShippingSamples();

        verify(jobLockService, never()).tryAcquire(any(), any());
        verify(sampleRequestMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("测试模式下 Redis 不可用时回退到本地锁")
    void refreshShippingSamples_shouldFallbackToLocalLockInTestMode() {
        DistributedJobLockService realLockService = new DistributedJobLockService(redisTemplate, true);
        LogisticsTrackJob job = new LogisticsTrackJob(
                logisticsTrackService, sampleRequestMapper, realLockService, true);
        SampleRequest sample = new SampleRequest();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq(JobLockKeys.LOGISTICS_TRACK), eq("1"), any(Duration.class)))
                .thenThrow(new RedisCommandExecutionException("redis down"));
        when(sampleRequestMapper.selectList(any())).thenReturn(List.of(sample));

        job.refreshShippingSamples();

        verify(logisticsTrackService).refreshAndProgress(sample);
    }

    @Test
    @DisplayName("非测试模式下 Redis 异常应抛出")
    void refreshShippingSamples_shouldThrowWhenRedisFailsInNonTestMode() {
        DistributedJobLockService realLockService = new DistributedJobLockService(redisTemplate, false);
        LogisticsTrackJob job = new LogisticsTrackJob(
                logisticsTrackService, sampleRequestMapper, realLockService, true);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq(JobLockKeys.LOGISTICS_TRACK), eq("1"), any(Duration.class)))
                .thenThrow(new RedisCommandExecutionException("redis down"));

        try {
            job.refreshShippingSamples();
        } catch (RedisCommandExecutionException ex) {
            // expected
        }

        verify(sampleRequestMapper, never()).selectList(any());
    }
}
