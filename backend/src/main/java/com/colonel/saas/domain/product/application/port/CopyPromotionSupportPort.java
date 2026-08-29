package com.colonel.saas.domain.product.application.port;

import com.colonel.saas.entity.ProductOperationState;
import com.colonel.saas.entity.ProductSnapshot;
import com.colonel.saas.domain.shared.attribution.AttributionOwnerType;

import java.util.UUID;

/**
 * 复制推广应用层所需的商品 legacy 支撑端口。
 *
 * <p>当前由 ProductService 过渡实现，先切断应用层对 legacy 大 Service 的直接依赖；
 * 后续再把上下文读取和转链执行继续下沉到商品域应用 / 基础设施。</p>
 */
public interface CopyPromotionSupportPort {

    Context prepareCopyPromotionContext(String activityId, String productId, String actionLabel);

    GeneratedPromotionLink generatePromotionLinkForCopy(
            String activityId,
            String productId,
            UUID userId,
            UUID deptId,
            String externalUniqueId,
            Integer promotionScene,
            boolean needShortLink,
            String scene,
            String talentId,
            String idempotencyKey);

    default GeneratedPromotionLink generatePromotionLinkForCopy(
            String activityId,
            String productId,
            UUID userId,
            UUID deptId,
            String externalUniqueId,
            Integer promotionScene,
            boolean needShortLink,
            String scene,
            String talentId,
            String idempotencyKey,
            AttributionOwnerType attributionOwnerType) {
        return generatePromotionLinkForCopy(
                activityId, productId, userId, deptId, externalUniqueId, promotionScene,
                needShortLink, scene, talentId, idempotencyKey);
    }

    record Context(ProductSnapshot snapshot, ProductOperationState state) {
    }

    record GeneratedPromotionLink(String shortLink, String promoteLink, String pickSource) {
    }
}
