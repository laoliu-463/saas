package com.colonel.saas.job;

import com.colonel.saas.gateway.douyin.DouyinProductGateway;
import com.colonel.saas.mapper.ColonelsettlementActivityMapper;
import com.colonel.saas.service.DistributedJobLockService;
import com.colonel.saas.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductActivitySyncJobTest {

    @Mock
    private ProductService productService;
    @Mock
    private DistributedJobLockService jobLockService;
    @Mock
    private ColonelsettlementActivityMapper activityMapper;

    @BeforeEach
    void setUp() {
        lenient().when(jobLockService.tryAcquire(any(), any(Duration.class)))
                .thenReturn(true);
        lenient().when(jobLockService.tryAcquire(eq(JobLockKeys.PRODUCT_ACTIVITY_SYNC), any(Duration.class)))
                .thenReturn(true);
        lenient().when(productService.refreshActivitySnapshots(any()))
                .thenReturn(new ProductService.ActivityProductRefreshResult(3, 1, 1, 2, 0));
    }

    @Test
    void syncAll_shouldReturnWhenDisabled() {
        ProductActivitySyncJob job = job(false, "");

        job.syncAll();

        verifyNoInteractions(jobLockService, activityMapper, productService);
    }

    @Test
    void syncAll_shouldReturnWhenLockNotAcquired() {
        ProductActivitySyncJob job = job(true, "");
        when(jobLockService.tryAcquire(eq(JobLockKeys.PRODUCT_ACTIVITY_SYNC), any(Duration.class))).thenReturn(false);

        job.syncAll();

        verify(jobLockService).tryAcquire(eq(JobLockKeys.PRODUCT_BACKFILL_GLOBAL), any(Duration.class));
        verify(jobLockService).tryAcquire(eq(JobLockKeys.PRODUCT_ACTIVITY_SYNC), any(Duration.class));
        verifyNoInteractions(activityMapper, productService);
        verify(jobLockService, never()).release(JobLockKeys.PRODUCT_ACTIVITY_SYNC);
        verify(jobLockService).release(JobLockKeys.PRODUCT_BACKFILL_GLOBAL);
    }

    @Test
    void syncAll_shouldUseWhitelistActivitiesWhenConfigured() {
        ProductActivitySyncJob job = job(true, " ACT-1,ACT-2, ACT-1 ");

        job.syncAll();

        verify(activityMapper, never()).selectActiveActivityIds(anyInt(), any(LocalDateTime.class));
        verify(productService, times(2)).refreshActivitySnapshots(any());
        verify(activityMapper).touchLastSyncAt(eq("ACT-1"), any(LocalDateTime.class));
        verify(activityMapper).touchLastSyncAt(eq("ACT-2"), any(LocalDateTime.class));
        verify(jobLockService).release(JobLockKeys.PRODUCT_ACTIVITY_SYNC);
    }

    @Test
    void syncAll_shouldContinueWhenSingleActivityFails() {
        ProductActivitySyncJob job = job(true, "ACT-1,ACT-2");
        when(productService.refreshActivitySnapshots(any()))
                .thenThrow(new RuntimeException("upstream down"))
                .thenReturn(new ProductService.ActivityProductRefreshResult(5, 2, 2, 3, 0));

        job.syncAll();

        verify(productService, times(2)).refreshActivitySnapshots(any());
        verify(activityMapper, never()).touchLastSyncAt(eq("ACT-1"), any(LocalDateTime.class));
        verify(activityMapper).touchLastSyncAt(eq("ACT-2"), any(LocalDateTime.class));
        verify(jobLockService).release(JobLockKeys.PRODUCT_ACTIVITY_SYNC);
    }

    @Test
    void syncAll_shouldNotTouchLastSyncAtWhenActivityIncomplete() {
        ProductActivitySyncJob job = job(true, "ACT-1");
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

        job.syncAll();

        verify(activityMapper, never()).touchLastSyncAt(eq("ACT-1"), any(LocalDateTime.class));
        verify(jobLockService).release(JobLockKeys.PRODUCT_ACTIVITY_SYNC);
    }

    @Test
    void syncAll_shouldLoadActivitiesRefreshAndTouchLastSyncAt() {
        ProductActivitySyncJob job = job(true, "");
        when(activityMapper.selectActiveActivityIds(eq(20), any(LocalDateTime.class)))
                .thenReturn(List.of("ACT-10", "ACT-20"));

        job.syncAll();

        ArgumentCaptor<DouyinProductGateway.ActivityProductQueryRequest> captor =
                ArgumentCaptor.forClass(DouyinProductGateway.ActivityProductQueryRequest.class);
        verify(productService, times(2)).refreshActivitySnapshots(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(DouyinProductGateway.ActivityProductQueryRequest::activityId)
                .containsExactly("ACT-10", "ACT-20");
        assertThat(captor.getAllValues())
                .extracting(DouyinProductGateway.ActivityProductQueryRequest::count)
                .containsExactly(20, 20);
        verify(activityMapper).touchLastSyncAt(eq("ACT-10"), any(LocalDateTime.class));
        verify(activityMapper).touchLastSyncAt(eq("ACT-20"), any(LocalDateTime.class));
        verify(jobLockService).release(JobLockKeys.PRODUCT_ACTIVITY_SYNC);
    }

    private ProductActivitySyncJob job(boolean enabled, String whitelistActivities) {
        ProductActivitySyncJob job = new ProductActivitySyncJob(productService, jobLockService, activityMapper, 0);
        ReflectionTestUtils.setField(job, "enabled", enabled);
        ReflectionTestUtils.setField(job, "batchSize", 20);
        ReflectionTestUtils.setField(job, "pageSize", 20);
        ReflectionTestUtils.setField(job, "maxActivitiesPerRun", 20);
        ReflectionTestUtils.setField(job, "whitelistActivities", whitelistActivities);
        return job;
    }
}
