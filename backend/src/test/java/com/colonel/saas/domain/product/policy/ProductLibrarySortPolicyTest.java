package com.colonel.saas.domain.product.policy;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProductLibrarySortPolicyTest {

    private final ProductLibrarySortPolicy policy = new ProductLibrarySortPolicy(new ProductDisplayPolicy());

    @Test
    void sort_shouldKeepLegacyDefaultOrder() {
        ProductSortFixture highCommission = product("high-commission", null, false, null, "50.00", daysAgo(2));
        ProductSortFixture promoted = product("promoted", "https://promo.example", false, null, "5.00", daysAgo(3));
        ProductSortFixture newest = product("newest", null, false, null, "1.00", daysAgo(1));
        ProductSortFixture pinned = product("pinned", null, true, daysAhead(1), "0.50", daysAgo(10));

        List<ProductSortFixture> products = new ArrayList<>(List.of(highCommission, promoted, newest, pinned));

        policy.sort(products, "default", ProductSortFixture::sortKey);

        assertThat(products)
                .extracting(ProductSortFixture::productId)
                .containsExactly("pinned", "promoted", "high-commission", "newest");
    }

    @Test
    void sort_shouldKeepLegacyLatestOrder() {
        ProductSortFixture pinnedOld = product("pinned-old", null, true, daysAhead(1), "99.00", daysAgo(3));
        ProductSortFixture newest = product("newest", null, false, null, "1.00", daysAgo(1));
        ProductSortFixture promotedMiddle = product("promoted-middle", "https://promo.example", false, null, "80.00", daysAgo(2));

        List<ProductSortFixture> products = new ArrayList<>(List.of(pinnedOld, newest, promotedMiddle));

        policy.sort(products, "latest", ProductSortFixture::sortKey);

        assertThat(products)
                .extracting(ProductSortFixture::productId)
                .containsExactly("newest", "promoted-middle", "pinned-old");
    }

    private static ProductSortFixture product(
            String productId,
            String promoteLink,
            boolean pinned,
            LocalDateTime pinnedUntil,
            String cosRatio,
            LocalDateTime selectedAt) {
        return new ProductSortFixture(productId, new ProductLibrarySortPolicy.LibrarySortKey(
                pinned,
                pinnedUntil,
                promoteLink,
                null,
                new BigDecimal(cosRatio),
                selectedAt));
    }

    private static LocalDateTime daysAgo(int days) {
        return LocalDateTime.now().minusDays(days);
    }

    private static LocalDateTime daysAhead(int days) {
        return LocalDateTime.now().plusDays(days);
    }

    private record ProductSortFixture(
            String productId,
            ProductLibrarySortPolicy.LibrarySortKey sortKey) {
    }
}
