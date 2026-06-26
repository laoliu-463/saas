package com.colonel.saas.domain.product.application;

import com.colonel.saas.common.enums.ProductBizStatus;
import com.colonel.saas.common.time.AppZone;
import com.colonel.saas.domain.product.policy.ProductAuditSupplementPayload;
import com.colonel.saas.domain.product.policy.ProductDisplayPolicy;
import com.colonel.saas.domain.product.policy.ProductPinPolicy;
import com.colonel.saas.entity.Merchant;
import com.colonel.saas.entity.ProductOperationState;
import com.colonel.saas.entity.ProductSnapshot;
import com.colonel.saas.service.ProductBizStatusService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * 活动商品读侧视图组装器。
 */
public final class ActivityProductViewAssembler {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ProductBizStatusService productBizStatusService;
    private final ProductDisplayPolicy productDisplayPolicy;

    public ActivityProductViewAssembler(
            ProductBizStatusService productBizStatusService,
            ProductDisplayPolicy productDisplayPolicy) {
        this.productBizStatusService = productBizStatusService;
        this.productDisplayPolicy = productDisplayPolicy;
    }

    public Map<String, Object> toActivityProductView(
            ProductSnapshot snapshot,
            ProductOperationState state,
            DecisionSummary decisionSummary,
            OrderSummary orderSummary,
            PromotionSummary promotionSummary,
            Merchant merchant,
            Function<UUID, String> userDisplayNameResolver) {
        return toActivityProductView(
                snapshot, state, decisionSummary, orderSummary, promotionSummary, merchant,
                userDisplayNameResolver, null, null);
    }

    public Map<String, Object> toActivityProductView(
            ProductSnapshot snapshot,
            ProductOperationState state,
            DecisionSummary decisionSummary,
            OrderSummary orderSummary,
            PromotionSummary promotionSummary,
            Merchant merchant,
            Function<UUID, String> userDisplayNameResolver,
            Map<UUID, String> assigneeNameMap,
            String activityName) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", snapshot.getId());
        view.put("relationId", snapshot.getId());
        view.put("activityId", snapshot.getActivityId());
        view.put("activityName", activityName);
        view.put("productId", snapshot.getProductId());
        view.put("title", snapshot.getTitle());
        view.put("cover", snapshot.getCover());
        view.put("price", snapshot.getPrice());
        view.put("priceText", snapshot.getPriceText());
        view.put("shopId", snapshot.getShopId());
        view.put("shopName", snapshot.getShopName());
        Integer activityProductStatus = productDisplayPolicy.normalizeActivityProductStatus(snapshot.getStatus());
        String activityProductStatusText = productDisplayPolicy.normalizeActivityProductStatusText(
                snapshot.getStatus(), snapshot.getStatusText());
        view.put("status", activityProductStatus);
        view.put("statusText", activityProductStatusText);
        view.put("categoryName", snapshot.getCategoryName());
        view.put("productStock", snapshot.getProductStock());
        view.put("shopScore", resolveShopScoreFromSnapshot(snapshot));
        view.put("sales", snapshot.getSales());
        view.put("detailUrl", snapshot.getDetailUrl());
        view.put("promotionStartTime", snapshot.getPromotionStartTime());
        view.put("promotionEndTime", snapshot.getPromotionEndTime());
        view.put("activityCosRatio", snapshot.getActivityCosRatio());
        view.put("activityCosRatioText", snapshot.getActivityCosRatioText());
        view.put("cosType", snapshot.getCosType());
        view.put("cosTypeText", snapshot.getCosTypeText());
        view.put("adServiceRatio", snapshot.getAdServiceRatio());
        view.put("activityAdCosRatio", snapshot.getActivityAdCosRatio());
        view.put("hasDouinGoodsTag", snapshot.getHasDouinGoodsTag());
        view.put("syncTime", snapshot.getSyncTime());

        ProductBizStatus currentStatus = ProductBizStatus.PENDING_AUDIT;
        if (state != null) {
            ProductBizStatus resolvedStatus = productBizStatusService.readBizStatus(state);
            if (resolvedStatus != null) {
                currentStatus = resolvedStatus;
            }
        }
        view.put("bizStatus", currentStatus.name());
        view.put("bizStatusLabel", currentStatus.getLabel());

        if (state != null) {
            view.put("boundActivityId", state.getBoundActivityId());
            view.put("assigneeId", state.getAssigneeId());
            view.put("assigneeName", resolveUserDisplayName(state.getAssigneeId(), assigneeNameMap, userDisplayNameResolver));
            view.put("auditStatus", state.getAuditStatus());
            view.put("auditRemark", state.getAuditRemark());
            view.put("shortLink", state.getShortLink());
            view.put("promoteLink", state.getPromoteLink());
            view.put("selectedToLibrary", Boolean.TRUE.equals(state.getSelectedToLibrary()));
            view.put("libraryVisible", Boolean.TRUE.equals(state.getSelectedToLibrary()));
            view.put("selectedAt", state.getSelectedAt());
            view.put("pinned", ProductPinPolicy.isPinned(state, LocalDateTime.now()));
            view.put("pinnedUntil", state.getPinnedUntil());
            Map<String, Object> auditSupplement = ProductAuditSupplementPayload.parse(state.getAuditPayload());
            view.put("auditSupplementSummary", buildAuditSupplementSummary(auditSupplement));
            view.put("auditSupplementComplete", isAuditSupplementComplete(auditSupplement));
            Boolean supportsAds = ProductAuditSupplementPayload.readBoolean(auditSupplement, "supportsAds");
            if (supportsAds != null) {
                view.put("supportsAds", supportsAds);
            }
            String adsRule = ProductAuditSupplementPayload.readString(auditSupplement, "adsRule");
            if (StringUtils.hasText(adsRule)) {
                view.put("adsRule", adsRule);
            }
            applyDisplayMark(view, state);
            applyActivityProductStatusFields(view, activityProductStatus, state);
        } else {
            applyDisplayMark(view, null);
            applyActivityProductStatusFields(view, activityProductStatus, null);
        }
        applyDecisionSummary(view, decisionSummary);

        BigDecimal commissionRate = resolveCommissionRate(snapshot);
        BigDecimal serviceFeeRate = resolveServiceFeeRate(snapshot);
        BigDecimal estimatedCommission = estimateFee(snapshot.getPrice(), commissionRate);
        BigDecimal estimatedServiceFee = estimateFee(snapshot.getPrice(), serviceFeeRate);
        LocalDateTime promotionEndTime = parseDateTime(snapshot.getPromotionEndTime());
        long remainingDays = calculateRemainingDays(promotionEndTime);
        boolean activityExpired = promotionEndTime != null && promotionEndTime.isBefore(LocalDateTime.now());
        boolean promotionAvailable = !activityExpired && StringUtils.hasText(snapshot.getDetailUrl());
        boolean hasMaterial = StringUtils.hasText(snapshot.getTitle()) && StringUtils.hasText(snapshot.getDetailUrl());
        boolean hasSampleRule = !activityExpired && StringUtils.hasText(snapshot.getStatusText());
        long platformSales = snapshot.getSales() == null ? 0L : snapshot.getSales();
        long orderCount = orderSummary == null ? 0L : orderSummary.orderCount();
        long attributedCount = orderSummary == null ? 0L : orderSummary.attributedCount();
        long unattributedCount = orderSummary == null ? 0L : orderSummary.unattributedCount();
        BigDecimal gmv = orderSummary == null ? yuan(0L) : yuan(orderSummary.gmvCent());
        BigDecimal serviceFee = orderSummary == null ? yuan(0L) : yuan(orderSummary.serviceFeeCent());

        view.put("commissionRate", commissionRate);
        view.put("serviceFeeRate", serviceFeeRate);
        view.put("estimatedCommission", estimatedCommission.toPlainString());
        view.put("estimatedCommissionAmount", estimatedCommission.toPlainString());
        view.put("estimatedServiceFee", estimatedServiceFee.toPlainString());
        view.put("estimatedServiceFeeAmount", estimatedServiceFee.toPlainString());
        view.put("activityExpired", activityExpired);
        view.put("activityRemainingDays", Math.max(remainingDays, 0));
        view.put("timeLeft", formatTimeLeft(promotionEndTime));
        view.put("promotionAvailable", promotionAvailable);
        view.put("hasMaterial", hasMaterial);
        view.put("hasSampleRule", hasSampleRule);
        view.put("sales30d", platformSales);
        view.put("promotionLinkCount", promotionSummary == null ? 0 : promotionSummary.linkCount());
        view.put("orderCount", orderCount);
        view.put("attributedCount", attributedCount);
        view.put("attributedOrderCount", attributedCount);
        view.put("unattributedCount", unattributedCount);
        view.put("unattributedOrderCount", unattributedCount);
        view.put("gmv", gmv.toPlainString());
        view.put("gmv30d", gmv.toPlainString());
        view.put("serviceFee", serviceFee.toPlainString());
        view.put("lastOrderTime", orderSummary == null ? null : orderSummary.lastOrderTime());
        view.put("attributionRate", formatRate(orderCount == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(attributedCount)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(orderCount), 2, RoundingMode.HALF_UP)));

        if (merchant != null) {
            view.put("merchantId", merchant.getMerchantId());
            view.put("merchantName", merchant.getMerchantName());
            view.put("merchantShopName", merchant.getShopName());
            view.put("merchantStatus", merchant.getStatus());
        } else {
            view.put("merchantId", snapshot.getShopId() == null ? null : String.valueOf(snapshot.getShopId()));
            view.put("merchantName", snapshot.getShopName());
            view.put("merchantShopName", snapshot.getShopName());
            view.put("merchantStatus", null);
        }

        PromotionView promotionView = buildPromotionView(currentStatus, state, promotionSummary);
        view.put("promotionLinkStatus", promotionView.status());
        view.put("promotionLinkStatusLabel", promotionView.statusLabel());
        view.put("promotionLink", promotionView.link());
        view.put("promotionLinkGeneratedAt", promotionView.generatedAt());
        view.put("promotionLinkExpireAt", promotionView.expireAt());
        view.put("promotionLinkFailReason", promotionView.failReason());
        Map<String, Object> promotion = new LinkedHashMap<>();
        promotion.put("status", promotionView.status());
        promotion.put("statusLabel", promotionView.statusLabel());
        promotion.put("link", promotionView.link());
        promotion.put("generatedAt", promotionView.generatedAt());
        promotion.put("expireAt", promotionView.expireAt());
        promotion.put("failReason", promotionView.failReason());
        promotion.put("copyEnabled", StringUtils.hasText(promotionView.link()));
        view.put("promotion", promotion);

        view.put("systemTags", buildSystemTags(snapshot, state, commissionRate, serviceFeeRate, platformSales, promotionSummary, activityExpired, remainingDays));
        view.put("alertTags", buildAlertTags(snapshot, state, commissionRate, serviceFeeRate, activityExpired));
        return view;
    }

    public BigDecimal resolveCommissionRate(ProductSnapshot snapshot) {
        BigDecimal fromText = parsePercentValue(snapshot.getActivityCosRatioText());
        if (fromText.compareTo(BigDecimal.ZERO) > 0) {
            return fromText;
        }
        return normalizeRatioNumber(snapshot.getActivityCosRatio());
    }

    public BigDecimal resolveServiceFeeRate(ProductSnapshot snapshot) {
        BigDecimal rate = parsePercentValue(snapshot.getAdServiceRatio());
        if (rate.compareTo(BigDecimal.ZERO) > 0) {
            return rate;
        }
        return normalizeRatioNumber(snapshot.getActivityAdCosRatio());
    }

    public BigDecimal estimateFee(Long priceCent, BigDecimal ratePercent) {
        if (priceCent == null || priceCent <= 0 || ratePercent == null || ratePercent.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(priceCent)
                .multiply(ratePercent)
                .divide(BigDecimal.valueOf(10000), 2, RoundingMode.HALF_UP);
    }

    public String formatRate(BigDecimal rate) {
        BigDecimal value = rate == null ? BigDecimal.ZERO : rate.setScale(2, RoundingMode.HALF_UP);
        return value.stripTrailingZeros().toPlainString() + "%";
    }

    public String normalizePercentText(String raw) {
        return formatRate(parsePercentValue(raw));
    }

    public LocalDateTime parseDateTime(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String value = raw.trim();
        if (value.matches("^\\d{13}$")) {
            return AppZone.fromEpochMilli(Long.parseLong(value));
        }
        if (value.matches("^\\d{10}$")) {
            return AppZone.fromEpochSecond(Long.parseLong(value));
        }
        List<DateTimeFormatter> formatters = List.of(
                DateTimeFormatter.ISO_LOCAL_DATE_TIME,
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
                DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd")
        );
        for (DateTimeFormatter formatter : formatters) {
            try {
                if (formatter == formatters.get(formatters.size() - 1)) {
                    return LocalDate.parse(value, formatter).atStartOfDay();
                }
                return LocalDateTime.parse(value, formatter);
            } catch (DateTimeParseException ignore) {
                // try next
            }
        }
        try {
            return LocalDate.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd")).atStartOfDay();
        } catch (DateTimeParseException ignore) {
            return null;
        }
    }

    public long calculateRemainingDays(LocalDateTime endTime) {
        if (endTime == null) {
            return -1;
        }
        long days = java.time.Duration.between(LocalDateTime.now(), endTime).toDays();
        if (days < 0) {
            return 0;
        }
        return days;
    }

    public String formatTimeLeft(LocalDateTime endTime) {
        if (endTime == null) {
            return "长期";
        }
        LocalDateTime now = LocalDateTime.now();
        if (endTime.isBefore(now)) {
            return "已结束";
        }
        java.time.Duration duration = java.time.Duration.between(now, endTime);
        long days = duration.toDays();
        long hours = duration.minusDays(days).toHours();
        if (days > 0) {
            return days + "天 " + hours + "小时";
        }
        long minutes = duration.minusHours(duration.toHours()).toMinutes();
        if (hours > 0) {
            return hours + "小时 " + minutes + "分钟";
        }
        return Math.max(minutes, 1) + "分钟";
    }

    public Integer resolveShopScoreFromSnapshot(ProductSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        Map<String, Object> payload = parseSnapshotPayload(snapshot.getRawPayload());
        return parseInteger(readString(payload, "shopScore"));
    }

    private BigDecimal yuan(Long cent) {
        long value = cent == null ? 0L : cent;
        return BigDecimal.valueOf(value).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    public BigDecimal normalizeRatioNumber(Long raw) {
        if (raw == null || raw <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal value = BigDecimal.valueOf(raw);
        if (raw >= 1000) {
            return value.divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal parsePercentValue(String raw) {
        if (!StringUtils.hasText(raw)) {
            return BigDecimal.ZERO;
        }
        String normalized = raw.trim()
                .replace("%", "")
                .replace("％", "")
                .replace(",", "")
                .replace(" ", "");
        try {
            return new BigDecimal(normalized).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }

    private Integer parseInteger(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String digits = raw.replaceAll("[^0-9]", "");
        if (!StringUtils.hasText(digits)) {
            return null;
        }
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Map<String, Object> parseSnapshotPayload(String rawPayload) {
        if (!StringUtils.hasText(rawPayload)) {
            return Map.of();
        }
        try {
            return OBJECT_MAPPER.readValue(rawPayload, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private String readString(Map<String, Object> payload, String key) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        Object value = payload.get(key);
        return value == null ? null : String.valueOf(value).trim();
    }

    private String resolveUserDisplayName(
            UUID userId,
            Map<UUID, String> userDisplayNames,
            Function<UUID, String> userDisplayNameResolver) {
        if (userId == null) {
            return null;
        }
        if (userDisplayNames != null) {
            return userDisplayNames.get(userId);
        }
        return userDisplayNameResolver == null ? null : userDisplayNameResolver.apply(userId);
    }

    public void applyDecisionSummary(Map<String, Object> view, DecisionSummary decisionSummary) {
        if (decisionSummary == null) {
            view.put("latestDecisionLevel", null);
            view.put("latestDecisionLabel", null);
            view.put("latestDecisionReason", null);
            view.put("latestDecisionAt", null);
            return;
        }
        view.put("latestDecisionLevel", decisionSummary.level());
        view.put("latestDecisionLabel", decisionSummary.label());
        view.put("latestDecisionReason", decisionSummary.reason());
        view.put("latestDecisionAt", decisionSummary.time());
    }

    private void applyDisplayMark(Map<String, Object> view, ProductOperationState state) {
        var presentation = productDisplayPolicy.resolveDisplayPresentation(
                state != null,
                state != null && Boolean.TRUE.equals(state.getSelectedToLibrary()),
                state == null ? null : state.getDisplayStatus(),
                state == null ? null : state.getHiddenReason(),
                state == null ? null : state.getFirstDisplayedAt(),
                state == null ? null : state.getLastDisplayedAt());
        view.put("displayStatus", presentation.displayStatus().name());
        view.put("displayMark", presentation.displayMark());
        view.put("displayMarkLabel", presentation.displayMarkLabel());
        if (state != null) {
            view.put("hiddenReason", presentation.hiddenReason());
            view.put("firstDisplayedAt", presentation.firstDisplayedAt());
            view.put("lastDisplayedAt", presentation.lastDisplayedAt());
            view.put("libraryVisible", presentation.libraryVisible());
        }
    }

    public void applyActivityProductStatusFields(
            Map<String, Object> view,
            Integer upstreamStatus,
            ProductOperationState state) {
        var presentation = productDisplayPolicy.resolveActivityProductStatusPresentation(
                upstreamStatus,
                readString(view, "statusText"),
                state == null ? null : state.getAuditStatus(),
                state == null ? null : state.getBizStatus(),
                state != null && Boolean.TRUE.equals(state.getManualDisabled()),
                state != null && Boolean.TRUE.equals(state.getSelectedToLibrary()),
                state != null && StringUtils.hasText(state.getPromoteLink()),
                state != null && StringUtils.hasText(state.getShortLink()),
                state == null ? null : state.getDisplayStatus(),
                state == null ? null : state.getHiddenReason());
        view.put("officialStatus", presentation.officialStatus());
        view.put("reviewStatus", presentation.reviewStatus());
        view.put("publishStatus", presentation.publishStatus());
        view.put("manualDisabled", presentation.manualDisabled());
        view.put("selectedToLibrary", presentation.selectedToLibrary());
        view.put("displayStatus", presentation.displayStatus().name());
        view.put("displayMark", presentation.displayMark());
        view.put("displayMarkLabel", presentation.displayMarkLabel());
        view.put("hiddenReason", presentation.hiddenReason());
    }

    private Map<String, Object> buildAuditSupplementSummary(Map<String, Object> auditSupplement) {
        if (auditSupplement == null || auditSupplement.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        copyAuditSummaryField(summary, auditSupplement, "sampleThresholdRemark");
        copyAuditSummaryField(summary, auditSupplement, "promotionScript");
        copyAuditSummaryField(summary, auditSupplement, "shippingInfo");
        copyAuditSummaryField(summary, auditSupplement, "rewardRemark");
        copyAuditSummaryField(summary, auditSupplement, "participationRequirements");
        copyAuditSummaryField(summary, auditSupplement, "campaignTimeRemark");
        if (auditSupplement.containsKey("supportsAds")) {
            summary.put("supportsAds", auditSupplement.get("supportsAds"));
        }
        copyAuditSummaryField(summary, auditSupplement, "adsRule");
        List<String> sellingPoints = ProductAuditSupplementPayload.readStringList(auditSupplement, "sellingPoints");
        if (!sellingPoints.isEmpty()) {
            summary.put("sellingPointCount", sellingPoints.size());
            summary.put("sellingPointsPreview", sellingPoints.stream().limit(2).toList());
        }
        List<String> materialFiles = ProductAuditSupplementPayload.readStringList(auditSupplement, "materialFiles");
        if (!materialFiles.isEmpty()) {
            summary.put("materialFileCount", materialFiles.size());
        }
        List<String> goodsTags = ProductAuditSupplementPayload.readStringList(auditSupplement, "goodsTags");
        if (!goodsTags.isEmpty()) {
            summary.put("goodsTags", goodsTags);
        }
        List<String> productTags = ProductAuditSupplementPayload.readStringList(auditSupplement, "productTags");
        if (!productTags.isEmpty()) {
            summary.put("productTags", productTags);
        }
        return summary;
    }

    private void copyAuditSummaryField(Map<String, Object> target, Map<String, Object> source, String key) {
        Object value = source.get(key);
        if (value != null && StringUtils.hasText(String.valueOf(value))) {
            target.put(key, String.valueOf(value).trim());
        }
    }

    private boolean isAuditSupplementComplete(Map<String, Object> auditSupplement) {
        if (auditSupplement == null || auditSupplement.isEmpty()) {
            return false;
        }
        return StringUtils.hasText(ProductAuditSupplementPayload.readString(auditSupplement, "sampleThresholdRemark"))
                && StringUtils.hasText(ProductAuditSupplementPayload.readString(auditSupplement, "promotionScript"));
    }

    private PromotionView buildPromotionView(
            ProductBizStatus currentStatus,
            ProductOperationState state,
            PromotionSummary promotionSummary) {
        String link = state == null ? null : state.getPromoteLink();
        String generatedAt = promotionSummary == null || promotionSummary.lastLinkTime() == null
                ? null
                : promotionSummary.lastLinkTime().toString();
        String expireAt = null;
        if (StringUtils.hasText(link)) {
            return new PromotionView("READY", "已生成", link, generatedAt, expireAt, null);
        }
        if (currentStatus == ProductBizStatus.LINKED || currentStatus == ProductBizStatus.FOLLOWING) {
            return new PromotionView("FAILED", "生成失败", null, generatedAt, expireAt, "推广链接缺失，请后台重试");
        }
        if (currentStatus == ProductBizStatus.ASSIGNED) {
            return new PromotionView("PENDING", "生成中", null, generatedAt, expireAt, null);
        }
        return new PromotionView("PENDING", "未生成", null, generatedAt, expireAt, null);
    }

    private List<String> buildSystemTags(
            ProductSnapshot snapshot,
            ProductOperationState state,
            BigDecimal commissionRate,
            BigDecimal serviceFeeRate,
            long platformSales,
            PromotionSummary promotionSummary,
            boolean activityExpired,
            long remainingDays) {
        List<String> tags = new ArrayList<>();
        if (commissionRate.compareTo(BigDecimal.valueOf(20)) >= 0) {
            tags.add("高佣");
        }
        if (serviceFeeRate.compareTo(BigDecimal.TEN) >= 0) {
            tags.add("高服务费");
        }
        if (platformSales >= 1_000) {
            tags.add("高销量");
        }
        if (Boolean.TRUE.equals(snapshot.getHasDouinGoodsTag())) {
            tags.add("抖音商品标");
        }
        if (!activityExpired && remainingDays >= 0 && remainingDays <= 3) {
            tags.add("活动临期");
        }
        if (state != null && StringUtils.hasText(state.getPromoteLink())) {
            tags.add("已转链");
        }
        if (promotionSummary != null && promotionSummary.linkCount() > 0) {
            tags.add("已有推广记录");
        }
        return tags;
    }

    private List<String> buildAlertTags(
            ProductSnapshot snapshot,
            ProductOperationState state,
            BigDecimal commissionRate,
            BigDecimal serviceFeeRate,
            boolean activityExpired) {
        List<String> tags = new ArrayList<>();
        if (!StringUtils.hasText(snapshot.getDetailUrl())) {
            tags.add("无商品链接");
        }
        if (activityExpired) {
            tags.add("活动过期");
        }
        Integer stock = parseInteger(snapshot.getProductStock());
        if (stock != null && stock <= 10) {
            tags.add("库存不足");
        }
        if (commissionRate.compareTo(BigDecimal.ZERO) <= 0) {
            tags.add("佣金异常");
        }
        if (serviceFeeRate.compareTo(BigDecimal.ZERO) <= 0) {
            tags.add("服务费异常");
        }
        if (state == null || state.getAssigneeId() == null) {
            tags.add("未分配负责人");
        }
        return tags;
    }

    private record PromotionView(
            String status,
            String statusLabel,
            String link,
            String generatedAt,
            String expireAt,
            String failReason) {
    }

    public record DecisionSummary(
            String level,
            String label,
            String reason,
            String time) {
    }

    public record OrderSummary(
            long orderCount,
            long attributedCount,
            long unattributedCount,
            long gmvCent,
            long serviceFeeCent,
            LocalDateTime lastOrderTime) {
    }

    public record PromotionSummary(
            int linkCount,
            LocalDateTime lastLinkTime,
            List<Map<String, Object>> linkRecords) {
    }
}
