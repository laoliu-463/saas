package com.colonel.saas.job;

import com.colonel.saas.domain.talent.infrastructure.ComplaintAttachmentReconciler;
import com.colonel.saas.service.DistributedJobLockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TalentComplaintAttachmentReconcileJobTest {

    @Mock
    private ComplaintAttachmentReconciler reconciler;
    @Mock
    private DistributedJobLockService lockService;

    private TalentComplaintAttachmentReconcileJob job;

    @BeforeEach
    void setUp() {
        job = new TalentComplaintAttachmentReconcileJob(
                reconciler, lockService, 24, 100);
    }

    @Test
    void reconcile_shouldSkipWhenDistributedLockIsNotAcquired() {
        when(lockService.tryAcquire(
                eq(JobLockKeys.TALENT_COMPLAINT_ATTACHMENT_RECONCILE),
                any(Duration.class),
                anyString())).thenReturn(false);

        job.reconcile();

        verify(reconciler, never()).reconcile(any(), any(Integer.class));
        verify(lockService, never()).releaseWithOwner(anyString(), anyString());
    }

    @Test
    void reconcile_shouldUseConfiguredGraceAndBatchAndOwnerSafeRelease() {
        when(lockService.tryAcquire(
                eq(JobLockKeys.TALENT_COMPLAINT_ATTACHMENT_RECONCILE),
                any(Duration.class),
                anyString())).thenReturn(true);
        when(reconciler.reconcile(Duration.ofHours(24), 100))
                .thenReturn(new ComplaintAttachmentReconciler.ReconcileResult(2, 1, 1, 0, 0));

        job.reconcile();

        verify(reconciler).reconcile(Duration.ofHours(24), 100);
        ArgumentCaptor<String> acquiredOwner = ArgumentCaptor.forClass(String.class);
        verify(lockService).tryAcquire(
                eq(JobLockKeys.TALENT_COMPLAINT_ATTACHMENT_RECONCILE),
                any(Duration.class),
                acquiredOwner.capture());
        ArgumentCaptor<String> releasedOwner = ArgumentCaptor.forClass(String.class);
        verify(lockService).releaseWithOwner(
                eq(JobLockKeys.TALENT_COMPLAINT_ATTACHMENT_RECONCILE),
                releasedOwner.capture());
        assertThat(acquiredOwner.getValue()).startsWith("complaint-attachment-reconcile:");
        assertThat(releasedOwner.getValue()).isEqualTo(acquiredOwner.getValue());
    }
}
