package com.colonel.saas.douyin.ratelimit;

import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.common.exception.UpstreamErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class RedisDouyinRateLimiter implements DouyinRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedisDouyinRateLimiter.class);
    private static final long WINDOW_TTL_MS = 1000L;
    private static final int MAX_APP_ID_KEY_LENGTH = 128;
    private static final String DEFAULT_APP_ID = "default";

    private static final DefaultRedisScript<Long> RATE_LIMIT_SCRIPT = new DefaultRedisScript<>("""
            local globalCurrent = tonumber(redis.call('get', KEYS[1]) or '0')
            local appCurrent = tonumber(redis.call('get', KEYS[2]) or '0')
            local globalLimit = tonumber(ARGV[1])
            local appLimit = tonumber(ARGV[2])
            local ttlMs = tonumber(ARGV[3])
            if globalCurrent >= globalLimit or appCurrent >= appLimit then
                return 0
            end
            globalCurrent = redis.call('incr', KEYS[1])
            if globalCurrent == 1 then
                redis.call('pexpire', KEYS[1], ttlMs)
            end
            appCurrent = redis.call('incr', KEYS[2])
            if appCurrent == 1 then
                redis.call('pexpire', KEYS[2], ttlMs)
            end
            return 1
            """, Long.class);

    private final RedisTemplate<String, Object> redisTemplate;
    private final DouyinRateLimitProperties properties;

    public RedisDouyinRateLimiter(
            RedisTemplate<String, Object> redisTemplate,
            DouyinRateLimitProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    @Override
    public void acquire(String appId, String method) {
        if (!properties.isEnabled()) {
            return;
        }
        validateConfiguration();

        long timeoutMs = Math.max(0L, properties.getAcquireTimeoutMs());
        long deadlineNanos = System.nanoTime() + timeoutMs * 1_000_000L;

        do {
            if (tryAcquireOnce(appId)) {
                return;
            }
            if (System.nanoTime() >= deadlineNanos) {
                throw BusinessException.upstream(
                        UpstreamErrorCode.UPSTREAM_RATE_LIMIT,
                        "抖音接口调用触发本地限流，请稍后重试");
            }
            sleepBeforeRetry(method);
        } while (true);
    }

    private boolean tryAcquireOnce(String appId) {
        String window = Long.toString(System.currentTimeMillis() / WINDOW_TTL_MS);
        List<String> keys = List.of(
                "douyin:rate:global:" + window,
                "douyin:rate:app:" + sanitizeAppId(appId) + ":" + window
        );

        try {
            Long result = redisTemplate.execute(
                    RATE_LIMIT_SCRIPT,
                    keys,
                    Integer.toString(properties.getGlobalPerSecond()),
                    Integer.toString(properties.getAppPerSecond()),
                    Long.toString(WINDOW_TTL_MS));
            return Long.valueOf(1L).equals(result);
        } catch (RuntimeException ex) {
            log.warn("Douyin rate limiter Redis access failed", ex);
            throw BusinessException.upstream(
                    UpstreamErrorCode.UPSTREAM_SERVICE_ERROR,
                    "抖音接口限流器不可用，已阻断真实上游请求");
        }
    }

    private void validateConfiguration() {
        if (properties.getAppPerSecond() <= 0 || properties.getGlobalPerSecond() <= 0) {
            throw BusinessException.upstream(
                    UpstreamErrorCode.UPSTREAM_SERVICE_ERROR,
                    "抖音接口限流配置无效");
        }
    }

    private void sleepBeforeRetry(String method) {
        long backoffMs = Math.max(1L, properties.getBackoffMs());
        try {
            Thread.sleep(backoffMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while waiting for Douyin rate limit slot, method={}", method);
            throw BusinessException.upstream(
                    UpstreamErrorCode.UPSTREAM_RATE_LIMIT,
                    "等待抖音接口限流令牌时被中断");
        }
    }

    private String sanitizeAppId(String appId) {
        String value = StringUtils.hasText(appId) ? appId.trim() : DEFAULT_APP_ID;
        value = value.replaceAll("[^A-Za-z0-9._:-]", "_");
        if (value.length() > MAX_APP_ID_KEY_LENGTH) {
            value = value.substring(0, MAX_APP_ID_KEY_LENGTH);
        }
        return value;
    }
}
