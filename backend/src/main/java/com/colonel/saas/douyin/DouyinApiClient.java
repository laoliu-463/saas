package com.colonel.saas.douyin;

import com.colonel.saas.common.exception.BusinessException;
import com.doudian.open.utils.SignUtil;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@Service
public class DouyinApiClient {

    private static final Set<Integer> RETRYABLE_BUSINESS_CODES = Set.of(429, 50002, 60000);

    private final DouyinTokenService douyinTokenService;
    private final RestTemplate douyinRestTemplate;
    private final DouyinConfig douyinConfig;
    @Value("${douyin.test.enabled:false}")
    private boolean testEnabled;
    @Value("${douyin.api.retry.max-attempts:3}")
    private int retryMaxAttempts;
    @Value("${douyin.api.retry.initial-delay-ms:400}")
    private long retryInitialDelayMs;

    public DouyinApiClient(
            DouyinTokenService douyinTokenService,
            RestTemplate douyinRestTemplate,
            DouyinConfig douyinConfig) {
        this.douyinTokenService = douyinTokenService;
        this.douyinRestTemplate = douyinRestTemplate;
        this.douyinConfig = douyinConfig;
    }

    public Map<String, Object> post(String method, Map<String, Object> params) {
        String appId = resolveAppId(params);
        String token = douyinTokenService.getValidToken(appId);
        return doPost(method, params, appId, token);
    }

    public Map<String, Object> postWithoutAuth(String method, Map<String, Object> params) {
        String appId = resolveAppId(params);
        return doPost(method, params, appId, null);
    }

    private Map<String, Object> doPost(String method, Map<String, Object> params, String appId, String token) {
        if (testEnabled) {
            return buildTestResponse(method, appId, params);
        }
        int attempts = Math.max(1, retryMaxAttempts);
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
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
                    log.error("Douyin API request failed, method={}, exception={}",
                            method, ex.getClass().getSimpleName());
                    throw BusinessException.external("Douyin API request failed");
                }
                sleepBeforeRetry(method, attempt, attempts, ex.getClass().getSimpleName());
            }
        }
        if (lastFailure instanceof DouyinApiException douyinApiException) {
            throw douyinApiException;
        }
        throw lastFailure == null
                ? BusinessException.external("Douyin API request failed")
                : BusinessException.external("Douyin API request failed", lastFailure);
    }

    private Map<String, Object> executePost(String method, Map<String, Object> params, String appId, String token) {
        String appSecret = resolveAppSecret();
        String urlPath = resolveUrlPath(method);
        String methodName = urlPath.replace("/", ".");
        String timestamp = String.valueOf(Instant.now().toEpochMilli());

        Map<String, Object> payload = new TreeMap<>();
        if (params != null) {
            payload.putAll(params);
        }
        payload.remove("appId");
        String paramJson = toJson(payload);
        String sign = SignUtil.sign(appId, appSecret, methodName, timestamp, paramJson, "2");
        String requestUrl = buildRequestUrl(urlPath, appId, methodName, sign, timestamp, token);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Object responseObj = douyinRestTemplate.postForObject(
                requestUrl,
                new HttpEntity<>(paramJson, headers),
                Map.class
        );
        if (!(responseObj instanceof Map<?, ?> responseMap)) {
            throw BusinessException.external("Douyin API returned invalid response format");
        }
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

    private boolean isRetryableTransport(Throwable throwable) {
        if (throwable instanceof ResourceAccessException || throwable instanceof HttpServerErrorException) {
            return true;
        }
        Throwable cause = throwable.getCause();
        return cause != null && cause != throwable && isRetryableTransport(cause);
    }

    private boolean isRetryableBusinessCode(int code) {
        return RETRYABLE_BUSINESS_CODES.contains(code);
    }

    private void sleepBeforeRetry(String method, int attempt, int maxAttempts, String reason) {
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

    private String resolveAppId(Map<String, Object> params) {
        if (params != null && params.get("appId") != null) {
            String appId = String.valueOf(params.get("appId"));
            if (!appId.isBlank()) {
                return appId;
            }
        }
        String appId = douyinConfig.getClientKey();
        if (appId == null || appId.isBlank()) {
            appId = douyinConfig.getAppId();
        }
        if (appId == null || appId.isBlank()) {
            throw BusinessException.param("missing douyin.app.app-id/client-key config");
        }
        return appId;
    }

    private String resolveAppSecret() {
        String appSecret = douyinConfig.getClientSecret();
        if (!StringUtils.hasText(appSecret)) {
            throw BusinessException.param("missing douyin.app.client-secret config");
        }
        return appSecret;
    }

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

    private String toJson(Map<String, Object> payload) {
        try {
            return com.doudian.open.utils.JsonUtil.toJson(payload);
        } catch (Exception e) {
            throw BusinessException.param("Douyin parameter serialization failed", e);
        }
    }

    private boolean isSuccessCode(int code) {
        return code == 0 || code == 10000;
    }

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

    private Map<String, Object> castToStringObjectMap(Map<?, ?> source) {
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (entry.getKey() != null) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return result;
    }

    private Map<String, Object> buildTestResponse(String method, String appId, Map<String, Object> params) {
        Map<String, Object> response = new HashMap<>();
        response.put("err_no", 0);
        response.put("err_msg", "success");
        response.put("log_id", "test-" + Instant.now().toEpochMilli());
        response.put("test", true);
        response.put("test_method", method);
        response.put("test_app_id", appId);

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
