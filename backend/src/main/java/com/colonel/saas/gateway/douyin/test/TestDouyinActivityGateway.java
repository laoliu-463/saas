package com.colonel.saas.gateway.douyin.test;

import com.colonel.saas.douyin.api.ActivityApi;
import com.colonel.saas.gateway.douyin.DouyinActivityGateway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
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

@Component
@ConditionalOnProperty(name = "douyin.test.enabled", havingValue = "true")
public class TestDouyinActivityGateway implements DouyinActivityGateway {

    @Override
    public ActivityListResult listActivities(ActivityListQuery query) {
        List<ActivityItem> source = buildTestActivities();
        List<ActivityItem> filtered = source.stream()
                .filter(item -> query.status() == null || query.status() == 0 || item.status() == query.status())
                .filter(item -> matchesKeyword(item, query.activityInfo()))
                .sorted(buildComparator(query.searchType(), query.sortType()))
                .toList();
        long page = query.page() == null || query.page() < 1 ? 1 : query.page();
        long pageSize = query.pageSize() == null || query.pageSize() < 1 ? 20 : query.pageSize();
        int from = (int) ((page - 1) * pageSize);
        int to = Math.min(from + (int) pageSize, filtered.size());
        List<ActivityItem> pageList = from >= filtered.size() ? List.of() : filtered.subList(from, to);
        return new ActivityListResult(true, 11111111L, filtered.size(), pageList);
    }

    @Override
    public ActivityProductListResult listActivityProducts(ActivityProductListQuery query) {
        long activitySeed = asLong(query.activityId(), 0L);
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
                .filter(item -> query.status() == null || item.status() == query.status())
                .filter(item -> {
                    if (query.productInfo() == null || query.productInfo().isBlank()) {
                        return true;
                    }
                    String keyword = query.productInfo().trim().toLowerCase(java.util.Locale.ROOT);
                    return item.title().toLowerCase(java.util.Locale.ROOT).contains(keyword)
                            || String.valueOf(item.productId()).contains(keyword);
                })
                .toList();
        int pageSize = Math.min(Math.max(query.count() == null ? 20 : query.count(), 1), 20);
        long mode = query.retrieveMode() == null ? 1L : query.retrieveMode();
        int from = mode == 0L
                ? (int) (((query.page() == null || query.page() < 1 ? 1 : query.page()) - 1) * pageSize)
                : parseCursor(query.cursor());
        int to = Math.min(from + pageSize, filtered.size());
        List<ActivityProductItem> items = from >= filtered.size() ? List.of() : filtered.subList(from, to);
        String nextCursor = to >= filtered.size() ? "" : String.valueOf(to);
        return new ActivityProductListResult(true, activitySeed, 111111L,
                mode == 0L ? Long.valueOf(filtered.size()) : null, nextCursor, items);
    }

    @Override
    public Map<String, Object> createOrUpdate(ActivityApi.ActivityCreateOrUpdateCommand command) {
        long activityId = command.activityId() == null ? 900001L : command.activityId();
        return Map.of(
                "code", 10000,
                "msg", "success",
                "data", Map.of(
                        "activity_id", activityId,
                        "activity_name", command.activityName() == null ? "Mock活动" : command.activityName()
                )
        );
    }

    @Override
    public Map<String, Object> cancelActivityProduct(String appId, Map<String, Object> payload) {
        return Map.of(
                "code", 10000,
                "msg", "success",
                "data", Map.of(
                        "app_id", appId == null ? "test-app" : appId,
                        "payload", payload == null ? Map.of() : payload
                )
        );
    }

    @Override
    public Map<String, Object> activityDetail(String appId, String activityId) {
        long seed = activityId == null || activityId.isBlank() ? 0L : asLong(activityId, Math.abs(activityId.hashCode()));
        long colonelBuyinId = 46128341673481000L + (Math.floorMod(seed, 100000));
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("colonel_buyin_id", colonelBuyinId);
        detail.put("colonelBuyinId", colonelBuyinId);
        detail.put("activity_name", "Mock活动-" + activityId);
        detail.put("activityName", "Mock活动-" + activityId);
        detail.put("shop_id", 800001L);
        detail.put("shop_name", "Mock店铺");
        detail.put("commission_rate", "10");
        detail.put("service_rate", "5");
        detail.put("activity_start_time", mockActivityStartDate() + " 00:00:00");
        detail.put("activity_end_time", mockActivityEndDate() + " 23:59:59");
        detail.put("status_text", "推广中");
        return Map.of("data", Map.of("data", detail));
    }

    @Override
    public Map<String, Object> createOrUpdateActivity(ActivityMutateCommand command) {
        long id = command.activityId() == null ? 12345L : command.activityId();
        return Map.of("code", 10000, "msg", "success", "data", Map.of("activity_id", id));
    }

    private List<ActivityItem> buildTestActivities() {
        List<ActivityItem> activities = new ArrayList<>();
        int[] statusCycle = {1, 2, 3, 4, 5, 7};
        String[] categoryCycle = {"美妆个护", "女装穿搭", "家用电器", "食品饮料", "母婴用品", "运动户外"};
        String[] nameCycle = {"春季上新", "夏季爆款", "秋季清仓", "双11预热", "年货节", "新品招商"};
        for (int i = 1; i <= 36; i++) {
            int status = statusCycle[(i - 1) % statusCycle.length];
            int month = ((i - 1) % 9) + 1;
            int day = ((i - 1) % 20) + 1;
            activities.add(new ActivityItem(
                    100000L + i,
                    nameCycle[(i - 1) % nameCycle.length] + "-渠道演示活动-" + i,
                    String.format("2026-%02d-%02d 00:00:00", month, day),
                    String.format("2026-%02d-%02d 23:59:59", month, Math.min(day + 10, 28)),
                    status,
                    activityStatusText(status),
                    String.format("2026-%02d-%02d 00:00:00", Math.max(month - 1, 1), Math.max(day - 5, 1)),
                    String.format("2026-%02d-%02d 23:59:59", month, Math.max(day - 1, 1)),
                    Map.of("一级类目", categoryCycle[(i - 1) % categoryCycle.length]),
                    46128341673481000L + i
            ));
        }
        return activities;
    }

    private boolean matchesKeyword(ActivityItem item, String activityInfo) {
        if (activityInfo == null || activityInfo.isBlank()) {
            return true;
        }
        String keyword = activityInfo.trim().toLowerCase(java.util.Locale.ROOT);
        return String.valueOf(item.activityId()).toLowerCase(java.util.Locale.ROOT).contains(keyword)
                || item.activityName().toLowerCase(java.util.Locale.ROOT).contains(keyword);
    }

    private Comparator<ActivityItem> buildComparator(Long searchType, Long sortType) {
        Comparator<ActivityItem> comparator;
        if (searchType != null && searchType == 1L) {
            comparator = Comparator.comparing(ActivityItem::applicationStartTime, Comparator.nullsLast(String::compareTo));
        } else if (searchType != null && searchType == 2L) {
            comparator = Comparator.comparing(ActivityItem::applicationEndTime, Comparator.nullsLast(String::compareTo));
        } else {
            comparator = Comparator.comparing(ActivityItem::activityStartTime, Comparator.nullsLast(String::compareTo));
        }
        return sortType != null && sortType == 0L ? comparator : comparator.reversed();
    }

    private String buildProductTitle(int index, int rank) {
        String[] prefixes = {"活动演示商品", "招商主推商品", "寄样联动商品", "订单归因商品"};
        String[] suffixes = {"高佣款", "基础款", "冲量款", "复购款"};
        return prefixes[index % prefixes.length] + "-" + suffixes[rank % suffixes.length] + "-" + index;
    }

    private String buildShopName(int shopIndex) {
        String[] shopNames = {
                "活动店铺-美妆馆",
                "活动店铺-女装馆",
                "活动店铺-母婴馆",
                "活动店铺-数码馆",
                "活动店铺-食品馆",
                "活动店铺-家清馆",
                "活动店铺-家电馆",
                "活动店铺-日用馆",
                "活动店铺-运动馆"
        };
        return shopNames[Math.floorMod(shopIndex, shopNames.length)];
    }

    private String activityStatusText(int status) {
        return switch (status) {
            case 1 -> "未上线";
            case 2 -> "报名未开始";
            case 3 -> "报名中";
            case 4 -> "推广未开始";
            case 5 -> "推广中";
            case 7 -> "报名结束";
            default -> "任意状态";
        };
    }
}
