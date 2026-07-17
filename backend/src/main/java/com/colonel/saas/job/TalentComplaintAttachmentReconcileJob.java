package com.colonel.saas.job;

import com.colonel.saas.domain.talent.infrastructure.ComplaintAttachmentReconciler;
import com.colonel.saas.service.DistributedJobLockService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/** 低峰执行投诉附件孤儿文件对账；多实例间由 Redis owner 锁互斥。 */
@Slf4j
@Component
@ConditionalOnProperty(
        prefix = "talent.complaint.reconcile",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class TalentComplaintAttachmentReconcileJob {

    private static final Duration LOCK_TTL = Duration.ofMinutes(30);

    private final ComplaintAttachmentReconciler reconciler;
    private final DistributedJobLockService lockService;
    private final int graceHours;
    private final int batchSize;

    public TalentComplaintAttachmentReconcileJob(
            ComplaintAttachmentReconciler reconciler,
            DistributedJobLockService lockService,
            @Value("${talent.complaint.reconcile.grace-hours:24}") int graceHours,
            @Value("${talent.complaint.reconcile.batch-size:100}") int batchSize) {
        if (batchSize < 2 || batchSize > 100) {
            throw new IllegalArgumentException(
                    "complaint attachment reconcile batch size must be 2..100");
        }
        this.reconciler = reconciler;
        this.lockService = lockService;
        this.graceHours = graceHours;
        this.batchSize = batchSize;
    }

    @Scheduled(cron = "${talent.complaint.reconcile.cron:0 40 3 * * ?}")
    public void reconcile() {
        String owner = "complaint-attachment-reconcile:" + UUID.randomUUID();
        if (!lockService.tryAcquire(
                JobLockKeys.TALENT_COMPLAINT_ATTACHMENT_RECONCILE,
                LOCK_TTL,
                owner)) {
            log.info("complaint_attachment_reconcile_skipped reason=lock_not_acquired");
            return;
        }
        try {
            ComplaintAttachmentReconciler.ReconcileResult result =
                    reconciler.reconcile(Duration.ofHours(graceHours), batchSize);
            log.info(
                    "complaint_attachment_reconcile_completed candidates={} referenced={} deleted={} skipped={} failed={}",
                    result.candidateCount(),
                    result.referencedCount(),
                    result.deletedCount(),
                    result.skippedCount(),
                    result.failedCount());
        } catch (RuntimeException exception) {
            log.error(
                    "complaint_attachment_reconcile_failed errorType={}",
                    exception.getClass().getSimpleName());
        } finally {
            lockService.releaseWithOwner(
                    JobLockKeys.TALENT_COMPLAINT_ATTACHMENT_RECONCILE,
                    owner);
        }
    }
}
