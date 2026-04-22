package com.colonel.saas.douyin.api;

import com.colonel.saas.douyin.DouyinApiClient;
import com.colonel.saas.douyin.DouyinApiException;
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

    private static final String LEGACY_METHOD = "buyin.promotion.link.generate";
    private static final String FALLBACK_METHOD_1 = "buyin.kolProductShare";
    private static final String FALLBACK_METHOD_2 = "buyin.getProductShareMaterial";

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

        Map<String, Object> response = postWithFallback(params);
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

    private Map<String, Object> postWithFallback(Map<String, Object> params) {
        try {
            return douyinApiClient.post(LEGACY_METHOD, params);
        } catch (DouyinApiException ex) {
            if (!isApiServiceOff(ex)) {
                throw ex;
            }
            try {
                return douyinApiClient.post(FALLBACK_METHOD_1, params);
            } catch (DouyinApiException ex2) {
                if (!isApiServiceOff(ex2)) {
                    throw ex2;
                }
                return douyinApiClient.post(FALLBACK_METHOD_2, params);
            }
        }
    }

    private boolean isApiServiceOff(DouyinApiException ex) {
        if (ex == null) {
            return false;
        }
        String subCode = ex.getSubCode();
        String errorMsg = ex.getErrorMsg();
        return ex.getErrorCode() == 70000
                || containsIgnoreCase(subCode, "api-service-off")
                || containsIgnoreCase(errorMsg, "API不存在")
                || containsIgnoreCase(errorMsg, "API已下线");
    }

    private boolean containsIgnoreCase(String source, String needle) {
        if (source == null || needle == null) {
            return false;
        }
        return source.toLowerCase().contains(needle.toLowerCase());
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
            String promoteLink = firstNonBlank(
                    data != null ? asStringOrNull(data.get("promote_link")) : null,
                    data != null ? asStringOrNull(data.get("promotion_link")) : null,
                    data != null ? asStringOrNull(data.get("share_link")) : null,
                    data != null ? asStringOrNull(data.get("url")) : null
            );
            String shortLink = firstNonBlank(
                    data != null ? asStringOrNull(data.get("short_link")) : null,
                    data != null ? asStringOrNull(data.get("short_url")) : null
            );
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

        private static String firstNonBlank(String... values) {
            if (values == null) {
                return null;
            }
            for (String value : values) {
                if (StringUtils.hasText(value)) {
                    return value;
                }
            }
            return null;
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
