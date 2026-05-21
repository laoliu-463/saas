package com.colonel.saas.gateway.douyin.contract;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.entity.PickSourceMapping;
import com.colonel.saas.gateway.douyin.DouyinTokenGateway;
import com.colonel.saas.gateway.douyin.DouyinActivityGateway;
import com.colonel.saas.gateway.douyin.DouyinOrderGateway;
import com.colonel.saas.gateway.douyin.DouyinProductGateway;
import com.colonel.saas.gateway.douyin.DouyinPromotionGateway;
import com.colonel.saas.mapper.PickSourceMappingMapper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Component
public class DouyinContractFixtureProvider {

    private static final String DEFAULT_APP_KEY = "7623665273727387199";
    private static final String DEFAULT_SHOP_ID = "56591058";
    private static final String DEFAULT_AUTH_ID = "7351155267604218149";
    private static final String DEFAULT_ROLE_NAME = "招商团长";
    private static final String DEFAULT_INSTITUTION_NAME = "星链达客";
    private static final String DEFAULT_SHOP_NAME = "星链达客测试店";
    private static final String DEFAULT_SHOP_SCORE = "4.90";
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final PickSourceMappingMapper pickSourceMappingMapper;

    public DouyinContractFixtureProvider(PickSourceMappingMapper pickSourceMappingMapper) {
        this.pickSourceMappingMapper = pickSourceMappingMapper;
    }

    public String appKey() {
        return DEFAULT_APP_KEY;
    }

    public String shopId() {
        return DEFAULT_SHOP_ID;
    }

    public String authId() {
        return DEFAULT_AUTH_ID;
    }

    public DouyinTokenGateway.TokenPayload buildTokenPayload(String appId, String refreshTokenHint) {
        String finalAppId = hasText(appId) ? appId.trim() : DEFAULT_APP_KEY;
        String suffix = finalAppId.length() <= 6 ? finalAppId : finalAppId.substring(finalAppId.length() - 6);
        String accessToken = "contract_access_" + suffix + "_" + Instant.now().getEpochSecond();
        String refreshToken = hasText(refreshTokenHint)
                ? refreshTokenHint.trim()
                : "contract_refresh_" + suffix;
        return new DouyinTokenGateway.TokenPayload(
                accessToken,
                refreshToken,
                7200L,
                DEFAULT_AUTH_ID,
                "institution",
                1L
        );
    }

    public Map<String, Object> buildInstitutionInfoResponse(String appId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("app_key", hasText(appId) ? appId.trim() : DEFAULT_APP_KEY);
        data.put("shop_id", DEFAULT_SHOP_ID);
        data.put("auth_id", DEFAULT_AUTH_ID);
        data.put("buyin_id", DEFAULT_AUTH_ID);
        data.put("institution_id", DEFAULT_AUTH_ID);
        data.put("role_name", DEFAULT_ROLE_NAME);
        data.put("institution_name", DEFAULT_INSTITUTION_NAME);
        data.put("auth_subject_type", "self_use");
        return success("buyin.institutionInfo", data);
    }

    public DouyinActivityGateway.ActivityListResult buildActivityListResult(
            DouyinActivityGateway.ActivityListQuery query) {
        List<DouyinActivityGateway.ActivityItem> filtered = filterActivities(
                contractActivities(),
                query.status(),
                query.activityInfo(),
                query.searchType(),
                query.sortType()
        );
        long page = query.page() == null || query.page() < 1 ? 1 : query.page();
        long pageSize = query.pageSize() == null || query.pageSize() < 1 ? 20 : query.pageSize();
        int from = (int) ((page - 1) * pageSize);
        int to = Math.min(from + (int) pageSize, filtered.size());
        List<DouyinActivityGateway.ActivityItem> pageList = from >= filtered.size()
                ? List.of()
                : filtered.subList(from, to);
        return new DouyinActivityGateway.ActivityListResult(
                false,
                Long.parseLong(DEFAULT_AUTH_ID),
                filtered.size(),
                pageList
        );
    }

    public Map<String, Object> buildActivityListResponse(
            String appId,
            Integer status,
            Long searchType,
            Long sortType,
            Long page,
            Long pageSize,
            String activityInfo) {
        List<DouyinActivityGateway.ActivityItem> filtered = filterActivities(
                contractActivities(),
                status,
                activityInfo,
                searchType,
                sortType
        );
        long finalPage = page == null || page < 1 ? 1 : page;
        long finalPageSize = pageSize == null || pageSize < 1 ? 20 : pageSize;
        int from = (int) ((finalPage - 1) * finalPageSize);
        int to = Math.min(from + (int) finalPageSize, filtered.size());
        List<Map<String, Object>> activityList = (from >= filtered.size() ? List.<DouyinActivityGateway.ActivityItem>of() : filtered.subList(from, to))
                .stream()
                .map(this::toActivityRaw)
                .toList();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("activity_list", activityList);
        data.put("total", filtered.size());
        data.put("institution_id", DEFAULT_AUTH_ID);
        data.put("app_key", hasText(appId) ? appId.trim() : DEFAULT_APP_KEY);
        return success("alliance.instituteColonelActivityList", data);
    }

    public Map<String, Object> buildActivityDetailResponse(String appId, String activityId) {
        DouyinActivityGateway.ActivityItem item = contractActivities().stream()
                .filter(activity -> String.valueOf(activity.activityId()).equals(activityId))
                .findFirst()
                .orElse(contractActivities().get(0));
        Map<String, Object> data = toActivityRaw(item);
        data.put("app_key", hasText(appId) ? appId.trim() : DEFAULT_APP_KEY);
        return success("buyin.colonelActivityDetail", data);
    }

    public DouyinActivityGateway.ActivityProductListResult buildActivityProductListResult(
            DouyinActivityGateway.ActivityProductListQuery query) {
        List<DouyinProductGateway.ActivityProductItem> filtered = filterProducts(
                contractProducts(query.activityId()),
                query.status(),
                query.productInfo()
        );
        int pageSize = Math.min(Math.max(query.count() == null ? 20 : query.count(), 1), 20);
        long mode = query.retrieveMode() == null ? 1L : query.retrieveMode();
        int from = mode == 0L
                ? (int) (((query.page() == null || query.page() < 1 ? 1 : query.page()) - 1) * pageSize)
                : parseCursor(query.cursor());
        int to = Math.min(from + pageSize, filtered.size());
        List<DouyinActivityGateway.ActivityProductItem> items = from >= filtered.size()
                ? List.of()
                : filtered.subList(from, to).stream().map(this::toColonelActivityProductItem).toList();
        String nextCursor = to >= filtered.size() ? "" : String.valueOf(to);
        return new DouyinActivityGateway.ActivityProductListResult(
                false,
                asLong(query.activityId(), 0L),
                Long.parseLong(DEFAULT_AUTH_ID),
                mode == 0L ? Long.valueOf(filtered.size()) : null,
                nextCursor,
                items
        );
    }

    public DouyinProductGateway.ActivityProductListResult buildProductListResult(
            DouyinProductGateway.ActivityProductQueryRequest request) {
        List<DouyinProductGateway.ActivityProductItem> filtered = filterProducts(
                contractProducts(request.activityId()),
                request.status(),
                request.productInfo()
        );
        int pageSize = Math.min(Math.max(request.count() == null ? 20 : request.count(), 1), 20);
        long mode = request.retrieveMode() == null ? 1L : request.retrieveMode();
        int from = mode == 0L
                ? (int) (((request.page() == null || request.page() < 1 ? 1 : request.page()) - 1) * pageSize)
                : parseCursor(request.cursor());
        int to = Math.min(from + pageSize, filtered.size());
        List<DouyinProductGateway.ActivityProductItem> items = from >= filtered.size()
                ? List.of()
                : filtered.subList(from, to);
        String nextCursor = to >= filtered.size() ? "" : String.valueOf(to);
        return new DouyinProductGateway.ActivityProductListResult(
                false,
                asLong(request.activityId(), 0L),
                Long.parseLong(DEFAULT_AUTH_ID),
                mode == 0L ? Long.valueOf(filtered.size()) : null,
                nextCursor,
                items
        );
    }

    public Map<String, Object> buildProductListResponse(
            String appId,
            String activityId,
            Integer count,
            String cursor,
            String productInfo,
            Integer status,
            Long retrieveMode,
            Long page) {
        DouyinProductGateway.ActivityProductListResult result = buildProductListResult(
                new DouyinProductGateway.ActivityProductQueryRequest(
                        appId,
                        activityId,
                        4L,
                        1L,
                        count,
                        null,
                        null,
                        productInfo,
                        status,
                        retrieveMode,
                        cursor,
                        page
                )
        );
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("institution_id", result.institutionId());
        data.put("total", result.total());
        data.put("next_cursor", result.nextCursor());
        data.put("data", result.items().stream().map(this::toProductRaw).toList());
        data.put("app_key", hasText(appId) ? appId.trim() : DEFAULT_APP_KEY);
        return success("alliance.colonelActivityProduct", data);
    }

    public List<DouyinProductGateway.ProductSkuResult> buildProductSkus(String productId) {
        long seed = asLong(productId, 0L);
        return List.of(
                new DouyinProductGateway.ProductSkuResult(
                        productId + "-SKU1",
                        "标准装",
                        3990L + (seed % 100),
                        120,
                        "https://cdn.contract.local/sku/" + productId + "-1.png"
                ),
                new DouyinProductGateway.ProductSkuResult(
                        productId + "-SKU2",
                        "加量装",
                        4590L + (seed % 100),
                        48,
                        "https://cdn.contract.local/sku/" + productId + "-2.png"
                )
        );
    }

    public DouyinPromotionGateway.PromotionLinkResult buildPromotionLinkResult(
            DouyinPromotionGateway.PromotionLinkCommand command) {
        String activityId = command.context() == null || !hasText(command.context().activityId())
                ? "20260428001"
                : command.context().activityId();
        String productId = command.productIds() == null || command.productIds().isEmpty()
                ? "910001"
                : String.valueOf(command.productIds().get(0));
        String tail = digitsOnly(productId);
        if (tail.length() > 3) {
            tail = tail.substring(tail.length() - 3);
        }
        String shortId = ("PS" + tail + "7351").toUpperCase(Locale.ROOT);
        String pickSource = "ps_" + DEFAULT_AUTH_ID + "_" + (tail.isEmpty() ? "001" : tail);
        String shortLink = command.needShortLink()
                ? "https://contract.short.link/" + shortId
                : null;
        String promoteLink = "https://haohuo.jinritemai.com/views/product/item2?id="
                + productId
                + "&activity_id="
                + activityId
                + "&pick_source="
                + pickSource;
        String uuidSeed = UUID.nameUUIDFromBytes((activityId + ":" + productId + ":" + pickSource)
                .getBytes(StandardCharsets.UTF_8)).toString();
        return new DouyinPromotionGateway.PromotionLinkResult(
                pickSource,
                shortId,
                shortId,
                shortLink,
                promoteLink,
                uuidSeed
        );
    }

    public DouyinOrderGateway.OrderListResult buildOrderListResult(DouyinOrderGateway.DouyinOrderQueryRequest request) {
        List<DouyinOrderGateway.DouyinOrderItem> orders = new ArrayList<>();
        PickSourceMapping latestMapping = latestActiveMapping();
        long baseTime = request.startTime() > 0 ? request.startTime() : Instant.now().getEpochSecond() - 3600;

        if (latestMapping != null) {
            Map<String, Object> raw = new LinkedHashMap<>();
            raw.put("product_name", "契约联调商品-已归因");
            raw.put("colonel_activity_id", latestMapping.getActivityId());
            raw.put("pick_source", latestMapping.getPickSource());
            raw.put("pick_extra", latestMapping.getShortId());
            raw.put("merchant_id", DEFAULT_SHOP_ID);
            raw.put("shop_id", DEFAULT_SHOP_ID);
            raw.put("talent_uid", hasText(latestMapping.getTalentId()) ? latestMapping.getTalentId() : DEFAULT_AUTH_ID);
            raw.put("author_id", hasText(latestMapping.getTalentId()) ? latestMapping.getTalentId() : DEFAULT_AUTH_ID);
            orders.add(new DouyinOrderGateway.DouyinOrderItem(
                    "CONTRACT_ORD_ATTR_" + latestMapping.getShortId(),
                    latestMapping.getProductId(),
                    latestMapping.getProductId(),
                    DEFAULT_SHOP_ID,
                    DEFAULT_SHOP_NAME,
                    latestMapping.getTalentId(),
                    latestMapping.getTalentName(),
                    latestMapping.getPickSource(),
                    3990L,
                    399L,
                    1,
                    baseTime + 3600,
                    baseTime + 7200,
                    raw
            ));
        }

        Map<String, Object> unknownRaw = new LinkedHashMap<>();
        unknownRaw.put("product_name", "契约联调商品-映射缺失");
        unknownRaw.put("colonel_activity_id", "20260428001");
        unknownRaw.put("pick_source", "ps_unknown_mapping_001");
        unknownRaw.put("pick_extra", "PSUNK001");
        unknownRaw.put("merchant_id", DEFAULT_SHOP_ID);
        unknownRaw.put("shop_id", DEFAULT_SHOP_ID);
        unknownRaw.put("talent_uid", DEFAULT_AUTH_ID);
        orders.add(new DouyinOrderGateway.DouyinOrderItem(
                "CONTRACT_ORD_UNATTR_1",
                "910002",
                "910002",
                DEFAULT_SHOP_ID,
                DEFAULT_SHOP_NAME,
                DEFAULT_AUTH_ID,
                "未命中映射达人",
                "ps_unknown_mapping_001",
                4590L,
                459L,
                1,
                baseTime + 5400,
                null,
                unknownRaw
        ));

        Map<String, Object> noPickRaw = new LinkedHashMap<>();
        noPickRaw.put("product_name", "契约联调商品-未带推广参数");
        noPickRaw.put("colonel_activity_id", "20260428001");
        noPickRaw.put("merchant_id", DEFAULT_SHOP_ID);
        noPickRaw.put("shop_id", DEFAULT_SHOP_ID);
        noPickRaw.put("talent_uid", DEFAULT_AUTH_ID);
        orders.add(new DouyinOrderGateway.DouyinOrderItem(
                "CONTRACT_ORD_UNATTR_2",
                "910003",
                "910003",
                DEFAULT_SHOP_ID,
                DEFAULT_SHOP_NAME,
                DEFAULT_AUTH_ID,
                "未带推广参数达人",
                null,
                2990L,
                299L,
                1,
                baseTime + 7200,
                null,
                noPickRaw
        ));

        Map<String, Object> rawResponse = new LinkedHashMap<>();
        rawResponse.put("order_count", orders.size());
        rawResponse.put("has_more", false);
        rawResponse.put("next_cursor", "0");
        rawResponse.put("upstream_mode", "contract");
        return new DouyinOrderGateway.OrderListResult(orders, false, "0", rawResponse);
    }

    public Map<String, Object> buildOrderSettlementResponse(
            String appId,
            Integer size,
            String cursor,
            String timeType,
            String startTime,
            String endTime,
            String orderIds) {
        long startEpoch = parseEpochSecond(startTime, Instant.now().getEpochSecond() - 7200);
        long endEpoch = parseEpochSecond(endTime, Instant.now().getEpochSecond());
        DouyinOrderGateway.OrderListResult result = buildOrderListResult(
                new DouyinOrderGateway.DouyinOrderQueryRequest(
                        startEpoch,
                        endEpoch,
                        size == null ? 20 : size,
                        cursor
                )
        );
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("data", result.orders().stream().map(this::toOrderRaw).toList());
        data.put("has_more", result.hasMore());
        data.put("next_cursor", result.nextCursor());
        data.put("time_type", timeType == null ? "update" : timeType);
        data.put("order_ids", orderIds);
        data.put("app_key", hasText(appId) ? appId.trim() : DEFAULT_APP_KEY);
        return success("buyin.colonelMultiSettlementOrders", data);
    }


    private List<DouyinActivityGateway.ActivityItem> contractActivities() {
        return List.of(
                new DouyinActivityGateway.ActivityItem(
                        20260428001L,
                        "4月精选联盟活动",
                        "2026-04-01 00:00:00",
                        "2026-05-01 00:00:00",
                        5,
                        "推广中",
                        "2026-03-25 00:00:00",
                        "2026-03-31 23:59:59",
                        Map.of("一级类目", "美妆个护"),
                        7351155267604218149L
                ),
                new DouyinActivityGateway.ActivityItem(
                        20260501002L,
                        "高佣爆品活动",
                        "2026-05-01 00:00:00",
                        "2026-06-01 00:00:00",
                        2,
                        "报名未开始",
                        "2026-04-25 00:00:00",
                        "2026-04-30 23:59:59",
                        Map.of("一级类目", "日用百货"),
                        7351155267604218149L
                )
        );
    }

    private List<DouyinProductGateway.ActivityProductItem> contractProducts(String activityId) {
        String finalActivityId = hasText(activityId) ? activityId.trim() : "20260428001";
        if ("20260501002".equals(finalActivityId)) {
            return List.of(
                    buildProduct(920001L, "待开始活动-可推广商品", 3990L, 1, "可推广", "日用百货", finalActivityId),
                    buildProduct(920002L, "待开始活动-待审核商品", 4590L, 0, "待审核", "日用百货", finalActivityId),
                    buildProduct(920003L, "待开始活动-合作终止商品", 2990L, 3, "合作已终止", "日用百货", finalActivityId)
            );
        }
        return List.of(
                buildProduct(910001L, "测试爆品洗脸巾", 3990L, 1, "可推广", "美妆个护", finalActivityId),
                buildProduct(910002L, "测试爆品棉柔巾", 4590L, 0, "待审核", "美妆个护", finalActivityId),
                buildProduct(910003L, "测试爆品卸妆巾", 2990L, 2, "申请未通过", "美妆个护", finalActivityId)
        );
    }

    private DouyinProductGateway.ActivityProductItem buildProduct(
            long productId,
            String title,
            long price,
            int status,
            String statusText,
            String categoryName,
            String activityId) {
        long activityCosRatio = 2000L;
        Long activityAdCosRatio = 1000L;
        return new DouyinProductGateway.ActivityProductItem(
                productId,
                title,
                "https://cdn.contract.local/product/" + productId + ".png",
                price,
                String.format(Locale.ROOT, "%.2f", price / 100.0),
                2000L,
                Math.round(price * 0.2d),
                activityCosRatio,
                "20.00%",
                1,
                "双佣金",
                "10",
                activityAdCosRatio,
                true,
                status == 1,
                320L,
                Long.parseLong(DEFAULT_SHOP_ID),
                DEFAULT_SHOP_NAME,
                DEFAULT_SHOP_SCORE,
                status,
                statusText,
                categoryName,
                status == 1 ? "128" : "0",
                "满199减20",
                "2026-04-01 00:00:00",
                "2026-05-01 00:00:00",
                "2026-04-01 00:00:00",
                "2026-05-01 00:00:00",
                "https://haohuo.jinritemai.com/views/product/item2?id=" + productId + "&activity_id=" + activityId,
                "7293293346398011698",
                Map.of("origin_colonel_buyin_id", "7293293346398011698")
        );
    }

    private List<DouyinActivityGateway.ActivityItem> filterActivities(
            List<DouyinActivityGateway.ActivityItem> activities,
            Integer status,
            String keyword,
            Long searchType,
            Long sortType) {
        List<DouyinActivityGateway.ActivityItem> filtered = activities.stream()
                .filter(item -> status == null || status == 0 || item.status() == status)
                .filter(item -> {
                    if (!hasText(keyword)) {
                        return true;
                    }
                    String normalized = keyword.trim().toLowerCase(Locale.ROOT);
                    return item.activityName().toLowerCase(Locale.ROOT).contains(normalized)
                            || String.valueOf(item.activityId()).contains(normalized);
                })
                .toList();
        Comparator<DouyinActivityGateway.ActivityItem> comparator;
        if (searchType != null && searchType == 1L) {
            comparator = Comparator.comparing(DouyinActivityGateway.ActivityItem::applicationStartTime, Comparator.nullsLast(String::compareTo));
        } else if (searchType != null && searchType == 2L) {
            comparator = Comparator.comparing(DouyinActivityGateway.ActivityItem::applicationEndTime, Comparator.nullsLast(String::compareTo));
        } else {
            comparator = Comparator.comparing(DouyinActivityGateway.ActivityItem::activityStartTime, Comparator.nullsLast(String::compareTo));
        }
        if (sortType == null || sortType != 0L) {
            comparator = comparator.reversed();
        }
        return filtered.stream().sorted(comparator).toList();
    }

    private List<DouyinProductGateway.ActivityProductItem> filterProducts(
            List<DouyinProductGateway.ActivityProductItem> items,
            Integer status,
            String keyword) {
        return items.stream()
                .filter(item -> status == null || item.status() == status)
                .filter(item -> {
                    if (!hasText(keyword)) {
                        return true;
                    }
                    String normalized = keyword.trim().toLowerCase(Locale.ROOT);
                    return item.title().toLowerCase(Locale.ROOT).contains(normalized)
                            || String.valueOf(item.productId()).contains(normalized);
                })
                .toList();
    }

    private Map<String, Object> toActivityRaw(DouyinActivityGateway.ActivityItem item) {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("activity_id", item.activityId());
        raw.put("activity_name", item.activityName());
        raw.put("activity_start_time", item.activityStartTime());
        raw.put("activity_end_time", item.activityEndTime());
        raw.put("status", item.status());
        raw.put("application_start_time", item.applicationStartTime());
        raw.put("application_end_time", item.applicationEndTime());
        raw.put("categories_limit", item.categoriesLimit());
        raw.put("colonel_buyin_id", item.colonelBuyinId());
        return raw;
    }

    private Map<String, Object> toProductRaw(DouyinProductGateway.ActivityProductItem item) {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("product_id", item.productId());
        raw.put("title", item.title());
        raw.put("cover", item.cover());
        raw.put("price", item.price());
        raw.put("cos_ratio", item.cosRatio());
        raw.put("cos_fee", item.cosFee());
        raw.put("activity_cos_ratio", item.activityCosRatio());
        raw.put("cos_type", item.cosType());
        raw.put("ad_service_ratio", item.adServiceRatio());
        raw.put("activity_ad_cos_ratio", item.activityAdCosRatio());
        raw.put("has_douin_goods_tag", item.hasDouinGoodsTag());
        raw.put("in_stock", item.inStock());
        raw.put("sales", item.sales());
        raw.put("shop_id", item.shopId());
        raw.put("shop_name", item.shopName());
        raw.put("shop_score", item.shopScore());
        raw.put("status", item.status());
        raw.put("category_name", item.categoryName());
        raw.put("product_stock", item.productStock());
        raw.put("colonel_coupon_info", item.colonelCouponInfo());
        raw.put("activity_start_time", item.activityStartTime());
        raw.put("activity_end_time", item.activityEndTime());
        raw.put("promotion_start_time", item.promotionStartTime());
        raw.put("promotion_end_time", item.promotionEndTime());
        raw.put("detail_url", item.detailUrl());
        return raw;
    }

    private Map<String, Object> toOrderRaw(DouyinOrderGateway.DouyinOrderItem item) {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("order_id", item.externalOrderId());
        raw.put("product_id", item.productId());
        raw.put("shop_id", item.merchantId());
        raw.put("shop_name", item.merchantName());
        raw.put("talent_uid", item.talentId());
        raw.put("talent_name", item.talentName());
        raw.put("pick_source", item.pickSource());
        raw.put("order_amount", item.orderAmount());
        raw.put("service_fee", item.serviceFee());
        raw.put("order_status", item.orderStatus());
        raw.put("create_time", formatEpoch(item.createTime()));
        raw.put("settle_time", item.settleTime() == null ? null : formatEpoch(item.settleTime()));
        if (item.rawPayload() != null) {
            raw.putAll(item.rawPayload());
        }
        return raw;
    }

    private DouyinActivityGateway.ActivityProductItem toColonelActivityProductItem(
            DouyinProductGateway.ActivityProductItem item) {
        return new DouyinActivityGateway.ActivityProductItem(
                item.productId(),
                item.title(),
                item.cover(),
                item.price(),
                item.priceText(),
                item.cosRatio(),
                item.cosFee(),
                item.activityCosRatio(),
                item.activityCosRatioText(),
                item.cosType(),
                item.cosTypeText(),
                item.adServiceRatio(),
                item.activityAdCosRatio(),
                item.hasDouinGoodsTag(),
                item.inStock(),
                item.sales(),
                item.shopId(),
                item.shopName(),
                item.shopScore(),
                item.status(),
                item.statusText(),
                item.categoryName(),
                item.productStock(),
                item.colonelCouponInfo(),
                item.activityStartTime(),
                item.activityEndTime(),
                item.promotionStartTime(),
                item.promotionEndTime(),
                item.detailUrl(),
                item.originColonelBuyinId(),
                item.rawPayload() == null ? Map.of() : new LinkedHashMap<>(item.rawPayload())
        );
    }

    private Map<String, Object> success(String method, Map<String, Object> data) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("err_no", 0);
        result.put("err_msg", "success");
        result.put("log_id", "contract-" + method.replace('.', '-') + "-" + Instant.now().toEpochMilli());
        result.put("data", data);
        result.put("upstream_mode", "contract");
        return result;
    }

    private PickSourceMapping latestActiveMapping() {
        return pickSourceMappingMapper.selectOne(new LambdaQueryWrapper<PickSourceMapping>()
                .eq(PickSourceMapping::getStatus, 1)
                .orderByDesc(PickSourceMapping::getUpdateTime)
                .last("limit 1"));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private long asLong(String value, long defaultValue) {
        if (!hasText(value)) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ignore) {
            return defaultValue;
        }
    }

    private int parseCursor(String cursor) {
        if (!hasText(cursor)) {
            return 0;
        }
        try {
            return Math.max(Integer.parseInt(cursor.trim()), 0);
        } catch (NumberFormatException ignore) {
            return 0;
        }
    }

    private long parseEpochSecond(String value, long defaultValue) {
        if (!hasText(value)) {
            return defaultValue;
        }
        try {
            return LocalDateTime.parse(value.trim(), DATE_TIME_FORMATTER).atZone(java.time.ZoneId.systemDefault()).toEpochSecond();
        } catch (Exception ignore) {
            return defaultValue;
        }
    }

    private String formatEpoch(Long epochSecond) {
        if (epochSecond == null) {
            return null;
        }
        return DATE_TIME_FORMATTER.format(LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSecond), java.time.ZoneId.systemDefault()));
    }

    private String digitsOnly(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (char ch : value.toCharArray()) {
            if (Character.isDigit(ch)) {
                builder.append(ch);
            }
        }
        return builder.toString();
    }
}
