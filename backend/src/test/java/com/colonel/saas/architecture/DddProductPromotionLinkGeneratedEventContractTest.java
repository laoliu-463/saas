package com.colonel.saas.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DddProductPromotionLinkGeneratedEventContractTest {

    @Test
    void productPromotionLinkGeneratedEventShouldExposeAttributionPayload() throws IOException {
        assertFileContains(
                "src/main/java/com/colonel/saas/domain/product/event/ProductPromotionLinkGeneratedEvent.java",
                "record ProductPromotionLinkGeneratedEvent",
                "String activityId",
                "String productId",
                "String talentId",
                "UUID channelUserId",
                "UUID deptId",
                "UUID mappingId",
                "String pickSource",
                "String promotionLink",
                "String shortLink",
                "String scene",
                "Integer promotionScene",
                "String idempotencyKey",
                "LocalDateTime occurredAt");

        assertFileContains(
                "src/main/java/com/colonel/saas/constant/ProductDomainEventTypes.java",
                "PRODUCT_PROMOTION_LINK_GENERATED = \"ProductPromotionLinkGeneratedEvent\"");
    }

    @Test
    void productPublisherShouldWriteOutboxAndSpringEventForPromotionLinkGenerated() throws IOException {
        String publisher = readProjectFile(
                "src/main/java/com/colonel/saas/domain/product/event/ProductDomainEventPublisher.java");

        assertThat(publisher)
                .contains(
                        "public void publishPromotionLinkGenerated(",
                        "ProductPromotionLinkGeneratedEvent event = new ProductPromotionLinkGeneratedEvent(",
                        "publishSpringEvent(event)",
                        "payload.put(\"activityId\", activityId)",
                        "payload.put(\"productId\", productId)",
                        "payload.put(\"talentId\", talentId)",
                        "payload.put(\"mappingId\", mappingId == null ? null : mappingId.toString())",
                        "payload.put(\"pickSource\", pickSource)",
                        "payload.put(\"promotionLink\", promotionLink)",
                        "payload.put(\"shortLink\", shortLink)",
                        "\"ProductPromotionLinkGenerated:\" + productId + \":\" + mappingId",
                        "ProductDomainEventTypes.PRODUCT_PROMOTION_LINK_GENERATED",
                        "OutboxEventAppender.AGGREGATE_PRODUCT");

        assertFileContains(
                "src/test/java/com/colonel/saas/service/ProductDomainEventPublisherTest.java",
                "publishPromotionLinkGenerated_shouldAppendOutboxAndPublishSpringEvent",
                "ProductPromotionLinkGeneratedEvent",
                "containsEntry(\"mappingId\", mappingId.toString())",
                "eq(ProductDomainEventTypes.PRODUCT_PROMOTION_LINK_GENERATED)");
    }

    @Test
    void productServiceShouldPublishEventAfterPromotionLinkAndPickSourceMappingAreSaved() throws IOException {
        String source = readProjectFile("src/main/java/com/colonel/saas/service/ProductService.java");
        String legacyPath = section(
                source,
                "if (!StringUtils.hasText(idempotencyKey)) {",
                "String scopeKey = promotionLinkIdempotencyService.buildScopeKey");

        assertThat(legacyPath)
                .contains(
                        "generatePromotionLinkInternal(",
                        "                    talentId);");

        String idempotentPath = section(
                source,
                "DouyinPromotionGateway.PromotionLinkResult result = generatePromotionLinkInternal(",
                "promotionLinkIdempotencyService.markCompleted");

        assertThat(idempotentPath)
                .contains("                    talentId,\n                    idempotencyKey);");

        String successPath = section(
                source,
                "PromotionLink link = new PromotionLink();",
                "} catch (RuntimeException ex) {");

        assertThat(successPath)
                .contains(
                        "link.setId(UUID.randomUUID());",
                        "link.setPickSource(result.pickSource());",
                        "promotionLinkRecordFacade.save(link);",
                        "pickSourceMappingService.saveOrUpdate(",
                        "link.getId(),",
                        "publishPromotionLinkGenerated(snapshot, link, result, userId, deptId, talentId, finalPromotionScene, finalScene, idempotencyKey);");

        assertThat(countOccurrences(successPath, "publishPromotionLinkGenerated(snapshot, link, result, userId, deptId, talentId, finalPromotionScene, finalScene, idempotencyKey);"))
                .isEqualTo(2);

        String helper = section(
                source,
                "private void publishPromotionLinkGenerated(",
                "private String buildPickExtra(");
        assertThat(helper)
                .contains(
                        "productDomainEventPublisher.publishPromotionLinkGenerated(",
                        "snapshot.getActivityId()",
                        "snapshot.getProductId()",
                        "talentId",
                        "userId",
                        "deptId",
                        "link.getId()",
                        "result.pickSource()",
                        "result.promoteLink()",
                        "result.shortLink()");
    }

    @Test
    void productServiceShouldKeepIdempotencyAndEventIdentityBoundaries() throws IOException {
        String source = readProjectFile("src/main/java/com/colonel/saas/service/ProductService.java");
        assertThat(source)
                .contains(
                        "String scopeKey = promotionLinkIdempotencyService.buildScopeKey(userId, activityId, productId, idempotencyKey);",
                        "promotionLinkIdempotencyService.findCompleted(scopeKey)",
                        "promotionLinkIdempotencyService.tryAcquireInFlight(scopeKey)",
                        "promotionLinkIdempotencyService.markCompleted(scopeKey, result)",
                        "promotionLinkIdempotencyService.releaseInFlight(scopeKey)");

        String publisher = readProjectFile(
                "src/main/java/com/colonel/saas/domain/product/event/ProductDomainEventPublisher.java");
        assertThat(publisher)
                .contains(
                        "\"ProductPromotionLinkGenerated:\" + productId + \":\" + mappingId",
                        "ProductDomainEventTypes.PRODUCT_PROMOTION_LINK_GENERATED",
                        "appendOutbox(");
    }

    @Test
    void copyPromotionApplicationShouldStillOnlyCoordinateThroughSupportPort() throws IOException {
        String source = readProjectFile(
                "src/main/java/com/colonel/saas/domain/product/application/CopyPromotionApplicationService.java");

        assertThat(source)
                .contains(
                        "copyPromotionSupportPort.prepareCopyPromotionContext(",
                        "copyPromotionSupportPort.generatePromotionLinkForCopy(")
                .doesNotContain(
                        "import com.colonel.saas.service.ProductService",
                        "DouyinPromotionGateway",
                        "ProductDomainEventPublisher",
                        "publishPromotionLinkGenerated");
    }

    private static void assertFileContains(String relativePath, String... expectedFragments) throws IOException {
        String source = readProjectFile(relativePath);
        assertThat(source).as(relativePath).contains(expectedFragments);
    }

    private static String section(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        int endIndex = source.indexOf(end, startIndex);
        assertThat(startIndex).as("section start: %s", start).isGreaterThanOrEqualTo(0);
        assertThat(endIndex).as("section end: %s", end).isGreaterThan(startIndex);
        return source.substring(startIndex, endIndex).replace("\r\n", "\n");
    }

    private static int countOccurrences(String source, String fragment) {
        int count = 0;
        int index = 0;
        while ((index = source.indexOf(fragment, index)) >= 0) {
            count++;
            index += fragment.length();
        }
        return count;
    }

    private static String readProjectFile(String relativePath) throws IOException {
        return Files.readString(Path.of(System.getProperty("user.dir")).resolve(relativePath))
                .replace("\r\n", "\n");
    }
}
