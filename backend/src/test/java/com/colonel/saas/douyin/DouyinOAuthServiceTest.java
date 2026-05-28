package com.colonel.saas.douyin;

import com.colonel.saas.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DouyinOAuthServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ValueOperations<String, Object> valueOperations;
    @Mock
    private DouyinConfig douyinConfig;
    @Mock
    private DouyinTokenService douyinTokenService;

    private DouyinOAuthProperties properties;
    private DouyinOAuthService service;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(douyinConfig.getClientKey()).thenReturn("client-key");
        when(douyinConfig.getAppId()).thenReturn("default-app");

        properties = new DouyinOAuthProperties();
        properties.setAuthorizeUrl("https://op.jinritemai.com/oauth2/authorize");
        properties.setPowerManageUrl("https://buyin.jinritemai.com/dashboard/institution/power-manage");
        properties.setRedirectUri("http://localhost:8081/api/douyin/oauth/callback");
        properties.setFrontendSuccessUrl("http://localhost:3001/system/douyin?oauth=success");
        properties.setFrontendFailureUrl("http://localhost:3001/system/douyin?oauth=failed");
        properties.setStateTtlMinutes(10L);

        service = new DouyinOAuthService(redisTemplate, douyinConfig, properties, douyinTokenService);
    }

    @Test
    void createAuthorizeUrl_shouldStoreStateAndReturnOfficialAuthorizeUrl() {
        DouyinOAuthService.AuthorizeUrlResult result = service.createAuthorizeUrl(null);

        assertThat(result.authorizeUrl()).startsWith("https://op.jinritemai.com/oauth2/authorize?");
        assertThat(result.authorizeUrl()).contains("app_key=client-key");
        assertThat(result.authorizeUrl()).contains("response_type=code");
        assertThat(result.authorizeUrl()).contains("redirect_uri=http%3A%2F%2Flocalhost%3A8081%2Fapi%2Fdouyin%2Foauth%2Fcallback");
        assertThat(result.authorizeUrl()).contains("state=" + result.state());
        assertThat(result.redirectUri()).isEqualTo("http://localhost:8081/api/douyin/oauth/callback");
        assertThat(result.powerManageUrl()).isEqualTo("https://buyin.jinritemai.com/dashboard/institution/power-manage");
        assertThat(result.state()).isNotBlank();

        ArgumentCaptor<Duration> durationCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(valueOperations).set(startsWith("douyin:oauth:state:"), eq(""), durationCaptor.capture());
        assertThat(durationCaptor.getValue()).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void createAuthorizeUrl_shouldStoreRequestedAppIdForCallbackExchange() {
        DouyinOAuthService.AuthorizeUrlResult result = service.createAuthorizeUrl(" custom-app ");

        assertThat(result.authorizeUrl()).contains("app_key=custom-app");
        verify(valueOperations).set(startsWith("douyin:oauth:state:"), eq("custom-app"), any(Duration.class));
    }

    @Test
    void handleCallback_shouldExchangeCodeAndDeleteState() {
        when(valueOperations.get("douyin:oauth:state:state-123")).thenReturn("custom-app");

        String redirectUrl = service.handleCallback("code-abc", "state-123");

        assertThat(redirectUrl).isEqualTo("http://localhost:3001/system/douyin?oauth=success");
        verify(redisTemplate).delete("douyin:oauth:state:state-123");
        verify(douyinTokenService).exchangeCodeAndBootstrap(
                "custom-app",
                "code-abc",
                "authorization_code",
                null,
                null,
                null,
                null
        );
    }

    @Test
    void handleCallback_shouldRejectExpiredState() {
        when(valueOperations.get("douyin:oauth:state:expired-state")).thenReturn(null);

        assertThatThrownBy(() -> service.handleCallback("code-abc", "expired-state"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("oauth state is invalid or expired");
    }

    @Test
    void handleCallback_shouldRejectMissingCode() {
        assertThatThrownBy(() -> service.handleCallback(" ", "state-123"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("missing oauth code");
    }
}
