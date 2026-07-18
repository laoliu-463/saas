package com.colonel.saas.domain.product.facade.dto;

/**
 * 商品域提供给寄样域的推广复制结果。
 *
 * <p>只暴露寄样复制所需事实，不包含 pick_source、第三方原始响应或鉴权信息。</p>
 */
public record ProductPromotionCopyDTO(
        String text,
        boolean promotionLinkGenerated,
        String promotionLink,
        String fallbackReason) {
}
