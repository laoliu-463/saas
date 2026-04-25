package com.colonel.saas.gateway.douyin;

public interface DouyinAuthGateway {

    TokenPayload ensureToken(String appId);

    TokenPayload refreshToken(String appId, String refreshToken);

    TokenPayload createToken(TokenCreateCommand command);

    record TokenCreateCommand(
            String authorizationCode,
            String grantType,
            String testShop,
            String shopId,
            String authId,
            String authSubjectType) {
    }

    record TokenPayload(
            String accessToken,
            String refreshToken,
            Long expiresIn,
            String authorityId,
            String authSubjectType,
            Long tokenType) {
    }
}
