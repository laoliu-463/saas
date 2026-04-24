package com.colonel.saas.douyin;

import com.colonel.saas.common.exception.BusinessException;
import com.doudian.open.api.token_create.TokenCreateRequest;
import com.doudian.open.api.token_create.TokenCreateResponse;
import com.doudian.open.api.token_create.data.TokenCreateData;
import com.doudian.open.api.token_create.param.TokenCreateParam;
import com.doudian.open.api.token_refresh.TokenRefreshRequest;
import com.doudian.open.api.token_refresh.TokenRefreshResponse;
import com.doudian.open.api.token_refresh.data.TokenRefreshData;
import com.doudian.open.api.token_refresh.param.TokenRefreshParam;
import com.doudian.open.core.AccessToken;
import com.doudian.open.core.GlobalConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.function.Supplier;

@Component
public class DoudianTokenGateway {

    private static final Logger log = LoggerFactory.getLogger(DoudianTokenGateway.class);

    private final DouyinConfig douyinConfig;
    @Value("${douyin.mock.enabled:false}")
    private boolean mockEnabled;

    public DoudianTokenGateway(DouyinConfig douyinConfig) {
        this.douyinConfig = douyinConfig;
    }

    public TokenPayload createToken(TokenCreateCommand command) {
        if (mockEnabled) {
            throw new BusinessException("mock mode enabled: token gateway external call is blocked");
        }
        initSdkConfig();
        TokenCreateRequest request = new TokenCreateRequest();
        TokenCreateParam param = request.getParam();
        param.setCode(command.authorizationCode());
        param.setGrantType(command.grantType());
        if (StringUtils.hasText(command.testShop())) {
            param.setTestShop(command.testShop());
        }
        if (StringUtils.hasText(command.shopId())) {
            param.setShopId(command.shopId());
        }
        if (StringUtils.hasText(command.authId())) {
            param.setAuthId(command.authId());
        }
        if (StringUtils.hasText(command.authSubjectType())) {
            param.setAuthSubjectType(command.authSubjectType());
        }

        log.info("Executing TokenCreateRequest...");
        TokenCreateResponse response = request.execute(AccessToken.wrap("", null));
        log.info("TokenCreateResponse received: code={}, msg={}, subCode={}, subMsg={}",
                response.getCode(), response.getMsg(), response.getSubCode(), response.getSubMsg());
        ensureSuccess(response.getCode(), response.getMsg(), response.getSubCode(), response.getSubMsg());

        TokenCreateData data = response.getData();
        if (data == null) {
            throw new BusinessException("token.create response missing data");
        }

        String authorityId = safeText(data::getAuthorityId);
        String authSubjectType = safeText(data::getAuthSubjectType);
        Long tokenType = safeLong(data::getTokenType);
        log.info("TokenCreateData: accessToken={}, refreshToken={}, expiresIn={}, authorityId={}, authSubjectType={}, tokenType={}",
                data.getAccessToken(), data.getRefreshToken(), data.getExpiresIn(),
                authorityId, authSubjectType, tokenType);

        return new TokenPayload(
                data.getAccessToken(),
                data.getRefreshToken(),
                data.getExpiresIn(),
                authorityId,
                authSubjectType,
                tokenType
        );
    }

    public TokenPayload refreshToken(String refreshToken) {
        if (mockEnabled) {
            throw new BusinessException("mock mode enabled: token gateway external call is blocked");
        }
        initSdkConfig();
        TokenRefreshRequest request = new TokenRefreshRequest();
        TokenRefreshParam param = request.getParam();
        param.setGrantType("refresh_token");
        param.setRefreshToken(refreshToken);

        log.info("Executing TokenRefreshRequest...");
        TokenRefreshResponse response = request.execute(AccessToken.wrap("", null));
        log.info("TokenRefreshResponse received: code={}, msg={}, subCode={}, subMsg={}",
                response.getCode(), response.getMsg(), response.getSubCode(), response.getSubMsg());
        ensureSuccess(response.getCode(), response.getMsg(), response.getSubCode(), response.getSubMsg());

        TokenRefreshData data = response.getData();
        if (data == null) {
            throw new BusinessException("token.refresh response missing data");
        }

        String authorityId = safeText(data::getAuthorityId);
        String authSubjectType = safeText(data::getAuthSubjectType);
        log.info("TokenRefreshData: accessToken={}, refreshToken={}, expiresIn={}, authorityId={}, authSubjectType={}",
                data.getAccessToken(), data.getRefreshToken(), data.getExpiresIn(),
                authorityId, authSubjectType);

        return new TokenPayload(
                data.getAccessToken(),
                data.getRefreshToken(),
                data.getExpiresIn(),
                authorityId,
                authSubjectType,
                null
        );
    }

    private void initSdkConfig() {
        String appKey = douyinConfig.getClientKey();
        String appSecret = douyinConfig.getClientSecret();
        if (!StringUtils.hasText(appKey) || !StringUtils.hasText(appSecret)) {
            throw new BusinessException("missing douyin.app.client-key/client-secret config");
        }
        GlobalConfig.initAppKey(appKey);
        GlobalConfig.initAppSecret(appSecret);
        if (StringUtils.hasText(douyinConfig.getBaseUrl())) {
            GlobalConfig.getGlobalConfig().setOpenRequestUrl(douyinConfig.getBaseUrl());
        }
    }

    private void ensureSuccess(String code, String msg, String subCode, String subMsg) {
        if ("10000".equals(code)) {
            return;
        }
        int errorCode = parseBusinessCode(subCode, subMsg, code);
        String errorMessage = (subMsg == null || subMsg.isBlank()) ? msg : subMsg;
        throw new DouyinApiException(errorCode, errorMessage == null ? "unknown error" : errorMessage);
    }

    private int parseBusinessCode(String subCode, String subMsg, String fallbackCode) {
        String source = "";
        if (subCode != null && !subCode.isBlank()) {
            source = subCode;
        } else if (subMsg != null && !subMsg.isBlank()) {
            source = subMsg;
        }
        if (!source.isBlank()) {
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d{4,6})").matcher(source);
            if (matcher.find()) {
                try {
                    return Integer.parseInt(matcher.group(1));
                } catch (NumberFormatException ignore) {
                    // fallback
                }
            }
        }
        try {
            return Integer.parseInt(fallbackCode);
        } catch (Exception ignore) {
            return -1;
        }
    }

    public record TokenCreateCommand(
            String authorizationCode,
            String grantType,
            String testShop,
            String shopId,
            String authId,
            String authSubjectType) {
    }

    public record TokenPayload(
            String accessToken,
            String refreshToken,
            Long expiresIn,
            String authorityId,
            String authSubjectType,
            Long tokenType) {
    }

    private String safeText(Supplier<Object> getter) {
        Object value = safeValue(getter);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private Long safeLong(Supplier<Object> getter) {
        Object value = safeValue(getter);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException ignore) {
            return null;
        }
    }

    private Object safeValue(Supplier<Object> getter) {
        try {
            return getter.get();
        } catch (ClassCastException ex) {
            log.warn("Token response field type mismatch, ignoring incompatible value: {}", ex.getMessage());
            return null;
        }
    }
}
