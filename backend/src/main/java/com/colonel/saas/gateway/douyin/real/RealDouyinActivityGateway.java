package com.colonel.saas.gateway.douyin.real;

import com.colonel.saas.douyin.api.ActivityApi;
import com.colonel.saas.douyin.api.ProductApi;
import com.colonel.saas.gateway.douyin.DouyinActivityGateway;
import com.colonel.saas.gateway.douyin.contract.DouyinContractFixtureProvider;
import com.colonel.saas.gateway.douyin.contract.DouyinUpstreamModeSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Component
@ConditionalOnProperty(name = "douyin.test.enabled", havingValue = "false", matchIfMissing = true)
public class RealDouyinActivityGateway implements DouyinActivityGateway {

    private final ActivityApi activityApi;
    private final ProductApi productApi;
    private final DouyinUpstreamModeSupport upstreamModeSupport;
    private final DouyinContractFixtureProvider contractFixtureProvider;

    public RealDouyinActivityGateway(
            ActivityApi activityApi,
            ProductApi productApi,
            DouyinUpstreamModeSupport upstreamModeSupport,
            DouyinContractFixtureProvider contractFixtureProvider) {
        this.activityApi = activityApi;
        this.productApi = productApi;
        this.upstreamModeSupport = upstreamModeSupport;
        this.contractFixtureProvider = contractFixtureProvider;
    }

    @Override
    public ActivityListResult listActivities(ActivityListQuery query) {
        logGateway("RealDouyinActivityGateway", query.appId());
        if (upstreamModeSupport.isContract()) {
            return contractFixtureProvider.buildActivityListResult(query);
        }
        Map<String, Object> remote = activityApi.listActivities(
                query.appId(), query.status(), query.searchType(), query.sortType(),
                query.page(), query.pageSize(), query.activityInfo()
        );
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
        return new ActivityListResult(false, institutionId, total, list.stream().map(this::normalizeActivityItem).toList());
    }

    @Override
    public ActivityProductListResult listActivityProducts(ActivityProductListQuery query) {
        logGateway("RealDouyinActivityGateway", query.appId());
        if (upstreamModeSupport.isContract()) {
            return contractFixtureProvider.buildActivityProductListResult(query);
        }
        Map<String, Object> remote = productApi.listProductsByActivity(
                query.appId(), query.activityId(), query.searchType(), query.sortType(), query.count(),
                query.cooperationInfo(), query.cooperationType(), query.productInfo(), query.status(),
                query.retrieveMode(), query.cursor(), query.page()
        );
        Map<String, Object> dataNode = asMap(remote.get("data"));
        List<Map<String, Object>> rawItems = castListMap(asList(dataNode.get("data")));
        if (rawItems.isEmpty()) {
            rawItems = castListMap(asList(dataNode.get("list")));
        }
        List<ActivityProductItem> items = rawItems.stream().map(this::normalizeProductItem).toList();
        Long total = dataNode.containsKey("total") ? asLong(dataNode.get("total"), items.size()) : null;
        return new ActivityProductListResult(false, asLong(query.activityId(), 0L),
                asLong(dataNode.get("institution_id"), 0L), total, asString(dataNode.get("next_cursor")), items);
    }

    @Override
    public Map<String, Object> createOrUpdate(ActivityApi.ActivityCreateOrUpdateCommand command) {
        logGateway("RealDouyinActivityGateway.createOrUpdate", command == null ? null : command.appId());
        return activityApi.createOrUpdate(command);
    }

    @Override
    public Map<String, Object> cancelActivityProduct(String appId, Map<String, Object> payload) {
        logGateway("RealDouyinActivityGateway.cancelActivityProduct", appId);
        return activityApi.cancelActivityProduct(appId, payload);
    }

    @Override
    public Map<String, Object> activityDetail(String appId, String activityId) {
        logGateway("RealDouyinActivityGateway.activityDetail", appId);
        if (upstreamModeSupport.isContract()) {
            return contractFixtureProvider.buildActivityDetailResponse(appId, activityId);
        }
        return activityApi.detail(appId, activityId);
    }

    @Override
    public Map<String, Object> createOrUpdateActivity(ActivityMutateCommand command) {
        logGateway("RealDouyinActivityGateway.createOrUpdateActivity", command.appId());
        ActivityApi.ActivityCreateOrUpdateCommand apiCmd = new ActivityApi.ActivityCreateOrUpdateCommand(
                command.appId(),
                command.activityId(),
                command.applicationLimited(),
                command.isNewShop(),
                command.shopType(),
                command.activityName(),
                command.activityDesc(),
                command.applyStartTime(),
                command.applyEndTime(),
                command.commissionRate(),
                command.serviceRate(),
                command.wechatId(),
                command.phoneNum(),
                command.estimatedSingleSale(),
                command.activityType(),
                command.specifiedShopIds(),
                command.online(),
                command.categories(),
                command.shopScore(),
                command.minPromotionDays(),
                command.thresholdCrossBorder(),
                command.minExclusionDuration(),
                command.adCommissionRate(),
                command.adServiceRate(),
                command.cosLimitType()
        );
        return activityApi.createOrUpdate(apiCmd);
    }

    private ActivityItem normalizeActivityItem(Map<String, Object> raw) {
        int status = (int) asLong(pick(raw, "status"), 0L);
        return new ActivityItem(
                asLong(pick(raw, "activity_id", "activityId"), 0L),
                asString(pick(raw, "activity_name", "activityName")),
                asString(pick(raw, "activity_start_time", "activityStartTime")),
                asString(pick(raw, "activity_end_time", "activityEndTime")),
                status,
                activityStatusText(status),
                asString(pick(raw, "application_start_time", "applicationStartTime")),
                asString(pick(raw, "application_end_time", "applicationEndTime")),
                pick(raw, "categories_limit", "categoriesLimit"),
                asLong(pick(raw, "colonel_buyin_id", "colonelBuyinId"), 0L)
        );
    }

    private ActivityProductItem normalizeProductItem(Map<String, Object> raw) {
        int status = (int) asLong(pick(raw, "status"), 0L);
        int cosType = (int) asLong(pick(raw, "cos_type", "cosType"), 0L);
        long price = asLong(pick(raw, "price"), 0L);
        long activityCosRatio = asLong(pick(raw, "activity_cos_ratio"), 0L);
        Long activityAdCosRatio = raw.containsKey("activity_ad_cos_ratio") ? asLong(pick(raw, "activity_ad_cos_ratio"), 0L) : null;
        return new ActivityProductItem(
                asLong(pick(raw, "product_id", "productId"), 0L),
                asString(pick(raw, "title")),
                asString(pick(raw, "cover")),
                price,
                String.format(Locale.ROOT, "%.2f", price / 100.0),
                asLong(pick(raw, "cos_ratio"), 0L),
                asLong(pick(raw, "cos_fee"), 0L),
                activityCosRatio,
                String.format(Locale.ROOT, "%.2f%%", activityCosRatio / 100.0),
                cosType,
                cosType == 1 ? "双佣金" : "固定佣金",
                asString(pick(raw, "ad_service_ratio")),
                activityAdCosRatio,
                toBool(pick(raw, "has_douin_goods_tag")),
                toBool(pick(raw, "in_stock")),
                asLong(pick(raw, "sales"), 0L),
                asLong(pick(raw, "shop_id"), 0L),
                asString(pick(raw, "shop_name")),
                asString(pick(raw, "shop_score")),
                status,
                productStatusText(status),
                asString(pick(raw, "category_name")),
                asString(pick(raw, "product_stock")),
                asString(pick(raw, "colonel_coupon_info")),
                asString(pick(raw, "activity_start_time")),
                asString(pick(raw, "activity_end_time")),
                asString(pick(raw, "promotion_start_time")),
                asString(pick(raw, "promotion_end_time")),
                asString(pick(raw, "detail_url"))
        );
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
        if (value == null) return defaultValue;
        if (value instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private String asString(Object value) {
        if (value == null) return null;
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private void logGateway(String gatewayName, String appId) {
        log.info(
                "gateway={}, upstreamMode={}, appKey={}, shopId={}, authId={}",
                gatewayName,
                upstreamModeSupport.value(),
                mask(appId == null ? contractFixtureProvider.appKey() : appId),
                contractFixtureProvider.shopId(),
                contractFixtureProvider.authId()
        );
    }

    private String mask(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.trim();
        if (normalized.length() <= 8) {
            return normalized;
        }
        return normalized.substring(0, 4) + "****" + normalized.substring(normalized.length() - 4);
    }
}
