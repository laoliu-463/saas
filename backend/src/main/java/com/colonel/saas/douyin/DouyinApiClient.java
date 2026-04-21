package com.colonel.saas.douyin;

import com.colonel.saas.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
public class DouyinApiClient {

    private final DouyinTokenService douyinTokenService;
    private final RestTemplate douyinRestTemplate;
    private final DouyinConfig douyinConfig;

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

        Map<String, Object> payload = new HashMap<>();
        if (params != null) {
            payload.putAll(params);
        }
        payload.put("access_token", token);
        payload.putIfAbsent("app_id", appId);
        payload.remove("appId");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> response;
        try {
            Object responseObj = douyinRestTemplate.postForObject(
                    buildMethodUrl(method),
                    new HttpEntity<>(payload, headers),
                    Map.class
            );
            if (!(responseObj instanceof Map<?, ?> responseMap)) {
                throw new BusinessException("抖音接口返回格式非法");
            }
            response = castToStringObjectMap(responseMap);
        } catch (Exception e) {
            log.error("Douyin API request failed, method={}", method, e);
            throw new BusinessException("抖音接口请求失败", e);
        }

        int code = parseCode(response);
        if (code != 0) {
            String msg = parseMessage(response);
            if (code == 31012) {
                douyinTokenService.markReauthorizeRequired(appId, msg);
                log.error("Token 已过期，需重新授权, method={}, appId={}, code={}, msg={}", method, appId, code, msg);
            }
            log.error("Douyin API business error, method={}, code={}, msg={}", method, code, msg);
            throw new DouyinApiException(code, msg);
        }

        log.info("Douyin API call success, method={}", method);
        return response;
    }

    private String resolveAppId(Map<String, Object> params) {
        if (params != null && params.get("appId") != null) {
            String appId = String.valueOf(params.get("appId"));
            if (!appId.isBlank()) {
                return appId;
            }
        }
        String appId = douyinConfig.getAppId();
        if (appId == null || appId.isBlank()) {
            throw new BusinessException("缺少 douyin.app.app-id 配置");
        }
        return appId;
    }

    private String buildMethodUrl(String method) {
        if (method == null || method.isBlank()) {
            throw new BusinessException("抖音 method 不能为空");
        }
        String baseUrl = Objects.requireNonNullElse(douyinConfig.getBaseUrl(), "https://open.douyin.com");
        return baseUrl.endsWith("/") ? baseUrl + method : baseUrl + "/" + method;
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
        Object errMsg = response.get("err_msg");
        if (errMsg == null) {
            errMsg = response.get("message");
        }
        return errMsg == null ? "unknown error" : String.valueOf(errMsg);
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
}
