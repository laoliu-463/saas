package com.colonel.saas.constant;

import java.util.Locale;

/**
 * 活动商品在商品库中的展示状态枚举。
 * <p>
 * 对应 {@code product} 表中的 {@code display_status} 字段。商品的展示状态由
 * 系统规则自动判定（参见 {@code ProductDisplayRuleJob}），也可由运营人员手动修改。
 * </p>
 * <p>
 * 状态流转：
 * <ul>
 *   <li>{@code PENDING}（待定）— 新关联的商品初始状态，等待展示规则判定</li>
 *   <li>{@code DISPLAYING}（展示中）— 商品通过规则判定，可在前台展示</li>
 *   <li>{@code HIDDEN}（已隐藏）— 商品被手动隐藏或规则判定为不展示</li>
 * </ul>
 * </p>
 *
 * @see com.colonel.saas.job.ProductDisplayRuleJob
 * @see com.colonel.saas.constant.ProductDomainEventTypes#PRODUCT_DISPLAY_RULE_APPLIED
 */
public enum ProductDisplayStatus {

    /** 待定 — 新关联商品的默认状态，等待规则判定 */
    PENDING("待定"),

    /** 展示中 — 商品满足展示条件，可在前台展示 */
    DISPLAYING("展示中"),

    /** 已隐藏 — 商品被隐藏，不参与前台展示 */
    HIDDEN("已隐藏");

    /** 中文标签，用于前端展示 */
    private final String label;

    ProductDisplayStatus(String label) {
        this.label = label;
    }

    /**
     * 获取中文展示标签。
     *
     * @return 状态的中文描述
     */
    public String getLabel() {
        return label;
    }

    /**
     * 根据字符串编码解析为枚举值。
     * <p>
     * 解析逻辑：
     * <ul>
     *   <li>null 或空白字符串 → 返回 {@link #PENDING}（默认值）</li>
     *   <li>忽略大小写和首尾空格进行匹配</li>
     *   <li>无法匹配时返回 {@link #PENDING}</li>
     * </ul>
     * </p>
     *
     * @param code 状态编码字符串
     * @return 对应的枚举值，不会返回 null
     */
    public static ProductDisplayStatus fromCode(String code) {
        if (code == null || code.isBlank()) {
            return PENDING;
        }
        try {
            return valueOf(code.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return PENDING;
        }
    }
}
