package com.colonel.saas.douyin;

import com.colonel.saas.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.security.SecureRandom;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Slf4j
@Service
public class DouyinOAuthService {

    private static final String STATE_KEY_PREFIX = "douyin:oauth:state:";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RedisTemplate<String, Object> redisTemplate;
    private final DouyinConfig douyinConfig;
    private final DouyinOAuthProperties properties;
    private final DouyinTokenService douyinTokenService;

    public DouyinOAuthService(RedisTemplate<String, Object> redisTemplate,
                              DouyinConfig douyinConfig,
                              DouyinOAuthProperties properties,
                              DouyinTokenService douyinTokenService) {
        this.redisTemplate = redisTemplate;
        this.douyinConfig = douyinConfig;
        this.properties = properties;
        this.douyinTokenService = douyinTokenService;
    }

    public AuthorizeUrlResult createAuthorizeUrl(String appId) {
        String appKey = resolveAppKey(appId);
        String state = generateState();
        String normalizedAppId = normalizeAppId(appId);
        redisTemplate.opsForValue().set(
                stateKey(state),
                normalizedAppId == null ? "" : normalizedAppId,
                Duration.ofMinutes(Math.max(1L, properties.getStateTtlMinutes()))
        );

        String baseAuthorizeUrl = requireText(properties.getAuthorizeUrl(), "douyin oauth authorize url is not configured");
        String separator = baseAuthorizeUrl.contains("?") ? "&" : "?";
        String authorizeUrl = baseAuthorizeUrl
                + separator
                + "app_key=" + encodeQueryParam(appKey)
                + "&response_type=code"
                + "&redirect_uri=" + encodeQueryParam(requireText(properties.getRedirectUri(), "douyin oauth redirect uri is not configured"))
                + "&state=" + encodeQueryParam(state);

        log.info("Douyin OAuth authorize URL generated, appId={}, statePresent=true", maskAppId(appKey));
        return new AuthorizeUrlResult(authorizeUrl, state, properties.getRedirectUri());
    }

    public String handleCallback(String code, String state) {
        String finalState = requireText(state, "missing oauth state");
        String finalCode = requireText(code, "missing oauth code");
        Object storedAppId = redisTemplate.opsForValue().get(stateKey(finalState));
        if (storedAppId == null) {
            throw BusinessException.param("oauth state is invalid or expired");
        }
        redisTemplate.delete(stateKey(finalState));

        String appId = storedAppId instanceof String stored && StringUtils.hasText(stored) ? stored : null;
        douyinTokenService.exchangeCodeAndBootstrap(appId, finalCode, "authorization_code", null, null, null, null);
        log.info("Douyin OAuth callback handled successfully, appId={}", maskAppId(appId));
        return properties.getFrontendSuccessUrl();
    }

    public String failureRedirectUrl() {
        return properties.getFrontendFailureUrl();
    }

    private String resolveAppKey(String appId) {
        String normalizedAppId = normalizeAppId(appId);
        if (StringUtils.hasText(normalizedAppId)) {
            return normalizedAppId;
        }
        if (StringUtils.hasText(douyinConfig.getClientKey())) {
            return douyinConfig.getClientKey().trim();
        }
        if (StringUtils.hasText(douyinConfig.getAppId())) {
            return douyinConfig.getAppId().trim();
        }
        throw BusinessException.param("douyin client key is not configured");
    }

    private String normalizeAppId(String appId) {
        return StringUtils.hasText(appId) ? appId.trim() : null;
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw BusinessException.param(message);
        }
        return value.trim();
    }

    private String generateState() {
        byte[] bytes = new byte[24];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String encodeQueryParam(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String stateKey(String state) {
        return STATE_KEY_PREFIX + state;
    }

    private String maskAppId(String appId) {
        if (!StringUtils.hasText(appId)) {
            return "-";
        }
        String value = appId.trim();
        if (value.length() <= 8) {
            return "****";
        }
        return value.substring(0, 4) + "..." + value.substring(value.length() - 4);
    }

    public record AuthorizeUrlResult(String authorizeUrl, String state, String redirectUri) {
    }
}
