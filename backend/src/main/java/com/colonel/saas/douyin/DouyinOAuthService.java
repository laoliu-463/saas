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

/**
 * 抖音 OAuth 授权服务：负责生成授权 URL、管理 state 防 CSRF 以及处理 OAuth 回调。
 * <p>
 * 授权流程：
 * <ol>
 *   <li>前端调用 {@link #createAuthorizeUrl} 获取授权页 URL 并跳转</li>
 *   <li>用户在抖音授权页完成授权后，抖音回调 redirect_uri 携带 code 和 state</li>
 *   <li>后端调用 {@link #handleCallback} 验证 state、兑换 code 获取 token</li>
 * </ol>
 * state 以 Redis 缓存方式存储，TTL 由配置项控制，过期后回调自动失败。
 */
@Slf4j
@Service
public class DouyinOAuthService {

    /** Redis state 缓存 key 前缀 */
    private static final String STATE_KEY_PREFIX = "douyin:oauth:state:";
    /** 安全随机数生成器，用于生成防 CSRF 的 state 参数 */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /** Redis 操作模板，用于缓存 OAuth state */
    private final RedisTemplate<String, Object> redisTemplate;
    /** 抖音基础配置（clientKey/appId 等） */
    private final DouyinConfig douyinConfig;
    /** OAuth 专属配置属性（authorizeUrl、redirectUri、stateTtlMinutes 等） */
    private final DouyinOAuthProperties properties;
    /** 抖音 Token 服务，负责兑换 code 并引导 Token 刷新链路 */
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

    /**
     * 生成抖音 OAuth 授权页 URL，同时将 state 缓存到 Redis 用于回调校验。
     *
     * @param appId 抖音应用 ID，为空时使用默认配置的 clientKey/appId
     * @return 包含授权 URL、state、redirectUri 和权限管理页地址的结果对象
     * @throws BusinessException 未配置 clientKey 或 authorizeUrl 时抛出
     */
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
        return new AuthorizeUrlResult(authorizeUrl, state, properties.getRedirectUri(), properties.getPowerManageUrl());
    }

    /**
     * 处理抖音 OAuth 回调：验证 state 防 CSRF，兑换 code 获取并引导 Token 初始化，返回前端成功跳转地址。
     *
     * @param code  抖音回调携带的授权码
     * @param state 回调携带的 state 参数，需与 Redis 缓存匹配
     * @return 前端授权成功后的跳转地址
     * @throws BusinessException state 无效/过期或参数缺失时抛出
     */
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

    /** 返回前端授权失败后的跳转地址，由配置属性控制。 */
    public String failureRedirectUrl() {
        return properties.getFrontendFailureUrl();
    }

    /** 解析实际使用的 appKey：优先使用传入的 appId，其次从默认配置中获取。 */
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

    /** 规范化 appId：去首尾空白，空白字符串返回 null。 */
    private String normalizeAppId(String appId) {
        return StringUtils.hasText(appId) ? appId.trim() : null;
    }

    /** 校验文本非空：为空时抛出参数异常，非空时返回去除首尾空白后的值。 */
    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw BusinessException.param(message);
        }
        return value.trim();
    }

    /** 生成 URL-safe 的随机 state 参数（24 字节 → Base64url 编码），用于防 CSRF。 */
    private String generateState() {
        byte[] bytes = new byte[24];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** 将查询参数值进行 UTF-8 URL 编码。 */
    private String encodeQueryParam(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /** 拼接 Redis state 缓存的完整 key。 */
    private String stateKey(String state) {
        return STATE_KEY_PREFIX + state;
    }

    /** 脱敏 appId 用于日志输出：过短时用 **** 代替，否则保留首尾各 4 位。 */
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

    /** OAuth 授权 URL 生成结果：包含授权页地址、state、回调地址和权限管理页地址。 */
    public record AuthorizeUrlResult(String authorizeUrl, String state, String redirectUri, String powerManageUrl) {
    }
}
