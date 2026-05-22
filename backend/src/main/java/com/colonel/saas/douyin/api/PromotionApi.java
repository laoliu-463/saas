package com.colonel.saas.douyin.api;

import com.colonel.saas.douyin.DouyinApiClient;
import com.colonel.saas.douyin.DouyinApiException;
import com.colonel.saas.douyin.util.ShortCodeGenerator;
import com.colonel.saas.service.PickSourceMappingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.UUID;

@Service
@Slf4j
public class PromotionApi {

    private static final String INST_PICK_SOURCE_CONVERT_METHOD = "buyin.instPickSourceConvert";
    private static final String LEGACY_METHOD = "buyin.promotion.link.generate";
    private static final String FALLBACK_METHOD_1 = "buyin.kolProductShare";
    private static final String FALLBACK_METHOD_2 = "buyin.getProductShareMaterial";
    private static final int MAX_PICK_EXTRA_LENGTH = 20;

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
        String normalizedPickExtra = normalizePickExtra(context == null ? null : context.pickExtra());

        if (context != null && StringUtils.hasText(context.sourceUrl())) {
            Map<String, Object> response = convertBySourceUrl(context.sourceUrl(), normalizedPickExtra);
            PromotionLinkResult result = PromotionLinkResult.from(
                    response,
                    shortId,
                    uuidSeed.toString(),
                    normalizedPickExtra
            );
            saveMappingIfNecessary(result, uuidSeed, context);
            return result;
        }

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
        PromotionLinkResult result = PromotionLinkResult.from(
                response,
                shortId,
                uuidSeed.toString(),
                normalizedPickExtra
        );
        saveMappingIfNecessary(result, uuidSeed, context);
        return result;
    }

    private Map<String, Object> convertBySourceUrl(String productUrl, String pickExtra) {
        Map<String, Object> params = new HashMap<>();
        params.put("product_url", productUrl);
        params.put("pick_extra", pickExtra);
        return douyinApiClient.post(INST_PICK_SOURCE_CONVERT_METHOD, params);
    }

    private void saveMappingIfNecessary(PromotionLinkResult result, UUID uuidSeed, PromotionContext context) {
        if (context == null || context.userId() == null || result == null || !StringUtils.hasText(result.pickSource())) {
            return;
        }
        pickSourceMappingService.saveOrUpdate(
                context.userId(),
                null,
                context.deptId(),
                null,
                null,
                result.shortId(),
                uuidSeed,
                result.pickSource(),
                context.productId(),
                context.activityId(),
                context.sourceUrl(),
                result.promoteLink(),
                null,
                context.scene(),
                result.pickExtra()
        );
    }

    private String normalizePickExtra(String pickExtra) {
        if (!StringUtils.hasText(pickExtra)) {
            return null;
        }
        String normalized = pickExtra.trim()
                .replaceAll("[^A-Za-z0-9_]", "_");
        if (normalized.length() <= MAX_PICK_EXTRA_LENGTH) {
            return normalized;
        }
        if (normalized.startsWith("channel_")) {
            String tail = normalized.substring("channel_".length());
            int allowedTailLength = MAX_PICK_EXTRA_LENGTH - "channel_".length();
            if (tail.length() > allowedTailLength) {
                tail = tail.substring(0, allowedTailLength);
            }
            return "channel_" + tail;
        }
        return normalized.substring(0, MAX_PICK_EXTRA_LENGTH).toLowerCase(Locale.ROOT);
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
            String pickSource,
            String pickExtra,
            String shortId,
            String shortLink,
            String promoteLink,
            String uuidSeed
    ) {
        public static PromotionLinkResult from(Map<String, Object> response, String shortId, String uuidSeed) {
            return from(response, shortId, uuidSeed, null);
        }

        public static PromotionLinkResult from(
                Map<String, Object> response,
                String shortId,
                String uuidSeed,
                String desiredPickExtra) {
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
                    data != null ? asStringOrNull(data.get("converted_link")) : null,
                    data != null ? asStringOrNull(data.get("converted_url")) : null,
                    data != null ? asStringOrNull(data.get("product_url")) : null,
                    data != null ? asStringOrNull(data.get("share_link")) : null,
                    data != null ? asStringOrNull(data.get("url")) : null
            );
            String shortLink = firstNonBlank(
                    data != null ? asStringOrNull(data.get("short_link")) : null,
                    data != null ? asStringOrNull(data.get("short_url")) : null
            );
            String responsePickSource = data != null ? asStringOrNull(data.get("pick_source")) : null;
            String responsePickExtra = data != null ? asStringOrNull(data.get("pick_extra")) : null;
            String finalPickSource = firstNonBlank(
                    responsePickSource,
                    extractPickSource(promoteLink)
            );
            String finalPickExtra = firstNonBlank(
                    responsePickExtra,
                    extractPickExtra(promoteLink),
                    desiredPickExtra
            );
            return new PromotionLinkResult(
                    finalPickSource,
                    finalPickExtra,
                    shortId,
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

        private static String extractPickSource(String promoteLink) {
            if (StringUtils.hasText(promoteLink)) {
                try {
                    String query = URI.create(promoteLink).getQuery();
                    if (query != null) {
                        String[] parts = query.split("&");
                        for (String part : parts) {
                            String[] kv = part.split("=", 2);
                            if (kv.length == 2 && "pick_source".equals(kv[0])) {
                                return URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
                            }
                        }
                    }
                } catch (Exception ex) {
                    logMalformedPromotionLink("pick_source", promoteLink, ex);
                    return null;
                }
            }
            return null;
        }

        private static String extractPickExtra(String promoteLink) {
            if (!StringUtils.hasText(promoteLink)) {
                return null;
            }
            try {
                String query = URI.create(promoteLink).getQuery();
                if (query == null) {
                    return null;
                }
                String[] parts = query.split("&");
                for (String part : parts) {
                    String[] kv = part.split("=", 2);
                    if (kv.length == 2 && "pick_extra".equals(kv[0])) {
                        return URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
                    }
                }
            } catch (Exception ex) {
                logMalformedPromotionLink("pick_extra", promoteLink, ex);
                return null;
            }
            return null;
        }

        private static void logMalformedPromotionLink(String field, String promoteLink, Exception ex) {
            PromotionApi.log.warn(
                    "Failed to parse {} from promotion link: {}",
                    field,
                    describePromotionLink(promoteLink),
                    ex
            );
        }

        private static String describePromotionLink(String promoteLink) {
            if (!StringUtils.hasText(promoteLink)) {
                return "blank";
            }
            return "length=" + promoteLink.length() + ", hash=" + Integer.toHexString(promoteLink.hashCode());
        }
    }

    public record PromotionContext(
            UUID userId,
            UUID deptId,
            String productId,
            String activityId,
            String sourceUrl,
            String scene,
            String pickExtra
    ) {
    }
}
