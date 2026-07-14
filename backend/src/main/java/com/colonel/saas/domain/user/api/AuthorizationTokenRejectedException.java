package com.colonel.saas.domain.user.api;

public final class AuthorizationTokenRejectedException extends RuntimeException {

    public AuthorizationTokenRejectedException() {
        super("授权令牌已失效，请重新登录");
    }
}
