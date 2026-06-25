package com.colonel.saas.douyin.ratelimit;

/**
 * Rate limiter for outbound Douyin Open API calls.
 */
public interface DouyinRateLimiter {

    void acquire(String appId, String method);

    static DouyinRateLimiter noop() {
        return (appId, method) -> {
        };
    }
}
