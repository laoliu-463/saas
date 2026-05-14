package com.colonel.saas.gateway.douyin.test;

import com.colonel.saas.gateway.douyin.DouyinProductGateway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
@ConditionalOnProperty(name = "douyin.test.enabled", havingValue = "true")
public class TestDouyinProductGateway implements DouyinProductGateway {

    @Override
    public ActivityProductListResult queryActivityProducts(ActivityProductQueryRequest request) {
        long activitySeed = asLong(request.activityId(), 0L);
        int seedOffset = (int) (activitySeed % 37);
        int shopOffset = (int) (activitySeed % 9);
        List<ActivityProductItem> all = new ArrayList<>();
        for (int i = 1; i <= 80; i++) {
            int rank = i + seedOffset;
            int itemStatus = switch (rank % 6) {
                case 0 -> 0;
                case 1 -> 1;
                case 2 -> 2;
                case 3 -> 3;
                case 4 -> 6;
                default -> 4;
            };
            long productId = 900000L + rank + activitySeed * 100L;
            long price = 9900L + rank * 13L + seedOffset * 17L;
            int shopIndex = (rank + shopOffset) % 9;
            all.add(new ActivityProductItem(
                    productId,
                    buildProductTitle(i, rank),
                    "https://example.com/test-product-" + i + ".png",
                    price,
                    String.format(Locale.ROOT, "%.2f", price / 100.0),
                    10L + (rank % 20),
                    1000L + rank * 11L + seedOffset * 7L,
                    1000L + (rank % 20) * 100L,
                    (10 + (rank % 20)) + "%",
                    rank % 2,
                    rank % 2 == 1 ? "阶梯佣金" : "固定服务费率",
                    rank % 2 == 1 ? String.valueOf(8 + (seedOffset % 4)) : null,
                    rank % 2 == 1 ? Long.valueOf(6 + (seedOffset % 5)) : null,
                    rank % 3 == 0,
                    rank % 5 != 0,
                    200L + rank * 3L + seedOffset * 5L,
                    800000L + shopIndex,
                    buildShopName(shopIndex),
                    "4." + (70 + (rank % 20)),
                    itemStatus,
                    productStatusText(itemStatus),
                    rank % 2 == 0 ? "美妆个护" : "女装穿搭",
                    String.valueOf(Math.max(1000 - rank, 0)),
                    i % 2 == 0 ? "满199减20" : "满299减40",
                    String.format("2026-04-%02d", 1 + (seedOffset % 9)),
                    String.format("2026-05-%02d", 10 + (seedOffset % 9)),
                    String.format("2026-04-%02d", 1 + (seedOffset % 9)),
                    String.format("2026-05-%02d", 10 + (seedOffset % 9)),
                    "https://example.com/test-detail/" + productId,
                    null,
                    java.util.Map.of()
            ));
        }
        List<ActivityProductItem> filtered = all.stream()
                .filter(item -> request.status() == null || item.status() == request.status())
                .filter(item -> {
                    if (request.productInfo() == null || request.productInfo().isBlank()) {
                        return true;
                    }
                    String keyword = request.productInfo().trim().toLowerCase(Locale.ROOT);
                    return item.title().toLowerCase(Locale.ROOT).contains(keyword)
                            || String.valueOf(item.productId()).contains(keyword);
                })
                .toList();
        int pageSize = Math.min(Math.max(request.count() == null ? 20 : request.count(), 1), 20);
        long mode = request.retrieveMode() == null ? 1L : request.retrieveMode();
        int from = mode == 0L
                ? (int) (((request.page() == null || request.page() < 1 ? 1 : request.page()) - 1) * pageSize)
                : parseCursor(request.cursor());
        int to = Math.min(from + pageSize, filtered.size());
        List<ActivityProductItem> items = from >= filtered.size() ? List.of() : filtered.subList(from, to);
        String nextCursor = to >= filtered.size() ? "" : String.valueOf(to);
        return new ActivityProductListResult(true, activitySeed, 111111L,
                mode == 0L ? Long.valueOf(filtered.size()) : null, nextCursor, items);
    }

    @Override
    public List<ProductSkuResult> queryProductSkus(String productId) {
        long productSeed = asLong(productId, 0L);
        return List.of(
                new ProductSkuResult(productId + "-SKU1", "标准装", 9900L + (productSeed % 1000), 99, "https://example.com/test-sku-1.png"),
                new ProductSkuResult(productId + "-SKU2", "加量装", 12900L + (productSeed % 1000), 48, "https://example.com/test-sku-2.png")
        );
    }

    private String buildProductTitle(int index, int rank) {
        String[] prefixes = {"主演示商品", "寄样演示商品", "新客拉新商品", "订单排查商品"};
        String[] suffixes = {"高佣款", "基础款", "冲量款", "复购款"};
        return prefixes[index % prefixes.length] + "-" + suffixes[rank % suffixes.length] + "-" + index;
    }

    private String buildShopName(int shopIndex) {
        String[] shopNames = {
                "演示店铺-美妆馆",
                "演示店铺-女装馆",
                "演示店铺-母婴馆",
                "演示店铺-数码馆",
                "演示店铺-食品馆",
                "演示店铺-家清馆",
                "演示店铺-家电馆",
                "演示店铺-日用馆",
                "演示店铺-运动馆"
        };
        return shopNames[Math.floorMod(shopIndex, shopNames.length)];
    }

    private int parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return 0;
        }
        try {
            return Math.max(Integer.parseInt(cursor.trim()), 0);
        } catch (Exception e) {
            return 0;
        }
    }

    private long asLong(String value, long defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private String productStatusText(int status) {
        return switch (status) {
            case 0 -> "待审核";
            case 1 -> "推广中";
            case 2 -> "申请未通过";
            case 3 -> "合作已终止";
            case 4 -> "合作前取消";
            case 6 -> "合作已到期";
            default -> "未知状态";
        };
    }
}

