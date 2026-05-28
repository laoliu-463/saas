package com.colonel.saas.gateway.douyin.test;

import com.colonel.saas.gateway.douyin.DouyinProductGateway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.colonel.saas.gateway.douyin.test.TestMockActivityProductSupport.asLong;
import static com.colonel.saas.gateway.douyin.test.TestMockActivityProductSupport.formatPriceText;
import static com.colonel.saas.gateway.douyin.test.TestMockActivityProductSupport.mockActivityEndDate;
import static com.colonel.saas.gateway.douyin.test.TestMockActivityProductSupport.mockActivityStartDate;
import static com.colonel.saas.gateway.douyin.test.TestMockActivityProductSupport.mockPromotionEndDate;
import static com.colonel.saas.gateway.douyin.test.TestMockActivityProductSupport.mockPromotionStartDate;
import static com.colonel.saas.gateway.douyin.test.TestMockActivityProductSupport.parseCursor;
import static com.colonel.saas.gateway.douyin.test.TestMockActivityProductSupport.productStatusText;
import static com.colonel.saas.gateway.douyin.test.TestMockActivityProductSupport.resolveMockProductStatus;

/**
 * 测试环境抖店商品网关适配器。
 * <p>
 * 实现 {@link DouyinProductGateway} 接口，在 {@code douyin.test.enabled=true} 时替代真实的
 * 抖店商品网关，为本地开发和 test 环境提供不依赖真实抖店开放平台的 Mock 商品数据。
 * </p>
 *
 * <ul>
 *   <li><b>活动商品查询（queryActivityProducts）</b>：根据 activityId 种子值确定性地生成 80 条 Mock 商品，支持状态过滤、关键词搜索、游标/分页翻页</li>
 *   <li><b>商品 SKU 查询（queryProductSkus）</b>：根据 productId 生成 2 条固定的 Mock SKU（标准装、加量装），用于 SKU 映射验证</li>
 * </ul>
 *
 * <p>架构角色：Gateway 测试适配器（Test Double），所属领域：商品域。
 * 与真实网关的关系：实现同一 {@link DouyinProductGateway} 接口，通过 {@code douyin.test.enabled}
 * 属性切换。Mock 数据的生成逻辑与 {@link TestDouyinActivityGateway} 共享
 * {@link TestMockActivityProductSupport} 工具类，确保同一 activityId 在两个网关中产生的商品数据一致。</p>
 *
 * @see DouyinProductGateway
 * @see TestMockActivityProductSupport
 * @see TestDouyinActivityGateway
 */
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
            int itemStatus = resolveMockProductStatus(activitySeed, rank);
            long productId = 900000L + rank + activitySeed * 100L;
            long price = 9900L + rank * 13L + seedOffset * 17L;
            int shopIndex = (rank + shopOffset) % 9;
            all.add(new ActivityProductItem(
                    productId,
                    buildProductTitle(i, rank),
                    "https://example.com/test-product-" + i + ".png",
                    price,
                    formatPriceText(price),
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
                    mockActivityStartDate(),
                    mockActivityEndDate(),
                    mockPromotionStartDate(),
                    mockPromotionEndDate(),
                    "https://example.com/test-detail/" + productId,
                    String.valueOf(46128341673481000L + (productId % 1000)),
                    Map.of("origin_colonel_buyin_id", String.valueOf(46128341673481000L + (productId % 1000)))
            ));
        }
        List<ActivityProductItem> filtered = all.stream()
                .filter(item -> request.status() == null || item.status() == request.status())
                .filter(item -> {
                    if (request.productInfo() == null || request.productInfo().isBlank()) {
                        return true;
                    }
                    String keyword = request.productInfo().trim().toLowerCase(java.util.Locale.ROOT);
                    return item.title().toLowerCase(java.util.Locale.ROOT).contains(keyword)
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
}
