package com.colonel.saas.douyin;

import lombok.Getter;

@Getter
public class DouyinApiException extends RuntimeException {

    private final int errorCode;
    private final String errorMsg;

    public DouyinApiException(int errorCode, String errorMsg) {
        super("Douyin API error: code=" + errorCode + ", msg=" + errorMsg);
        this.errorCode = errorCode;
        this.errorMsg = errorMsg;
    }
}
