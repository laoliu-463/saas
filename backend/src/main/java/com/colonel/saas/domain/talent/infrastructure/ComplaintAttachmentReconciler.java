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
        List<ComplaintAttachmentStorage.ReconcileCandidate> candidates =
                storage.findReconcileCandidates(cutoff, batchSize);
        if (candidates == null || candidates.isEmpty()) {
            return ReconcileResult.empty();
        }

        List<String> candidateKeys = candidates.stream()
                .filter(Objects::nonNull)
                .map(ComplaintAttachmentStorage.ReconcileCandidate::storageKey)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (candidateKeys.isEmpty()) {
            return new ReconcileResult(candidates.size(), 0, 0, candidates.size(), 0);
        }
        Set<String> existingKeys = toSet(
                attachmentMapper.selectExistingStorageKeys(candidateKeys));
        int deleted = 0;
        int skipped = 0;
        int failed = 0;
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
        return new ReconcileResult(
                candidates.size(), existingKeys.size(), deleted, skipped, failed);
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
