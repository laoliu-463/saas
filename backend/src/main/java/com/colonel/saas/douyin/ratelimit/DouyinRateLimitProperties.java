package com.colonel.saas.douyin.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "douyin.rate-limit")
public class DouyinRateLimitProperties {

    /**
     * Keep enabled by default for real upstream calls.
     */
    private boolean enabled = true;

    /**
     * Douyin application-level limit.
     */
    private int appPerSecond = 60;

    /**
     * Douyin platform/global limit shared by this SaaS deployment.
     */
    private int globalPerSecond = 900;

    /**
     * Maximum time to wait for a slot before surfacing rate limit to caller.
     */
    private long acquireTimeoutMs = 3000;

    /**
     * Sleep interval between rejected attempts.
     */
    private long backoffMs = 50;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getAppPerSecond() {
        return appPerSecond;
    }

    public void setAppPerSecond(int appPerSecond) {
        this.appPerSecond = appPerSecond;
    }

    public int getGlobalPerSecond() {
        return globalPerSecond;
    }

    public void setGlobalPerSecond(int globalPerSecond) {
        this.globalPerSecond = globalPerSecond;
    }

    public long getAcquireTimeoutMs() {
        return acquireTimeoutMs;
    }

    public void setAcquireTimeoutMs(long acquireTimeoutMs) {
        this.acquireTimeoutMs = acquireTimeoutMs;
    }

    public long getBackoffMs() {
        return backoffMs;
    }

    public void setBackoffMs(long backoffMs) {
        this.backoffMs = backoffMs;
    }
}
