package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DddEventDispatcherRuntimeRetryContractTest {

    @Test
    void dispatcherShouldBeScheduledAndLockRetryableOutboxRows() throws IOException {
        String app = readProjectFile("src/main/java/com/colonel/saas/ColonelSaasApplication.java");
        String dispatcher = readProjectFile("src/main/java/com/colonel/saas/job/DomainEventDispatcherJob.java");
        String mapper = readProjectFile("src/main/java/com/colonel/saas/domain/event/DomainEventOutboxMapper.java");

        assertThat(app)
                .contains("@EnableScheduling");
        assertThat(dispatcher)
                .contains(
                        "@Component",
                        "@ConditionalOnProperty(name = \"app.domain-event.dispatch-enabled\", havingValue = \"true\", matchIfMissing = true)",
                        "@Scheduled(fixedDelayString = \"${app.domain-event.dispatch-interval-ms:5000}\")",
                        "@Transactional(rollbackFor = Exception.class)",
                        "private static final int MAX_RETRY = 3;",
                        "private static final int BATCH_SIZE = 20;",
                        "domainEventOutboxService.lockPendingEvents(MAX_RETRY, BATCH_SIZE)");
        assertThat(mapper)
                .contains(
                        "WHERE status IN ('PENDING', 'FAILED')",
                        "retry_count < COALESCE(max_retry, #{maxRetry})",
                        "next_retry_at IS NULL OR next_retry_at <= CURRENT_TIMESTAMP",
                        "FOR UPDATE SKIP LOCKED");
    }

    @Test
    void dispatchFailureShouldPersistRetryStateWithoutRollbackOnlyTrap() throws IOException {
        String dispatcher = readProjectFile("src/main/java/com/colonel/saas/job/DomainEventDispatcherJob.java");
        String configRouter = readProjectFile("src/main/java/com/colonel/saas/domain/event/ConfigChangedEventRouter.java");
        String service = readProjectFile("src/main/java/com/colonel/saas/domain/event/DomainEventOutboxService.java");

        assertThat(dispatcher)
                .contains(
                        "int retryCount = event.getRetryCount() == null ? 1 : event.getRetryCount() + 1;",
                        "int maxRetry = event.getMaxRetry() == null ? MAX_RETRY : event.getMaxRetry();",
                        "domainEventOutboxService.markFailed(event.getEventId(), retryCount, ex.getMessage(), maxRetry)",
                        "log.warn(\"Domain event dispatch failed");
        assertThat(configRouter)
                .contains(
                        "@Transactional(noRollbackFor = Exception.class)",
                        "saveConsumeLog(payload.eventId(), consumer.consumerName(), DomainEventOutboxService.CONSUME_FAILED",
                        "throw lastError");
        assertThat(service)
                .contains(
                        "String status = retryCount >= maxRetry",
                        "DomainEventStatus.DEAD.name()",
                        "DomainEventStatus.FAILED.name()",
                        "LocalDateTime nextRetry = retryCount >= maxRetry",
                        "Math.pow(2, Math.min(retryCount, 6)) * 5L",
                        "truncate(errorMessage, 2000)");
    }

    @Test
    void adminReplayShouldResetDeadEventsToPendingForNextDispatcherRun() throws IOException {
        String service = readProjectFile("src/main/java/com/colonel/saas/domain/event/DomainEventOutboxService.java");
        String mapper = readProjectFile("src/main/java/com/colonel/saas/domain/event/DomainEventOutboxMapper.java");
        String controller = readProjectFile("src/main/java/com/colonel/saas/controller/OutboxAdminController.java");

        assertThat(service)
                .contains(
                        "public void retryDeadEvent(UUID eventId)",
                        "domainEventOutboxMapper.resetToPending(eventId)");
        assertThat(mapper)
                .contains(
                        "int resetToPending(",
                        "SET status = 'PENDING'",
                        "retry_count = 0",
                        "error_message = NULL",
                        "next_retry_at = CURRENT_TIMESTAMP");
        assertThat(controller)
                .contains(
                        "@RequestMapping(\"/api/admin/outbox-events\")",
                        "@RequireRoles({RoleCodes.ADMIN})",
                        "@PostMapping(\"/{id}/retry\")",
                        "domainEventOutboxService.retryDeadEvent(eventId)");
    }

    @Test
    void existingTestsShouldCoverDispatcherFailureAndRetryBranches() throws IOException {
        String dispatcherTest = readProjectFile("src/test/java/com/colonel/saas/job/DomainEventDispatcherJobTest.java");
        String serviceTest = readProjectFile("src/test/java/com/colonel/saas/domain/event/DomainEventOutboxServiceTest.java");

        assertThat(dispatcherTest)
                .contains(
                        "dispatchPendingEvents_configChangedDispatchFails_marksFailed",
                        "verify(domainEventOutboxService).markFailed(event.getEventId(), 1, \"config boom\", 3)",
                        "dispatchPendingEvents_dispatchFails_marksFailed",
                        "verify(domainEventOutboxService).markFailed(event.getEventId(), 3, \"boom\", 3)",
                        "dispatchPendingEvents_firstFailure_retryCountStartsAtOne",
                        "dispatchPendingEvents_dryRunEnabled_doesNotDispatchOrUpdateStatus");
        assertThat(serviceTest)
                .contains(
                        "markFailed_shouldScheduleRetryAndTruncateErrorBeforeMaxRetry",
                        "markFailed_shouldMoveToDeadWhenRetryLimitReached",
                        "retryDeadEvent_shouldResetToPending",
                        "lockPendingEvents_shouldDelegateRetryAndLimit");
    }

    private static String readProjectFile(String relativePath) throws IOException {
        return Files.readString(Path.of(System.getProperty("user.dir")).resolve(relativePath))
                .replace("\r\n", "\n");
    }
}
