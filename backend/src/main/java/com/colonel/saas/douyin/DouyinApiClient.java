package com.colonel.saas.douyin;

import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.common.exception.UpstreamErrorCode;
import com.colonel.saas.douyin.ratelimit.DouyinRateLimiter;
import com.doudian.open.utils.SignUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * 抖音开放平台 HTTP 客户端核心组件。
 * <p>
 * 封装抖音开放平台的 HTTP POST 请求，提供签名计算、Token 注入、重试机制和测试模式等功能，
 * 是所有抖音 API 调用的底层传输层。
 *
 * <ul>
 *   <li>请求签名 — 使用 appKey/appSecret 计算 HMAC 签名</li>
 *   <li>Token 管理 — 自动获取有效 Token 并注入请求</li>
 *   <li>自动重试 — 对可重试的业务错误码和传输错误进行指数退避重试</li>
 *   <li>测试模式 — 在 test.enabled=true 时返回模拟响应</li>
 *   <li>方法路径转换 — 支持点号（buyin.xxx）和斜杠（buyin/xxx）两种格式</li>
 * </ul>
 *
 * 所属业务领域：抖音开放平台 / 基础设施
 *
 * @see DouyinTokenService
 * @see DouyinConfig
 * @see DouyinApiException
 */
@Slf4j
@Service
public class DouyinApiClient {

    /** 可重试的业务错误码集合：429(限流)、50002(系统繁忙)、60000(服务不可用) */
    private static final Set<Integer> RETRYABLE_BUSINESS_CODES = Set.of(429, 50002, 60000);

    private final DouyinTokenService douyinTokenService;
    private final RestTemplate douyinRestTemplate;
    private final DouyinConfig douyinConfig;
    private final DouyinRateLimiter douyinRateLimiter;
    @Value("${douyin.test.enabled:false}")
    private boolean testEnabled;
    @Value("${douyin.api.retry.max-attempts:3}")
    private int retryMaxAttempts;
    @Value("${douyin.api.retry.initial-delay-ms:400}")
    private long retryInitialDelayMs;

    @Autowired
    public DouyinApiClient(
            DouyinTokenService douyinTokenService,
            @Qualifier("douyinRestTemplate")
            RestTemplate douyinRestTemplate,
            DouyinConfig douyinConfig,
            DouyinRateLimiter douyinRateLimiter) {
        this.douyinTokenService = douyinTokenService;
        this.douyinRestTemplate = douyinRestTemplate;
        this.douyinConfig = douyinConfig;
        this.douyinRateLimiter = douyinRateLimiter;
    }

    DouyinApiClient(
            DouyinTokenService douyinTokenService,
            RestTemplate douyinRestTemplate,
            DouyinConfig douyinConfig) {
        this(douyinTokenService, douyinRestTemplate, douyinConfig, DouyinRateLimiter.noop());
    }

    /**
     * 发送带鉴权的 HTTP POST 请求到抖音开放平台。
     * <p>
     * 自动获取有效 Token 并注入请求，支持自动重试。
     *
     * @param method 抖音 API 方法名（如 {@code "buyin.promotion.link.generate"}）
     * @param params 请求业务参数（可包含 {@code appId} 指定目标应用）
     * @return API 响应（err_no + data 结构）
     * @throws BusinessException  Token 获取失败或参数校验失败时抛出
     * @throws DouyinApiException 上游 API 返回非成功业务码时抛出
     */
    public Map<String, Object> post(String method, Map<String, Object> params) {
        // 第一步：解析目标应用 ID（优先取 params.appId，回退到配置）
        String appId = resolveAppId(params);
        // 第二步：获取有效的 access_token
        String token = douyinTokenService.getValidToken(appId);
        // 第三步：执行带重试策略的 POST 请求
        return doPost(method, params, appId, token);
    }

    /**
     * 发送无鉴权的 HTTP POST 请求到抖音开放平台。
     * <p>
     * 不注入 access_token，适用于无需用户授权的公共接口。
     *
     * @param method 抖音 API 方法名
     * @param params 请求业务参数
     * @return API 响应
     * @throws DouyinApiException 上游 API 返回非成功业务码时抛出
     */
    public Map<String, Object> postWithoutAuth(String method, Map<String, Object> params) {
        // 第一步：解析目标应用 ID（无需 access_token 的公共接口）
        String appId = resolveAppId(params);
        // 第二步：执行 POST 请求，token 传 null 表示不注入鉴权
        return doPost(method, params, appId, null);
    }

    /**
     * 带重试策略的 POST 请求执行核心。
     * <p>
     * 在测试模式下直接返回模拟响应；否则对可重试错误进行指数退避重试。
     *
     * <ol>
     *   <li>检查测试模式标记，若启用则返回 {@link #buildTestResponse} 模拟响应</li>
     *   <li>调用 {@link #executePost} 发送真实请求</li>
     *   <li>捕获 DouyinApiException：业务码可重试则重试，否则直接抛出</li>
     *   <li>捕获 RuntimeException：传输层可重试则重试，否则包装为 BusinessException</li>
     *   <li>所有重试耗尽后抛出最后一次异常</li>
     * </ol>
     *
     * @param method API 方法名
     * @param params 请求参数
     * @param appId  应用 ID
     * @param token  access_token（可为 null）
     * @return API 响应
     * @throws BusinessException  请求失败且不可重试时抛出
     * @throws DouyinApiException 业务错误不可重试时抛出
     */
    private Map<String, Object> doPost(String method, Map<String, Object> params, String appId, String token) {
        // 第一步：测试模式下直接返回模拟响应
        if (testEnabled) {
            return buildTestResponse(method, appId, params);
        }
        // 第二步：进入重试循环，最多重试 retryMaxAttempts 次
        int attempts = Math.max(1, retryMaxAttempts);
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                douyinRateLimiter.acquire(appId, method);
                return executePost(method, params, appId, token);
            } catch (DouyinApiException ex) {
                lastFailure = ex;
                if (!isRetryableBusinessCode(ex.getErrorCode()) || attempt >= attempts) {
                    throw ex;
                }
                sleepBeforeRetry(method, attempt, attempts, "business-code=" + ex.getErrorCode());
            } catch (BusinessException ex) {
                lastFailure = ex;
                throw ex;
            } catch (RuntimeException ex) {
                lastFailure = ex;
                if (!isRetryableTransport(ex) || attempt >= attempts) {
                    // 改造后：按异常类型映射 UpstreamErrorCode，便于前端按错误码分支提示
                    log.error("Douyin API request failed, method={}, exception={}",
                            method, ex.getClass().getSimpleName());
                    throw wrapTransportException(method, ex);
                }
                sleepBeforeRetry(method, attempt, attempts, ex.getClass().getSimpleName());
            }
        }
        if (lastFailure instanceof DouyinApiException douyinApiException) {
            throw douyinApiException;
        }
        throw lastFailure == null
                ? BusinessException.upstream(UpstreamErrorCode.EXTERNAL_GENERIC, "Douyin API request failed")
                : wrapTransportException(method, lastFailure);
    }

    /**
     * 将传输层异常包装为带 UpstreamErrorCode 的 BusinessException。
     *
     * <p>区分规则：</p>
     * <ul>
     *   <li>{@link ResourceAccessException}（连接 / 读取超时）→ {@code DOUYIN_TIMEOUT}</li>
     *   <li>其他 → {@code UPSTREAM_SERVICE_ERROR}</li>
     * </ul>
     */
    private BusinessException wrapTransportException(String method, RuntimeException ex) {
        if (isTimeoutException(ex)) {
            return BusinessException.upstream(UpstreamErrorCode.DOUYIN_TIMEOUT,
                    "抖音接口调用超时（method=" + method + "）", ex);
        }
        return BusinessException.upstream(UpstreamErrorCode.UPSTREAM_SERVICE_ERROR,
                "Douyin API request failed: " + ex.getMessage(), ex);
    }

    /**
     * 判断异常是否为超时（连接 / 读取）。
     */
    private boolean isTimeoutException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof java.net.SocketTimeoutException) {
                return true;
            }
            if (current instanceof ResourceAccessException) {
                return true;
            }
            Throwable cause = current.getCause();
            if (cause == current) {
                break;
            }
            current = cause;
        }
        return false;
    }

    /**
     * 执行单次 HTTP POST 请求（含签名和参数组装）。
     * <p>
     * 完整的请求构建流程：解析方法路径 -> 组装参数 JSON -> 计算 HMAC 签名 ->
     * 构建请求 URL -> 发送 POST -> 解析响应 -> 校验业务码。
     *
     * @param method API 方法名
     * @param params 请求业务参数
     * @param appId  应用 ID
     * @param token  access_token（可为 null）
     * @return API 响应 Map
     * @throws BusinessException  响应格式无效时抛出
     * @throws DouyinApiException API 返回非成功业务码时抛出
     */
    private Map<String, Object> executePost(String method, Map<String, Object> params, String appId, String token) {
        // 第一步：解析应用密钥和 API 路径，生成时间戳
        String appSecret = resolveAppSecret();
        String urlPath = resolveUrlPath(method);
        String methodName = urlPath.replace("/", ".");
        String timestamp = String.valueOf(Instant.now().toEpochMilli());

        // 第二步：组装请求参数 JSON（按 key 排序），计算 HMAC 签名
        Map<String, Object> payload = new TreeMap<>();
        if (params != null) {
            payload.putAll(params);
        }
        payload.remove("appId");
        String paramJson = toJson(payload);
        String sign = SignUtil.sign(appId, appSecret, methodName, timestamp, paramJson, "2");
        // 第三步：构建完整请求 URL，拼接鉴权参数和可选的 access_token
        String requestUrl = buildRequestUrl(urlPath, appId, methodName, sign, timestamp, token);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // 第四步：发送 HTTP POST 请求
        Object responseObj = douyinRestTemplate.postForObject(
                requestUrl,
                new HttpEntity<>(paramJson, headers),
                Map.class
        );
        if (!(responseObj instanceof Map<?, ?> responseMap)) {
            throw BusinessException.external("Douyin API returned invalid response format");
        }
        // 第五步：将响应转为 String-Object Map，校验业务码
        Map<String, Object> response = castToStringObjectMap(responseMap);

        int code = parseCode(response);
        if (!isSuccessCode(code)) {
            String msg = parseMessage(response);
            String subCode = pickText(response, "sub_code");
            String logId = pickText(response, "log_id");
            if (code == 31012) {
                log.warn("Douyin API reported concurrent token operation, method={}, appId={}, code={}, msg={}",
                        method, appId, code, msg);
            }
            log.error("Douyin API business error, method={}, code={}, subCode={}, logId={}, msg={}",
                    method, code, subCode, logId, msg);
            throw new DouyinApiException(code, msg, subCode, logId, method);
        }

        log.info("Douyin API call success, method={}", method);
        return response;
    }

    /**
     * 判断异常是否为可重试的传输层错误。
     * <p>
     * {@link ResourceAccessException}（连接超时等）和 {@link HttpServerErrorException}（5xx）
     * 被视为可重试；同时递归检查 cause 链。
     *
     * @param throwable 待判断的异常
     * @return true 表示可重试
     */
    private boolean isRetryableTransport(Throwable throwable) {
        if (throwable instanceof ResourceAccessException || throwable instanceof HttpServerErrorException) {
            return true;
        }
        Throwable cause = throwable.getCause();
        return cause != null && cause != throwable && isRetryableTransport(cause);
    }

    /**
     * 判断业务错误码是否可重试。
     * <p>
     * 可重试码集合：429（限流）、50002（系统繁忙）、60000（服务不可用）。
     *
     * @param code 业务错误码
     * @return true 表示可重试
     */
    private boolean isRetryableBusinessCode(int code) {
        return RETRYABLE_BUSINESS_CODES.contains(code);
    }

    /**
     * 重试前的指数退避等待。
     * <p>
     * 延迟时间 = {@code retryInitialDelayMs * attempt}，线程中断时抛出异常。
     *
     * @param method     API 方法名（日志用）
     * @param attempt    当前重试次数
     * @param maxAttempts 最大重试次数
     * @param reason     触发重试的原因（日志用）
     * @throws BusinessException 等待被中断时抛出
     */
    private void sleepBeforeRetry(String method, int attempt, int maxAttempts, String reason) {
        // 第一步：计算线性退避延迟 = 初始延迟 * 当前重试次数
        long delayMs = Math.max(0L, retryInitialDelayMs) * attempt;
        log.warn("Douyin API retry scheduled, method={}, attempt={}/{}, delayMs={}, reason={}",
                method, attempt, maxAttempts, delayMs, reason);
        if (delayMs <= 0L) {
            return;
        }
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw BusinessException.external("Douyin API retry interrupted", interrupted);
        }
    }

    /**
     * 解析应用 ID。
     * <p>
     * 优先从 params 中提取 {@code appId} 参数，其次从 {@link DouyinConfig} 读取 clientKey，
     * 再回退到 appId 配置。
     *
     * @param params 请求参数（可含 appId 键）
     * @return 应用 ID
     * @throws BusinessException 未找到任何应用 ID 配置时抛出
     */
    private String resolveAppId(Map<String, Object> params) {
        // 第一步：优先从请求参数中提取 appId
        if (params != null && params.get("appId") != null) {
            String appId = String.valueOf(params.get("appId"));
            if (!appId.isBlank()) {
                return appId;
            }
        }
        // 第二步：回退到配置的 clientKey，再回退到 appId
        String appId = douyinConfig.getClientKey();
        if (appId == null || appId.isBlank()) {
            appId = douyinConfig.getAppId();
        }
        // 第三步：三者均无值则抛出参数校验异常
        if (appId == null || appId.isBlank()) {
            throw BusinessException.param("missing douyin.app.app-id/client-key config");
        }
        return appId;
    }

    /**
     * 从配置中解析应用密钥。
     *
     * @return clientSecret
     * @throws BusinessException clientSecret 未配置时抛出
     */
    private String resolveAppSecret() {
        String appSecret = douyinConfig.getClientSecret();
        if (!StringUtils.hasText(appSecret)) {
            throw BusinessException.param("missing douyin.app.client-secret config");
        }
        return appSecret;
    }

    /**
     * 将 API 方法名转换为 URL 路径。
     * <p>
     * 处理规则：去除前导斜杠，将点号分隔（{@code buyin.xxx}）转换为斜杠分隔（{@code buyin/xxx}）。
     *
     * @param method API 方法名
     * @return 规范化后的 URL 路径
     * @throws BusinessException method 为空时抛出
     */
    private String resolveUrlPath(String method) {
        if (method == null || method.isBlank()) {
            throw BusinessException.param("Douyin method cannot be blank");
        }
        String normalized = method.trim();
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (!normalized.contains("/") && normalized.contains(".")) {
            normalized = normalized.replace(".", "/");
        }
        return normalized;
    }

    /**
     * 构建完整的 API 请求 URL。
     * <p>
     * 拼接 baseUrl、urlPath 和查询参数（app_key、method、v、sign、timestamp、access_token）。
     *
     * @param urlPath   URL 路径
     * @param appId     应用 ID
     * @param methodName 方法名
     * @param sign      HMAC 签名
     * @param timestamp 时间戳
     * @param token     access_token（可为 null，null 时不拼接）
     * @return 完整请求 URL
     */
    private String buildRequestUrl(String urlPath, String appId, String methodName, String sign, String timestamp, String token) {
        String baseUrl = Objects.requireNonNullElse(douyinConfig.getBaseUrl(), "https://openapi-fxg.jinritemai.com");
        String normalizedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        StringBuilder url = new StringBuilder();
        url.append(String.format("%s/%s?app_key=%s&method=%s&v=2&sign=%s&timestamp=%s",
                normalizedBase, urlPath, appId, methodName, sign, timestamp));
        if (StringUtils.hasText(token)) {
            url.append("&access_token=").append(token);
        }
        return url.toString();
    }

    /**
     * 将参数 Map 序列化为 JSON 字符串。
     *
     * @param payload 参数 Map
     * @return JSON 字符串
     * @throws BusinessException 序列化失败时抛出
     */
    private String toJson(Map<String, Object> payload) {
        try {
            return com.doudian.open.utils.JsonUtil.toJson(payload);
        } catch (Exception e) {
            throw BusinessException.param("Douyin parameter serialization failed", e);
        }
    }

    /**
     * 判断业务码是否表示成功。
     * <p>
     * 成功码：0 或 10000。
     *
     * @param code 业务码
     * @return true 表示成功
     */
    private boolean isSuccessCode(int code) {
        return code == 0 || code == 10000;
    }

    /**
     * 从响应 Map 中解析业务码。
     * <p>
     * 优先读取 {@code err_no}，回退到 {@code code}，解析失败返回 -1。
     *
     * @param response API 响应 Map
     * @return 业务码整数值
     */
    private int parseCode(Map<String, Object> response) {
        if (response == null) {
            return -1;
        }
        Object errNo = response.get("err_no");
        if (errNo == null) {
            errNo = response.get("code");
        }
        if (errNo instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(errNo));
        } catch (Exception ignore) {
            return -1;
        }
    }

    /**
     * 从响应 Map 中解析错误消息。
     * <p>
     * 按优先级读取：sub_msg > err_msg > message > msg，均无值时返回 "unknown error"。
     *
     * @param response API 响应 Map
     * @return 错误消息文本
     */
    private String parseMessage(Map<String, Object> response) {
        if (response == null) {
            return "empty response";
        }
        String subMsg = pickText(response, "sub_msg");
        if (subMsg != null) {
            return subMsg;
        }
        String errMsg = pickText(response, "err_msg");
        if (errMsg != null) {
            return errMsg;
        }
        String message = pickText(response, "message");
        if (message != null) {
            return message;
        }
        String msg = pickText(response, "msg");
        return msg == null ? "unknown error" : msg;
    }

    /**
     * 从 Map 中安全提取文本值。
     * <p>
     * 获取指定键的值并转为非空白字符串，null 或空白时返回 null。
     *
     * @param response Map 数据源
     * @param key      键名
     * @return 非空白字符串，或 null
     */
    private String pickText(Map<String, Object> response, String key) {
        if (response == null || key == null) {
            return null;
        }
        Object value = response.get(key);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    /**
     * 将通配符 Map 转换为 String-keyed Map。
     * <p>
     * 键通过 {@code String.valueOf()} 转换，null 键被跳过。
     *
     * @param source 原始 Map
     * @return String 键的 Map 副本
     */
    private Map<String, Object> castToStringObjectMap(Map<?, ?> source) {
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (entry.getKey() != null) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return result;
    }

    /**
     * 构建测试模式的模拟 API 响应。
     * <p>
     * 根据 method 名称返回预设的模拟数据，支持订单列表、活动列表、商品列表等场景。
     * 未知方法返回通用 {ok: true} 响应。
     *
     * @param method API 方法名
     * @param appId  应用 ID
     * @param params 请求参数
     * @return 模拟的 API 响应 Map
     */
    private Map<String, Object> buildTestResponse(String method, String appId, Map<String, Object> params) {
        // 第一步：构建通用响应头（成功码 + 日志标识）
        Map<String, Object> response = new HashMap<>();
        response.put("err_no", 0);
        response.put("err_msg", "success");
        response.put("log_id", "test-" + Instant.now().toEpochMilli());
        response.put("test", true);
        response.put("test_method", method);
        response.put("test_app_id", appId);

        // 第二步：根据 API 方法名返回对应的模拟数据
        Map<String, Object> data = new HashMap<>();
        if ("buyin.colonelMultiSettlementOrders".equals(method) || "buyin.instituteOrderColonel".equals(method)) {
            data.put("order_list", java.util.List.of());
            data.put("has_more", false);
            data.put("next_cursor", "0");
        } else if ("alliance.instituteColonelActivityList".equals(method)) {
            data.put("data", java.util.List.of(
                    Map.of("activity_id", 10001L, "activity_name", "Test活动A"),
                    Map.of("activity_id", 10002L, "activity_name", "Test活动B")
            ));
            data.put("total", 2);
        } else if ("alliance.colonelActivityProduct".equals(method)) {
            data.put("data", java.util.List.of(
                    Map.of(
                            "product_id", 900001L,
                            "title", "Test商品1",
                            "cos_type", 1,
                            "origin_colonel_buyin_id", "46128341673481001"
                    ),
                    Map.of(
                            "product_id", 900002L,
                            "title", "Test商品2",
                            "cos_type", 0,
                            "origin_colonel_buyin_id", "46128341673481002"
                    )
            ));
            data.put("has_more", false);
        } else {
            data.put("ok", true);
        }
        response.put("data", data);
        return response;
    }
}
