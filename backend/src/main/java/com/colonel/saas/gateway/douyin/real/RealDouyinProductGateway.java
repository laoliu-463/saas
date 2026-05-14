package com.colonel.saas.gateway.douyin.real;

import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.douyin.api.ProductApi;
import com.colonel.saas.gateway.douyin.contract.DouyinContractFixtureProvider;
import com.colonel.saas.gateway.douyin.contract.DouyinUpstreamModeSupport;
import com.colonel.saas.gateway.douyin.DouyinProductGateway;
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
public class RealDouyinProductGateway implements DouyinProductGateway {

    private final ProductApi productApi;
    private final DouyinUpstreamModeSupport upstreamModeSupport;
    private final DouyinContractFixtureProvider contractFixtureProvider;

    public RealDouyinProductGateway(
            ProductApi productApi,
            DouyinUpstreamModeSupport upstreamModeSupport,
            DouyinContractFixtureProvider contractFixtureProvider) {
        this.productApi = productApi;
        this.upstreamModeSupport = upstreamModeSupport;
        this.contractFixtureProvider = contractFixtureProvider;
    }

    @Override
    public ActivityProductListResult queryActivityProducts(ActivityProductQueryRequest request) {
        logGateway(request.appId());
        if (upstreamModeSupport.isContract()) {
            return contractFixtureProvider.buildProductListResult(request);
        }
        Map<String, Object> remote = productApi.listProductsByActivity(
                request.appId(),
                request.activityId(),
                request.searchType(),
                request.sortType(),
                request.count(),
                request.cooperationInfo(),
                request.cooperationType(),
                request.productInfo(),
                request.status(),
                request.retrieveMode(),
                request.cursor(),
                request.page()
        );
        Map<String, Object> dataNode = asMap(remote.get("data"));
        List<Map<String, Object>> rawItems = castListMap(asList(dataNode.get("data")));
        if (rawItems.isEmpty()) {
            rawItems = castListMap(asList(dataNode.get("list")));
        }
        List<ActivityProductItem> items = rawItems.stream().map(this::normalizeProductItem).toList();
        Long total = dataNode.containsKey("total") ? asLong(dataNode.get("total"), items.size()) : null;
        return new ActivityProductListResult(false, asLong(request.activityId(), 0L),
                asLong(dataNode.get("institution_id"), 0L), total, asString(dataNode.get("next_cursor")), items);
    }

    @Override
    public List<ProductSkuResult> queryProductSkus(String productId) {
        logGateway(null);
        if (upstreamModeSupport.isContract()) {
            return contractFixtureProvider.buildProductSkus(productId);
        }
        Map<String, Object> response = productApi.getProductSkusV2(productId);
        Map<String, Object> dataNode = asMap(response.get("data"));
        List<Map<String, Object>> skuRows = extractBuyinSkuRows(dataNode);
        return skuRows.stream().map(sku -> {
            String skuId = firstNonBlank(
                    asString(sku.get("sku_id")),
                    asString(sku.get("skuId")),
                    asString(sku.get("id"))
            );
            String spec1 = firstNonBlank(asString(sku.get("spec_detail_name1")), asString(sku.get("specName1")));
            String spec2 = firstNonBlank(asString(sku.get("spec_detail_name2")), asString(sku.get("specName2")));
            String spec3 = firstNonBlank(asString(sku.get("spec_detail_name3")), asString(sku.get("specName3")));
            String skuName = firstNonBlank(
                    asString(sku.get("sku_name")),
                    asString(sku.get("skuName")),
                    buildSkuName(null, spec1, spec2, spec3),
                    skuId
            );
            return new ProductSkuResult(
                    skuId,
                    skuName,
                    asLong(pick(sku, "price", "sku_price", "skuPrice"), 0L),
                    toInt(pick(sku, "stock_num", "stockNum", "stock", "stock_count"), 0),
                    firstNonBlank(
                            asString(pick(sku, "picture_url", "pictureUrl", "cover", "image", "pic"))
                    )
            );
        }).toList();
    }

    private List<Map<String, Object>> extractBuyinSkuRows(Map<String, Object> dataNode) {
        Object skus = pick(dataNode, "skus", "sku_map", "skuMap", "sku_list", "skuList", "list");
        if (skus instanceof List<?> list) {
            return castListMap(list);
        }
        if (skus instanceof Map<?, ?> map) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Map<String, Object> sku = asMap(entry.getValue());
                if (sku.isEmpty()) {
                    continue;
                }
                if (!sku.containsKey("sku_id") && entry.getKey() != null) {
                    sku = new LinkedHashMap<>(sku);
                    sku.put("sku_id", String.valueOf(entry.getKey()));
                }
                result.add(sku);
            }
            return result;
        }
        return List.of();
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String buildSkuName(String productName, String spec1, String spec2, String spec3) {
        List<String> parts = new java.util.ArrayList<>();
        if (spec1 != null && !spec1.isBlank()) parts.add(spec1);
        if (spec2 != null && !spec2.isBlank()) parts.add(spec2);
        if (spec3 != null && !spec3.isBlank()) parts.add(spec3);
        if (parts.isEmpty()) {
            return productName;
        }
        String specName = String.join(" / ", parts);
        if (productName == null || productName.isBlank()) {
            return specName;
        }
        return productName + " " + specName;
    }

    private int toInt(Object value, int defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private ActivityProductItem normalizeProductItem(Map<String, Object> raw) {
        int status = (int) asLong(pick(raw, "status"), 0L);
        int cosType = (int) asLong(pick(raw, "cos_type", "cosType"), 0L);
        long price = asLong(pick(raw, "price"), 0L);
        long activityCosRatio = asLong(pick(raw, "activity_cos_ratio"), 0L);
        Long activityAdCosRatio = raw.containsKey("activity_ad_cos_ratio") ? asLong(pick(raw, "activity_ad_cos_ratio"), 0L) : null;
        String originColonelBuyinId = asString(pick(raw, "origin_colonel_buyin_id", "originColonelBuyinId"));
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
                asString(pick(raw, "detail_url")),
                originColonelBuyinId,
                new LinkedHashMap<>(raw)
        );
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

    private void logGateway(String appId) {
        log.info(
                "gateway=RealDouyinProductGateway, upstreamMode={}, appKey={}, shopId={}, authId={}",
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
