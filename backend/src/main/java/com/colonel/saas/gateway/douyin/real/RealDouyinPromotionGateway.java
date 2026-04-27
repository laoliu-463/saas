package com.colonel.saas.gateway.douyin.real;

import com.colonel.saas.douyin.api.PromotionApi;
import com.colonel.saas.gateway.douyin.DouyinPromotionGateway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "douyin.test.enabled", havingValue = "false", matchIfMissing = true)
public class RealDouyinPromotionGateway implements DouyinPromotionGateway {

    private final PromotionApi promotionApi;

    public RealDouyinPromotionGateway(PromotionApi promotionApi) {
        this.promotionApi = promotionApi;
    }

    @Override
    public PromotionLinkResult generateLink(PromotionLinkCommand command) {
        PromotionApi.PromotionContext context = command.context() == null ? null : new PromotionApi.PromotionContext(
                command.context().userId(),
                command.context().deptId(),
                command.context().productId(),
                command.context().activityId(),
                command.context().sourceUrl(),
                command.context().scene()
        );
        PromotionApi.PromotionLinkResult result = promotionApi.generateLink(
                command.externalUniqueId(),
                command.promotionScene(),
                command.productIds(),
                command.needShortLink(),
                context
        );
        return new PromotionLinkResult(
                result.pickSource(),
                result.pickExtra(),
                result.shortId(),
                result.shortLink(),
                result.promoteLink(),
                result.uuidSeed()
        );
    }
}

