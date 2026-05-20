package com.colonel.saas.service.talent.profile.provider;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.colonel.saas.service.talent.TalentInputParseResult;
import com.colonel.saas.service.talent.profile.TalentProfileFieldNames;
import com.colonel.saas.service.talent.profile.TalentProfileProvider;
import com.colonel.saas.service.talent.profile.TalentProfileQuery;
import com.colonel.saas.service.talent.profile.TalentProfileResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class PublicWebTalentProvider implements TalentProfileProvider {

    private static final Pattern JSON_SNIPPET = Pattern.compile("\\{[^{}]{0,20000}?\"nickname\"[^{}]{0,20000}?\\}");
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";

    private final ObjectMapper objectMapper;
    private final boolean enabled;

    public PublicWebTalentProvider(
            ObjectMapper objectMapper,
            @Value("${talent.profile.public-web.enabled:true}") boolean enabled) {
        this.objectMapper = objectMapper;
        this.enabled = enabled;
    }

    @Override
    public String providerCode() {
        return "public_web";
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    public boolean supports(TalentProfileQuery query) {
        return enabled && query != null && StringUtils.hasText(query.getInput());
    }

    @Override
    public TalentProfileResult fetch(TalentProfileQuery query) {
        String targetUrl = resolveTargetUrl(query);
        if (!StringUtils.hasText(targetUrl)) {
            return failed("PUBLIC_WEB_NO_URL", "cannot resolve public profile url from input");
        }
        try {
            HttpResponse response = HttpRequest.get(targetUrl)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "text/html,application/json")
                    .timeout(15_000)
                    .execute();
            if (response.getStatus() < 200 || response.getStatus() >= 300) {
                return failed("PUBLIC_WEB_HTTP_" + response.getStatus(),
                        "public page request failed with status " + response.getStatus());
            }
            String body = response.body();
            if (!StringUtils.hasText(body)) {
                return failed("PUBLIC_WEB_EMPTY_BODY", "public page response body is empty");
            }
            if (body.contains("验证码") || body.toLowerCase().contains("captcha")) {
                return failed("PUBLIC_WEB_BLOCKED", "public page blocked by anti-bot challenge");
            }
            return parseHtml(body, targetUrl, query);
        } catch (Exception ex) {
            return failed("PUBLIC_WEB_ERROR", ex.getMessage());
        }
    }

    private String resolveTargetUrl(TalentProfileQuery query) {
        TalentInputParseResult parsed = query.getParsed();
        if (parsed != null && StringUtils.hasText(parsed.getProfileUrl())) {
            return parsed.getProfileUrl().trim();
        }
        String input = query.getInput().trim();
        if (input.startsWith("http://") || input.startsWith("https://")) {
            return input;
        }
        if (parsed != null && StringUtils.hasText(parsed.getSecUid())) {
            return "https://www.douyin.com/user/" + parsed.getSecUid();
        }
        if (StringUtils.hasText(input)) {
            return "https://www.douyin.com/user/" + input;
        }
        return null;
    }

    private TalentProfileResult parseHtml(String body, String url, TalentProfileQuery query) {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("sourceUrl", url);
        raw.put("htmlLength", body.length());

        JsonNode embedded = extractEmbeddedJson(body);
        if (embedded != null) {
            raw.put("embeddedJson", objectMapper.convertValue(embedded, Map.class));
        }

        String nickname = firstText(body, embedded, "\"nickname\"\\s*:\\s*\"([^\"]+)\"");
        String avatarUrl = firstText(body, embedded, "\"avatarUrl\"\\s*:\\s*\"([^\"]+)\"", "\"avatar_url\"\\s*:\\s*\"([^\"]+)\"");
        Long fans = firstLong(body, embedded, "\"follower_count\"\\s*:\\s*(\\d+)", "\"followerCount\"\\s*:\\s*(\\d+)", "\"fans_count\"\\s*:\\s*(\\d+)");
        Long likes = firstLong(body, embedded, "\"total_favorited\"\\s*:\\s*(\\d+)", "\"totalFavorited\"\\s*:\\s*(\\d+)", "\"like_count\"\\s*:\\s*(\\d+)");
        Long following = firstLong(body, embedded, "\"following_count\"\\s*:\\s*(\\d+)", "\"followingCount\"\\s*:\\s*(\\d+)");
        Long works = firstLong(body, embedded, "\"aweme_count\"\\s*:\\s*(\\d+)", "\"awemeCount\"\\s*:\\s*(\\d+)", "\"works_count\"\\s*:\\s*(\\d+)");
        String ip = firstText(body, embedded, "\"ip_location\"\\s*:\\s*\"([^\"]+)\"", "\"ipLocation\"\\s*:\\s*\"([^\"]+)\"");
        String secUid = firstText(body, embedded, "\"sec_uid\"\\s*:\\s*\"([^\"]+)\"", "\"secUid\"\\s*:\\s*\"([^\"]+)\"");
        String uniqueId = firstText(body, embedded, "\"unique_id\"\\s*:\\s*\"([^\"]+)\"", "\"uniqueId\"\\s*:\\s*\"([^\"]+)\"");

        List<String> fetched = new ArrayList<>();
        List<String> unsupported = new ArrayList<>(TalentProfileResult.DEFAULT_UNSUPPORTED);
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
        if (StringUtils.hasText(uniqueId)) {
            fetched.add(TalentProfileFieldNames.DOUYIN_ACCOUNT);
        }
        if (StringUtils.hasText(secUid)) {
            fetched.add(TalentProfileFieldNames.SEC_UID);
        }

        if (fetched.isEmpty()) {
            return failed("PUBLIC_WEB_PARSE_EMPTY", "no public profile fields parsed from page");
        }

        String syncStatus = unsupported.isEmpty()
                ? TalentProfileResult.STATUS_SUCCESS
                : TalentProfileResult.STATUS_PARTIAL_SUCCESS;
        return TalentProfileResult.builder()
                .success(true)
                .providerCode(providerCode())
                .syncStatus(syncStatus)
                .douyinAccount(uniqueId)
                .talentUid(query.getParsed() == null ? null : query.getParsed().getUid())
                .secUid(secUid)
                .nickname(nickname)
                .avatarUrl(decodeUrl(avatarUrl))
                .fansCount(fans)
                .likeCount(likes)
                .followingCount(following)
                .worksCount(works)
                .ipLocation(ip)
                .fetchedFields(fetched)
                .unsupportedFields(unsupported)
                .rawPayload(raw)
                .build();
    }

    private JsonNode extractEmbeddedJson(String body) {
        Matcher matcher = JSON_SNIPPET.matcher(body);
        while (matcher.find()) {
            String snippet = matcher.group();
            try {
                return objectMapper.readTree(snippet);
            } catch (Exception ignored) {
                // try next
            }
        }
        int renderIdx = body.indexOf("RENDER_DATA");
        if (renderIdx >= 0) {
            int start = body.indexOf('{', renderIdx);
            int end = body.indexOf("</script>", start);
            if (start >= 0 && end > start) {
                String encoded = body.substring(start, end).trim();
                try {
                    String decoded = URLDecoder.decode(encoded, StandardCharsets.UTF_8);
                    return objectMapper.readTree(decoded);
                } catch (Exception ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private String firstText(String body, JsonNode node, String... patterns) {
        for (String pattern : patterns) {
            Matcher matcher = Pattern.compile(pattern).matcher(body);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        if (node != null) {
            String fromNode = textFromNode(node, "nickname");
            if (StringUtils.hasText(fromNode)) {
                return fromNode;
            }
        }
        return null;
    }

    private Long firstLong(String body, JsonNode node, String... patterns) {
        for (String pattern : patterns) {
            Matcher matcher = Pattern.compile(pattern).matcher(body);
            if (matcher.find()) {
                return Long.parseLong(matcher.group(1));
            }
        }
        return null;
    }

    private String textFromNode(JsonNode node, String field) {
        JsonNode value = node.findValue(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private String decodeUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return url;
        }
        return url.replace("\\u002F", "/").replace("\\/", "/");
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
