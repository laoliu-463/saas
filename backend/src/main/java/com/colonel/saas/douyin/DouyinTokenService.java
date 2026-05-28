package com.colonel.saas.douyin;

import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.gateway.douyin.DouyinTokenGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.Locale;

/**
 * 抖音开放平台 Token 管理服务.
 *
 * <p>职责：
 * <ul>
 *   <li>获取有效 Access Token（含自动刷新、缓存、分布式锁）</li>
 *   <li>使用 Refresh Token 刷新 Access Token</li>
 *   <li>通过 Authorization Code 换取 Token（OAuth2.0 授权流程）</li>
 *   <li>查询当前 Token 状态（用于管理后台调试展示）</li>
 *   <li>标记重新授权需求（Token 被吊销等异常场景）</li>
 * </ul>
 *
 * <p>Redis Key 设计：
 * <pre>
 * douyin:token:<appId>                    — Access Token，TTL = expiresIn
 * douyin:refresh:<appId>                  — Refresh Token，TTL = 14天
 * douyin:token:expire_at:<appId>          — Token 过期时间戳，TTL = expiresIn
 * douyin:token:lock:<appId>               — 分布式锁，防止多实例同时刷新
 * douyin:token:reauthorize_required:<appId> — 重新授权标记，TTL = 1天
 * </pre>
 *
 * <p>Test 模式：{@code douyin.test.enabled=true} 时，所有 Token 操作均使用模拟数据，
 * 不会真实请求抖音 API，便于本地调试和集成测试。
 *
 * @see DoudianTokenGateway
 * @see DouyinConfig
 * @see DouyinTokenService.TokenStatus
 */
@Slf4j
@Service
public class DouyinTokenService {

    private static final int TOKEN_EXPIRED_ERROR_CODE = 31008;
    private static final String TOKEN_KEY_PREFIX = "douyin:token:";
    private static final String REFRESH_KEY_PREFIX = "douyin:refresh:";
    private static final String LOCK_KEY_PREFIX = "douyin:token:lock:";
    private static final String EXPIRE_AT_KEY_PREFIX = "douyin:token:expire_at:";
    private static final String REAUTHORIZE_REQUIRED_KEY_PREFIX = "douyin:token:reauthorize_required:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final DouyinTokenGateway douyinTokenGateway;
    private final DouyinConfig douyinConfig;
    private final long refreshThresholdSeconds;
    private final long redisLockMinutes;
    private final Executor tokenRefreshExecutor;

    public DouyinTokenService(
            RedisTemplate<String, Object> redisTemplate,
            DouyinTokenGateway douyinTokenGateway,
            DouyinConfig douyinConfig,
            @Qualifier("applicationTaskExecutor") Executor tokenRefreshExecutor,
            @Value("${douyin.token.refresh-threshold-seconds:300}") long refreshThresholdSeconds,
            @Value("${douyin.token.redis-lock-minutes:5}") long redisLockMinutes) {
        this.redisTemplate = redisTemplate;
        this.douyinTokenGateway = douyinTokenGateway;
        this.douyinConfig = douyinConfig;
        this.tokenRefreshExecutor = tokenRefreshExecutor;
        this.refreshThresholdSeconds = refreshThresholdSeconds;
        this.redisLockMinutes = redisLockMinutes;
    }

    public String getValidToken(String appId) {
        String finalAppId = resolveCacheKey(appId);
        bootstrapGatewayTokenIfAvailable(finalAppId);
        if (isReauthorizeRequired(finalAppId)) {
            throw BusinessException.stateInvalid("token requires re-authorization");
        }
        Object tokenObj = redisTemplate.opsForValue().get(tokenKey(finalAppId));
        String token = tokenObj instanceof String ? (String) tokenObj : null;

        if (token == null || token.isBlank()) {
            try {
                refreshToken(finalAppId);
            } catch (TokenRefreshInProgressException ignore) {
                String waitedToken = waitForToken(finalAppId);
                if (waitedToken != null) {
                    return waitedToken;
                }
                throw ignore;
            }
            String refreshedToken = getTokenFromCache(finalAppId);
            if (refreshedToken == null || refreshedToken.isBlank()) {
                throw BusinessException.param("获取 Access Token 失败: API 返回结果中未包含有效 access_token");
            }
            return refreshedToken;
        }
        if (isTokenExpiringSoon(finalAppId)) {
            triggerAsyncRefresh(finalAppId);
        }
        return token;
    }

    public void refreshToken(String appId) {
        String finalAppId = resolveCacheKey(appId);
        String lockKey = lockKey(finalAppId);
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", Duration.ofMinutes(redisLockMinutes));
        if (!Boolean.TRUE.equals(locked)) {
            throw new TokenRefreshInProgressException();
        }

        try {
            String refreshToken = getRefreshToken(finalAppId);
            if (refreshToken == null || refreshToken.isBlank()) {
                throw BusinessException.param("missing refresh_token, cannot refresh token");
            }
            DouyinTokenGateway.TokenPayload payload = douyinTokenGateway.refreshToken(finalAppId, refreshToken);
            cacheTokenPayload(finalAppId, payload);

            log.info("Douyin token refreshed successfully for appId={}", finalAppId);
        } catch (BusinessException e) {
            log.error("Douyin token refresh business error, appId={}, msg={}", finalAppId, e.getMessage());
            throw e;
        } catch (DouyinApiException e) {
            log.error("Douyin token refresh failed, appId={}, code={}", finalAppId, e.getErrorCode());
            if (e.getErrorCode() == TOKEN_EXPIRED_ERROR_CODE) {
                markReauthorizeRequired(finalAppId, "code=" + e.getErrorCode() + ", msg=" + e.getErrorMsg());
            }
            throw e;
        } catch (Exception e) {
            log.error("Douyin token refresh error, appId={}", finalAppId, e);
            throw BusinessException.param("failed to refresh token");
        } finally {
            redisTemplate.delete(lockKey);
        }
    }

    public boolean isTokenExpiringSoon(String appId) {
        String finalAppId = resolveCacheKey(appId);
        Object expireAtObj = redisTemplate.opsForValue().get(expireAtKey(finalAppId));
        long expireAt = asLong(expireAtObj, 0L);
        if (expireAt <= 0L) {
            return true;
        }
        long now = Instant.now().getEpochSecond();
        return expireAt - now < refreshThresholdSeconds;
    }

    public void markReauthorizeRequired(String appId, String reason) {
        String finalAppId = resolveCacheKey(appId);
        redisTemplate.opsForValue().set(
                reauthorizeRequiredKey(finalAppId),
                String.format("required_at=%s, reason=%s", LocalDateTime.now(), reason == null ? "" : reason),
                Duration.ofDays(1)
        );
        log.error("Token 标记为需要重新授权, appId={}, reason={}", finalAppId, reason);
    }

    public void saveRefreshToken(String appId, String refreshToken) {
        String finalAppId = resolveCacheKey(appId);
        if (refreshToken == null || refreshToken.isBlank()) {
            throw BusinessException.param("refresh_token cannot be blank");
        }
        redisTemplate.opsForValue().set(refreshKey(finalAppId), refreshToken.trim(), Duration.ofDays(14));
    }

    public void bootstrapWithRefreshToken(String appId, String refreshToken) {
        String finalAppId = resolveCacheKey(appId);
        String normalizedRefreshToken = normalizeRefreshToken(refreshToken);
        DouyinTokenGateway.TokenPayload payload = douyinTokenGateway.refreshToken(finalAppId, normalizedRefreshToken);
        cacheTokenPayload(finalAppId, payload, normalizedRefreshToken);
    }

    public void exchangeCodeAndBootstrap(String appId, String authorizationCode) {
        exchangeCodeAndBootstrap(appId, authorizationCode, "authorization_code", null, null, null, null);
    }

    public void exchangeCodeAndBootstrap(String appId,
                                         String authorizationCode,
                                         String grantType,
                                         String testShop,
                                         String shopId,
                                         String authId,
                                         String authSubjectType) {
        String finalAppId = resolveCacheKey(appId);
        String finalGrantType = normalizeGrantType(grantType);
        String finalAuthorizationCode = normalizeAuthorizationCode(authorizationCode, finalGrantType);
        validateAuthScopeParams(authSubjectType, shopId);
        String configuredClientKey = douyinConfig.getClientKey();
        if (configuredClientKey != null
                && !configuredClientKey.isBlank()
                && appId != null
                && !appId.isBlank()
                && !configuredClientKey.equals(appId.trim())) {
            log.warn("Token exchange appId differs from configured clientKey, appId={}, clientKey={}",
                    appId, configuredClientKey);
        }
        log.info("Douyin token bootstrap normalized request: appId={}, grantType={}, shopId={}, authIdPresent={}, authSubjectType={}, codeState={}",
                maskAppId(finalAppId),
                finalGrantType,
                shopId,
                hasText(authId),
                authSubjectType,
                finalAuthorizationCode == null ? "absent" : (finalAuthorizationCode.isEmpty() ? "empty" : "present"));

        DouyinTokenGateway.TokenCreateCommand command = new DouyinTokenGateway.TokenCreateCommand(
                finalAuthorizationCode,
                finalGrantType,
                testShop,
                shopId,
                authId,
                authSubjectType
        );
        try {
            DouyinTokenGateway.TokenPayload payload = douyinTokenGateway.createToken(command);
            cacheTokenPayload(finalAppId, payload);
        } catch (DouyinApiException e) {
            if (e.getErrorCode() == 31005) {
                throw BusinessException.param("authorization code does not match current app or has expired, please re-authorize with current DOUYIN_CLIENT_KEY and retry within 10 minutes");
            }
            throw e;
        }
    }

    public TokenStatus getTokenStatus(String appId) {
        String finalAppId = resolveCacheKey(appId);
        bootstrapGatewayTokenIfAvailable(finalAppId);
        String accessToken = getTokenFromCache(finalAppId);
        String refreshToken = getRefreshToken(finalAppId);
        Object expireAtObj = redisTemplate.opsForValue().get(expireAtKey(finalAppId));
        long expireAt = asLong(expireAtObj, 0L);

        boolean hasAccessToken = accessToken != null && !accessToken.isBlank();
        boolean hasRefreshToken = refreshToken != null && !refreshToken.isBlank();
        boolean reauthorizeRequired = isReauthorizeRequired(finalAppId);
        boolean tokenExpiringSoon = hasAccessToken && isTokenExpiringSoon(finalAppId);

        return new TokenStatus(
                finalAppId,
                hasAccessToken,
                maskSecret(accessToken),
                hasRefreshToken,
                maskSecret(refreshToken),
                expireAt,
                tokenExpiringSoon,
                reauthorizeRequired
        );
    }

    private boolean isReauthorizeRequired(String appId) {
        Object value = redisTemplate.opsForValue().get(reauthorizeRequiredKey(appId));
        return value != null;
    }

    private void cacheTokenPayload(String appId, DouyinTokenGateway.TokenPayload payload) {
        cacheTokenPayload(appId, payload, null);
    }

    private void cacheTokenPayload(String appId, DouyinTokenGateway.TokenPayload payload, String fallbackRefreshToken) {
        if (payload == null) {
            throw BusinessException.param("token payload is empty");
        }
        String newAccessToken = payload.accessToken();
        String normalizedAccessToken = trimToNull(newAccessToken);
        if (normalizedAccessToken == null) {
            throw BusinessException.param("token payload missing access_token");
        }
        String newRefreshToken = normalizeRefreshToken(payload.refreshToken(), fallbackRefreshToken);
        long expiresIn = payload.expiresIn() == null ? 7200L : payload.expiresIn();
        if (expiresIn <= 0L) {
            expiresIn = 7200L;
        }
        long expireAt = Instant.now().getEpochSecond() + expiresIn;

        redisTemplate.opsForValue().set(tokenKey(appId), normalizedAccessToken, Duration.ofSeconds(expiresIn));
        redisTemplate.opsForValue().set(refreshKey(appId), newRefreshToken, Duration.ofDays(14));
        redisTemplate.opsForValue().set(expireAtKey(appId), String.valueOf(expireAt), Duration.ofSeconds(expiresIn));
        redisTemplate.delete(reauthorizeRequiredKey(appId));
    }

    private void triggerAsyncRefresh(String appId) {
        CompletableFuture.runAsync(() -> {
            log.info("Triggering async token refresh, appId={}", appId);
            try {
                refreshToken(appId);
            } catch (TokenRefreshInProgressException e) {
                log.debug("Token refresh skipped, another instance is refreshing, appId={}", appId);
            } catch (BusinessException e) {
                log.error("Async token refresh failed, appId={}, msg={}", appId, e.getMessage());
            } catch (Exception e) {
                log.error("Async token refresh failed, appId={}", appId, e);
            }
        }, tokenRefreshExecutor);
    }

    private String getTokenFromCache(String appId) {
        Object tokenObj = redisTemplate.opsForValue().get(tokenKey(appId));
        return tokenObj instanceof String ? (String) tokenObj : null;
    }

    private String waitForToken(String appId) {
        for (int i = 0; i < 3; i++) {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
            String cachedToken = getTokenFromCache(appId);
            if (cachedToken != null && !cachedToken.isBlank()) {
                return cachedToken;
            }
        }
        return null;
    }

    private String getRefreshToken(String appId) {
        Object refreshObj = redisTemplate.opsForValue().get(refreshKey(appId));
        if (refreshObj instanceof String refresh && !refresh.isBlank()) {
            return refresh;
        }
        return null;
    }

    private String resolveCacheKey(String appId) {
        String explicitAppId = trimToNull(appId);
        return explicitAppId != null ? explicitAppId : resolveConfiguredPrimaryKey();
    }

    private String resolveConfiguredPrimaryKey() {
        String configuredClientKey = trimToNull(douyinConfig.getClientKey());
        if (configuredClientKey != null) {
            return configuredClientKey;
        }
        String configuredAppId = trimToNull(douyinConfig.getAppId());
        if (configuredAppId != null) {
            return configuredAppId;
        }
        throw BusinessException.param("missing douyin.app.app-id / client-key config");
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeRefreshToken(String refreshToken) {
        return normalizeRefreshToken(refreshToken, null);
    }

    private String normalizeRefreshToken(String refreshToken, String fallbackRefreshToken) {
        String normalized = trimToNull(refreshToken);
        if (normalized != null) {
            return normalized;
        }
        String fallback = trimToNull(fallbackRefreshToken);
        if (fallback != null) {
            return fallback;
        }
            throw BusinessException.param("refresh_token cannot be blank");
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

    private String reauthorizeRequiredKey(String appId) {
        return REAUTHORIZE_REQUIRED_KEY_PREFIX + appId;
    }

    private void bootstrapGatewayTokenIfAvailable(String appId) {
        DouyinTokenGateway.TokenPayload payload = douyinTokenGateway.ensureToken(appId);
        if (payload != null) {
            cacheTokenPayload(appId, payload);
        }
    }

    private static class TokenRefreshInProgressException extends BusinessException {
        TokenRefreshInProgressException() {
            super("token refresh task is already running");
        }
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

    private String maskSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            return "";
        }
        return "****";
    }

    private String normalizeGrantType(String grantType) {
        if (grantType == null || grantType.isBlank()) {
            return "authorization_code";
        }
        String normalized = grantType.trim().toLowerCase(Locale.ROOT);
        if (!"authorization_code".equals(normalized)) {
            throw BusinessException.param("联盟自研应用仅支持 grant_type=authorization_code");
        }
        return normalized;
    }

    private String normalizeAuthorizationCode(String authorizationCode, String grantType) {
        if (authorizationCode == null || authorizationCode.isBlank()) {
            throw BusinessException.param("authorization_code cannot be blank");
        }
        return authorizationCode.trim();
    }

    private void validateAuthScopeParams(String authSubjectType, String shopId) {
        if (hasText(authSubjectType) && hasText(shopId)) {
            throw BusinessException.param("auth_subject_type and shop_id cannot be provided at the same time");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String maskAppId(String appId) {
        if (appId == null || appId.length() <= 6) {
            return appId;
        }
        return appId.substring(0, 3) + "***" + appId.substring(appId.length() - 3);
    }
    /**
     * 抖音 Access Token 状态查询结果.
     *
     * <p>此对象由 {@link #getTokenStatus(String)} 方法构建，
     * 用于向管理后台或调试接口展示 Token 当前状态。
     * 内部仅返回固定占位符，不暴露任何 Token 字符。</p>
     *
     * <p>响应字段与前端协议对应关系：</p>
     * <pre>
     * {@code
     * {
     *   "appId": "36",
     *   "hasAccessToken": true,
     *   "maskedAccessToken": "****",          // 仅表示 token 已配置
     *   "hasRefreshToken": true,
     *   "maskedRefreshToken": "****",
     *   "tokenExpireAtEpochSeconds": 1779614841,  // Unix 秒级时间戳
     *   "tokenExpiringSoon": false,               // 距离过期是否不足阈值（默认5分钟）
     *   "reauthorizeRequired": false              // 是否需重新授权（Redis 标记）
     * }
     * }</pre>
     *
     * @see #getTokenStatus(String)
     * @see #maskSecret(String)
     */
    public static class TokenStatus {
        /** 应用 ID（app_id 或 client_key），对应 Redis Key 前缀 {@code douyin:token:<appId>} */
        private final String appId;
        /** Access Token 是否存在于 Redis 缓存中（不等于有效， possibly expired） */
        private final boolean hasAccessToken;
        /** Access Token 脱敏展示：固定占位符，不暴露任何 token 字符 */
        private final String maskedAccessToken;
        /** Refresh Token 是否存在于 Redis 缓存（TTL 14 天） */
        private final boolean hasRefreshToken;
        /** Refresh Token 脱敏展示，同 maskedAccessToken 规则 */
        private final String maskedRefreshToken;
        /**
         * Access Token 过期时间，Unix 秒级时间戳（Epoch Seconds）。
         * <p>
         * 由签发时间(iat) + expiresIn 计算得出，存入 Redis 并设置相同 TTL。
         * 若从未设置过则为 {@code 0L}。
         *
         * @see java.time.Instant#getEpochSecond()
         */
        private final long tokenExpireAtEpochSeconds;
        /**
         * Access Token 是否即将过期。
         * <p>
         * 判定条件：{@code tokenExpireAtEpochSeconds - now < refreshThresholdSeconds}（默认 300 秒 / 5 分钟）。
         * 若为 {@code true}，表示 Token 仍然有效，但后端会自动触发异步刷新。
         *
         * @see #isTokenExpiringSoon(String)
         */
        private final boolean tokenExpiringSoon;
        /**
         * 是否需要重新授权（Re-authorization Required）。
         * <p>
         * 当抖音 API 返回特定错误（如 token 被吊销）时，
         * 调用方通过 {@link #markReauthorizeRequired(String, String)} 将 Redis Key
         * {@code douyin:token:reauthorize_required:<appId>} 标记为存在。
         * 此标记存在期间，任何获取 Token 的操作会直接抛出异常，强制用户重新授权。
         *
         * @see #markReauthorizeRequired(String, String)
         * @see #isReauthorizeRequired(String)
         */
        private final boolean reauthorizeRequired;

        /**
         * 构造 TokenStatus 实例.
         *
         * @param appId                  应用 ID
         * @param hasAccessToken         Access Token 是否存在
         * @param maskedAccessToken       Access Token 脱敏字符串
         * @param hasRefreshToken         Refresh Token 是否存在
         * @param maskedRefreshToken      Refresh Token 脱敏字符串
         * @param tokenExpireAtEpochSeconds Token 过期时间戳（Unix 秒）
         * @param tokenExpiringSoon       Token 是否即将过期
         * @param reauthorizeRequired     是否需重新授权
         */
        public TokenStatus(String appId,
                           boolean hasAccessToken,
                           String maskedAccessToken,
                           boolean hasRefreshToken,
                           String maskedRefreshToken,
                           long tokenExpireAtEpochSeconds,
                           boolean tokenExpiringSoon,
                           boolean reauthorizeRequired) {
            this.appId = appId;
            this.hasAccessToken = hasAccessToken;
            this.maskedAccessToken = maskedAccessToken;
            this.hasRefreshToken = hasRefreshToken;
            this.maskedRefreshToken = maskedRefreshToken;
            this.tokenExpireAtEpochSeconds = tokenExpireAtEpochSeconds;
            this.tokenExpiringSoon = tokenExpiringSoon;
            this.reauthorizeRequired = reauthorizeRequired;
        }

        /** @return 应用 ID */
        public String getAppId() {
            return appId;
        }

        /** @return Access Token 是否存在于 Redis */
        public boolean isHasAccessToken() {
            return hasAccessToken;
        }

        /** @return Access Token 脱敏字符串（不暴露完整 token） */
        public String getMaskedAccessToken() {
            return maskedAccessToken;
        }

        /** @return Refresh Token 是否存在于 Redis */
        public boolean isHasRefreshToken() {
            return hasRefreshToken;
        }

        /** @return Refresh Token 脱敏字符串 */
        public String getMaskedRefreshToken() {
            return maskedRefreshToken;
        }

        /**
         * @return Access Token 过期时间戳（Unix 秒）。
         *         若从未设置则为 0L
         */
        public long getTokenExpireAtEpochSeconds() {
            return tokenExpireAtEpochSeconds;
        }

        /**
         * @return Token 是否距离过期不足阈值（默认 5 分钟）
         */
        public boolean isTokenExpiringSoon() {
            return tokenExpiringSoon;
        }

        /**
         * @return 是否需要用户重新授权（Redis 中存在 reauthorize_required 标记）
         */
        public boolean isReauthorizeRequired() {
            return reauthorizeRequired;
        }
    }
}
