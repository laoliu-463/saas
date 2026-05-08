package com.colonel.saas.dto.product;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ProductAuditSupplement {

    private static final int SCHEMA_VERSION = 1;

    private String exclusivePriceRemark;
    private String shippingInfo;
    private List<String> sellingPoints = List.of();
    private String promotionScript;
    private Boolean supportsAds;
    private String rewardRemark;
    private String participationRequirements;
    private String campaignTimeRemark;
    private List<String> materialFiles = List.of();

    // 寄样门槛配置
    private Long sampleThresholdSales;
    private Integer sampleThresholdLevel;
    private String sampleThresholdRemark;

    public static ProductAuditSupplement fromMap(Map<String, Object> payload) {
        ProductAuditSupplement supplement = new ProductAuditSupplement();
        if (payload == null || payload.isEmpty()) {
            return supplement;
        }
        supplement.setExclusivePriceRemark(asText(payload.get("exclusivePriceRemark")));
        supplement.setShippingInfo(asText(payload.get("shippingInfo")));
        supplement.setSellingPoints(normalizeStringList(payload.get("sellingPoints")));
        supplement.setPromotionScript(asText(payload.get("promotionScript")));
        supplement.setSupportsAds(asBoolean(payload.get("supportsAds")));
        supplement.setRewardRemark(asText(payload.get("rewardRemark")));
        supplement.setParticipationRequirements(asText(payload.get("participationRequirements")));
        supplement.setCampaignTimeRemark(asText(payload.get("campaignTimeRemark")));
        supplement.setMaterialFiles(normalizeStringList(payload.get("materialFiles")));

        // 门槛解析
        supplement.setSampleThresholdSales(asLong(payload.get("sampleThresholdSales")));
        supplement.setSampleThresholdLevel(asInteger(payload.get("sampleThresholdLevel")));
        supplement.setSampleThresholdRemark(asText(payload.get("sampleThresholdRemark")));

        return supplement;
    }

    public boolean isEmpty() {
        return !StringUtils.hasText(exclusivePriceRemark)
                && !StringUtils.hasText(shippingInfo)
                && sellingPoints.isEmpty()
                && !StringUtils.hasText(promotionScript)
                && supportsAds == null
                && !StringUtils.hasText(rewardRemark)
                && !StringUtils.hasText(participationRequirements)
                && !StringUtils.hasText(campaignTimeRemark)
                && materialFiles.isEmpty()
                && sampleThresholdSales == null
                && sampleThresholdLevel == null
                && !StringUtils.hasText(sampleThresholdRemark);
    }

    public Map<String, Object> toMap() {
        if (isEmpty()) {
            return Map.of();
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", SCHEMA_VERSION);
        putText(payload, "exclusivePriceRemark", exclusivePriceRemark);
        putText(payload, "shippingInfo", shippingInfo);
        if (!sellingPoints.isEmpty()) {
            payload.put("sellingPoints", List.copyOf(sellingPoints));
        }
        putText(payload, "promotionScript", promotionScript);
        if (supportsAds != null) {
            payload.put("supportsAds", supportsAds);
        }
        putText(payload, "rewardRemark", rewardRemark);
        putText(payload, "participationRequirements", participationRequirements);
        putText(payload, "campaignTimeRemark", campaignTimeRemark);
        if (!materialFiles.isEmpty()) {
            payload.put("materialFiles", List.copyOf(materialFiles));
        }

        // 门槛持久化
        if (sampleThresholdSales != null) payload.put("sampleThresholdSales", sampleThresholdSales);
        if (sampleThresholdLevel != null) payload.put("sampleThresholdLevel", sampleThresholdLevel);
        putText(payload, "sampleThresholdRemark", sampleThresholdRemark);

        return payload;
    }

    public String getExclusivePriceRemark() {
        return exclusivePriceRemark;
    }

    public void setExclusivePriceRemark(String exclusivePriceRemark) {
        this.exclusivePriceRemark = normalizeText(exclusivePriceRemark);
    }

    public String getShippingInfo() {
        return shippingInfo;
    }

    public void setShippingInfo(String shippingInfo) {
        this.shippingInfo = normalizeText(shippingInfo);
    }

    public List<String> getSellingPoints() {
        return sellingPoints;
    }

    public void setSellingPoints(List<String> sellingPoints) {
        this.sellingPoints = sellingPoints == null ? List.of() : List.copyOf(sellingPoints);
    }

    public String getPromotionScript() {
        return promotionScript;
    }

    public void setPromotionScript(String promotionScript) {
        this.promotionScript = normalizeText(promotionScript);
    }

    public Boolean getSupportsAds() {
        return supportsAds;
    }

    public void setSupportsAds(Boolean supportsAds) {
        this.supportsAds = supportsAds;
    }

    public String getRewardRemark() {
        return rewardRemark;
    }

    public void setRewardRemark(String rewardRemark) {
        this.rewardRemark = normalizeText(rewardRemark);
    }

    public String getParticipationRequirements() {
        return participationRequirements;
    }

    public void setParticipationRequirements(String participationRequirements) {
        this.participationRequirements = normalizeText(participationRequirements);
    }

    public String getCampaignTimeRemark() {
        return campaignTimeRemark;
    }

    public void setCampaignTimeRemark(String campaignTimeRemark) {
        this.campaignTimeRemark = normalizeText(campaignTimeRemark);
    }

    public List<String> getMaterialFiles() {
        return materialFiles;
    }

    public void setMaterialFiles(List<String> materialFiles) {
        this.materialFiles = materialFiles == null ? List.of() : List.copyOf(materialFiles);
    }

    public Long getSampleThresholdSales() {
        return sampleThresholdSales;
    }

    public void setSampleThresholdSales(Long sampleThresholdSales) {
        this.sampleThresholdSales = sampleThresholdSales;
    }

    public Integer getSampleThresholdLevel() {
        return sampleThresholdLevel;
    }

    public void setSampleThresholdLevel(Integer sampleThresholdLevel) {
        this.sampleThresholdLevel = sampleThresholdLevel;
    }

    public String getSampleThresholdRemark() {
        return sampleThresholdRemark;
    }

    public void setSampleThresholdRemark(String sampleThresholdRemark) {
        this.sampleThresholdRemark = normalizeText(sampleThresholdRemark);
    }

    private static void putText(Map<String, Object> payload, String key, String value) {
        if (StringUtils.hasText(value)) {
            payload.put(key, value);
        }
    }

    private static String asText(Object rawValue) {
        return rawValue == null ? null : normalizeText(String.valueOf(rawValue));
    }

    private static Boolean asBoolean(Object rawValue) {
        if (rawValue == null) {
            return null;
        }
        return Boolean.parseBoolean(String.valueOf(rawValue));
    }

    private static Long asLong(Object rawValue) {
        if (rawValue == null) return null;
        try {
            return Long.parseLong(String.valueOf(rawValue));
        } catch (Exception e) {
            return null;
        }
    }

    private static Integer asInteger(Object rawValue) {
        if (rawValue == null) return null;
        try {
            return Integer.parseInt(String.valueOf(rawValue));
        } catch (Exception e) {
            return null;
        }
    }

    private static String normalizeText(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return null;
        }
        String value = rawValue.trim();
        return value.isEmpty() ? null : value;
    }

    private static List<String> normalizeStringList(Object rawValue) {
        if (rawValue == null) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        if (rawValue instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (item == null) {
                    continue;
                }
                String value = normalizeText(String.valueOf(item));
                if (StringUtils.hasText(value)) {
                    normalized.add(value);
                }
            }
            return List.copyOf(normalized);
        }
        String value = normalizeText(String.valueOf(rawValue));
        if (StringUtils.hasText(value)) {
            normalized.add(value);
        }
        return List.copyOf(normalized);
    }
}
