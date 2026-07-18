package com.colonel.saas.common.web;

import org.slf4j.MDC;

/** 请求链路 ID 的统一读取入口，避免异常日志和响应各自生成不同 ID。 */
public final class RequestIdContext {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    private RequestIdContext() {
    }

    public static String current() {
        return MDC.get(MDC_KEY);
    }
}
