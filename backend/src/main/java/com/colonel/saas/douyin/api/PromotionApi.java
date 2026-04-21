package com.colonel.saas.douyin.api;

import com.colonel.saas.douyin.DouyinApiClient;
import com.colonel.saas.douyin.util.ShortCodeGenerator;
import com.colonel.saas.service.PickSourceMappingService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PromotionApi {

    private final DouyinApiClient douyinApiClient;
    private final PickSourceMappingService pickSourceMappingService;

    public PromotionApi(DouyinApiClient douyinApiClient, PickSourceMappingService pickSourceMappingService) {
        this.douyinApiClient = douyinApiClient;
        this.pickSourceMappingService = pickSourceMappingService;
    }

    public PromotionLinkResult generateLink(
            String externalUniqueId,
            int promotionScene,
            List<String> productIds,
            boolean needShortLink
    ) {
        return generateLink(externalUniqueId, promotionScene, productIds, needShortLink, null);
    }

    public PromotionLinkResult generateLink(
            String externalUniqueId,
            int promotionScene,
            List<String> productIds,
            boolean needShortLink,
            PromotionContext context
    ) {
        UUID uuidSeed = UUID.randomUUID();
        String shortId = ShortCodeGenerator.generate(uuidSeed);

        Map<String, Object> params = new HashMap<>();
        params.put("external_unique_id", externalUniqueId);
        params.put("promotion_scene", promotionScene);
        params.put("product_ids", productIds);
        params.put("need_short_link", needShortLink);

        Map<String, Object> extra = new HashMap<>();
        extra.put("uuid_seed", uuidSeed.toString());
        extra.put("pick_source", shortId);
        params.put("extra", extra);

        Map<String, Object> response = douyinApiClient.post("buyin.promotion.link.generate", params);
        PromotionLinkResult result = PromotionLinkResult.from(response, shortId, uuidSeed.toString());
        if (context != null && context.userId() != null) {
            pickSourceMappingService.saveOrUpdate(
                    context.userId(),
                    context.deptId(),
                    result.shortId(),
                    uuidSeed,
                    result.shortId(),
                    context.productId(),
                    context.activityId(),
                    context.sourceUrl(),
                    result.promoteLink()
            );
        }
        return result;
    }

    public record PromotionLinkResult(
            String shortId,
            String shortLink,
            String promoteLink,
            String uuidSeed
    ) {
        public static PromotionLinkResult from(Map<String, Object> response, String shortId, String uuidSeed) {
            Map<String, Object> data = null;
            if (response != null) {
                Object dataObj = response.get("data");
                if (dataObj instanceof Map<?, ?> mapData) {
                    data = new HashMap<>();
                    for (Map.Entry<?, ?> entry : mapData.entrySet()) {
                        if (entry.getKey() != null) {
                            data.put(String.valueOf(entry.getKey()), entry.getValue());
                        }
                    }
                }
            }
            String promoteLink = data != null ? asStringOrNull(data.get("promote_link")) : null;
            String shortLink = data != null ? asStringOrNull(data.get("short_link")) : null;
            String extractedShortId = extractShortId(
                    data != null ? asStringOrNull(data.get("pick_source")) : null,
                    promoteLink,
                    shortId
            );
            return new PromotionLinkResult(
                    extractedShortId,
                    shortLink,
                    promoteLink,
                    uuidSeed
            );
        }

        private static String asStringOrNull(Object value) {
            return value == null ? null : String.valueOf(value);
        }

        private static String extractShortId(String pickSource, String promoteLink, String fallback) {
            if (StringUtils.hasText(pickSource) && pickSource.length() <= 10) {
                return pickSource;
            }
            if (StringUtils.hasText(promoteLink)) {
                try {
                    String query = URI.create(promoteLink).getQuery();
                    if (query != null) {
                        String[] parts = query.split("&");
                        for (String part : parts) {
                            String[] kv = part.split("=", 2);
                            if (kv.length == 2 && ("pick_source".equals(kv[0]) || "pick_extra".equals(kv[0]))) {
                                String decoded = URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
                                if (decoded.length() <= 10) {
                                    return decoded;
                                }
                            }
                        }
                    }
                } catch (Exception ignore) {
                    return fallback;
                }
            }
            return fallback;
        }
    }

    public record PromotionContext(
            UUID userId,
            UUID deptId,
            String productId,
            String activityId,
            String sourceUrl
    ) {
    }
}
