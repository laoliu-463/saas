package com.colonel.saas.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * real-pre 环境配置守卫。
 * <p>
 * 在应用启动时（{@link PostConstruct}）对 real-pre 受保护环境执行强制安全检查，
 * 防止不安全的配置组合进入真实环境。若检查不通过，应用将拒绝启动。
 * </p>
 *
 * <p>检查规则：</p>
 * <ul>
 *   <li><strong>Profile 隔离</strong> —— 受保护环境不能与其他 Profile 混用</li>
 *   <li><strong>测试开关关闭</strong> —— app.test.enabled 和 douyin.test.enabled 必须为 false</li>
 *   <li><strong>Webhook 签名验证</strong> —— douyin.webhook.verify-sign 必须为 true</li>
 *   <li><strong>JWT 密钥</strong> —— 不能为空，不能是默认占位符</li>
 *   <li><strong>抖音凭据</strong> —— app-id、client-key、client-secret 不能为空</li>
 *   <li><strong>CORS 配置</strong> —— 不能使用通配符 * 或 scheme://* 等不安全模式</li>
 * </ul>
 *
 * <p>与其他组件的关系：</p>
 * <ul>
 *   <li>{@link SecurityConfig} —— JWT 密钥和 CORS 配置的使用方</li>
 *   <li>{@link RuntimeExposurePolicy} —— 运行时路径暴露策略，本类确保 real-pre 环境配置正确</li>
 *   <li>{@link StartupEnvironmentLogger} —— 启动时输出环境信息，本类确保环境配置安全</li>
 * </ul>
 *
 * @see RuntimeExposurePolicy
 * @see StartupEnvironmentLogger
 */
@Configuration
public class RealProdEnvironmentGuard {

    /** 默认 JWT 密钥占位符，受保护环境不允许使用此值 */
    private static final String DEFAULT_JWT_SECRET = "test-secret-key-replace-before-real-pre-runtime";

    /** Spring 环境对象，用于获取当前激活的 Profile 列表 */
    private final Environment environment;
    /** 应用全局测试开关（app.test.enabled） */
    private final boolean appTestEnabled;
    /** 抖音测试开关（douyin.test.enabled） */
    private final boolean douyinTestEnabled;
    /** Webhook 签名验证开关（douyin.webhook.verify-sign） */
    private final boolean verifyWebhookSign;
    /** JWT 签名密钥 */
    private final String jwtSecret;
    /** 抖音应用 ID */
    private final String douyinAppId;
    /** 抖音客户端 Key */
    private final String douyinClientKey;
    /** 抖音客户端密钥 */
    private final String douyinClientSecret;
    /** CORS 允许的源模式 */
    private final String corsAllowedOriginPatterns;
    /** Redis 密码 */
    private final String redisPassword;
    /** 数据库密码 */
    private final String dbPassword;
    /** 订单同步开关 */
    private final boolean orderSyncEnabled;
    /** 物流服务商 */
    private final String logisticsProvider;
    /** 独家归因开关 */
    private final boolean exclusiveEnabled;
    /** 达人采集模式 */
    private final String talentCollectMode;
    /** 是否允许公开页爬取 */
    private final boolean talentPublicPageCrawlEnabled;
    /** 受保护的环境 Profile 集合：进入这些环境时强制执行安全检查 */
    private static final Set<String> PROTECTED_PROFILES = Set.of("real-pre");
    /** real-pre 运行时只允许激活 real-pre 单一 Profile */
    private static final Set<String> ALLOWED_PROTECTED_PROFILES = Set.of("real-pre");

    /**
     * 构造函数，通过 {@code @Value} 注入所有待检查的配置项。
     *
     * @param environment               Spring 环境对象
     * @param appTestEnabled            应用测试开关（默认 false）
     * @param douyinTestEnabled         抖音测试开关（默认 false）
     * @param verifyWebhookSign         Webhook 签名验证开关（默认 true）
     * @param jwtSecret                 JWT 签名密钥（默认空）
     * @param douyinAppId               抖音应用 ID（默认空）
     * @param douyinClientKey           抖音客户端 Key（默认空）
     * @param douyinClientSecret        抖音客户端密钥（默认空）
     * @param corsAllowedOriginPatterns CORS 允许的源模式（默认仅允许 localhost）
     * @param redisPassword             Redis 密码（默认空）
     * @param dbPassword                数据库密码（默认空）
     * @param orderSyncEnabled          订单同步开关（受保护环境必须开启）
     * @param logisticsProvider         物流服务商（受保护环境不允许 mock）
     * @param exclusiveEnabled          独家归因开关（V1 受保护环境默认不允许开启）
     * @param talentCollectMode         达人采集模式（受保护环境不允许 crawler 回退）
     * @param talentPublicPageCrawlEnabled 公开页爬取开关（受保护环境必须关闭）
     */
    public RealProdEnvironmentGuard(
            Environment environment,
            @Value("${app.test.enabled:false}") boolean appTestEnabled,
            @Value("${douyin.test.enabled:false}") boolean douyinTestEnabled,
            @Value("${douyin.webhook.verify-sign:true}") boolean verifyWebhookSign,
            @Value("${security.jwt.secret:}") String jwtSecret,
            @Value("${douyin.app.app-id:}") String douyinAppId,
            @Value("${douyin.app.client-key:}") String douyinClientKey,
            @Value("${douyin.app.client-secret:}") String douyinClientSecret,
            @Value("${app.cors.allowed-origin-patterns:http://localhost:*,http://127.0.0.1:*}") String corsAllowedOriginPatterns,
            @Value("${REDIS_PASSWORD:}") String redisPassword,
            @Value("${DB_PASSWORD:}") String dbPassword,
            @Value("${order.sync.enabled:true}") boolean orderSyncEnabled,
            @Value("${logistics.provider:mock}") String logisticsProvider,
            @Value("${exclusive.enabled:false}") boolean exclusiveEnabled,
            @Value("${talent.collect.mode:api}") String talentCollectMode,
            @Value("${talent.data.public-page-crawl-enabled:false}") boolean talentPublicPageCrawlEnabled) {
        this.environment = environment;
        this.appTestEnabled = appTestEnabled;
        this.douyinTestEnabled = douyinTestEnabled;
        this.verifyWebhookSign = verifyWebhookSign;
        this.jwtSecret = jwtSecret;
        this.douyinAppId = douyinAppId;
        this.douyinClientKey = douyinClientKey;
        this.douyinClientSecret = douyinClientSecret;
        this.corsAllowedOriginPatterns = corsAllowedOriginPatterns;
        this.redisPassword = redisPassword;
        this.dbPassword = dbPassword;
        this.orderSyncEnabled = orderSyncEnabled;
        this.logisticsProvider = logisticsProvider;
        this.exclusiveEnabled = exclusiveEnabled;
        this.talentCollectMode = talentCollectMode;
        this.talentPublicPageCrawlEnabled = talentPublicPageCrawlEnabled;
    }

    /**
     * 应用启动时执行安全校验。
     * <p>
     * 仅在存在受保护 Profile（real-pre）时执行检查。
     * 非受保护环境直接跳过，不做任何校验。
     * </p>
     *
     * @throws IllegalStateException 任一项安全检查不通过时抛出，阻止应用启动
     */
    @PostConstruct
    public void validate() {
        Set<String> activeProfiles = normalizedActiveProfiles();
        if (activeProfiles.isEmpty()) {
            return;
        }
        if (!hasProtectedProfile(activeProfiles)) {
            return;
        }
        requireNoForbiddenProfileMix(activeProfiles);
        requireFalse("app.test.enabled", appTestEnabled);
        requireFalse("douyin.test.enabled", douyinTestEnabled);
        requireTrue("douyin.webhook.verify-sign", verifyWebhookSign);
        requireSecret("security.jwt.secret", jwtSecret, DEFAULT_JWT_SECRET);
        requireSecret("douyin.app.app-id", douyinAppId, null);
        requireSecret("douyin.app.client-key", douyinClientKey, null);
        requireSecret("douyin.app.client-secret", douyinClientSecret, null);
        requireSecret("REDIS_PASSWORD", redisPassword, null);
        requireSecret("DB_PASSWORD", dbPassword, null);
        requireTrue("order.sync.enabled", orderSyncEnabled);
        requireFalse("exclusive.enabled", exclusiveEnabled);
        requireFalse("talent.data.public-page-crawl-enabled", talentPublicPageCrawlEnabled);
        requireNonMockLogisticsProvider();
        requireApiOnlyTalentCollectMode();
        requireSafeCorsOriginPatterns();
    }

    /**
     * 校验达人采集模式是否仅限 API 模式。
     * <p>
     * 受保护环境不允许使用爬虫（crawler）或 API+爬虫混合模式，
     * 因为这些模式涉及非公开数据采集，存在合规风险。
     * </p>
     *
     * @throws IllegalStateException talent.collect.mode 为空、为 crawler 或 api_then_crawler 时抛出
     */
    private void requireApiOnlyTalentCollectMode() {
        String normalized = talentCollectMode == null ? "" : talentCollectMode.trim().toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(normalized)) {
            throw new IllegalStateException(activeProfileLabel(normalizedActiveProfiles())
                    + " profile requires non-empty talent.collect.mode");
        }
        if ("crawler".equals(normalized) || "api_then_crawler".equals(normalized)) {
            throw new IllegalStateException(activeProfileLabel(normalizedActiveProfiles())
                    + " profile does not allow non-public talent crawler mode: " + normalized);
        }
    }

    /**
     * 校验物流服务商是否为非 mock 实现。
     * <p>
     * 受保护环境必须使用真实物流服务商（如快递100、快递鸟），
     * 不允许使用 mock 实现，以确保物流查询和订阅功能的可靠性。
     * </p>
     *
     * @throws IllegalStateException logistics.provider 为空或等于 mock 时抛出
     */
    private void requireNonMockLogisticsProvider() {
        if (!StringUtils.hasText(logisticsProvider)) {
            throw new IllegalStateException(activeProfileLabel(normalizedActiveProfiles())
                    + " profile requires non-empty logistics.provider");
        }
        if ("mock".equalsIgnoreCase(logisticsProvider.trim())) {
            throw new IllegalStateException(activeProfileLabel(normalizedActiveProfiles())
                    + " profile does not allow logistics.provider=mock");
        }
    }

    /**
     * 校验 CORS 允许的源模式是否安全。
     * <p>
     * 遍历以逗号分隔的 CORS 源模式列表，逐个检查是否为不安全的通配符模式
     * （如 *、http://*、https://*:* 等）。当凭证（cookies）启用时，
     * 允许所有来源会导致安全风险，因此受保护环境禁止此类配置。
     * </p>
     *
     * @throws IllegalStateException CORS 配置为空或包含不安全通配符模式时抛出
     */
    private void requireSafeCorsOriginPatterns() {
        if (!StringUtils.hasText(corsAllowedOriginPatterns)) {
            throw new IllegalStateException(activeProfileLabel(normalizedActiveProfiles())
                    + " profile requires non-empty app.cors.allowed-origin-patterns");
        }
        for (String pattern : corsAllowedOriginPatterns.split(",")) {
            String trimmed = pattern.trim();
            if (!StringUtils.hasText(trimmed)) {
                continue;
            }
            if (isUnsafeWildcardOriginPattern(trimmed)) {
                throw new IllegalStateException(activeProfileLabel(normalizedActiveProfiles())
                        + " profile does not allow unsafe CORS pattern when credentials are enabled: " + trimmed);
            }
        }
    }

    /**
     * 判断 CORS 源模式是否为不安全的通配符。
     * <p>匹配以下模式：{@code *}、{@code http://*}、{@code https://*}、{@code http://*:*}、{@code https://*:*}。</p>
     *
     * @param pattern CORS 源模式（已去除首尾空白）
     * @return 如果是不安全的通配符模式则返回 true
     */
    private static boolean isUnsafeWildcardOriginPattern(String pattern) {
        return "*".equals(pattern)
                || "http://*".equalsIgnoreCase(pattern)
                || "https://*".equalsIgnoreCase(pattern)
                || "http://*:*".equalsIgnoreCase(pattern)
                || "https://*:*".equalsIgnoreCase(pattern);
    }

    /**
     * 获取当前激活的 Spring Profile 集合（已标准化）。
     * <p>将所有非空白的激活 Profile 去除首尾空白后转为小写，按出现顺序存入 LinkedHashSet。</p>
     *
     * @return 标准化后的 Profile 集合（保持插入顺序）
     */
    private Set<String> normalizedActiveProfiles() {
        Set<String> profiles = new LinkedHashSet<>();
        Arrays.stream(environment.getActiveProfiles())
                .filter(StringUtils::hasText)
                .map(profile -> profile.trim().toLowerCase(Locale.ROOT))
                .forEach(profiles::add);
        return profiles;
    }

    /**
     * 判断当前激活的 Profile 中是否包含受保护的环境 Profile。
     *
     * @param activeProfiles 标准化后的激活 Profile 集合
     * @return 如果存在 real-pre Profile 则返回 true
     */
    private boolean hasProtectedProfile(Set<String> activeProfiles) {
        for (String profile : activeProfiles) {
            if (PROTECTED_PROFILES.contains(profile)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 校验受保护环境 Profile 是否与其他 Profile 共存。
     * <p>多 Profile 叠加会导致安全配置被绕过，因此必须严格隔离。</p>
     *
     * @param activeProfiles 标准化后的激活 Profile 集合
     * @throws IllegalStateException 存在禁止的 Profile 混用时抛出
     */
    private void requireNoForbiddenProfileMix(Set<String> activeProfiles) {
        for (String profile : activeProfiles) {
            if (!ALLOWED_PROTECTED_PROFILES.contains(profile)) {
                throw new IllegalStateException(activeProfileLabel(activeProfiles)
                        + " profile cannot be combined with " + profile);
            }
        }
    }

    /**
     * 校验布尔配置项必须为 false。
     *
     * @param key   配置键名（用于错误信息）
     * @param value 当前配置值
     * @throws IllegalStateException 值为 true 时抛出
     */
    private void requireFalse(String key, boolean value) {
        if (value) {
            throw new IllegalStateException(activeProfileLabel(normalizedActiveProfiles()) + " profile does not allow " + key + "=true");
        }
    }

    /**
     * 校验布尔配置项必须为 true。
     *
     * @param key   配置键名（用于错误信息）
     * @param value 当前配置值
     * @throws IllegalStateException 值为 false 时抛出
     */
    private void requireTrue(String key, boolean value) {
        if (!value) {
            throw new IllegalStateException(activeProfileLabel(normalizedActiveProfiles()) + " profile requires " + key + "=true");
        }
    }

    /**
     * 校验密钥/凭据类配置项是否安全。
     * <p>
     * 检查规则：
     * <ul>
     *   <li>值不能为空或纯空白</li>
     *   <li>不能是已知的默认占位符（由 {@code disallowedExact} 指定）</li>
     *   <li>不能以 "change-me" 开头（通用占位符模式）</li>
     * </ul>
     *
     * @param key              配置键名（用于错误信息）
     * @param value            当前配置值
     * @param disallowedExact  不允许的精确值（如 JWT 默认密钥），为 null 则不做精确匹配
     * @throws IllegalStateException 值为空、匹配默认占位符或以 change-me 开头时抛出
     */
    private void requireSecret(String key, String value, String disallowedExact) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(activeProfileLabel(normalizedActiveProfiles()) + " profile requires non-empty " + key);
        }
        String normalized = value.trim();
        if (disallowedExact != null && disallowedExact.equals(normalized)) {
            throw new IllegalStateException(activeProfileLabel(normalizedActiveProfiles()) + " profile does not allow default placeholder " + key);
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.startsWith("change-me")) {
            throw new IllegalStateException(activeProfileLabel(normalizedActiveProfiles()) + " profile does not allow placeholder " + key);
        }
    }

    /**
     * 获取受保护环境的 Profile 名称标签，用于构建错误信息前缀。
     * <p>
     * 遍历当前激活的 Profile 列表，返回第一个受保护 Profile 的名称。
     * 如果未找到受保护 Profile，则返回默认值 "protected"。
     * </p>
     *
     * @param activeProfiles 标准化后的激活 Profile 集合
     * @return 受保护环境的 Profile 名称，或默认值 "protected"
     */
    private String activeProfileLabel(Set<String> activeProfiles) {
        for (String profile : activeProfiles) {
            if (PROTECTED_PROFILES.contains(profile)) {
                return profile;
            }
        }
        return "protected";
    }
}
