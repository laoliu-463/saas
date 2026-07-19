package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DddOutboxInventoryContractTest {

    @Test
    void outboxEntityShouldExposeDurableEventLedgerFields() throws IOException {
        String entity = readProjectFile("src/main/java/com/colonel/saas/domain/event/DomainEventOutbox.java");
        String status = readProjectFile("src/main/java/com/colonel/saas/domain/event/DomainEventStatus.java");

        assertThat(entity)
                .contains(
                        "@TableName(value = \"domain_event_outbox\", autoResultMap = true)",
                        "@TableId(value = \"event_id\", type = IdType.INPUT)",
                        "private UUID eventId;",
                        "private String eventType;",
                        "private String aggregateType;",
                        "private String aggregateId;",
                        "private Integer eventVersion;",
                        "private String payload;",
                        "private String status;",
                        "private Integer retryCount;",
                        "private String errorMessage;",
                        "private LocalDateTime occurredAt;",
                        "private LocalDateTime publishedAt;",
                        "private String createdBy;",
                        "private String traceId;",
                        "@TableField(\"event_key\")",
                        "private String eventKey;",
                        "@TableField(value = \"headers\", typeHandler = JsonbTypeHandler.class)",
                        "private String headers;",
                        "@TableField(\"max_retry\")",
                        "private Integer maxRetry;",
                        "@TableField(\"next_retry_at\")",
                        "private LocalDateTime nextRetryAt;");

        assertThat(status)
                .contains("PENDING", "PROCESSING", "PUBLISHED", "FAILED", "DEAD");
    }

    @Test
    void outboxMapperAndServiceShouldCoverLockingStateTransitionsAndReplay() throws IOException {
        String mapper = readProjectFile("src/main/java/com/colonel/saas/domain/event/DomainEventOutboxMapper.java");
        String service = readProjectFile("src/main/java/com/colonel/saas/domain/event/DomainEventOutboxService.java");

        assertThat(mapper)
                .contains(
                        "List<DomainEventOutbox> lockPendingEvents",
                        "WHERE status IN ('PENDING', 'FAILED')",
                        "retry_count < COALESCE(max_retry, #{maxRetry})",
                        "next_retry_at IS NULL OR next_retry_at <= CURRENT_TIMESTAMP",
                        "FOR UPDATE SKIP LOCKED",
                        "int updateDispatchResult(",
                        "int resetToPending(",
                        "status = 'PENDING'",
                        "retry_count = 0",
                        "next_retry_at = CURRENT_TIMESTAMP");

        assertThat(service)
                .contains(
                        "public static final String CONSUME_SUCCESS = \"SUCCESS\";",
                        "public static final String CONSUME_FAILED = \"FAILED\";",
                        "public List<DomainEventOutbox> lockPendingEvents(int maxRetry, int limit)",
                        "public void markPublished(UUID eventId, int retryCount)",
                        "DomainEventStatus.PUBLISHED.name()",
                        "public void markFailed(UUID eventId, int retryCount, String errorMessage, int maxRetry)",
                        "DomainEventStatus.DEAD.name()",
                        "DomainEventStatus.FAILED.name()",
                        "truncate(errorMessage, 2000)",
                        "public void retryDeadEvent(UUID eventId)",
                        "domainEventOutboxMapper.resetToPending(eventId)",
                        "public List<DomainEventOutbox> pageEvents(String status, long page, long size)",
                        "public DomainEventOutbox findById(UUID eventId)");
    }

    @Test
    void outboxAppenderAndAdminEndpointShouldKeepIdempotencyAndManualReplayDiscoverable() throws IOException {
        String appender = readProjectFile("src/main/java/com/colonel/saas/domain/event/OutboxEventAppender.java");
        String controller = readProjectFile("src/main/java/com/colonel/saas/controller/OutboxAdminController.java");

        assertThat(appender)
                .contains(
                        "public static final String AGGREGATE_PRODUCT = \"PRODUCT\";",
                        "public static final String AGGREGATE_ACTIVITY = \"ACTIVITY\";",
                        "public static final String AGGREGATE_PARTNER = \"PARTNER\";",
                        "public static final String AGGREGATE_SAMPLE = \"SAMPLE\";",
                        "public static final String AGGREGATE_ORDER = \"ORDER\";",
                        "public UUID appendIfAbsent(",
                        "eq(DomainEventOutbox::getEventKey, eventKey)",
                        "return existing.getEventId();",
                        "outbox.setStatus(DomainEventStatus.PENDING.name());",
                        "outbox.setRetryCount(0);",
                        "outbox.setMaxRetry(5);",
                        "domainEventOutboxMapper.insert(outbox);");

        assertThat(controller)
                .contains(
                        "@RequestMapping(\"/api/admin/outbox-events\")",
                        "@RequireRoles({RoleCodes.ADMIN})",
                        "@GetMapping",
                        "domainEventOutboxService.pageEvents(status, page, size)",
                        "@PostMapping(\"/{id}/retry\")",
                        "domainEventOutboxService.retryDeadEvent(eventId)");
    }

    @Test
    void existingTestsShouldKeepOutboxInventoryExecutable() throws IOException {
        assertFileContains(
                "src/test/java/com/colonel/saas/domain/event/OutboxEventAppenderTest.java",
                "appendIfAbsent_shouldInsertWhenKeyNotExists",
                "appendIfAbsent_shouldSkipDuplicateKey",
                "verify(domainEventOutboxMapper).insert(captor.capture())",
                "verify(domainEventOutboxMapper, never()).insert(any())");

        assertFileContains(
                "src/test/java/com/colonel/saas/domain/event/DomainEventOutboxServiceTest.java",
                "saveConfigChangedEvent_shouldInsertPendingOutboxWithFirstConfigKey",
                "markPublished_shouldPersistPublishedStatusAndPublishedAt",
                "markFailed_shouldScheduleRetryAndTruncateErrorBeforeMaxRetry",
                "markFailed_shouldMoveToDeadWhenRetryLimitReached",
                "retryDeadEvent_shouldResetToPending",
                "lockPendingEvents_shouldDelegateRetryAndLimit",
                "pageEvents_shouldTrimStatusAndReturnPagedRecords",
                "findById_shouldDelegateSelectById");

        assertFileContains(
                "src/test/java/com/colonel/saas/architecture/DddOutbox001OrderRoutingTest.java",
                "appendOrderSynced_shouldWriteOutboxWhenRoutingEnabled",
                "republishSpringEvent_shouldDeserializeOrderSyncedEvent",
                "orderRefundFactSynced_shouldWriteOutboxAndRepublishSpringEvent");
    }

    private static void assertFileContains(String relativePath, String... expectedFragments) throws IOException {
        String source = readProjectFile(relativePath);
        assertThat(source).as(relativePath).contains(expectedFragments);
    }

    private static String readProjectFile(String relativePath) throws IOException {
        return Files.readString(Path.of(System.getProperty("user.dir")).resolve(relativePath))
                .replace("\r\n", "\n");
    }
}
