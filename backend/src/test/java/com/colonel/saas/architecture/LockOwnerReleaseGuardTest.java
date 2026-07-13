package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LOCK-OWNER-GUARD (P9.5): executable redline scans for unsafe lock-release patterns.
 *
 * <p>设计原则 (按用户修正清单):
 * <ul>
 *   <li>业务任务锁: 强制 owner, 必须 releaseWithOwner(key, owner) - release(String) 违规</li>
 *   <li>系统级互斥锁: 允许无 owner, 在 {@code ALLOWED_UNOWNED_RELEASE_CLASSES} 白名单</li>
 *   <li>stale 清理: 仅允许 owner 校验释放, release(String) 违规</li>
 * </ul>
 *
 * <p>豁免: 系统级互斥锁 (LogCleanupJob / LogCleanup 等) 是天然单实例互斥,
 * 不需要 owner 校验。
 */
class LockOwnerReleaseGuardTest {

    /**
     * 允许无 owner 释放的系统级互斥锁 (按用户清单):
     * - ColonelPartnerSyncJob
     * - DouyinTokenRefreshJob
     * - ExclusiveEvaluateJob
     * - LogCleanupJob
     * - LogisticsTrackJob
     * - PerformanceBackfillJob
     * - PerformanceCacheWarmupJob
     * - PerformanceRecalculateFailedJob
     * - ProductDisplayRuleJob
     * - ProductPinCleanupJob
     * - SampleLifecycleJob
     * - SampleLogisticsSyncJob
     * - TalentClaimReleaseJob
     * - TalentWeeklyRefreshJob
     * - StaleProductSyncJobReconcileJob (其内部用 releaseWithOwner, 但顶层 tryAcquire 也调 release)
     */
    private static final Set<String> ALLOWED_UNOWNED_RELEASE_CLASSES = Set.of(
            "ColonelPartnerSyncJob",
            "DouyinTokenRefreshJob",
            "ExclusiveEvaluateJob",
            "LogCleanupJob",
            "LogisticsTrackJob",
            "PerformanceBackfillJob",
            "PerformanceCacheWarmupJob",
            "PerformanceRecalculateFailedJob",
            "ProductDisplayRuleJob",
            "ProductPinCleanupJob",
            "SampleLifecycleJob",
            "SampleLogisticsSyncJob",
            "TalentClaimReleaseJob",
            "TalentWeeklyRefreshJob",
            "StaleProductSyncJobReconcileJob");

    /** 业务任务锁 (强制 owner) - 必须改用 releaseWithOwner */
    private static final Set<String> BUSINESS_LOCK_CLASSES = Set.of(
            "ProductActivitySyncJob",
            "ProductActivityBackfillService",
            "OrderSyncService");

    /** 匹配 release(String) 调用 - 排除 releaseWithOwner */
    private static final Pattern UNOWNED_RELEASE_PATTERN = Pattern.compile(
            "(?<!\\.releaseWithOwner)\\.release\\(\\s*[A-Za-z_][A-Za-z0-9_]*\\s*\\)");

    @Test
    void businessTaskLocksMustUseOwnerSafeRelease() throws IOException {
        Set<String> violations = new LinkedHashSet<>();
        Path backendRoot = backendRoot();
        for (String className : BUSINESS_LOCK_CLASSES) {
            Path sourcePath = findClass(backendRoot, className);
            if (sourcePath == null) {
                continue;
            }
            String source = Files.readString(sourcePath);
            java.util.regex.Matcher matcher = UNOWNED_RELEASE_PATTERN.matcher(source);
            while (matcher.find()) {
                String line = lineAt(source, matcher.start());
                // 排除注释行
                String trimmed = line.trim();
                if (trimmed.startsWith("//") || trimmed.startsWith("*")) {
                    continue;
                }
                violations.add(className + ": " + trimmed);
            }
        }
        assertThat(violations)
                .as("业务任务锁必须使用 releaseWithOwner(key, owner) 而非 release(String)。"
                        + "当前违规调用:\n" + String.join("\n", violations))
                .isEmpty();
    }

    @Test
    void allowedUnownedReleaseClassesMustBeInWhitelist() throws IOException {
        Set<String> allJobClasses = new LinkedHashSet<>();
        Path backendRoot = backendRoot();
        try (Stream<Path> paths = Files.walk(backendRoot.resolve("src/main/java"))) {
            paths.filter(p -> p.toString().endsWith("Job.java"))
                    .filter(p -> !p.toString().contains("domain/"))
                    .forEach(p -> {
                        String fileName = p.getFileName().toString().replace(".java", "");
                        try {
                            String src = Files.readString(p);
                            // 只关注调用 jobLockService.release(JobLockKeys.*) 的类
                            if (UNOWNED_RELEASE_PATTERN.matcher(src).find()) {
                                allJobClasses.add(fileName);
                            }
                        } catch (IOException ignored) {
                        }
                    });
        }
        Set<String> unknown = new LinkedHashSet<>(allJobClasses);
        unknown.removeAll(ALLOWED_UNOWNED_RELEASE_CLASSES);
        unknown.removeAll(BUSINESS_LOCK_CLASSES);
        assertThat(unknown)
                .as("以下 Job 类使用了无 owner 锁释放 (release(String)), 但不在豁免清单中。"
                        + "请确认是系统级互斥锁(加入 ALLOWED_UNOWNED_RELEASE_CLASSES) 还是业务锁(改用 releaseWithOwner):\n"
                        + String.join("\n", unknown))
                .isEmpty();
    }

    @Test
    void staleReconcileMustUseOwnerSafeRelease() throws IOException {
        Path sourcePath = findClass(backendRoot(), "StaleProductSyncJobReconcileJob");
        assertThat(sourcePath)
                .as("StaleProductSyncJobReconcileJob must exist")
                .isNotNull();
        String source = Files.readString(sourcePath);
        // 找到 reconcileStaleManualJobs 方法体内是否有 .release(JobLockKeys.X) (无 owner)
        // 应该有 releaseWithOwner 才合规
        java.util.regex.Matcher matcher = UNOWNED_RELEASE_PATTERN.matcher(source);
        int count = 0;
        while (matcher.find()) {
            String line = lineAt(source, matcher.start()).trim();
            if (line.startsWith("//") || line.startsWith("*")) {
                continue;
            }
            count++;
        }
        // StaleProductSyncJobReconcileJob 的顶层 tryAcquire 也调 release (全局锁)
        // 允许 1 处 (line 86 global lock in finally block)
        assertThat(count)
                .as("StaleProductSyncJobReconcileJob 中 stale 清理应使用 releaseWithOwner 而非 release(String)。"
                        + "当前找到 " + count + " 处违规 (允许 1 处全局锁释放)。")
                .isLessThanOrEqualTo(1);
    }

    private Path backendRoot() {
        Path cwd = Path.of(System.getProperty("user.dir"));
        // 在 backend/ 下运行时 cwd 就是 backend; 否则追加
        Path candidate = Files.exists(cwd.resolve("src/main/java")) ? cwd : cwd.resolve("backend");
        return candidate;
    }

    private Path findClass(Path backendRoot, String className) {
        Path direct = backendRoot.resolve("src/main/java/com/colonel/saas/" + className + ".java");
        if (Files.exists(direct)) {
            return direct;
        }
        try (Stream<Path> paths = Files.walk(backendRoot.resolve("src/main/java"))) {
            return paths.filter(p -> p.getFileName().toString().equals(className + ".java"))
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    private String lineAt(String source, int offset) {
        int lineStart = source.lastIndexOf('\n', offset) + 1;
        int lineEnd = source.indexOf('\n', offset);
        if (lineEnd < 0) {
            lineEnd = source.length();
        }
        return source.substring(lineStart, lineEnd);
    }
}