package com.colonel.saas.domain.product.infrastructure;

import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.common.exception.UpstreamErrorCode;
import com.colonel.saas.domain.product.application.port.DouyinConvertPort;
import com.colonel.saas.douyin.DouyinApiException;
import com.colonel.saas.gateway.douyin.DouyinPromotionGateway;
import org.springframework.stereotype.Component;

import java.util.Arrays;

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
        DouyinPromotionGateway.PromotionLinkResult result;
        try {
            result = gateway.generateLink(
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
        } catch (DouyinApiException ex) {
            UpstreamErrorCode errorCode = Arrays.stream(UpstreamErrorCode.values())
                    .filter(candidate -> candidate.name().equals(ex.getErrorCodeTag()))
                    .findFirst()
                    .orElse(UpstreamErrorCode.EXTERNAL_GENERIC);
            throw BusinessException.upstream(errorCode, ex.getErrorMsg(), ex);
        }
        return new ConvertResult(
                result.pickSource(),
                result.pickExtra(),
                result.shortId(),
                result.shortLink(),
                result.promoteLink(),
                result.uuidSeed());
    }
}
