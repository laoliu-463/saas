package com.colonel.saas.gateway.douyin.test;

import com.colonel.saas.gateway.douyin.DouyinTokenGateway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 测试环境抖店 Token 网关适配器。
 * <p>
 * 实现 {@link DouyinTokenGateway} 接口，在 {@code douyin.test.enabled=true} 时替代真实的
 * 抖店 OAuth Token 网关，为本地开发和 test 环境提供不依赖真实抖店授权服务的 Mock Token。
 * </p>
 *
 * <ul>
 *   <li><b>获取/确保 Token（ensureToken）</b>：返回固定的 Mock access_token 和 refresh_token，有效期 30 天</li>
 *   <li><b>刷新 Token（refreshToken）</b>：行为与 ensureToken 一致，返回相同的 Mock Token 格式</li>
 *   <li><b>创建 Token（createToken）</b>：根据授权主体类型（authSubjectType）生成 Mock Token，默认为 COLONEL 类型</li>
 *   <li><b>机构信息查询（institutionInfo）</b>：返回固定的 Mock 机构信息（institution_id=11111111）</li>
 *   <li><b>探针创建 Token（probeCreateToken）</b>：不执行真实授权，返回 Mock 探针结果用于前端调试和预检</li>
 * </ul>
 *
 * <p>架构角色：Gateway 测试适配器（Test Double），所属领域：认证域（Token 管理）。
 * 与真实网关的关系：实现同一 {@link DouyinTokenGateway} 接口，通过 {@code douyin.test.enabled}
 * 属性切换。Mock Token 以 {@code test_access_token_} 前缀标识，避免与真实 Token 混淆。</p>
 *
 * @see DouyinTokenGateway
 */
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
