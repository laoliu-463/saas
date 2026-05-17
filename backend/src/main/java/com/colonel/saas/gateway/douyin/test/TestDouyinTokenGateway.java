package com.colonel.saas.gateway.douyin.test;

import com.colonel.saas.gateway.douyin.DouyinTokenGateway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "douyin.test.enabled", havingValue = "true")
public class TestDouyinTokenGateway implements DouyinTokenGateway {

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

    @Override
    public Map<String, Object> institutionInfo(String appId) {
        String finalAppId = appId == null || appId.isBlank() ? "test-app" : appId.trim();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("code", 10000);
        payload.put("msg", "success");
        payload.put("data", Map.of(
                "app_id", finalAppId,
                "institution_id", 11111111L,
                "institution_name", "Test Institution",
                "auth_subject_type", "COLONEL"
        ));
        return payload;
    }

    @Override
    public ProbeTokenCreateResult probeCreateToken(TokenCreateCommand command) {
        String grantType = command.grantType() == null || command.grantType().isBlank()
                ? "authorization_code"
                : command.grantType().trim();
        String codeState = command.authorizationCode() == null || command.authorizationCode().isBlank()
                ? "absent"
                : "present";
        return new ProbeTokenCreateResult(
                grantType,
                codeState,
                command.testShop(),
                command.shopId(),
                StringUtils.hasText(command.authId()),
                command.authSubjectType(),
                new TokenProbeResponseView(
                        "10000",
                        "success",
                        null,
                        null,
                        "****",
                        "****",
                        7200L,
                        "test-auth-probe",
                        "COLONEL",
                        1L
                )
        );
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
