package com.colonel.saas.gateway.douyin.mock;

import com.colonel.saas.gateway.douyin.DouyinPromotionGateway;
import com.colonel.saas.service.PickSourceMappingService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@ConditionalOnProperty(name = "douyin.mock.enabled", havingValue = "true")
public class MockDouyinPromotionGateway implements DouyinPromotionGateway {

    private final PickSourceMappingService pickSourceMappingService;

    public MockDouyinPromotionGateway(PickSourceMappingService pickSourceMappingService) {
        this.pickSourceMappingService = pickSourceMappingService;
    }

    @Override
    public PromotionLinkResult generateLink(PromotionLinkCommand command) {
        String activityId = command.context() == null ? "unknown-activity" : command.context().activityId();
        String productId = command.productIds() == null || command.productIds().isEmpty()
                ? "unknown-product"
                : command.productIds().get(0);
        UUID uuidSeed = UUID.randomUUID();
        String suffix = String.valueOf(Math.abs((activityId + "-" + productId + "-" + uuidSeed).hashCode()));
        String shortId = "MOCK" + suffix.substring(0, Math.min(6, suffix.length()));
        String pickSource = shortId;
        String pickExtra = shortId;
        String shortLink = "https://mock.short.link/" + shortId;
        String promoteLink = "https://mock.promote.link/activity/" + activityId + "/product/" + productId + "?pick_source=" + pickSource;

        if (command.context() != null && command.context().userId() != null) {
            pickSourceMappingService.saveOrUpdate(
                    command.context().userId(),
                    null,
                    command.context().deptId(),
                    null,
                    null,
                    shortId,
                    uuidSeed,
                    pickSource,
                    productId,
                    activityId,
                    command.context().sourceUrl(),
                    promoteLink,
                    null,
                    command.context().scene()
            );
        }

        return new PromotionLinkResult(
                pickSource,
                pickExtra,
                shortId,
                shortLink,
                promoteLink,
                uuidSeed.toString()
        );
    }
}
