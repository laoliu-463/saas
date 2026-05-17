package com.colonel.saas.gateway.douyin;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface DouyinPromotionGateway {

    PromotionLinkResult generateLink(PromotionLinkCommand command);

    /**
     * Low-level upstream POST for admin probes (any method name + JSON-like body).
     */
    Map<String, Object> rawUpstreamPost(String appId, String method, Map<String, Object> payload);

    record PromotionLinkCommand(
            String externalUniqueId,
            int promotionScene,
            List<String> productIds,
            boolean needShortLink,
            PromotionContext context) {
    }

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

    record PromotionLinkResult(
            String pickSource,
            String pickExtra,
            String shortId,
            String shortLink,
            String promoteLink,
            String uuidSeed) {
    }
}
