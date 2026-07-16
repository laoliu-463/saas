package com.colonel.saas.domain.product.policy;

import com.colonel.saas.domain.config.facade.ConfigDomainFacade;
import com.colonel.saas.domain.shared.policy.DomainText;
import com.colonel.saas.entity.ProductOperationState;
import com.colonel.saas.entity.ProductSnapshot;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 复制推广简介纯文本渲染 Policy（DDD-PRODUCT-004）。
 *
 * <p>从 {@code ProductService} 抽离的纯渲染逻辑，无 Spring DI 调用：
 * 模板优先（{@code configDomainFacade.getPromotionTemplate().copyBriefTemplate()}），
 * 缺失时回退硬编码简介。输入和输出均为值对象，便于单元测试。</p>
 */
public final class CopyTextPolicy {

    private static final Pattern LINE_SEPARATOR_PATTERN = Pattern.compile("[\\r\\n\\u2028\\u2029]+");

    private CopyTextPolicy() {
    }

    /**
     * 渲染复制简介正文。
     */
    public static String render(
            ConfigDomainFacade configDomainFacade,
            ProductSnapshot snapshot,
            ProductOperationState state,
            String promotionLink) {
        var templateConfig = configDomainFacade == null ? null : configDomainFacade.getPromotionTemplate();
        String template = templateConfig == null ? null : templateConfig.copyBriefTemplate();
        if (DomainText.hasText(template)) {
            return renderTemplate(template, snapshot, state, promotionLink);
        }
        return renderHardcoded(snapshot, state, promotionLink);
    }

    /**
     * 渲染寄样合作台使用的抖音分享文本。
     *
     * <p>价格严格按商品快照的“分”口径转换，佣金严格按快照的基点口径转换；
     * 奖励和推广时间只读取审核载荷与商品快照，不补造业务事实。</p>
     */
    public static String renderDouyinShare(
            ProductSnapshot snapshot,
            ProductOperationState state,
            String promotionLink) {
        Map<String, Object> auditSupplement = parseAuditPayload(
                state == null ? null : state.getAuditPayload());
        String promotionStartTime = firstText(
                readString(auditSupplement, "promotionStartTime"),
                snapshot == null ? null : snapshot.getPromotionStartTime());
        String promotionEndTime = firstText(
                readString(auditSupplement, "promotionEndTime"),
                snapshot == null ? null : snapshot.getPromotionEndTime());
        String commissionRate = snapshot == null
                ? null
                : firstText(
                        formatBasisPointRate(snapshot.getActivityCosRatio()),
                        snapshot.getActivityCosRatioText());
        String activityAdCommissionRate = snapshot == null
                ? null
                : formatBasisPointRate(snapshot.getActivityAdCosRatio());

        List<String> lines = new ArrayList<>();
        lines.add("【抖音】" + displaySingleLineText(snapshot == null ? null : snapshot.getTitle()));
        lines.add("【店铺名称】" + displaySingleLineText(snapshot == null ? null : snapshot.getShopName()));
        lines.add("【售价】" + displaySingleLineText(formatPrice(snapshot)));
        lines.add("【佣金率】" + displaySingleLineText(commissionRate));
        lines.add("【投放期佣金】" + displaySingleLineText(activityAdCommissionRate));
        lines.add("【奖励说明】" + displaySingleLineText(readString(auditSupplement, "rewardRemark")));
        lines.add("【开始时间】" + displaySingleLineText(promotionStartTime));
        lines.add("【结束时间】" + displaySingleLineText(promotionEndTime));
        lines.add("【推广链接】");
        lines.add(displayPromotionLink(promotionLink));
        return String.join("\n", lines);
    }

    private static String renderTemplate(
            String template,
            ProductSnapshot snapshot,
            ProductOperationState state,
            String promotionLink) {
        Map<String, Object> auditSupplement = parseAuditPayload(state == null ? null : state.getAuditPayload());
        String productName = displayText(snapshot.getTitle());
        String commissionRate = displayText(snapshot.getActivityCosRatioText());
        String shortLink = displayText(firstText(promotionLink));
        String serviceFeeRate = displayText(formatRate(null));
        String customText = displayText(readString(auditSupplement, "exclusivePriceRemark"));
        return template
                .replace("{productName}", productName)
                .replace("{product_name}", productName)
                .replace("{productId}", displayText(snapshot.getProductId()))
                .replace("{product_id}", displayText(snapshot.getProductId()))
                .replace("{commissionRate}", commissionRate)
                .replace("{commission_rate}", commissionRate)
                .replace("{serviceFeeRate}", serviceFeeRate)
                .replace("{service_fee_rate}", serviceFeeRate)
                .replace("{shortLink}", shortLink)
                .replace("{promotion_link}", shortLink)
                .replace("{custom_text}", customText);
    }

    private static String renderHardcoded(
            ProductSnapshot snapshot,
            ProductOperationState state,
            String promotionLink) {
        Map<String, Object> auditSupplement = parseAuditPayload(state == null ? null : state.getAuditPayload());
        List<String> sellingPoints = readStringList(auditSupplement, "sellingPoints");
        String sellingPointText = sellingPoints.isEmpty() ? "-" : String.join("、", sellingPoints);
        String promotionScript = readString(auditSupplement, "promotionScript");
        String copyPromotionLink = firstText(promotionLink);
        List<String> lines = new ArrayList<>();
        lines.add("【商品】" + displayText(snapshot.getTitle()) + "（" + displayText(snapshot.getShopName()) + "）");
        lines.add("【售价】" + displayText(snapshot.getPriceText())
                + "  【佣金率】" + displayText(snapshot.getActivityCosRatioText())
                + "  【近30天】" + displayText(snapshot.getSales()));
        lines.add("【卖点】" + sellingPointText);
        lines.add("【话术】" + displayText(promotionScript));
        lines.add("【寄样门槛】销售额≥" + displayText(readString(auditSupplement, "sampleThresholdSales"))
                + " / 等级≥LV" + displayText(readString(auditSupplement, "sampleThresholdLevel")));
        lines.add("【专属价说明】" + displayText(readString(auditSupplement, "exclusivePriceRemark")));
        if (DomainText.hasText(copyPromotionLink)) {
            lines.add("【链接】" + copyPromotionLink);
        } else {
            lines.add("【推广链接】未生成");
        }
        return String.join("\n", lines);
    }

    public static String displayText(Object value) {
        if (value == null) {
            return "-";
        }
        String text = String.valueOf(value).trim();
        if (!DomainText.hasText(text)
                || "null".equalsIgnoreCase(text)
                || "undefined".equalsIgnoreCase(text)) {
            return "-";
        }
        return text;
    }

    public static String firstText(String... candidates) {
        if (candidates == null) {
            return null;
        }
        for (String candidate : candidates) {
            if (DomainText.hasText(candidate)) {
                return candidate.trim();
            }
        }
        return null;
    }

    /**
     * 选择首个非空推广链接；链接原值包含控制字符或行分隔符时视为非法。
     */
    public static String safePromotionLink(String... candidates) {
        if (candidates == null) {
            return null;
        }
        for (String candidate : candidates) {
            if (!DomainText.hasText(candidate)) {
                continue;
            }
            boolean unsafe = candidate.codePoints().anyMatch(codePoint ->
                    Character.isISOControl(codePoint) || codePoint == 0x2028 || codePoint == 0x2029);
            return unsafe ? null : candidate.trim();
        }
        return null;
    }

    // ---- internal helpers (replicated from ProductService for pure policy) ----

    private static Map<String, Object> parseAuditPayload(String raw) {
        if (!DomainText.hasText(raw)) {
            return Map.of();
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(raw, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private static String readString(Map<String, Object> map, String key) {
        if (map == null) {
            return null;
        }
        Object value = map.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private static List<String> readStringList(Map<String, Object> map, String key) {
        if (map == null) {
            return List.of();
        }
        Object value = map.get(key);
        if (value instanceof List<?> list) {
            List<String> out = new ArrayList<>(list.size());
            for (Object v : list) {
                if (v != null) {
                    out.add(String.valueOf(v));
                }
            }
            return out;
        }
        return List.of();
    }

    private static String formatRate(Object rate) {
        if (rate == null) {
            return null;
        }
        return rate + "%";
    }

    private static String formatPrice(ProductSnapshot snapshot) {
        if (snapshot == null) {
            return "-";
        }
        if (snapshot.getPrice() != null) {
            return formatDecimal(BigDecimal.valueOf(snapshot.getPrice(), 2)) + "元";
        }
        String priceText = firstText(snapshot.getPriceText());
        if (!DomainText.hasText(priceText)) {
            return "-";
        }
        String normalized = priceText
                .replace("¥", "")
                .replace("￥", "")
                .replace("元", "")
                .replace(",", "")
                .trim();
        try {
            return formatDecimal(new BigDecimal(normalized)) + "元";
        } catch (NumberFormatException ignored) {
            return priceText.endsWith("元") ? priceText : priceText + "元";
        }
    }

    private static String formatBasisPointRate(Long rate) {
        if (rate == null) {
            return null;
        }
        return formatDecimal(BigDecimal.valueOf(rate).movePointLeft(2)) + "%";
    }

    private static String formatDecimal(BigDecimal value) {
        BigDecimal normalized = value.stripTrailingZeros();
        if (normalized.scale() < 0) {
            normalized = normalized.setScale(0);
        }
        return normalized.toPlainString();
    }

    private static String displayPromotionLink(String promotionLink) {
        String link = safePromotionLink(promotionLink);
        return DomainText.hasText(link) ? link : "未生成";
    }

    private static String displaySingleLineText(Object value) {
        if (value == null) {
            return "-";
        }
        String text = LINE_SEPARATOR_PATTERN.matcher(String.valueOf(value)).replaceAll(" ").trim();
        if (!DomainText.hasText(text)
                || "null".equalsIgnoreCase(text)
                || "undefined".equalsIgnoreCase(text)) {
            return "-";
        }
        return text;
    }
}
