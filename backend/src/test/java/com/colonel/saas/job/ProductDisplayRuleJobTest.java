package com.colonel.saas.job;

import com.colonel.saas.service.DistributedJobLockService;
import com.colonel.saas.service.ProductDisplayRuleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductDisplayRuleJobTest {

    @Mock
    private ProductDisplayRuleService displayRuleService;
    @Mock
    private DistributedJobLockService jobLockService;

    @BeforeEach
    void grantLock() {
        lenient().when(jobLockService.tryAcquire(eq(JobLockKeys.PRODUCT_BACKFILL_GLOBAL), any(Duration.class), anyString()))
                .thenReturn(true);
        lenient().when(jobLockService.tryAcquire(eq(JobLockKeys.PRODUCT_DISPLAY_REFRESH), any(Duration.class), anyString()))
                .thenReturn(true);
    }

    @Test
    void reconcileDisplayStatus_shouldCallService() {
        ProductDisplayRuleJob job = new ProductDisplayRuleJob(displayRuleService, jobLockService);
        when(displayRuleService.reconcileAll()).thenReturn(12);

        job.reconcileDisplayStatus();

        verify(displayRuleService).reconcileAll();
        verify(jobLockService).releaseWithOwner(eq(JobLockKeys.PRODUCT_DISPLAY_REFRESH), anyString());
        verify(jobLockService).releaseWithOwner(eq(JobLockKeys.PRODUCT_BACKFILL_GLOBAL), anyString());
    }

    @Test
    void reconcileDisplayStatus_shouldSkipWhenLockNotAcquired() {
        ProductDisplayRuleJob job = new ProductDisplayRuleJob(displayRuleService, jobLockService);
        when(jobLockService.tryAcquire(eq(JobLockKeys.PRODUCT_DISPLAY_REFRESH), any(Duration.class), anyString())).thenReturn(false);

        job.reconcileDisplayStatus();

        verify(displayRuleService, never()).reconcileAll();
        verify(jobLockService, never()).releaseWithOwner(eq(JobLockKeys.PRODUCT_DISPLAY_REFRESH), anyString());
        verify(jobLockService).releaseWithOwner(eq(JobLockKeys.PRODUCT_BACKFILL_GLOBAL), anyString());
    }
}
