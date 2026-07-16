package com.colonel.saas.domain.talent.infrastructure;

import com.colonel.saas.mapper.TalentComplaintAttachmentMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** 对账受控目录与数据库附件元数据，清理超过 grace 的孤儿文件。 */
@Slf4j
@Component
public class ComplaintAttachmentReconciler {

    private static final int MAX_BATCH_SIZE = 100;

    private final ComplaintAttachmentStorage storage;
    private final TalentComplaintAttachmentMapper attachmentMapper;
    private final Clock clock;

    @Autowired
    public ComplaintAttachmentReconciler(
            ComplaintAttachmentStorage storage,
            TalentComplaintAttachmentMapper attachmentMapper) {
        this(storage, attachmentMapper, Clock.systemUTC());
    }

    ComplaintAttachmentReconciler(
            ComplaintAttachmentStorage storage,
            TalentComplaintAttachmentMapper attachmentMapper,
            Clock clock) {
        this.storage = Objects.requireNonNull(storage, "storage");
        this.attachmentMapper = Objects.requireNonNull(attachmentMapper, "attachmentMapper");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public ReconcileResult reconcile(Duration grace, int requestedBatchSize) {
        if (grace == null || grace.isZero() || grace.isNegative()) {
            throw new IllegalArgumentException("complaint attachment grace must be positive");
        }
        int batchSize = Math.max(1, Math.min(requestedBatchSize, MAX_BATCH_SIZE));
        Instant cutoff = clock.instant().minus(grace);
        int temporaryBudget = batchSize == 1 ? 1 : Math.max(1, batchSize / 4);
        List<ComplaintAttachmentStorage.TemporaryCandidate> temporaryCandidates =
                storage.findTemporaryCandidates(cutoff, temporaryBudget);
        int deleted = 0;
        int skipped = 0;
        int failed = 0;
        int temporaryIndex = 0;
        for (ComplaintAttachmentStorage.TemporaryCandidate candidate : temporaryCandidates) {
            temporaryIndex++;
            try {
                ComplaintAttachmentStorage.DeleteResult result =
                        storage.deleteTemporaryCandidate(candidate);
                if (result == ComplaintAttachmentStorage.DeleteResult.DELETED) {
                    deleted++;
                } else if (result == ComplaintAttachmentStorage.DeleteResult.FAILED) {
                    failed++;
                } else {
                    skipped++;
                }
            } catch (RuntimeException exception) {
                failed++;
                log.warn(
                        "complaint_attachment_reconcile_temporary_failed candidateIndex={} errorType={}",
                        temporaryIndex,
                        exception.getClass().getSimpleName());
            }
        }

        int attachmentBudget = batchSize - temporaryCandidates.size();
        if (attachmentBudget <= 0) {
            return new ReconcileResult(
                    temporaryCandidates.size(), 0, deleted, skipped, failed);
        }
        List<ComplaintAttachmentStorage.ReconcileCandidate> candidates =
                storage.findReconcileCandidates(cutoff, attachmentBudget);
        if (candidates == null || candidates.isEmpty()) {
            return new ReconcileResult(
                    temporaryCandidates.size(), 0, deleted, skipped, failed);
        }

        List<String> candidateKeys = candidates.stream()
                .filter(Objects::nonNull)
                .map(ComplaintAttachmentStorage.ReconcileCandidate::storageKey)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (candidateKeys.isEmpty()) {
            return new ReconcileResult(
                    temporaryCandidates.size() + candidates.size(),
                    0,
                    deleted,
                    skipped + candidates.size(),
                    failed);
        }
        Set<String> existingKeys = toSet(
                attachmentMapper.selectExistingStorageKeys(candidateKeys));
        int candidateIndex = 0;
        for (ComplaintAttachmentStorage.ReconcileCandidate candidate : candidates) {
            candidateIndex++;
            if (candidate == null || existingKeys.contains(candidate.storageKey())) {
                continue;
            }
            try {
                ComplaintAttachmentStorage.DeleteResult result =
                        storage.deleteReconcileCandidate(candidate);
                if (result == ComplaintAttachmentStorage.DeleteResult.DELETED) {
                    deleted++;
                } else if (result == ComplaintAttachmentStorage.DeleteResult.FAILED) {
                    failed++;
                } else {
                    skipped++;
                }
            } catch (RuntimeException exception) {
                failed++;
                log.warn(
                        "complaint_attachment_reconcile_candidate_failed candidateIndex={} errorType={}",
                        candidateIndex,
                        exception.getClass().getSimpleName());
            }
        }
        storage.advanceReconcileCursor(candidates.get(candidates.size() - 1).storageKey());
        return new ReconcileResult(
                temporaryCandidates.size() + candidates.size(),
                existingKeys.size(), deleted, skipped, failed);
    }

    private Set<String> toSet(Collection<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return Set.of();
        }
        return keys.stream()
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    public record ReconcileResult(
            int candidateCount,
            int referencedCount,
            int deletedCount,
            int skippedCount,
            int failedCount) {

        static ReconcileResult empty() {
            return new ReconcileResult(0, 0, 0, 0, 0);
        }
    }
}
