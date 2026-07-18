package com.colonel.saas.domain.sample.policy;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * 寄样合作单的订单复制文本策略。
 *
 * <p>只消费调用方已组装的只读事实，不查询服务、Mapper 或外部接口。</p>
 */
public class SampleOrderCopyPolicy {

    private static final long TEN_THOUSAND = 10_000L;
    private static final String PLACEHOLDER = "---";
    private static final Pattern UNSAFE_SINGLE_LINE_PATTERN =
            Pattern.compile("[\\p{Cc}\\u2028\\u2029]+");

    public String format(OrderCopyFacts facts) {
        return String.join("\n",
                "商品名称：" + textOrPlaceholder(facts.productName()),
                "商品ID：" + textOrPlaceholder(facts.productId()),
                "店铺：" + textOrPlaceholder(facts.shopName()),
                "申请数量：" + positiveQuantityOrPlaceholder(facts.quantity()),
                "商品规格：" + textOrPlaceholder(facts.productSpecification()),
                "申样备注：" + textOrEmpty(facts.remark()),
                "达人昵称：" + textOrPlaceholder(facts.talentNickname()),
                "抖音号：" + textOrPlaceholder(facts.talentDouyinNo()),
                "粉丝数：" + formatFollowers(facts.talentFansCount()),
                "近30天橱窗销量：" + nonNegativeNumberOrPlaceholder(facts.talentWindowSales30d()),
                "收货人：" + textOrPlaceholder(facts.recipientName()),
                "收货电话：" + textOrPlaceholder(facts.recipientPhone()),
                "收货地址：" + textOrPlaceholder(facts.recipientAddress()));
    }

    private String formatFollowers(Long value) {
        if (value == null || value < 0) {
            return PLACEHOLDER;
        }
        if (value < TEN_THOUSAND) {
            return String.valueOf(value);
        }
        return BigDecimal.valueOf(value)
                .divide(BigDecimal.valueOf(TEN_THOUSAND))
                .stripTrailingZeros()
                .toPlainString() + "W";
    }

    private String nonNegativeNumberOrPlaceholder(Long value) {
        return value == null || value < 0 ? PLACEHOLDER : String.valueOf(value);
    }

    private String positiveQuantityOrPlaceholder(Integer value) {
        return value == null || value <= 0 ? PLACEHOLDER : String.valueOf(value);
    }

    private String textOrPlaceholder(String value) {
        String singleLine = sanitizeSingleLine(value);
        return hasText(singleLine) ? singleLine : PLACEHOLDER;
    }

    private String textOrEmpty(String value) {
        String singleLine = sanitizeSingleLine(value);
        return hasText(singleLine) ? singleLine : "";
    }

    private String sanitizeSingleLine(String value) {
        if (value == null) {
            return null;
        }
        return UNSAFE_SINGLE_LINE_PATTERN.matcher(value).replaceAll(" ").trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /** 生成文案所需的只读事实。 */
    public record OrderCopyFacts(
            String productName,
            String productId,
            String shopName,
            Integer quantity,
            String productSpecification,
            String remark,
            String talentNickname,
            String talentDouyinNo,
            Long talentFansCount,
            Long talentWindowSales30d,
            String recipientName,
            String recipientPhone,
            String recipientAddress) {
    }
}
