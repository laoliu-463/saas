package com.colonel.saas.gateway.douyin;

import java.util.List;
import java.util.UUID;

public interface DouyinPromotionGateway {

    PromotionLinkResult generateLink(PromotionLinkCommand command);

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
            String sourceUrl) {
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
