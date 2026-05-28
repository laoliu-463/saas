package com.colonel.saas.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 达人资料采集模式配置属性。
 * <p>
 * 通过 {@code talent.collect.*} 前缀的配置项，控制达人资料采集的运行模式。
 * 与 {@code talent.enrich.mode}（Provider 演示数据模式）相互独立，互不影响。
 * </p>
 *
 * <p>采集模式说明：</p>
 * <ul>
 *   <li><strong>mock</strong> —— 纯 Mock 数据，用于本地开发和单元测试</li>
 *   <li><strong>crawler</strong> —— 仅使用爬虫方式采集达人资料</li>
 *   <li><strong>api</strong> —— 仅使用抖音开放平台 API 采集</li>
 *   <li><strong>api_then_crawler</strong>（默认） —— 优先使用 API，API 失败时回退到爬虫</li>
 * </ul>
 *
 * <p>与其他组件的关系：</p>
 * <ul>
 *   <li>{@link TalentCollectStartupReporter} —— 启动时读取本配置并输出环境状态日志</li>
 *   <li>{@link TalentCollectEnvironmentStatus} —— 采集状态枚举，描述真实联调状态</li>
 *   <li>{@link TalentEnrichModeGuard} —— 守卫 real-pre 环境不能使用 test enrich 模式</li>
 * </ul>
 *
 * @see TalentCollectEnvironmentStatus
 * @see TalentCollectStartupReporter
 */
@Component
@ConfigurationProperties(prefix = "talent.collect")
public class TalentCollectProperties {

    /**
     * 采集模式，支持：mock | crawler | api | api_then_crawler。
     * <p>默认 {@code api_then_crawler}：优先调用抖音 API，API 不可用时自动回退到爬虫。</p>
     */
    private String mode = "api_then_crawler";

    /** API 相关子配置 */
    private final Api api = new Api();

    /** 采集请求超时时间（秒），默认 10 秒 */
    private int timeoutSeconds = 10;

    /** 采集请求重试次数，默认 2 次，实际取值不低于 0 */
    private int retry = 2;

    /**
     * 获取当前采集模式。
     *
     * @return 采集模式字符串（mock/crawler/api/api_then_crawler）
     */
    public String getMode() {
        return mode;
    }

    /**
     * 设置采集模式。
     *
     * @param mode 采集模式字符串
     */
    public void setMode(String mode) {
        this.mode = mode;
    }

    /**
     * 获取 API 子配置。
     *
     * @return API 配置对象
     */
    public Api getApi() {
        return api;
    }

    /**
     * 获取采集请求超时时间。
     *
     * @return 超时秒数
     */
    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    /**
     * 设置采集请求超时时间。
     *
     * @param timeoutSeconds 超时秒数
     */
    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    /**
     * 获取采集请求重试次数。
     * <p>实际返回值不低于 0，防止配置为负数。</p>
     *
     * @return 重试次数（不低于 0）
     */
    public int getRetry() {
        return Math.max(0, retry);
    }

    /**
     * 设置采集请求重试次数。
     *
     * @param retry 重试次数
     */
    public void setRetry(int retry) {
        this.retry = retry;
    }

    /**
     * 判断是否为纯 Mock 模式。
     *
     * @return 如果模式为 mock（不区分大小写）返回 true
     */
    public boolean isMockOnly() {
        return "mock".equalsIgnoreCase(normalizedMode());
    }

    /**
     * 判断是否允许使用 API 采集。
     * <p>模式为 {@code api} 或 {@code api_then_crawler} 时返回 true。</p>
     *
     * @return 是否允许 API 采集
     */
    public boolean isApiAllowed() {
        String m = normalizedMode();
        return "api".equals(m) || "api_then_crawler".equals(m);
    }

    /**
     * 判断是否允许使用爬虫采集。
     * <p>模式为 {@code crawler} 或 {@code api_then_crawler} 时返回 true。</p>
     *
     * @return 是否允许爬虫采集
     */
    public boolean isCrawlerAllowed() {
        String m = normalizedMode();
        return "crawler".equals(m) || "api_then_crawler".equals(m);
    }

    /**
     * 判断是否为 API 优先、爬虫兜底模式。
     *
     * @return 如果模式为 api_then_crawler（不区分大小写）返回 true
     */
    public boolean isApiThenCrawler() {
        return "api_then_crawler".equalsIgnoreCase(normalizedMode());
    }

    /**
     * 标准化模式字符串：去除首尾空白并转小写。
     *
     * @return 标准化后的模式字符串，mode 为 null 时返回空字符串
     */
    private String normalizedMode() {
        return mode == null ? "" : mode.trim().toLowerCase();
    }

    /**
     * 抖音开放平台 API 子配置。
     */
    public static class Api {
        /** 是否启用 API 采集，默认禁用 */
        private boolean enabled = false;

        /**
         * 判断 API 是否启用。
         *
         * @return API 是否启用
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * 设置 API 启用状态。
         *
         * @param enabled 是否启用
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
