package com.colonel.saas.domain.product.policy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 商品审核补充信息的归一化读模型。
 */
public final class ProductAuditSupplementPayload {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ProductAuditSupplementPayload() {
    }

    public static Map<String, Object> parse(String rawPayload) {
        if (!StringUtils.hasText(rawPayload)) {
            return Map.of();
        }
        try {
            return normalize(OBJECT_MAPPER.readValue(rawPayload, new TypeReference<Map<String, Object>>() {}));
        } catch (Exception ex) {
            return Map.of();
        }
    }

    public static Map<String, Object> normalize(Map<String, Object> supplement) {
        if (supplement == null || supplement.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        putNormalizedText(normalized, "exclusivePriceRemark", supplement.get("exclusivePriceRemark"));
        putNormalizedText(normalized, "shippingInfo", supplement.get("shippingInfo"));
        putNormalizedText(normalized, "promotionScript", supplement.get("promotionScript"));
        putNormalizedText(normalized, "rewardRemark", supplement.get("rewardRemark"));
        putNormalizedText(normalized, "participationRequirements", supplement.get("participationRequirements"));
        putNormalizedText(normalized, "campaignTimeRemark", supplement.get("campaignTimeRemark"));
        putNormalizedText(normalized, "sampleThresholdRemark", supplement.get("sampleThresholdRemark"));
        List<String> sellingPoints = normalizeStringList(supplement.get("sellingPoints"));
        if (!sellingPoints.isEmpty()) {
            normalized.put("sellingPoints", sellingPoints);
        }
        List<String> materialFiles = normalizeStringList(supplement.get("materialFiles"));
        if (!materialFiles.isEmpty()) {
            normalized.put("materialFiles", materialFiles);
        }
        List<String> goodsTags = normalizeStringList(supplement.get("goodsTags"));
        if (goodsTags.isEmpty()) {
            goodsTags = normalizeStringList(supplement.get("goods_tags"));
        }
        if (!goodsTags.isEmpty()) {
            normalized.put("goodsTags", goodsTags);
        }
        List<String> productTags = normalizeStringList(supplement.get("productTags"));
        if (productTags.isEmpty()) {
            productTags = normalizeStringList(supplement.get("product_tags"));
        }
        if (!productTags.isEmpty()) {
            normalized.put("productTags", productTags);
        }
        if (supplement.containsKey("supportsAds") && supplement.get("supportsAds") != null) {
            normalized.put("supportsAds", Boolean.parseBoolean(String.valueOf(supplement.get("supportsAds"))));
        }
        putNormalizedText(normalized, "adsRule", supplement.get("adsRule"));
        if (!normalized.containsKey("adsRule")) {
            putNormalizedText(normalized, "adsRule", supplement.get("投流规则"));
        }
        putNormalizedBoolean(normalized, "freeSample", supplement.get("freeSample"));
        putNormalizedBoolean(normalized, "sampleFree", supplement.get("sampleFree"));
        putNormalizedBoolean(normalized, "sample_free", supplement.get("sample_free"));
        putNormalizedText(normalized, "sampleType", supplement.get("sampleType"));
        putNormalizedBoolean(normalized, "materialDownloadAvailable", supplement.get("materialDownloadAvailable"));
        putNormalizedBoolean(normalized, "materialDownload", supplement.get("materialDownload"));
        putNormalizedBoolean(normalized, "exclusivePrice", supplement.get("exclusivePrice"));
        putNormalizedBoolean(normalized, "handCardAvailable", supplement.get("handCardAvailable"));
        putNormalizedBoolean(normalized, "handCard", supplement.get("handCard"));
        List<String> handCardFiles = normalizeStringList(supplement.get("handCardFiles"));
        if (!handCardFiles.isEmpty()) {
            normalized.put("handCardFiles", handCardFiles);
        }
        putNormalizedBoolean(normalized, "productChainGroup", supplement.get("productChainGroup"));
        putNormalizedBoolean(normalized, "productChain", supplement.get("productChain"));
        putNormalizedBoolean(normalized, "doubleCommission", supplement.get("doubleCommission"));
        putNormalizedBoolean(normalized, "dedupeSelection", supplement.get("dedupeSelection"));
        putNormalizedBoolean(normalized, "dedup", supplement.get("dedup"));
        putNormalizedBoolean(normalized, "notInProductPool", supplement.get("notInProductPool"));
        putNormalizedBoolean(normalized, "notInLibrary", supplement.get("notInLibrary"));
        putNormalizedNumber(normalized, "sampleThresholdSales", supplement.get("sampleThresholdSales"));
        putNormalizedNumber(normalized, "sampleThresholdLevel", supplement.get("sampleThresholdLevel"));
        return normalized;
    }

    public static String readString(Map<String, Object> payload, String key) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        Object value = payload.get(key);
        return value == null ? null : String.valueOf(value).trim();
    }

    public static Boolean readBoolean(Map<String, Object> payload, String key) {
        if (payload == null || payload.isEmpty() || !payload.containsKey(key) || payload.get(key) == null) {
            return null;
        }
        return Boolean.parseBoolean(String.valueOf(payload.get(key)));
    }

    public static List<String> readStringList(Map<String, Object> payload, String key) {
        if (payload == null || payload.isEmpty()) {
            return List.of();
        }
        return normalizeStringList(payload.get(key));
    }

    private static void putNormalizedText(Map<String, Object> payload, String key, Object rawValue) {
        String value = rawValue == null ? null : String.valueOf(rawValue).trim();
        if (StringUtils.hasText(value)) {
            payload.put(key, value);
        }
    }

    private static void putNormalizedNumber(Map<String, Object> payload, String key, Object rawValue) {
        if (rawValue == null) {
            return;
        }
        if (rawValue instanceof Number number) {
            payload.put(key, number.longValue());
            return;
        }
        String value = String.valueOf(rawValue).trim();
        if (!StringUtils.hasText(value)) {
            return;
        }
        try {
            payload.put(key, Long.parseLong(value));
        } catch (NumberFormatException ex) {
            payload.put(key, value);
        }
    }

    private static void putNormalizedBoolean(Map<String, Object> payload, String key, Object rawValue) {
        if (rawValue == null) {
            return;
        }
        if (rawValue instanceof Boolean b) {
            payload.put(key, b);
            return;
        }
        String value = String.valueOf(rawValue).trim();
        if (!StringUtils.hasText(value)) {
            return;
        }
        payload.put(key, Boolean.parseBoolean(value));
    }

    private static List<String> normalizeStringList(Object rawValue) {
        if (rawValue == null) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        if (rawValue instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (item != null && StringUtils.hasText(String.valueOf(item))) {
                    normalized.add(String.valueOf(item).trim());
                }
            }
            return normalized;
        }
        String text = String.valueOf(rawValue).trim();
        if (StringUtils.hasText(text)) {
            normalized.add(text);
        }
        return normalized;
    }
}
