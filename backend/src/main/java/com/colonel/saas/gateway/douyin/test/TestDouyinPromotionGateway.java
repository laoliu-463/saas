package com.colonel.saas.gateway.douyin.test;

import com.colonel.saas.gateway.douyin.DouyinPromotionGateway;
import com.colonel.saas.service.PickSourceMappingService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@ConditionalOnProperty(name = "douyin.test.enabled", havingValue = "true")
public class TestDouyinPromotionGateway implements DouyinPromotionGateway {

    private final PickSourceMappingService pickSourceMappingService;

    public TestDouyinPromotionGateway(PickSourceMappingService pickSourceMappingService) {
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
        String pickExtra = command.context() != null && org.springframework.util.StringUtils.hasText(command.context().pickExtra())
                ? command.context().pickExtra()
                : shortId;
        String shortLink = "https://test.short.link/" + shortId;
        String promoteLink = "https://test.promote.link/activity/" + activityId + "/product/" + productId + "?pick_source=" + pickSource;

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
                    command.context().scene(),
                    pickExtra
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


