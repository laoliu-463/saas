package com.colonel.saas.controller;

import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.common.base.BaseController;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.douyin.DouyinApiException;
import com.colonel.saas.douyin.api.ActivityApi;
import com.colonel.saas.douyin.api.ProductApi;
import com.colonel.saas.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Validated
@RestController
@Tag(name = "团长活动管理")
@RequestMapping("/colonel/activities")
@RequireRoles({RoleCodes.BIZ_LEADER, RoleCodes.BIZ_STAFF, RoleCodes.CHANNEL_LEADER, RoleCodes.CHANNEL_STAFF, RoleCodes.ADMIN})
public class ColonelActivityController extends BaseController {

    private final ActivityApi activityApi;
    private final ProductApi productApi;
    private final ProductService productService;

    @Value("${douyin.mock.enabled:false}")
    private boolean douyinMockEnabled;

    public ColonelActivityController(ActivityApi activityApi, ProductApi productApi, ProductService productService) {
        this.activityApi = activityApi;
        this.productApi = productApi;
        this.productService = productService;
    }

    @Operation(summary = "团长活动列表", description = "查询机构创建的团长活动列表")
    @GetMapping
    public ApiResult<Map<String, Object>> list(
            @RequestParam(defaultValue = "0") Integer status,
            @RequestParam(defaultValue = "0") Long searchType,
            @RequestParam(defaultValue = "1") Long sortType,
            @RequestParam(defaultValue = "1") @Min(1) Long page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(20) Long pageSize,
            @RequestParam(required = false) String activityInfo,
            @RequestParam(required = false) String appId) {
        if (douyinMockEnabled) {
            return ok(buildMockActivityResult(status, searchType, sortType, page, pageSize, activityInfo));
        }

        try {
            Map<String, Object> remote = activityApi.listActivities(appId, status, searchType, sortType, page, pageSize, activityInfo);
            return ok(normalizeActivityResult(remote));
        } catch (DouyinApiException e) {
            throw mapActivityError(e);
        }
    }

    @Operation(summary = "活动商品列表", description = "查询团长活动下的商品列表，默认游标模式")
    @GetMapping("/{activityId}/products")
    public ApiResult<Map<String, Object>> listProducts(
            @PathVariable String activityId,
            @RequestParam(defaultValue = "4") Long searchType,
            @RequestParam(defaultValue = "1") Long sortType,
            @RequestParam(defaultValue = "20") @Min(1) @Max(20) Integer count,
            @RequestParam(required = false) String cooperationInfo,
            @RequestParam(defaultValue = "0") Integer cooperationType,
            @RequestParam(required = false) String productInfo,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Long retrieveMode,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) @Min(1) Long page,
            @RequestParam(required = false) String appId) {
        if (douyinMockEnabled) {
            Map<String, Object> mockData = buildMockProductResult(activityId, count, retrieveMode, cursor, page, status, productInfo);
            productService.upsertSnapshots(activityId, castListMap(asList(mockData.get("items"))));
            return ok(mockData);
        }

        try {
            Map<String, Object> remote = productApi.listProductsByActivity(
                    appId,
                    activityId,
                    searchType,
                    sortType,
                    count,
                    cooperationInfo,
                    cooperationType,
                    productInfo,
                    status,
                    retrieveMode,
                    cursor,
                    page
            );
            Map<String, Object> normalized = normalizeProductResult(activityId, remote);
            productService.upsertSnapshots(activityId, castListMap(asList(normalized.get("items"))));
            return ok(normalized);
        } catch (DouyinApiException e) {
            throw mapProductError(e);
        }
    }

    private BusinessException mapActivityError(DouyinApiException e) {
        String subCode = e.getSubCode() == null ? "" : e.getSubCode();
        if (e.getErrorCode() == 50002 && subCode.contains("4197")) {
            return new BusinessException("当前账号未完成招商团长授权，请检查抖店授权状态");
        }
        if (e.getErrorCode() == 50002 && subCode.contains("4200")) {
            return new BusinessException("抖店账号状态异常，请检查账号可用状态");
        }
        if (e.getErrorCode() == 40004 && subCode.contains("257")) {
            return new BusinessException("查询参数不合法，请检查筛选条件");
        }
        if (e.getErrorCode() == 20000 && subCode.contains("256")) {
            return new BusinessException("抖店服务异常，请稍后重试");
        }
        return new BusinessException("团长活动查询失败: " + e.getErrorMsg());
    }

    private BusinessException mapProductError(DouyinApiException e) {
        String subCode = e.getSubCode() == null ? "" : e.getSubCode();
        if (e.getErrorCode() == 50002 && subCode.contains("4097")) {
            return new BusinessException("每页最多查询 20 条商品");
        }
        if (e.getErrorCode() == 50002 && subCode.contains("8197")) {
            return new BusinessException("不允许继续翻页，请使用游标模式加载更多");
        }
        if (e.getErrorCode() == 50002 && subCode.contains("4197")) {
            return new BusinessException("当前账号未完成招商团长授权，请检查抖店授权状态");
        }
        if (e.getErrorCode() == 50002 && subCode.contains("4200")) {
            return new BusinessException("抖店账号状态异常，请检查账号可用状态");
        }
        if (e.getErrorCode() == 50002 && subCode.contains("257")) {
            return new BusinessException("查询参数不合法，请检查筛选条件");
        }
        if (e.getErrorCode() == 20000 && subCode.contains("256")) {
            return new BusinessException("抖店服务异常，请稍后重试");
        }
        return new BusinessException("活动商品查询失败: " + e.getErrorMsg());
    }

    private Map<String, Object> normalizeActivityResult(Map<String, Object> remote) {
        Map<String, Object> dataNode = asMap(remote.get("data"));
        List<Map<String, Object>> list = castListMap(asList(dataNode.get("activity_list")));
        if (list.isEmpty()) {
            list = castListMap(asList(dataNode.get("data")));
        }
        if (list.isEmpty()) {
            list = castListMap(asList(remote.get("activity_list")));
        }

        long total = asLong(dataNode.get("total"), asLong(remote.get("total"), list.size()));
        long institutionId = asLong(dataNode.get("institution_id"), 0L);

        List<Map<String, Object>> normalizedList = list.stream().map(this::normalizeActivityItem).toList();

        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("mock", false);
        normalized.put("institutionId", institutionId);
        normalized.put("total", total);
        normalized.put("activityList", normalizedList);
        return normalized;
    }

    private Map<String, Object> normalizeProductResult(String activityId, Map<String, Object> remote) {
        Map<String, Object> dataNode = asMap(remote.get("data"));
        List<Map<String, Object>> rawItems = castListMap(asList(dataNode.get("data")));
        if (rawItems.isEmpty()) {
            rawItems = castListMap(asList(dataNode.get("list")));
        }

        List<Map<String, Object>> items = rawItems.stream().map(this::normalizeProductItem).toList();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("mock", false);
        data.put("activityId", asLong(activityId, 0L));
        data.put("institutionId", asLong(dataNode.get("institution_id"), 0L));
        data.put("total", dataNode.containsKey("total") ? asLong(dataNode.get("total"), items.size()) : null);
        data.put("nextCursor", asString(dataNode.get("next_cursor")));
        data.put("items", items);
        return data;
    }

    private Map<String, Object> buildMockActivityResult(Integer status, Long searchType, Long sortType, Long page, Long pageSize, String activityInfo) {
        List<Map<String, Object>> source = buildMockActivities();

        List<Map<String, Object>> filtered = source.stream()
                .filter(item -> matchesActivityStatus(item, status))
                .filter(item -> matchesActivityKeyword(item, activityInfo))
                .sorted(buildActivityComparator(searchType, sortType))
                .toList();

        int from = (int) ((page - 1) * pageSize);
        int to = Math.min(from + pageSize.intValue(), filtered.size());
        List<Map<String, Object>> pageList = from >= filtered.size() ? List.of() : filtered.subList(from, to);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("mock", true);
        data.put("institutionId", 11111111L);
        data.put("total", filtered.size());
        data.put("activityList", pageList);
        return data;
    }

    private Map<String, Object> buildMockProductResult(
            String activityId,
            Integer count,
            Long retrieveMode,
            String cursor,
            Long page,
            Integer status,
            String productInfo) {
        List<Map<String, Object>> all = new ArrayList<>();
        int size = 80;
        for (int i = 1; i <= size; i++) {
            int itemStatus = switch (i % 6) {
                case 0 -> 0;
                case 1 -> 1;
                case 2 -> 2;
                case 3 -> 3;
                case 4 -> 6;
                default -> 4;
            };
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("productId", 900000L + i);
            item.put("title", "Mock活动商品-" + i);
            item.put("cover", "https://example.com/mock-product-" + i + ".png");
            item.put("price", 9900 + i * 13);
            item.put("priceText", String.format("%.2f", (9900 + i * 13) / 100.0));
            item.put("cosRatio", 10 + (i % 20));
            item.put("cosFee", 1000 + i * 11);
            item.put("activityCosRatio", 1000 + (i % 20) * 100);
            item.put("activityCosRatioText", (10 + (i % 20)) + "%");
            item.put("cosType", (i % 2));
            item.put("cosTypeText", i % 2 == 1 ? "双佣金" : "固定佣金");
            item.put("adServiceRatio", i % 2 == 1 ? "10" : null);
            item.put("activityAdCosRatio", i % 2 == 1 ? 8 : null);
            item.put("hasDouinGoodsTag", i % 3 == 0);
            item.put("inStock", i % 5 != 0);
            item.put("sales", 200 + i * 3);
            item.put("shopId", 800000L + (i % 9));
            item.put("shopName", "Mock店铺-" + (i % 9));
            item.put("shopScore", "4." + (70 + (i % 20)));
            item.put("status", itemStatus);
            item.put("statusText", productStatusText(itemStatus));
            item.put("categoryName", i % 2 == 0 ? "美妆个护" : "女装");
            item.put("productStock", String.valueOf(1000 - i));
            item.put("colonelCouponInfo", i % 2 == 0 ? "满100减20" : "满200减40");
            item.put("activityStartTime", "2026-04-01");
            item.put("activityEndTime", "2026-04-30");
            item.put("promotionStartTime", "2026-04-01");
            item.put("promotionEndTime", "2026-04-30");
            item.put("detailUrl", "https://example.com/mock-detail/" + (900000 + i));
            all.add(item);
        }

        List<Map<String, Object>> filtered = all.stream()
                .filter(item -> status == null || asLong(item.get("status"), -1L) == status)
                .filter(item -> {
                    if (productInfo == null || productInfo.isBlank()) return true;
                    String kw = productInfo.trim().toLowerCase(Locale.ROOT);
                    return String.valueOf(item.get("title")).toLowerCase(Locale.ROOT).contains(kw)
                            || String.valueOf(item.get("productId")).contains(kw);
                })
                .toList();

        int pageSize = Math.min(Math.max(count == null ? 20 : count, 1), 20);
        long mode = retrieveMode == null ? 1L : retrieveMode;
        int from;
        if (mode == 0L) {
            long pageNo = page == null || page < 1 ? 1 : page;
            from = (int) ((pageNo - 1) * pageSize);
        } else {
            from = parseCursor(cursor);
        }
        int to = Math.min(from + pageSize, filtered.size());
        List<Map<String, Object>> items = from >= filtered.size() ? List.of() : filtered.subList(from, to);
        String nextCursor = to >= filtered.size() ? "" : String.valueOf(to);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("mock", true);
        data.put("activityId", asLong(activityId, 0L));
        data.put("institutionId", 111111L);
        data.put("total", mode == 0L ? filtered.size() : null);
        data.put("nextCursor", nextCursor);
        data.put("items", items);
        return data;
    }

    private List<Map<String, Object>> buildMockActivities() {
        List<Map<String, Object>> activities = new ArrayList<>();
        int[] statusCycle = {1, 2, 3, 4, 5, 7};
        String[] categoryCycle = {"美妆个护", "女装", "家用电器", "食品饮料", "母婴用品", "运动户外"};
        String[] nameCycle = {"春季上新", "夏季爆款", "秋季清仓", "双11预热", "年货节", "新品招商"};

        for (int i = 1; i <= 36; i++) {
            int status = statusCycle[(i - 1) % statusCycle.length];
            int month = ((i - 1) % 9) + 1;
            int day = ((i - 1) % 20) + 1;

            String startDate = String.format("2026-%02d-%02d 00:00:00", month, day);
            String endDate = String.format("2026-%02d-%02d 23:59:59", month, Math.min(day + 10, 28));
            String applyStartDate = String.format("2026-%02d-%02d 00:00:00", Math.max(month - 1, 1), Math.max(day - 5, 1));
            String applyEndDate = String.format("2026-%02d-%02d 23:59:59", month, Math.max(day - 1, 1));

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("activityId", 100000L + i);
            item.put("activityName", nameCycle[(i - 1) % nameCycle.length] + "活动-" + i);
            item.put("activityStartTime", startDate);
            item.put("activityEndTime", endDate);
            item.put("status", status);
            item.put("statusText", activityStatusText(status));
            item.put("applicationStartTime", applyStartDate);
            item.put("applicationEndTime", applyEndDate);
            item.put("categoriesLimit", Map.of("一级类目", categoryCycle[(i - 1) % categoryCycle.length]));
            item.put("colonelBuyinId", 46128341673481000L + i);
            activities.add(item);
        }

        return activities;
    }

    private boolean matchesActivityStatus(Map<String, Object> item, Integer status) {
        if (status == null || status == 0) {
            return true;
        }
        return asLong(item.get("status"), 0L) == status;
    }

    private boolean matchesActivityKeyword(Map<String, Object> item, String activityInfo) {
        if (activityInfo == null || activityInfo.isBlank()) {
            return true;
        }
        String keyword = activityInfo.trim().toLowerCase(Locale.ROOT);
        String id = String.valueOf(item.getOrDefault("activityId", "")).toLowerCase(Locale.ROOT);
        String name = String.valueOf(item.getOrDefault("activityName", "")).toLowerCase(Locale.ROOT);
        return id.contains(keyword) || name.contains(keyword);
    }

    private Comparator<Map<String, Object>> buildActivityComparator(Long searchType, Long sortType) {
        long searchTypeValue = searchType == null ? 0L : searchType;
        String selectedField = "activityStartTime";
        if (searchTypeValue == 1L) {
            selectedField = "applicationStartTime";
        } else if (searchTypeValue == 2L) {
            selectedField = "applicationEndTime";
        }
        final String field = selectedField;
        Comparator<Map<String, Object>> comparator = Comparator.comparing(
                item -> String.valueOf(item.getOrDefault(field, "")),
                Comparator.nullsLast(String::compareTo)
        );
        return (sortType != null && sortType == 0L) ? comparator : comparator.reversed();
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

    private Map<String, Object> normalizeActivityItem(Map<String, Object> raw) {
        int status = (int) asLong(pick(raw, "status"), 0L);
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("activityId", asLong(pick(raw, "activity_id", "activityId"), 0L));
        item.put("activityName", asString(pick(raw, "activity_name", "activityName")));
        item.put("activityStartTime", asString(pick(raw, "activity_start_time", "activityStartTime")));
        item.put("activityEndTime", asString(pick(raw, "activity_end_time", "activityEndTime")));
        item.put("status", status);
        item.put("statusText", activityStatusText(status));
        item.put("applicationStartTime", asString(pick(raw, "application_start_time", "applicationStartTime")));
        item.put("applicationEndTime", asString(pick(raw, "application_end_time", "applicationEndTime")));
        item.put("categoriesLimit", pick(raw, "categories_limit", "categoriesLimit"));
        item.put("colonelBuyinId", asLong(pick(raw, "colonel_buyin_id", "colonelBuyinId"), 0L));
        return item;
    }

    private Map<String, Object> normalizeProductItem(Map<String, Object> raw) {
        Map<String, Object> item = new LinkedHashMap<>();
        int status = (int) asLong(pick(raw, "status"), 0L);
        int cosType = (int) asLong(pick(raw, "cos_type", "cosType"), 0L);
        long price = asLong(pick(raw, "price"), 0L);
        long activityCosRatio = asLong(pick(raw, "activity_cos_ratio"), 0L);
        item.put("productId", asLong(pick(raw, "product_id", "productId"), 0L));
        item.put("title", asString(pick(raw, "title")));
        item.put("cover", asString(pick(raw, "cover")));
        item.put("price", price);
        item.put("priceText", String.format("%.2f", price / 100.0));
        item.put("cosRatio", asLong(pick(raw, "cos_ratio"), 0L));
        item.put("cosFee", asLong(pick(raw, "cos_fee"), 0L));
        item.put("activityCosRatio", activityCosRatio);
        item.put("activityCosRatioText", String.format("%.2f%%", activityCosRatio / 100.0));
        item.put("cosType", cosType);
        item.put("cosTypeText", cosType == 1 ? "双佣金" : "固定佣金");
        item.put("adServiceRatio", asString(pick(raw, "ad_service_ratio")));
        item.put("activityAdCosRatio", asLong(pick(raw, "activity_ad_cos_ratio"), 0L));
        item.put("hasDouinGoodsTag", toBool(pick(raw, "has_douin_goods_tag")));
        item.put("inStock", toBool(pick(raw, "in_stock")));
        item.put("sales", asLong(pick(raw, "sales"), 0L));
        item.put("shopId", asLong(pick(raw, "shop_id"), 0L));
        item.put("shopName", asString(pick(raw, "shop_name")));
        item.put("shopScore", asString(pick(raw, "shop_score")));
        item.put("status", status);
        item.put("statusText", productStatusText(status));
        item.put("categoryName", asString(pick(raw, "category_name")));
        item.put("productStock", asString(pick(raw, "product_stock")));
        item.put("colonelCouponInfo", asString(pick(raw, "colonel_coupon_info")));
        item.put("activityStartTime", asString(pick(raw, "activity_start_time")));
        item.put("activityEndTime", asString(pick(raw, "activity_end_time")));
        item.put("promotionStartTime", asString(pick(raw, "promotion_start_time")));
        item.put("promotionEndTime", asString(pick(raw, "promotion_end_time")));
        item.put("detailUrl", asString(pick(raw, "detail_url")));
        return item;
    }

    private boolean toBool(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean b) return b;
        if (value instanceof Number n) return n.intValue() != 0;
        String text = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
        return "true".equals(text) || "1".equals(text) || "yes".equals(text);
    }

    private Object pick(Map<String, Object> raw, String... keys) {
        for (String key : keys) {
            if (raw.containsKey(key)) {
                return raw.get(key);
            }
        }
        return null;
    }

    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> converted = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    converted.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            return converted;
        }
        return Map.of();
    }

    private List<?> asList(Object value) {
        if (value instanceof List<?> list) {
            return list;
        }
        return List.of();
    }

    private List<Map<String, Object>> castListMap(List<?> list) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> converted = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (entry.getKey() != null) {
                        converted.put(String.valueOf(entry.getKey()), entry.getValue());
                    }
                }
                result.add(converted);
            }
        }
        return result;
    }

    private long asLong(Object value, long defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}

