package com.colonel.saas.domain.product.infrastructure;

import com.colonel.saas.domain.product.application.port.DouyinConvertPort;
import com.colonel.saas.gateway.douyin.DouyinPromotionGateway;
import org.springframework.stereotype.Component;

/**
 * 将 {@link DouyinConvertPort} 映射到 legacy {@link DouyinPromotionGateway}（DDD-PRODUCT-004）。
 */
@Component
public class DouyinPromotionGatewayConvertAdapter implements DouyinConvertPort {

    private final DouyinPromotionGateway gateway;

    public DouyinPromotionGatewayConvertAdapter(DouyinPromotionGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public ConvertResult convert(ConvertCommand command) {
        ConvertContext context = command.context();
        DouyinPromotionGateway.PromotionLinkResult result = gateway.generateLink(
                new DouyinPromotionGateway.PromotionLinkCommand(
                        command.externalUniqueId(),
                        command.promotionScene(),
                        command.productIds(),
                        command.needShortLink(),
                        new DouyinPromotionGateway.PromotionContext(
                                context.userId(),
                                context.deptId(),
                                context.productId(),
                                context.activityId(),
                                context.sourceUrl(),
                                context.scene(),
                                context.talentId(),
                                context.pickExtra())));
        return new ConvertResult(
                result.pickSource(),
                result.pickExtra(),
                result.shortId(),
                result.shortLink(),
                result.promoteLink(),
                result.uuidSeed());
    }
}
