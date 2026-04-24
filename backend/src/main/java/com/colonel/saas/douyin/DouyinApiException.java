package com.colonel.saas.douyin;

import lombok.Getter;

@Getter
public class DouyinApiException extends RuntimeException {

    private final int errorCode;
    private final String errorMsg;
    private final String subCode;
    private final String logId;
    private final String endpoint;

    public DouyinApiException(int errorCode, String errorMsg) {
        this(errorCode, errorMsg, null, null, null);
    }

    public DouyinApiException(int errorCode, String errorMsg, String subCode, String logId, String endpoint) {
        super("Douyin API error: code=" + errorCode
                + ", subCode=" + (subCode == null ? "" : subCode)
                + ", logId=" + (logId == null ? "" : logId)
                + ", endpoint=" + (endpoint == null ? "" : endpoint)
                + ", msg=" + errorMsg);
        this.errorCode = errorCode;
        this.errorMsg = errorMsg;
        this.subCode = subCode;
        this.logId = logId;
        this.endpoint = endpoint;
    }

    public int getErrorCode() {
        return errorCode;
    }

    public String getErrorMsg() {
        return errorMsg;
    }

    public String getSubCode() {
        return subCode;
    }

    public String getLogId() {
        return logId;
    }

    public String getEndpoint() {
        return endpoint;
    }
}
