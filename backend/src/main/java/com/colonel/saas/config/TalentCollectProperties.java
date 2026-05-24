package com.colonel.saas.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 达人资料采集模式：真实 API / 爬虫 / Mock，与 {@code talent.enrich.mode}（Provider 演示数据）独立。
 */
@Component
@ConfigurationProperties(prefix = "talent.collect")
public class TalentCollectProperties {

    /**
     * mock | crawler | api | api_then_crawler
     */
    private String mode = "api_then_crawler";

    private final Api api = new Api();

    private int timeoutSeconds = 10;

    private int retry = 2;

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public Api getApi() {
        return api;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public int getRetry() {
        return Math.max(0, retry);
    }

    public void setRetry(int retry) {
        this.retry = retry;
    }

    public boolean isMockOnly() {
        return "mock".equalsIgnoreCase(normalizedMode());
    }

    public boolean isApiAllowed() {
        String m = normalizedMode();
        return "api".equals(m) || "api_then_crawler".equals(m);
    }

    public boolean isCrawlerAllowed() {
        String m = normalizedMode();
        return "crawler".equals(m) || "api_then_crawler".equals(m);
    }

    public boolean isApiThenCrawler() {
        return "api_then_crawler".equalsIgnoreCase(normalizedMode());
    }

    private String normalizedMode() {
        return mode == null ? "" : mode.trim().toLowerCase();
    }

    public static class Api {
        private boolean enabled = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
