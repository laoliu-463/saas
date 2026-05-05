package com.colonel.saas.gateway.douyin.real;

import com.colonel.saas.douyin.api.PromotionApi;
import com.colonel.saas.gateway.douyin.contract.DouyinContractFixtureProvider;
import com.colonel.saas.gateway.douyin.contract.DouyinUpstreamModeSupport;
import com.colonel.saas.gateway.douyin.DouyinPromotionGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "douyin.test.enabled", havingValue = "false", matchIfMissing = true)
public class RealDouyinPromotionGateway implements DouyinPromotionGateway {

    private final PromotionApi promotionApi;
    private final DouyinUpstreamModeSupport upstreamModeSupport;
    private final DouyinContractFixtureProvider contractFixtureProvider;

    public RealDouyinPromotionGateway(
            PromotionApi promotionApi,
            DouyinUpstreamModeSupport upstreamModeSupport,
            DouyinContractFixtureProvider contractFixtureProvider) {
        this.promotionApi = promotionApi;
        this.upstreamModeSupport = upstreamModeSupport;
        this.contractFixtureProvider = contractFixtureProvider;
    }

    @Override
    public PromotionLinkResult generateLink(PromotionLinkCommand command) {
        logGateway();
        if (upstreamModeSupport.isContract()) {
            return contractFixtureProvider.buildPromotionLinkResult(command);
        }
        PromotionApi.PromotionContext context = command.context() == null ? null : new PromotionApi.PromotionContext(
                command.context().userId(),
                command.context().deptId(),
                command.context().productId(),
                command.context().activityId(),
                command.context().sourceUrl(),
                command.context().scene(),
                command.context().pickExtra()
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

    private void logGateway() {
        log.info(
                "gateway=RealDouyinPromotionGateway, upstreamMode={}, appKey={}, shopId={}, authId={}",
                upstreamModeSupport.value(),
                mask(contractFixtureProvider.appKey()),
                contractFixtureProvider.shopId(),
                contractFixtureProvider.authId()
        );
    }

    private String mask(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.trim();
        if (normalized.length() <= 8) {
            return normalized;
        }
        return normalized.substring(0, 4) + "****" + normalized.substring(normalized.length() - 4);
    }
}
