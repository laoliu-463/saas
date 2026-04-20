package com.colonel.saas.douyin;

import com.colonel.saas.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
public class DouyinTokenService {

    private static final String TOKEN_KEY_PREFIX = "douyin:token:";
    private static final String REFRESH_KEY_PREFIX = "douyin:refresh:";
    private static final String LOCK_KEY_PREFIX = "douyin:lock:token:";
    private static final String EXPIRE_AT_KEY_PREFIX = "douyin:token:expire_at:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final RestTemplate douyinRestTemplate;
    private final DouyinConfig douyinConfig;
    private final long refreshThresholdSeconds;
    private final long redisLockMinutes;

    public DouyinTokenService(
            RedisTemplate<String, Object> redisTemplate,
            RestTemplate douyinRestTemplate,
            DouyinConfig douyinConfig,
            @Value("${douyin.token.refresh-threshold-seconds:300}") long refreshThresholdSeconds,
            @Value("${douyin.token.redis-lock-minutes:5}") long redisLockMinutes) {
        this.redisTemplate = redisTemplate;
        this.douyinRestTemplate = douyinRestTemplate;
        this.douyinConfig = douyinConfig;
        this.refreshThresholdSeconds = refreshThresholdSeconds;
        this.redisLockMinutes = redisLockMinutes;
    }

    public String getValidToken(String appId) {
        String finalAppId = resolveAppId(appId);
        Object tokenObj = redisTemplate.opsForValue().get(tokenKey(finalAppId));
        String token = tokenObj instanceof String ? (String) tokenObj : null;

        if (token == null || token.isBlank() || isTokenExpiringSoon(finalAppId)) {
            refreshToken(finalAppId);
            Object refreshedTokenObj = redisTemplate.opsForValue().get(tokenKey(finalAppId));
            if (!(refreshedTokenObj instanceof String refreshedToken) || refreshedToken.isBlank()) {
                throw new BusinessException("刷新抖音 token 后未获取到有效 access_token");
            }
            return refreshedToken;
        }
        return token;
    }

    public void refreshToken(String appId) {
        String finalAppId = resolveAppId(appId);
        String lockKey = lockKey(finalAppId);
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", Duration.ofMinutes(redisLockMinutes));
        if (!Boolean.TRUE.equals(locked)) {
            throw new BusinessException("Token 刷新任务正在执行中");
        }

        try {
            String refreshToken = getRefreshToken(finalAppId);
            if (refreshToken == null || refreshToken.isBlank()) {
                throw new BusinessException("缺少 refresh_token，请重新授权");
            }

            Map<String, Object> payload = new HashMap<>();
            payload.put("grant_type", "refresh_token");
            payload.put("refresh_token", refreshToken);
            payload.put("client_key", douyinConfig.getClientKey());
            payload.put("client_secret", douyinConfig.getClientSecret());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, Object> response = douyinRestTemplate.postForObject(
                    buildMethodUrl("buyin.oauth.token"),
                    new HttpEntity<>(payload, headers),
                    Map.class
            );

            int code = parseCode(response);
            if (code != 0) {
                if (code == 31012) {
                    throw new BusinessException("refresh_token 已失效，请重新授权");
                }
                throw new DouyinApiException(code, parseMessage(response));
            }

            Map<String, Object> data = getData(response);
            String newAccessToken = asString(data.get("access_token"));
            String newRefreshToken = asString(data.get("refresh_token"));
            long expiresIn = asLong(data.get("expires_in"), 7200L);
            long expireAt = Instant.now().getEpochSecond() + expiresIn;

            redisTemplate.opsForValue().set(tokenKey(finalAppId), newAccessToken, Duration.ofSeconds(expiresIn));
            redisTemplate.opsForValue().set(refreshKey(finalAppId), newRefreshToken, Duration.ofDays(30));
            redisTemplate.opsForValue().set(expireAtKey(finalAppId), expireAt, Duration.ofSeconds(expiresIn));

            log.info("Douyin token refreshed successfully for appId={}", finalAppId);
        } catch (DouyinApiException e) {
            log.error("Douyin token refresh failed, appId={}, code={}", finalAppId, e.getErrorCode());
            throw e;
        } catch (Exception e) {
            log.error("Douyin token refresh error, appId={}", finalAppId, e);
            throw new BusinessException("刷新抖音 token 失败");
        } finally {
            redisTemplate.delete(lockKey);
        }
    }

    public boolean isTokenExpiringSoon(String appId) {
        String finalAppId = resolveAppId(appId);
        Object expireAtObj = redisTemplate.opsForValue().get(expireAtKey(finalAppId));
        long expireAt = asLong(expireAtObj, 0L);
        if (expireAt <= 0L) {
            return true;
        }
        long now = Instant.now().getEpochSecond();
        return expireAt - now < refreshThresholdSeconds;
    }

    private String getRefreshToken(String appId) {
        Object refreshObj = redisTemplate.opsForValue().get(refreshKey(appId));
        if (refreshObj instanceof String refresh && !refresh.isBlank()) {
            return refresh;
        }
        return null;
    }

    private String resolveAppId(String appId) {
        String finalAppId = (appId == null || appId.isBlank()) ? douyinConfig.getAppId() : appId;
        if (finalAppId == null || finalAppId.isBlank()) {
            throw new BusinessException("缺少 douyin.app.app-id 配置");
        }
        return finalAppId;
    }

    private String buildMethodUrl(String method) {
        String baseUrl = Objects.requireNonNullElse(douyinConfig.getBaseUrl(), "https://open.douyin.com");
        return baseUrl.endsWith("/") ? baseUrl + method : baseUrl + "/" + method;
    }

    private String tokenKey(String appId) {
        return TOKEN_KEY_PREFIX + appId;
    }

    private String refreshKey(String appId) {
        return REFRESH_KEY_PREFIX + appId;
    }

    private String lockKey(String appId) {
        return LOCK_KEY_PREFIX + appId;
    }

    private String expireAtKey(String appId) {
        return EXPIRE_AT_KEY_PREFIX + appId;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getData(Map<String, Object> response) {
        if (response == null) {
            throw new BusinessException("抖音 token 响应为空");
        }
        Object dataObj = response.get("data");
        if (!(dataObj instanceof Map)) {
            throw new BusinessException("抖音 token 响应缺少 data");
        }
        return (Map<String, Object>) dataObj;
    }

    private int parseCode(Map<String, Object> response) {
        if (response == null) {
            return -1;
        }
        if (response.get("err_no") != null) {
            return (int) asLong(response.get("err_no"), -1L);
        }
        if (response.get("code") != null) {
            return (int) asLong(response.get("code"), -1L);
        }
        return -1;
    }

    private String parseMessage(Map<String, Object> response) {
        if (response == null) {
            return "empty response";
        }
        String errMsg = asString(response.get("err_msg"));
        if (errMsg != null && !errMsg.isBlank()) {
            return errMsg;
        }
        String message = asString(response.get("message"));
        if (message != null && !message.isBlank()) {
            return message;
        }
        return "unknown error";
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private long asLong(Object value, long defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignore) {
            return defaultValue;
        }
    }
}
