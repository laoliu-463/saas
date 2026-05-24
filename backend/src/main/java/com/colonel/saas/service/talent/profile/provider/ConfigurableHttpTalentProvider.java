package com.colonel.saas.service.talent.profile.provider;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.Method;
import com.colonel.saas.config.TalentCollectProperties;
import com.colonel.saas.service.talent.profile.TalentProfileFieldNames;
import com.colonel.saas.service.talent.profile.TalentProfileProvider;
import com.colonel.saas.service.talent.profile.TalentProfileQuery;
import com.colonel.saas.service.talent.profile.TalentProfileResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ConfigurableHttpTalentProvider implements TalentProfileProvider {

    private final ObjectMapper objectMapper;
    private final TalentCollectProperties collectProperties;
    private final boolean enabled;
    private final String endpoint;
    private final String method;
    private final String token;
    private final Map<String, String> headers;

    public ConfigurableHttpTalentProvider(
            ObjectMapper objectMapper,
            TalentCollectProperties collectProperties,
            @Value("${talent.profile.http.enabled:false}") boolean enabled,
            @Value("${talent.profile.http.endpoint:}") String endpoint,
            @Value("${talent.profile.http.method:GET}") String method,
            @Value("${talent.profile.http.token:}") String token,
            @Value("${talent.profile.http.header-authorization:}") String authorizationHeader) {
        this.objectMapper = objectMapper;
        this.collectProperties = collectProperties;
        this.enabled = enabled;
        this.endpoint = endpoint;
        this.method = method;
        this.token = token;
        this.headers = new LinkedHashMap<>();
        if (StringUtils.hasText(authorizationHeader)) {
            this.headers.put("Authorization", authorizationHeader);
        } else if (StringUtils.hasText(token)) {
            this.headers.put("Authorization", "Bearer " + token.trim());
        }
    }

    @Override
    public String providerCode() {
        return "configurable_http";
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    public boolean supports(TalentProfileQuery query) {
        return enabled
                && collectProperties.isApiAllowed()
                && !collectProperties.isMockOnly()
                && StringUtils.hasText(endpoint)
                && query != null
                && StringUtils.hasText(query.getInput())
                && !query.isManualFill();
    }

    @Override
    public TalentProfileResult fetch(TalentProfileQuery query) {
        try {
            HttpRequest request = HttpRequest.of(endpoint.trim())
                    .method(parseMethod(method))
                    .timeout(15_000);
            headers.forEach(request::header);
            if (Method.GET.equals(parseMethod(method))) {
                request.form("input", query.getInput());
                if (query.getParsed() != null && StringUtils.hasText(query.getParsed().getSecUid())) {
                    request.form("secUid", query.getParsed().getSecUid());
                }
                if (query.getParsed() != null && StringUtils.hasText(query.getParsed().getDouyinNo())) {
                    request.form("douyinAccount", query.getParsed().getDouyinNo());
                }
            } else {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("input", query.getInput());
                if (query.getParsed() != null) {
                    body.put("parsed", query.getParsed());
                }
                request.body(objectMapper.writeValueAsString(body), "application/json");
            }
            HttpResponse response = request.execute();
            if (response.getStatus() < 200 || response.getStatus() >= 300) {
                return failed("HTTP_STATUS_" + response.getStatus(), "configurable http provider returned status " + response.getStatus());
            }
            return mapResponse(response.body(), query.getInput());
        } catch (Exception ex) {
            return failed("HTTP_PROVIDER_ERROR", ex.getMessage());
        }
    }

    private TalentProfileResult mapResponse(String body, String input) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        JsonNode data = root.path("data").isMissingNode() ? root : root.path("data");
        List<String> fetched = new ArrayList<>();
        List<String> unsupported = new ArrayList<>(TalentProfileResult.DEFAULT_UNSUPPORTED);

        String nickname = text(data, "nickname");
        String avatarUrl = text(data, "avatarUrl", "avatar_url");
        Long fans = number(data, "fansCount", "fans_count");
        Long likes = number(data, "likeCount", "like_count", "likes_count");
        Long following = number(data, "followingCount", "following_count");
        Long works = number(data, "worksCount", "works_count");
        String ip = text(data, "ipLocation", "ip_location");
        String level = text(data, "talentLevel", "talent_level");
        Long sales = number(data, "sales30d", "sales_30d");

        if (StringUtils.hasText(nickname)) {
            fetched.add(TalentProfileFieldNames.NICKNAME);
        }
        if (StringUtils.hasText(avatarUrl)) {
            fetched.add(TalentProfileFieldNames.AVATAR_URL);
        }
        if (fans != null) {
            fetched.add(TalentProfileFieldNames.FANS_COUNT);
        }
        if (likes != null) {
            fetched.add(TalentProfileFieldNames.LIKE_COUNT);
        }
        if (following != null) {
            fetched.add(TalentProfileFieldNames.FOLLOWING_COUNT);
        }
        if (works != null) {
            fetched.add(TalentProfileFieldNames.WORKS_COUNT);
        }
        if (StringUtils.hasText(ip)) {
            fetched.add(TalentProfileFieldNames.IP_LOCATION);
        }
        if (StringUtils.hasText(level)) {
            fetched.add(TalentProfileFieldNames.TALENT_LEVEL);
            unsupported.remove(TalentProfileFieldNames.TALENT_LEVEL);
        }
        if (sales != null) {
            fetched.add(TalentProfileFieldNames.SALES_30D);
            unsupported.remove(TalentProfileFieldNames.SALES_30D);
        }

        Map<String, Object> raw = objectMapper.convertValue(root, Map.class);
        if (fetched.isEmpty()) {
            return failed("HTTP_EMPTY_PROFILE", "configurable http provider returned no profile fields");
        }
        String syncStatus = unsupported.isEmpty() ? TalentProfileResult.STATUS_SUCCESS : TalentProfileResult.STATUS_PARTIAL_SUCCESS;
        return TalentProfileResult.builder()
                .success(true)
                .providerCode(providerCode())
                .syncStatus(syncStatus)
                .douyinAccount(text(data, "douyinAccount", "douyin_account"))
                .talentUid(text(data, "talentUid", "talent_uid", "uid"))
                .secUid(text(data, "secUid", "sec_uid"))
                .nickname(nickname)
                .avatarUrl(avatarUrl)
                .fansCount(fans)
                .likeCount(likes)
                .followingCount(following)
                .worksCount(works)
                .ipLocation(ip)
                .talentLevel(level)
                .sales30d(sales)
                .fetchedFields(fetched)
                .unsupportedFields(unsupported)
                .rawPayload(raw == null ? Map.of("input", input, "body", body) : raw)
                .build();
    }

    private Method parseMethod(String raw) {
        if (!StringUtils.hasText(raw)) {
            return Method.GET;
        }
        return Method.valueOf(raw.trim().toUpperCase());
    }

    private String text(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (!value.isMissingNode() && !value.isNull() && StringUtils.hasText(value.asText())) {
                return value.asText().trim();
            }
        }
        return null;
    }

    private Long number(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (!value.isMissingNode() && !value.isNull()) {
                if (value.isNumber()) {
                    return value.asLong();
                }
                String text = value.asText("").trim();
                if (StringUtils.hasText(text)) {
                    try {
                        return Long.parseLong(text);
                    } catch (NumberFormatException ignored) {
                        return null;
                    }
                }
            }
        }
        return null;
    }

    private TalentProfileResult failed(String code, String message) {
        return TalentProfileResult.builder()
                .success(false)
                .providerCode(providerCode())
                .syncStatus(TalentProfileResult.STATUS_FAILED)
                .errorCode(code)
                .errorMessage(message)
                .unsupportedFields(new ArrayList<>(TalentProfileResult.DEFAULT_UNSUPPORTED))
                .build();
    }
}
