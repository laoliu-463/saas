package com.colonel.saas.domain.talent.infrastructure;

import com.colonel.saas.mapper.TalentComplaintAttachmentMapper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ComplaintAttachmentReconcilerTest {

    private static final Instant NOW = Instant.parse("2026-07-16T12:00:00Z");
    private static final FileTime OLD = FileTime.from(NOW.minus(Duration.ofHours(25)));
    private static final FileTime NEW = FileTime.from(NOW.minus(Duration.ofHours(23)));

    @TempDir
    Path tempDir;

    @Test
    void reconcile_shouldDeleteOnlyOldOrphansAndKeepDatabaseReferencedNewAndTempFiles()
            throws Exception {
        String orphan = key(1);
        String referenced = key(2);
        String recent = key(3);
        Path orphanPath = create(orphan, OLD);
        Path referencedPath = create(referenced, OLD);
        Path recentPath = create(recent, NEW);
        Path temp = tempDir.resolve("01/.upload.tmp");
        Files.createDirectories(temp.getParent());
        Files.write(temp, new byte[]{1});
        Files.setLastModifiedTime(temp, OLD);
        Path malformed = tempDir.resolve("01/not-a-generated-key.jpg");
        Files.write(malformed, new byte[]{2});
        Files.setLastModifiedTime(malformed, OLD);
        TalentComplaintAttachmentMapper mapper = mock(TalentComplaintAttachmentMapper.class);
        when(mapper.selectExistingStorageKeys(anyList())).thenReturn(List.of(referenced));
        ComplaintAttachmentReconciler reconciler = reconciler(
                new ComplaintAttachmentStorage(tempDir), mapper);

        ComplaintAttachmentReconciler.ReconcileResult result =
                reconciler.reconcile(Duration.ofHours(24), 100);

        assertThat(result).isEqualTo(new ComplaintAttachmentReconciler.ReconcileResult(
                2, 1, 1, 0, 0));
        assertThat(orphanPath).doesNotExist();
        assertThat(referencedPath).exists();
        assertThat(recentPath).exists();
        assertThat(temp).exists();
        assertThat(malformed).exists();
        verify(mapper, times(1)).selectExistingStorageKeys(anyList());
    }

    @Test
    void reconcile_shouldNeverFollowSymbolicLinks() throws Exception {
        Path outside = tempDir.resolveSibling("outside-complaint-evidence.jpg");
        Files.write(outside, new byte[]{9});
        Files.setLastModifiedTime(outside, OLD);
        Path link = tempDir.resolve(key(4));
        Files.createDirectories(link.getParent());
        try {
            Files.createSymbolicLink(link, outside);
        } catch (IOException | UnsupportedOperationException exception) {
            Assumptions.assumeTrue(false, "symbolic links unavailable: " + exception.getClass());
        }
        TalentComplaintAttachmentMapper mapper = mock(TalentComplaintAttachmentMapper.class);
        ComplaintAttachmentReconciler reconciler = reconciler(
                new ComplaintAttachmentStorage(tempDir), mapper);

        ComplaintAttachmentReconciler.ReconcileResult result =
                reconciler.reconcile(Duration.ofHours(24), 100);

        assertThat(result.candidateCount()).isZero();
        assertThat(outside).exists();
        assertThat(link).exists();
        Files.deleteIfExists(link);
        Files.deleteIfExists(outside);
    }

    @Test
    void reconcile_shouldCapOneRunAtOneHundredCandidates() throws Exception {
        for (int index = 0; index < 101; index++) {
            create(key(1000 + index), OLD);
        }
        TalentComplaintAttachmentMapper mapper = mock(TalentComplaintAttachmentMapper.class);
        when(mapper.selectExistingStorageKeys(anyList())).thenReturn(List.of());
        ComplaintAttachmentReconciler reconciler = reconciler(
                new ComplaintAttachmentStorage(tempDir), mapper);

        ComplaintAttachmentReconciler.ReconcileResult result =
                reconciler.reconcile(Duration.ofHours(24), 500);

        assertThat(result.candidateCount()).isEqualTo(100);
        assertThat(result.deletedCount()).isEqualTo(100);
        assertThat(countGeneratedFiles()).isEqualTo(1);
        verify(mapper, times(1)).selectExistingStorageKeys(anyList());
    }

    @Test
    void reconcile_shouldExposeDeleteFailureAfterThreeAttemptsAndContinue() throws Exception {
        String failingKey = key(10);
        String successfulKey = key(11);
        Path failing = create(failingKey, OLD);
        Path successful = create(successfulKey, OLD);
        AtomicInteger failingDeletes = new AtomicInteger();
        ComplaintAttachmentStorage storage = new ComplaintAttachmentStorage(
                tempDir,
                UUID::randomUUID,
                (source, target, atomic) -> Files.move(source, target),
                path -> {
                    if (path.getFileName().equals(failing.getFileName())) {
                        failingDeletes.incrementAndGet();
                        throw new IOException("sensitive root must not be logged");
                    }
                    return Files.deleteIfExists(path);
                });
        TalentComplaintAttachmentMapper mapper = mock(TalentComplaintAttachmentMapper.class);
        when(mapper.selectExistingStorageKeys(anyList())).thenReturn(List.of());

        ComplaintAttachmentReconciler.ReconcileResult result =
                reconciler(storage, mapper).reconcile(Duration.ofHours(24), 100);

        assertThat(result.deletedCount()).isEqualTo(1);
        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(failingDeletes).hasValue(3);
        assertThat(failing).exists();
        assertThat(successful).doesNotExist();
    }

    @Test
    void reconcile_shouldContinueWhenOneCandidateCannotBeInspected() throws Exception {
        Path unreadable = create(key(20), OLD);
        Path readable = create(key(21), OLD);
        ComplaintAttachmentStorage storage = new ComplaintAttachmentStorage(
                tempDir,
                UUID::randomUUID,
                (source, target, atomic) -> Files.move(source, target),
                Files::deleteIfExists,
                path -> {
                    if (path.getFileName().equals(unreadable.getFileName())) {
                        throw new IOException("simulated attribute failure");
                    }
                    return Files.readAttributes(
                            path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                });
        TalentComplaintAttachmentMapper mapper = mock(TalentComplaintAttachmentMapper.class);
        when(mapper.selectExistingStorageKeys(anyList())).thenReturn(List.of());

        ComplaintAttachmentReconciler.ReconcileResult result =
                reconciler(storage, mapper).reconcile(Duration.ofHours(24), 100);

        assertThat(result.candidateCount()).isEqualTo(1);
        assertThat(result.deletedCount()).isEqualTo(1);
        assertThat(unreadable).exists();
        assertThat(readable).doesNotExist();
    }

    @Test
    void deleteCandidate_shouldSkipWhenMtimeChangedAfterScan() throws Exception {
        Path changed = create(key(30), OLD);
        ComplaintAttachmentStorage storage = new ComplaintAttachmentStorage(tempDir);
        ComplaintAttachmentStorage.ReconcileCandidate candidate =
                storage.findReconcileCandidates(NOW.minus(Duration.ofHours(24)), 100).get(0);
        Files.setLastModifiedTime(changed, NEW);

        assertThat(storage.deleteReconcileCandidate(candidate))
                .isEqualTo(ComplaintAttachmentStorage.DeleteResult.SKIPPED);
        assertThat(changed).exists();
    }

    private ComplaintAttachmentReconciler reconciler(
            ComplaintAttachmentStorage storage,
            TalentComplaintAttachmentMapper mapper) {
        return new ComplaintAttachmentReconciler(
                storage,
                mapper,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private Path create(String storageKey, FileTime modified) throws IOException {
        Path path = tempDir.resolve(storageKey);
        Files.createDirectories(path.getParent());
        Files.write(path, new byte[]{1, 2, 3});
        Files.setLastModifiedTime(path, modified);
        return path;
    }

    private long countGeneratedFiles() throws IOException {
        try (var paths = Files.walk(tempDir)) {
            return paths.filter(Files::isRegularFile).count();
        }
    }

    private String key(int number) {
        String hex = String.format("%032x", number);
        return hex.substring(0, 2) + "/" + hex + ".jpg";
    }
}
