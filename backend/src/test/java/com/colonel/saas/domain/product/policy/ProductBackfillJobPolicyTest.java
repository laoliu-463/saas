package com.colonel.saas.domain.product.policy;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProductBackfillJobPolicyTest {

    private final ProductBackfillJobPolicy policy = new ProductBackfillJobPolicy();

    @Test
    void newJobId_shouldKeepLegacyProductBackfillPrefix() {
        UUID uuid = UUID.fromString("00000000-0000-0000-0000-000000000123");

        assertThat(policy.newJobId(uuid))
                .isEqualTo("product-backfill-00000000-0000-0000-0000-000000000123");
    }

    @Test
    void statusFromCounts_shouldKeepLegacyBackfillStatusSemantics() {
        assertThat(policy.statusFromCounts(0, 0, 0, 0)).isEqualTo("FAILED");
        assertThat(policy.statusFromCounts(2, 2, 0, 0)).isEqualTo("SUCCESS");
        assertThat(policy.statusFromCounts(2, 1, 1, 0)).isEqualTo("PARTIAL");
        assertThat(policy.statusFromCounts(2, 1, 0, 1)).isEqualTo("PARTIAL");
        assertThat(policy.statusFromCounts(2, 0, 0, 2)).isEqualTo("FAILED");
    }

    @Test
    void activityStatus_shouldKeepLegacyStopReasonSemantics() {
        assertThat(policy.statusForStopReason("API_ERROR", true)).isEqualTo("SUCCESS");
        assertThat(policy.statusForStopReason("MAX_PAGES_REACHED", false)).isEqualTo("INCOMPLETE_MAX_PAGES");
        assertThat(policy.statusForStopReason("MAX_ROWS_REACHED", false)).isEqualTo("INCOMPLETE_MAX_ROWS");
        assertThat(policy.statusForStopReason("API_ERROR", false)).isEqualTo("FAILED");
        assertThat(policy.statusForStopReason("FAILED_LOCKED", false)).isEqualTo("FAILED");
        assertThat(policy.statusForStopReason("REPEATED_CURSOR", false)).isEqualTo("INCOMPLETE_CURSOR_ERROR");
    }

    @Test
    void asyncIdempotencyKey_shouldBeStableForSameNormalizedRequestAndRequester() {
        UUID requestedBy = UUID.fromString("00000000-0000-0000-0000-000000000456");
        ProductBackfillJobPolicy.BackfillRequestIdentity first =
                new ProductBackfillJobPolicy.BackfillRequestIdentity(
                        "CUSTOM_ACTIVITY_IDS",
                        List.of("ACT-1", "ACT-2"),
                        20,
                        50,
                        1000,
                        50_000,
                        true,
                        false,
                        "DEFERRED",
                        requestedBy);
        ProductBackfillJobPolicy.BackfillRequestIdentity second =
                new ProductBackfillJobPolicy.BackfillRequestIdentity(
                        "CUSTOM_ACTIVITY_IDS",
                        List.of("ACT-1", "ACT-2"),
                        20,
                        50,
                        1000,
                        50_000,
                        true,
                        false,
                        "DEFERRED",
                        requestedBy);

        assertThat(policy.asyncIdempotencyKey(first))
                .isEqualTo(policy.asyncIdempotencyKey(second))
                .contains("CUSTOM_ACTIVITY_IDS")
                .contains("ACT-1,ACT-2")
                .contains(requestedBy.toString());
    }
}
