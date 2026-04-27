package com.colonel.saas.gateway.douyin.test;

import com.colonel.saas.gateway.douyin.DouyinAuthGateway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "douyin.test.enabled", havingValue = "true")
public class TestDouyinAuthGateway implements DouyinAuthGateway {

    @Override
    public TokenPayload ensureToken(String appId) {
        return buildPayload(appId, null);
    }

    @Override
    public TokenPayload refreshToken(String appId, String refreshToken) {
        return buildPayload(appId, null);
    }

    @Override
    public TokenPayload createToken(TokenCreateCommand command) {
        String appId = "test-app";
        String authSubjectType = command.authSubjectType() == null || command.authSubjectType().isBlank()
                ? "COLONEL"
                : command.authSubjectType().trim();
        return buildPayload(appId, authSubjectType);
    }

    private TokenPayload buildPayload(String appId, String authSubjectType) {
        String finalAppId = appId == null || appId.isBlank() ? "test-app" : appId.trim();
        return new TokenPayload(
                "test_access_token_" + finalAppId,
                "test_refresh_token_" + finalAppId,
                30L * 24 * 60 * 60,
                "test-auth-" + finalAppId,
                authSubjectType,
                1L
        );
    }
}


