package com.colonel.saas.domain.user.api;

public final class AuthorizationUnavailableException extends RuntimeException {

    public AuthorizationUnavailableException() {
        super("授权事实暂时不可用");
    }

    public AuthorizationUnavailableException(Throwable cause) {
        super("授权事实暂时不可用", cause);
    }
}
