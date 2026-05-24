package com.colonel.saas.job;

import com.colonel.saas.service.DistributedJobLockService;
import com.colonel.saas.service.ProductPinService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductPinCleanupJobTest {

    private static final String LOCK_KEY = "product:pin:expire:job:lock";

    @Mock
    private ProductPinService productPinService;
    @Mock
    private DistributedJobLockService jobLockService;

    @BeforeEach
    void grantLock() {
        lenient().when(jobLockService.tryAcquire(eq(LOCK_KEY), any(Duration.class)))
                .thenReturn(true);
    }

    @Test
    void cleanupExpiredPins_shouldCallServiceWhenLockAcquired() {
        ProductPinCleanupJob job = new ProductPinCleanupJob(productPinService, jobLockService);
        when(productPinService.expirePinnedProducts()).thenReturn(2);

        job.cleanupExpiredPins();

        verify(productPinService).expirePinnedProducts();
        verify(jobLockService).release(LOCK_KEY);
    }

    @Test
    void cleanupExpiredPins_shouldSkipWhenLockNotAcquired() {
        ProductPinCleanupJob job = new ProductPinCleanupJob(productPinService, jobLockService);
        when(jobLockService.tryAcquire(eq(LOCK_KEY), any(Duration.class))).thenReturn(false);

        job.cleanupExpiredPins();

        verify(productPinService, never()).expirePinnedProducts();
        verify(jobLockService, never()).release(LOCK_KEY);
    }

    @Test
    void cleanupExpiredPins_shouldNotPropagateServiceException() {
        ProductPinCleanupJob job = new ProductPinCleanupJob(productPinService, jobLockService);
        when(productPinService.expirePinnedProducts()).thenThrow(new RuntimeException("db down"));

        org.junit.jupiter.api.Assertions.assertDoesNotThrow(job::cleanupExpiredPins);

        verify(jobLockService).release(LOCK_KEY);
    }
}
