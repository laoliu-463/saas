package com.colonel.saas.domain.product.infrastructure;

import com.colonel.saas.domain.product.port.DouyinConvertPort;
import com.colonel.saas.gateway.douyin.DouyinPromotionGateway;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * {@link DouyinConvertPort} 适配器（DDD-PRODUCT-004）。
 *
 * <p>把 domain 侧 port 调用委派给基础设施侧 {@link DouyinPromotionGateway}，
 * 保持商品域应用层不直接接触 gateway 包。</p>
 */
@Component
public class DouyinConvertPortAdapter implements DouyinConvertPort {

    private final DouyinPromotionGateway douyinPromotionGateway;

    public DouyinConvertPortAdapter(DouyinPromotionGateway douyinPromotionGateway) {
        this.douyinPromotionGateway = douyinPromotionGateway;
    }

    @Override
    public PromotionLinkResult convert(PromotionLinkCommand command) {
        DouyinPromotionGateway.PromotionLinkResult gwResult = douyinPromotionGateway.generateLink(
                new DouyinPromotionGateway.PromotionLinkCommand(
                        command.externalUniqueId(),
                        command.promotionScene(),
                        command.productIds(),
                        command.needShortLink(),
                        new DouyinPromotionGateway.PromotionContext(
                                command.context().userId(),
                                command.context().deptId(),
                                command.context().productId(),
                                command.context().activityId(),
                                command.context().sourceUrl(),
                                command.context().scene(),
                                command.context().talentId(),
                                command.context().pickExtra()
                        )
                )
        );
        return new PromotionLinkResult(
                gwResult.pickSource(),
                gwResult.pickExtra(),
                gwResult.shortId(),
                gwResult.shortLink(),
                gwResult.promoteLink(),
                gwResult.uuidSeed()
        );
    }

    @Override
    public Map<String, Object> rawUpstreamPost(String appId, String method, Map<String, Object> payload) {
        return douyinPromotionGateway.rawUpstreamPost(appId, method, payload);
    }
}
