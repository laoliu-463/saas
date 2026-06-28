package com.colonel.saas.domain.product.policy;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * 商品库列表排序策略。
 */
public class ProductLibrarySortPolicy {

    private final ProductDisplayPolicy productDisplayPolicy;

    public ProductLibrarySortPolicy(ProductDisplayPolicy productDisplayPolicy) {
        this.productDisplayPolicy = productDisplayPolicy;
    }

    public <T> void sort(List<T> products, String sortBy, Function<T, LibrarySortKey> keyExtractor) {
        if (products == null || products.size() <= 1) {
            return;
        }
        Objects.requireNonNull(keyExtractor, "keyExtractor must not be null");
        if ("latest".equals(sortBy)) {
            products.sort(Comparator.comparing(
                    product -> selectedAt(keyExtractor.apply(product)),
                    Comparator.nullsLast(Comparator.reverseOrder())));
            return;
        }
        products.sort((left, right) -> compareDefault(
                keyExtractor.apply(left),
                keyExtractor.apply(right)));
    }

    private int compareDefault(LibrarySortKey left, LibrarySortKey right) {
        boolean leftPinned = isPinnedAndNotExpired(left);
        boolean rightPinned = isPinnedAndNotExpired(right);
        if (leftPinned != rightPinned) {
            return leftPinned ? -1 : 1;
        }

        int sub = productDisplayPolicy.compareLibraryPresentation(
                new ProductDisplayPolicy.LibraryPresentationKey(
                        productDisplayPolicy.hasPromotionLink(promoteLink(left), shortLink(left)),
                        cosRatio(left),
                        selectedAt(left)),
                new ProductDisplayPolicy.LibraryPresentationKey(
                        productDisplayPolicy.hasPromotionLink(promoteLink(right), shortLink(right)),
                        cosRatio(right),
                        selectedAt(right)));
        if (sub != 0) {
            return sub;
        }

        LocalDateTime leftSelectedAt = selectedAt(left);
        LocalDateTime rightSelectedAt = selectedAt(right);
        if (leftSelectedAt == null && rightSelectedAt == null) {
            return 0;
        }
        if (leftSelectedAt == null) {
            return 1;
        }
        if (rightSelectedAt == null) {
            return -1;
        }
        return rightSelectedAt.compareTo(leftSelectedAt);
    }

    private boolean isPinnedAndNotExpired(LibrarySortKey key) {
        return ProductPinPolicy.isPinnedForPresentation(
                key != null && key.pinned(),
                key == null ? null : key.pinnedUntil(),
                LocalDateTime.now());
    }

    private String promoteLink(LibrarySortKey key) {
        return key == null ? null : key.promoteLink();
    }

    private String shortLink(LibrarySortKey key) {
        return key == null ? null : key.shortLink();
    }

    private BigDecimal cosRatio(LibrarySortKey key) {
        return key == null ? null : key.cosRatio();
    }

    private LocalDateTime selectedAt(LibrarySortKey key) {
        return key == null ? null : key.selectedAt();
    }

    public record LibrarySortKey(
            boolean pinned,
            LocalDateTime pinnedUntil,
            String promoteLink,
            String shortLink,
            BigDecimal cosRatio,
            LocalDateTime selectedAt) {
    }
}
