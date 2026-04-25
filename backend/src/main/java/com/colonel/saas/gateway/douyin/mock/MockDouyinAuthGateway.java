package com.colonel.saas.gateway.douyin.mock;

import com.colonel.saas.gateway.douyin.DouyinAuthGateway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "douyin.mock.enabled", havingValue = "true")
public class MockDouyinAuthGateway implements DouyinAuthGateway {

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
        String appId = "mock-app";
        String authSubjectType = command.authSubjectType() == null || command.authSubjectType().isBlank()
                ? "COLONEL"
                : command.authSubjectType().trim();
        return buildPayload(appId, authSubjectType);
    }

    private TokenPayload buildPayload(String appId, String authSubjectType) {
        String finalAppId = appId == null || appId.isBlank() ? "mock-app" : appId.trim();
        return new TokenPayload(
                "mock_access_token_" + finalAppId,
                "mock_refresh_token_" + finalAppId,
                30L * 24 * 60 * 60,
                "mock-auth-" + finalAppId,
                authSubjectType,
                1L
        );
    }
}
