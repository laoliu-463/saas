package com.colonel.saas.security;

import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.service.OrderSyncService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import org.springframework.util.StringUtils;

/** 将统一 API 业务失败信息传递给请求完成后的操作日志拦截器。 */
@ControllerAdvice
public class OperationLogResponseAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(
            MethodParameter returnType,
            Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(
            Object body,
            MethodParameter returnType,
            MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request,
            ServerHttpResponse response) {
        if (body instanceof ApiResult<?> result
                && request instanceof ServletServerHttpRequest servletRequest) {
            HttpServletRequest httpRequest = servletRequest.getServletRequest();
            if (result.getCode() == 200 && result.getData() instanceof OrderSyncService.SyncResult) {
                // OrderSyncService 已按整批结果写一条汇总，避免 HTTP 拦截器重复记录。
                httpRequest.setAttribute(OperationLogInterceptor.ATTR_SKIP, true);
                return body;
            }
            if (result.getCode() == 200) {
                return body;
            }
            String errorCode = StringUtils.hasText(result.getErrorCode())
                    ? result.getErrorCode()
                    : "API_" + result.getCode();
            httpRequest.setAttribute(OperationLogInterceptor.ATTR_ERROR_CODE, errorCode);
            httpRequest.setAttribute(OperationLogInterceptor.ATTR_ERROR_MESSAGE, result.getMsg());
        }
        return body;
    }
}
