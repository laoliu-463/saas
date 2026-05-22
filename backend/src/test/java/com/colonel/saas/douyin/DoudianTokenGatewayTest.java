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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DoudianTokenGatewayTest {

    private DouyinConfig config;
    private DoudianTokenGateway gateway;

    @BeforeEach
    void setUp() {
        config = new DouyinConfig();
        config.setClientKey("client-key");
        config.setClientSecret("client-secret");
        config.setBaseUrl("https://example.test/open");
        gateway = new DoudianTokenGateway(config);
    }

    @Test
    void maskSecret_shouldNotRevealTokenPrefixOrSuffix() throws Exception {
        Method maskSecret = DoudianTokenGateway.class.getDeclaredMethod("maskSecret", String.class);
        maskSecret.setAccessible(true);

        String masked = (String) maskSecret.invoke(gateway, "access-token-123456");

        assertThat(masked).isEqualTo("****");
        assertThat(masked).doesNotContain("acce", "3456");
    }

    @Test
    void tokenOperations_shouldBeBlockedWhenTestModeEnabled() {
        ReflectionTestUtils.setField(gateway, "testEnabled", true);

        assertThatThrownBy(() -> gateway.createToken(new DoudianTokenGateway.TokenCreateCommand(
                "code",
                "authorization_code",
                null,
                null,
                null,
                null
        ))).isInstanceOf(BusinessException.class)
                .hasMessageContaining("test mode enabled");
        assertThatThrownBy(() -> gateway.probeCreateToken(new DoudianTokenGateway.TokenCreateCommand(
                "code",
                "authorization_code",
                null,
                null,
                null,
                null
        ))).isInstanceOf(BusinessException.class)
                .hasMessageContaining("test mode enabled");
        assertThatThrownBy(() -> gateway.refreshToken("refresh"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("test mode enabled");
    }

    @Test
    void initSdkConfig_shouldRejectMissingClientCredentials() {
        DoudianTokenGateway gateway = new DoudianTokenGateway(new DouyinConfig());

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(gateway, "initSdkConfig"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("missing douyin.app.client-key/client-secret config");
    }

    @Test
    void ensureSuccess_shouldAcceptSuccessAndParseBusinessErrors() {
        assertThatCode(() -> ReflectionTestUtils.invokeMethod(
                gateway,
                "ensureSuccess",
                "10000",
                "success",
                null,
                null
        )).doesNotThrowAnyException();
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                gateway,
                "ensureSuccess",
                "400",
                "failed",
                "isv.business-error:42901",
                null
        )).isInstanceOf(DouyinApiException.class)
                .satisfies(error -> assertThat(((DouyinApiException) error).getErrorCode()).isEqualTo(42901));
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                gateway,
                "ensureSuccess",
                "not-number",
                null,
                null,
                null
        )).isInstanceOf(DouyinApiException.class)
                .satisfies(error -> {
                    DouyinApiException apiError = (DouyinApiException) error;
                    assertThat(apiError.getErrorCode()).isEqualTo(-1);
                    assertThat(apiError.getMessage()).contains("unknown error");
                });
    }

    @Test
    void parseBusinessCode_shouldPreferSubCodeThenSubMessageThenFallback() {
        assertThat(ReflectionTestUtils.<Integer>invokeMethod(
                gateway,
                "parseBusinessCode",
                "sub-42901",
                "msg-50000",
                "400"
        )).isEqualTo(42901);
        assertThat(ReflectionTestUtils.<Integer>invokeMethod(
                gateway,
                "parseBusinessCode",
                " ",
                "message contains 50001",
                "400"
        )).isEqualTo(50001);
        assertThat(ReflectionTestUtils.<Integer>invokeMethod(
                gateway,
                "parseBusinessCode",
                null,
                null,
                "400"
        )).isEqualTo(400);
        assertThat(ReflectionTestUtils.<Integer>invokeMethod(
                gateway,
                "parseBusinessCode",
                null,
                null,
                "not-number"
        )).isEqualTo(-1);
    }

    @Test
    void safeTextAndSafeLong_shouldNormalizeValuesAndIgnoreBadTypes() {
        assertThat(ReflectionTestUtils.<String>invokeMethod(
                gateway,
                "safeText",
                (Supplier<Object>) () -> " value "
        )).isEqualTo("value");
        assertThat(ReflectionTestUtils.<String>invokeMethod(
                gateway,
                "safeText",
                (Supplier<Object>) () -> " "
        )).isNull();
        assertThat(ReflectionTestUtils.<String>invokeMethod(
                gateway,
                "safeText",
                (Supplier<Object>) () -> {
                    throw new ClassCastException("bad text");
                }
        )).isNull();
        assertThat(ReflectionTestUtils.<Long>invokeMethod(
                gateway,
                "safeLong",
                (Supplier<Object>) () -> 12
        )).isEqualTo(12L);
        assertThat(ReflectionTestUtils.<Long>invokeMethod(
                gateway,
                "safeLong",
                (Supplier<Object>) () -> " 34 "
        )).isEqualTo(34L);
        assertThat(ReflectionTestUtils.<Long>invokeMethod(
                gateway,
                "safeLong",
                (Supplier<Object>) () -> "bad"
        )).isNull();
    }

    @Test
    void maskSecret_shouldReturnEmptyForBlankInput() throws Exception {
        Method maskSecret = DoudianTokenGateway.class.getDeclaredMethod("maskSecret", String.class);
        maskSecret.setAccessible(true);

        assertThat(maskSecret.invoke(gateway, (String) null)).isEqualTo("");
        assertThat(maskSecret.invoke(gateway, " ")).isEqualTo("");
    }

    @Test
    void createToken_shouldMapSdkDataAndPopulateRequestParameters() {
        TokenCreateResponse response = successCreateResponse();
        TokenCreateParam param = new TokenCreateParam();

        try (MockedConstruction<TokenCreateRequest> ignored = mockConstruction(
                TokenCreateRequest.class,
                (mock, context) -> {
                    when(mock.getParam()).thenReturn(param);
                    when(mock.execute(any())).thenReturn(response);
                })) {
            DoudianTokenGateway.TokenPayload payload = gateway.createToken(new DoudianTokenGateway.TokenCreateCommand(
                    "auth-code",
                    "authorization_code",
                    "test-shop",
                    "shop-001",
                    "auth-001",
                    "merchant"
            ));

            assertThat(payload.accessToken()).isEqualTo("access-token");
            assertThat(payload.refreshToken()).isEqualTo("refresh-token");
            assertThat(payload.expiresIn()).isEqualTo(3600L);
            assertThat(payload.authorityId()).isEqualTo("auth-id");
            assertThat(payload.authSubjectType()).isEqualTo("shop");
            assertThat(payload.tokenType()).isEqualTo(2L);
            assertThat(param.getCode()).isEqualTo("auth-code");
            assertThat(param.getGrantType()).isEqualTo("authorization_code");
            assertThat(param.getTestShop()).isEqualTo("test-shop");
            assertThat(param.getShopId()).isEqualTo("shop-001");
            assertThat(param.getAuthId()).isEqualTo("auth-001");
            assertThat(param.getAuthSubjectType()).isEqualTo("merchant");
        }
    }

    @Test
    void createToken_shouldRejectSuccessResponseWithoutData() {
        TokenCreateResponse response = new TokenCreateResponse();
        response.setCode("10000");
        response.setMsg("success");
        TokenCreateParam param = new TokenCreateParam();

        try (MockedConstruction<TokenCreateRequest> ignored = mockConstruction(
                TokenCreateRequest.class,
                (mock, context) -> {
                    when(mock.getParam()).thenReturn(param);
                    when(mock.execute(any())).thenReturn(response);
                })) {
            assertThatThrownBy(() -> gateway.createToken(new DoudianTokenGateway.TokenCreateCommand(
                    null,
                    "authorization_code",
                    "",
                    " ",
                    null,
                    ""
            ))).isInstanceOf(BusinessException.class)
                    .hasMessageContaining("token.create response missing data");
            assertThat(param.getCode()).isNull();
            assertThat(param.getTestShop()).isNull();
            assertThat(param.getShopId()).isNull();
            assertThat(param.getAuthId()).isNull();
            assertThat(param.getAuthSubjectType()).isNull();
        }
    }

    @Test
    void probeCreateToken_shouldReturnMaskedResponseViewForSdkResponse() {
        TokenCreateResponse response = successCreateResponse();
        TokenCreateParam param = new TokenCreateParam();

        try (MockedConstruction<TokenCreateRequest> ignored = mockConstruction(
                TokenCreateRequest.class,
                (mock, context) -> {
                    when(mock.getParam()).thenReturn(param);
                    when(mock.execute(any())).thenReturn(response);
                })) {
            DoudianTokenGateway.TokenCreateProbeResult result = gateway.probeCreateToken(
                    new DoudianTokenGateway.TokenCreateCommand(
                            "",
                            "authorization_code",
                            null,
                            null,
                            null,
                            null
                    ));

            assertThat(result.grantType()).isEqualTo("authorization_code");
            assertThat(result.codeState()).isEqualTo("empty");
            assertThat(result.authIdPresent()).isFalse();
            assertThat(result.rawResponse()).isSameAs(response);
            assertThat(result.responseView().maskedAccessToken()).isEqualTo("****");
            assertThat(result.responseView().maskedRefreshToken()).isEqualTo("****");
            assertThat(result.responseView().expiresIn()).isEqualTo(3600L);
            assertThat(result.responseView().authorityId()).isEqualTo("auth-id");
            assertThat(result.responseView().authSubjectType()).isEqualTo("shop");
            assertThat(result.responseView().tokenType()).isEqualTo(2L);
        }
    }

    @Test
    void probeCreateToken_shouldReturnResponseViewWithoutData() {
        TokenCreateResponse response = new TokenCreateResponse();
        response.setCode("40001");
        response.setMsg("failed");
        response.setSubCode("sub");
        response.setSubMsg("sub message");
        TokenCreateParam param = new TokenCreateParam();

        try (MockedConstruction<TokenCreateRequest> ignored = mockConstruction(
                TokenCreateRequest.class,
                (mock, context) -> {
                    when(mock.getParam()).thenReturn(param);
                    when(mock.execute(any())).thenReturn(response);
                })) {
            DoudianTokenGateway.TokenCreateProbeResult result = gateway.probeCreateToken(
                    new DoudianTokenGateway.TokenCreateCommand(
                            null,
                            "authorization_code",
                            null,
                            null,
                            null,
                            null
                    ));

            assertThat(result.codeState()).isEqualTo("absent");
            assertThat(result.responseView().code()).isEqualTo("40001");
            assertThat(result.responseView().msg()).isEqualTo("failed");
            assertThat(result.responseView().subCode()).isEqualTo("sub");
            assertThat(result.responseView().subMsg()).isEqualTo("sub message");
            assertThat(result.responseView().maskedAccessToken()).isNull();
            assertThat(result.responseView().maskedRefreshToken()).isNull();
            assertThat(result.responseView().expiresIn()).isNull();
            assertThat(result.responseView().tokenType()).isNull();
        }
    }

    @Test
    void refreshToken_shouldMapSdkDataAndPopulateRequestParameters() {
        TokenRefreshResponse response = new TokenRefreshResponse();
        response.setCode("10000");
        response.setMsg("success");
        TokenRefreshData data = new TokenRefreshData();
        data.setAccessToken("access-token");
        data.setRefreshToken("refresh-token-next");
        data.setExpiresIn(7200L);
        data.setAuthorityId("authority-id");
        data.setAuthSubjectType("shop");
        response.setData(data);
        TokenRefreshParam param = new TokenRefreshParam();

        try (MockedConstruction<TokenRefreshRequest> ignored = mockConstruction(
                TokenRefreshRequest.class,
                (mock, context) -> {
                    when(mock.getParam()).thenReturn(param);
                    when(mock.execute(any())).thenReturn(response);
                })) {
            DoudianTokenGateway.TokenPayload payload = gateway.refreshToken("refresh-token-old");

            assertThat(payload.accessToken()).isEqualTo("access-token");
            assertThat(payload.refreshToken()).isEqualTo("refresh-token-next");
            assertThat(payload.expiresIn()).isEqualTo(7200L);
            assertThat(payload.authorityId()).isEqualTo("authority-id");
            assertThat(payload.authSubjectType()).isEqualTo("shop");
            assertThat(payload.tokenType()).isNull();
            assertThat(param.getGrantType()).isEqualTo("refresh_token");
            assertThat(param.getRefreshToken()).isEqualTo("refresh-token-old");
        }
    }

    @Test
    void refreshToken_shouldRejectSuccessResponseWithoutData() {
        TokenRefreshResponse response = new TokenRefreshResponse();
        response.setCode("10000");
        response.setMsg("success");
        TokenRefreshParam param = new TokenRefreshParam();

        try (MockedConstruction<TokenRefreshRequest> ignored = mockConstruction(
                TokenRefreshRequest.class,
                (mock, context) -> {
                    when(mock.getParam()).thenReturn(param);
                    when(mock.execute(any())).thenReturn(response);
                })) {
            assertThatThrownBy(() -> gateway.refreshToken("refresh-token"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("token.refresh response missing data");
        }
    }

    private TokenCreateResponse successCreateResponse() {
        TokenCreateResponse response = new TokenCreateResponse();
        response.setCode("10000");
        response.setMsg("success");
        TokenCreateData data = new TokenCreateData();
        data.setAccessToken("access-token");
        data.setRefreshToken("refresh-token");
        data.setExpiresIn(3600L);
        data.setAuthorityId("auth-id");
        data.setAuthSubjectType("shop");
        data.setTokenType(2L);
        response.setData(data);
        return response;
    }
}
