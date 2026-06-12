package com.colonel.saas.domain.product.application.dto;

/**
 * 复制推广简介（DDD-PRODUCT-004）返回结果。
 *
 * <p>封装面向渠道/达人复制的"商品简介 + 推广链接"结果，包含降级原因和真实写入开关状态，
 * 便于上游 Controller 透传给前端并支持降级提示。</p>
 *
 * @param copyText                复制简介正文（带换行 / 链接 / 卖点）
 * @param promotionLinkGenerated  是否触发了真实转链；false 表示走降级文案
 * @param promotionLink           推广链接（短链优先，其次长链）
 * @param pickSource              转链 pick_source（用于订单归因）
 * @param fallbackReason          降级原因（real promotion write 关闭时为常量字符串）
 * @param realPromotionWriteEnabled   真实转链功能总开关
 * @param allowRealPromotionWrite     当前环境是否允许真实写入
 */
public record PromotionLinkCopyResult(
        String copyText,
        boolean promotionLinkGenerated,
        String promotionLink,
        String pickSource,
        String fallbackReason,
        boolean realPromotionWriteEnabled,
        boolean allowRealPromotionWrite
) {
}
