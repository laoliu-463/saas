package com.colonel.saas.gateway.douyin.test;

import com.colonel.saas.gateway.douyin.DouyinPromotionGateway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "douyin.test.enabled", havingValue = "true")
public class TestDouyinPromotionGateway implements DouyinPromotionGateway {

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

        return new PromotionLinkResult(
                pickSource,
                pickExtra,
                shortId,
                shortLink,
                promoteLink,
                uuidSeed.toString()
        );
    }

    @Override
    public Map<String, Object> rawUpstreamPost(String appId, String method, Map<String, Object> payload) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (payload != null) {
            data.putAll(payload);
        }
        data.put("method", method == null ? "" : method);
        if (appId != null && !appId.isBlank()) {
            data.put("appId", appId.trim());
        }
        return Map.of("code", 10000, "msg", "success", "data", data);
    }
}

