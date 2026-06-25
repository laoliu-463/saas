package com.colonel.saas.domain.product.port;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 抖音转链 Port（DDD-PRODUCT-004）。
 *
 * <p>商品域应用层依赖此 Port 触达抖店转链能力，Port 把"上游平台 SDK"封装为
 * 业务侧无依赖的接口，便于：
 * <ul>
 *   <li>应用层不直接接触 {@code gateway.douyin.*} 包</li>
 *   <li>未来替换为 Mock / 异步队列 / 其它平台时只换 adapter</li>
 *   <li>单元测试时用 Stub / Mock 实现替换</li>
 * </ul>
 *
 * <p>实现位于 {@code domain.product.infrastructure} 包，委派给
 * {@code com.colonel.saas.gateway.douyin.DouyinPromotionGateway}。</p>
 */
public interface DouyinConvertPort {

    /**
     * 生成推广链接（含 pick_source / 短链接）。
     *
     * @param command 转链命令（外部唯一 ID + 推广场景 + 商品 ID 列表 + 上下文）
     * @return 转链结果
     */
    PromotionLinkResult convert(PromotionLinkCommand command);

    /**
     * 原始上游 POST（管理后台探针专用，DDD-PRODUCT-004 不参与业务路径）。
     */
    Map<String, Object> rawUpstreamPost(String appId, String method, Map<String, Object> payload);

    /**
     * 转链命令。
     */
    record PromotionLinkCommand(
            String externalUniqueId,
            int promotionScene,
            List<String> productIds,
            boolean needShortLink,
            PromotionContext context) {
    }

    /**
     * 推广上下文（用户/部门/商品/活动/达人等归属信息）。
     */
    record PromotionContext(
            UUID userId,
            UUID deptId,
            String productId,
            String activityId,
            String sourceUrl,
            String scene,
            String talentId,
            String pickExtra) {
    }

    /**
     * 转链结果。
     */
    record PromotionLinkResult(
            String pickSource,
            String pickExtra,
            String shortId,
            String shortLink,
            String promoteLink,
            String uuidSeed) {
    }
}
