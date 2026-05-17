package com.colonel.saas.gateway.douyin;

import java.util.Map;

public interface DouyinTokenGateway {

    TokenPayload ensureToken(String appId);

    TokenPayload refreshToken(String appId, String refreshToken);

    TokenPayload createToken(TokenCreateCommand command);

    Map<String, Object> institutionInfo(String appId);

    /**
     * token.create SDK probe (no Redis write). Real profile delegates to Doudian SDK adapter.
     */
    ProbeTokenCreateResult probeCreateToken(TokenCreateCommand command);

    record ProbeTokenCreateResult(
            String grantType,
            String codeState,
            String testShop,
            String shopId,
            boolean authIdPresent,
            String authSubjectType,
            TokenProbeResponseView response) {
    }

    record TokenProbeResponseView(
            String code,
            String msg,
            String subCode,
            String subMsg,
            String maskedAccessToken,
            String maskedRefreshToken,
            Long expiresIn,
            String authorityId,
            String authSubjectType,
            Long tokenType) {
    }

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
