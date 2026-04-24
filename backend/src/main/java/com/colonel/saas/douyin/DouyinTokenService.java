package com.colonel.saas.douyin;

import com.colonel.saas.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * <p>Mock 模式：{@code douyin.mock.enabled=true} 时，所有 Token 操作均使用模拟数据，
 * 不会真实请求抖音 API，便于本地调试和集成测试。
 *
 * @see DoudianTokenGateway
 * @see DouyinConfig
 * @see DouyinTokenService.TokenStatus
 */
@Slf4j
@Service
public class DouyinTokenService {

    private static final Logger log = LoggerFactory.getLogger(DouyinTokenService.class);

    private static final String TOKEN_KEY_PREFIX = "douyin:token:";
    private static final String REFRESH_KEY_PREFIX = "douyin:refresh:";
    private static final String LOCK_KEY_PREFIX = "douyin:token:lock:";
    private static final String EXPIRE_AT_KEY_PREFIX = "douyin:token:expire_at:";
    private static final String REAUTHORIZE_REQUIRED_KEY_PREFIX = "douyin:token:reauthorize_required:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final DoudianTokenGateway doudianTokenGateway;
    private final DouyinConfig douyinConfig;
    private final long refreshThresholdSeconds;
    private final long redisLockMinutes;
    private final Executor tokenRefreshExecutor;
    @Value("${douyin.mock.enabled:false}")
    private boolean mockEnabled;

    public DouyinTokenService(
            RedisTemplate<String, Object> redisTemplate,
            DoudianTokenGateway doudianTokenGateway,
            DouyinConfig douyinConfig,
            @Qualifier("applicationTaskExecutor") Executor tokenRefreshExecutor,
            @Value("${douyin.token.refresh-threshold-seconds:300}") long refreshThresholdSeconds,
            @Value("${douyin.token.redis-lock-minutes:5}") long redisLockMinutes) {
        this.redisTemplate = redisTemplate;
        this.doudianTokenGateway = doudianTokenGateway;
        this.douyinConfig = douyinConfig;
        this.tokenRefreshExecutor = tokenRefreshExecutor;
        this.refreshThresholdSeconds = refreshThresholdSeconds;
        this.redisLockMinutes = redisLockMinutes;
    }

    public String getValidToken(String appId) {
        if (mockEnabled) {
            String finalAppId = resolveCacheKey(appId);
            ensureMockToken(finalAppId);
            return getTokenFromCache(finalAppId);
        }
        String finalAppId = resolveCacheKey(appId);
        if (isReauthorizeRequired(finalAppId)) {
            throw new BusinessException("token requires re-authorization");
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
                throw new BusinessException("闂佸憡甯￠弨閬嶅蓟婵犲洤绠柡宥庡幘閸?token 闂佸憡鑹剧€涒晛锕㈤銏″殧鐎瑰嫭婢樼徊鍧楁煕閹烘柨顣兼繝鈧笟鈧?access_token");
            }
            return refreshedToken;
        }
        if (isTokenExpiringSoon(finalAppId)) {
            triggerAsyncRefresh(finalAppId);
        }
        return token;
    }

    public void refreshToken(String appId) {
        if (mockEnabled) {
            String finalAppId = resolveCacheKey(appId);
            ensureMockToken(finalAppId);
            return;
        }
        String finalAppId = resolveCacheKey(appId);
        String lockKey = lockKey(finalAppId);
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", Duration.ofMinutes(redisLockMinutes));
        if (!Boolean.TRUE.equals(locked)) {
            throw new TokenRefreshInProgressException();
        }

        try {
            String refreshToken = getRefreshToken(finalAppId);
            if (refreshToken == null || refreshToken.isBlank()) {
                throw new BusinessException("missing refresh_token, cannot refresh token");
            }
            DoudianTokenGateway.TokenPayload payload = doudianTokenGateway.refreshToken(refreshToken);
            cacheTokenPayload(finalAppId, payload);

            log.info("Douyin token refreshed successfully for appId={}", finalAppId);
        } catch (BusinessException e) {
            log.error("Douyin token refresh business error, appId={}, msg={}", finalAppId, e.getMessage());
            throw e;
        } catch (DouyinApiException e) {
            log.error("Douyin token refresh failed, appId={}, code={}", finalAppId, e.getErrorCode());
            throw e;
        } catch (Exception e) {
            log.error("Douyin token refresh error, appId={}", finalAppId, e);
            throw new BusinessException("failed to refresh token");
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
        log.error("Token 閻庤鐡曠亸顏嗘崲閸愵喖瀚夐柣鏇炲€荤粈澶愭⒒閸ワ絽浜鹃梻浣瑰絻缁夌敻寮绘繝鍥х闁割偆鍠愮紞鈧? appId={}, reason={}", finalAppId, reason);
    }

    public void saveRefreshToken(String appId, String refreshToken) {
        String finalAppId = resolveCacheKey(appId);
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException("refresh_token cannot be blank");
        }
        redisTemplate.opsForValue().set(refreshKey(finalAppId), refreshToken.trim(), Duration.ofDays(14));
    }

    public void bootstrapWithRefreshToken(String appId, String refreshToken) {
        if (mockEnabled) {
            String finalAppId = resolveCacheKey(appId);
            ensureMockToken(finalAppId);
            return;
        }
        String finalAppId = resolveCacheKey(appId);
        String normalizedRefreshToken = normalizeRefreshToken(refreshToken);
        DoudianTokenGateway.TokenPayload payload = doudianTokenGateway.refreshToken(normalizedRefreshToken);
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
        if (mockEnabled) {
            String finalAppId = resolveCacheKey(appId);
            ensureMockToken(finalAppId);
            return;
        }
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
        DoudianTokenGateway.TokenCreateCommand command = new DoudianTokenGateway.TokenCreateCommand(
                finalAuthorizationCode,
                finalGrantType,
                testShop,
                shopId,
                authId,
                authSubjectType
        );
        try {
            DoudianTokenGateway.TokenPayload payload = doudianTokenGateway.createToken(command);
            cacheTokenPayload(finalAppId, payload);
        } catch (DouyinApiException e) {
            if (e.getErrorCode() == 31005) {
                throw new BusinessException("authorization code does not match current app or has expired, please re-authorize with current DOUYIN_CLIENT_KEY and retry within 10 minutes");
            }
            throw e;
        }
    }

    public TokenStatus getTokenStatus(String appId) {
        if (mockEnabled) {
            String finalAppId = resolveCacheKey(appId);
            ensureMockToken(finalAppId);
        }
        String finalAppId = resolveCacheKey(appId);
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

    private void cacheTokenPayload(String appId, DoudianTokenGateway.TokenPayload payload) {
        cacheTokenPayload(appId, payload, null);
    }

    private void cacheTokenPayload(String appId, DoudianTokenGateway.TokenPayload payload, String fallbackRefreshToken) {
        if (payload == null) {
            throw new BusinessException("token payload is empty");
        }
        String newAccessToken = payload.accessToken();
        String newRefreshToken = normalizeRefreshToken(payload.refreshToken(), fallbackRefreshToken);
        long expiresIn = payload.expiresIn() == null ? 7200L : payload.expiresIn();
        if (newAccessToken == null || newAccessToken.isBlank()) {
            throw new BusinessException("闂佺鍩栭悧鐘绘偂?token 闂佸憡绻傜粔瀵歌姳閼碱剛纾介柛婵嗗濮?access_token");
        }
        if (newRefreshToken == null || newRefreshToken.isBlank()) {
            throw new BusinessException("闂佺鍩栭悧鐘绘偂?token 闂佸憡绻傜粔瀵歌姳閼碱剛纾介柛婵嗗濮?refresh_token");
        }
        long expireAt = Instant.now().getEpochSecond() + expiresIn;

        redisTemplate.opsForValue().set(tokenKey(appId), newAccessToken, Duration.ofSeconds(expiresIn));
        // refresh_token 闁哄牆顦伴弲銉╁嫉?14 濠㈠灈鏅槐婵堢磽閹惧磭鎽?TTL 濞戞挸楠搁柦鈺呭矗妫颁胶绠介柟闀愭缁旀挳鎳?
        redisTemplate.opsForValue().set(refreshKey(appId), newRefreshToken, Duration.ofDays(14));
        redisTemplate.opsForValue().set(expireAtKey(appId), String.valueOf(expireAt), Duration.ofSeconds(expiresIn));
        redisTemplate.delete(reauthorizeRequiredKey(appId));
    }

    private void triggerAsyncRefresh(String appId) {
        CompletableFuture.runAsync(() -> {
            log.info("閻庢鍠掗崑鎾斥攽椤旂⒈鍎忛柛鈺傜洴瀵?Token, appId={}", appId);
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
        throw new BusinessException("missing douyin.app.app-id / client-key config");
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
        throw new BusinessException("refresh_token cannot be blank");
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

    private void ensureMockToken(String appId) {
        String accessToken = "mock_access_token_" + appId;
        String refreshToken = "mock_refresh_token_" + appId;
        long expiresIn = 30L * 24 * 60 * 60;
        long expireAt = Instant.now().getEpochSecond() + expiresIn;
        redisTemplate.opsForValue().set(tokenKey(appId), accessToken, Duration.ofSeconds(expiresIn));
        redisTemplate.opsForValue().set(refreshKey(appId), refreshToken, Duration.ofDays(30));
        redisTemplate.opsForValue().set(expireAtKey(appId), String.valueOf(expireAt), Duration.ofSeconds(expiresIn));
        redisTemplate.delete(reauthorizeRequiredKey(appId));
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
        String normalized = secret.trim();
        if (normalized.length() <= 8) {
            return "****";
        }
        return normalized.substring(0, 4) + "..." + normalized.substring(normalized.length() - 4);
    }

    private String normalizeGrantType(String grantType) {
        if (grantType == null || grantType.isBlank()) {
            return "authorization_code";
        }
        String normalized = grantType.trim().toLowerCase(Locale.ROOT);
        if (!"authorization_code".equals(normalized) && !"authorization_self".equals(normalized)) {
            throw new BusinessException("grant_type 婵炲濮撮幊蹇涘极椤曗偓楠?authorization_code / authorization_self");
        }
        return normalized;
    }

    private String normalizeAuthorizationCode(String authorizationCode, String grantType) {
        if ("authorization_self".equals(grantType)) {
            return authorizationCode == null ? "" : authorizationCode.trim();
        }
        if (authorizationCode == null || authorizationCode.isBlank()) {
            throw new BusinessException("authorization_code cannot be blank");
        }
        return authorizationCode.trim();
    }

    private void validateAuthScopeParams(String authSubjectType, String shopId) {
        if (hasText(authSubjectType) && hasText(shopId)) {
            throw new BusinessException("auth_subject_type and shop_id cannot be provided at the same time");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
    /**
     * 抖音 Access Token 状态查询结果.
     *
     * <p>此对象由 {@link #getTokenStatus(String)} 方法构建，
     * 用于向管理后台或调试接口展示 Token 当前状态。
     * 内部仅返回脱敏后的 Token 片段（masked），不做敏感信息暴露。</p>
     *
     * <p>响应字段与前端协议对应关系：</p>
     * <pre>
     * {@code
     * {
     *   "appId": "36",
     *   "hasAccessToken": true,
     *   "maskedAccessToken": "mock...n_36",   // 仅展示前4位 + "..." + 后4位
     *   "hasRefreshToken": true,
     *   "maskedRefreshToken": "mock...n_36",
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
        /** Access Token 脱敏展示：前4位 + "..." + 后4位，长度不足8位则显示 "****" */
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
