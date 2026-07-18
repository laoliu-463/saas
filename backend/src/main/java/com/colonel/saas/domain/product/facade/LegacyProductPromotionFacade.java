package com.colonel.saas.domain.product.facade;

import com.colonel.saas.domain.product.application.CopyPromotionApplicationService;
import com.colonel.saas.domain.product.application.dto.PromotionLinkCopyResult;
import com.colonel.saas.domain.product.facade.dto.ProductPromotionCopyDTO;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 推广复制门面的兼容实现，复用现有商品域复制/转链应用服务。
 */
@Service
public class LegacyProductPromotionFacade implements ProductPromotionFacade {

    private final CopyPromotionApplicationService copyPromotionApplicationService;

    public LegacyProductPromotionFacade(
            CopyPromotionApplicationService copyPromotionApplicationService) {
        this.copyPromotionApplicationService = copyPromotionApplicationService;
    }

    @Override
    public ProductPromotionCopyDTO copyForSample(
            String activityId,
            String productId,
            UUID userId,
            UUID deptId,
            String talentId,
            String idempotencyKey) {
        PromotionLinkCopyResult result = copyPromotionApplicationService.copyPromotionForSample(
                activityId, productId, userId, deptId, talentId, idempotencyKey);
        return new ProductPromotionCopyDTO(
                result.copyText(),
                result.promotionLinkGenerated(),
                result.promotionLink(),
                result.fallbackReason());
    }
}
