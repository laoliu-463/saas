package com.colonel.saas.domain.product.application.port;

import java.util.List;
import java.util.UUID;

/**
 * 商品域转链端口（DDD-PRODUCT-004）。
 *
 * <p>隔离 {@link com.colonel.saas.gateway.douyin.DouyinPromotionGateway}，使
 * {@link com.colonel.saas.service.ProductService} 只依赖领域端口而非 SDK 适配层。</p>
 */
public interface DouyinConvertPort {

    ConvertResult convert(ConvertCommand command);

    record ConvertContext(
            UUID userId,
            UUID deptId,
            String productId,
            String activityId,
            String sourceUrl,
            String scene,
            String talentId,
            String pickExtra) {
    }

    record ConvertCommand(
            String externalUniqueId,
            int promotionScene,
            List<String> productIds,
            boolean needShortLink,
            ConvertContext context) {
    }

    record ConvertResult(
            String pickSource,
            String pickExtra,
            String shortId,
            String shortLink,
            String promoteLink,
            String uuidSeed) {
    }
}
