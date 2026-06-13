package com.colonel.saas.domain.product.application;

import com.colonel.saas.service.ProductService;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 复制讲解 / 转链应用层（DDD-PRODUCT-004）。
 *
 * <p>Controller 写路径统一经本服务委派 {@link ProductService#generatePromotionLinkCopy}，
 * 不改变现有 API 契约与业务行为。</p>
 */
@Service
public class CopyPromotionApplicationService {

    private final ProductService productService;

    public CopyPromotionApplicationService(ProductService productService) {
        this.productService = productService;
    }

    public ProductService.PromotionLinkCopyResult generatePromotionLinkCopy(
            String activityId,
            String productId,
            UUID userId,
            UUID deptId,
            String externalUniqueId,
            Integer promotionScene,
            boolean needShortLink,
            String scene,
            String talentId,
            String idempotencyKey) {
        return productService.generatePromotionLinkCopy(
                activityId,
                productId,
                userId,
                deptId,
                externalUniqueId,
                promotionScene,
                needShortLink,
                scene,
                talentId,
                idempotencyKey);
    }
}
