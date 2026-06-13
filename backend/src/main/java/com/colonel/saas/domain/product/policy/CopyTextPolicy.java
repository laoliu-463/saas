package com.colonel.saas.domain.product.policy;

import com.colonel.saas.domain.config.facade.ConfigDomainFacade;
import com.colonel.saas.entity.ProductOperationState;
import com.colonel.saas.entity.ProductSnapshot;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 复制推广简介纯文本渲染 Policy（DDD-PRODUCT-004）。
 *
 * <p>从 {@code ProductService} 抽离的纯渲染逻辑，无 Spring DI 调用：
 * 模板优先（{@code configDomainFacade.getPromotionTemplate().copyBriefTemplate()}），
 * 缺失时回退硬编码简介。输入和输出均为值对象，便于单元测试。</p>
 */
public final class CopyTextPolicy {

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
        if (StringUtils.hasText(template)) {
            return renderTemplate(template, snapshot, state, promotionLink);
        }
        return renderHardcoded(snapshot, state, promotionLink);
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
        if (StringUtils.hasText(copyPromotionLink)) {
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
        if (!StringUtils.hasText(text)
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
            if (StringUtils.hasText(candidate)) {
                return candidate.trim();
            }
        }
        return null;
    }

    // ---- internal helpers (replicated from ProductService for pure policy) ----

    private static Map<String, Object> parseAuditPayload(String raw) {
        if (!StringUtils.hasText(raw)) {
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
}
