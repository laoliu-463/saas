package com.colonel.saas.douyin;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.colonel.saas.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DouyinApiClientTest {

    @Mock
    private DouyinTokenService douyinTokenService;
    @Mock
    private RestTemplate douyinRestTemplate;
    @Mock
    private DouyinConfig douyinConfig;

    @InjectMocks
    private DouyinApiClient douyinApiClient;

    @Test
    void post_shouldIncludeTokenAndAppId() {
        when(douyinConfig.getAppId()).thenReturn("app123");
        when(douyinConfig.getClientSecret()).thenReturn("secret123");
        when(douyinConfig.getBaseUrl()).thenReturn("https://openapi-fxg.jinritemai.com");
        doReturn("token").when(douyinTokenService).getValidToken(any());
        when(douyinRestTemplate.postForObject(any(String.class), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(Map.of("code", 10000));

        Map<String, Object> params = new HashMap<>();
        params.put("page_size", 20);
        douyinApiClient.post("buyin.colonel.activity.list", params);

        ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(douyinRestTemplate).postForObject(urlCaptor.capture(), entityCaptor.capture(), eq(Map.class));
        assertThat(urlCaptor.getValue()).startsWith("https://openapi-fxg.jinritemai.com/buyin/colonel/activity/list?");
        assertThat(urlCaptor.getValue()).contains("app_key=app123");
        assertThat(urlCaptor.getValue()).contains("method=buyin.colonel.activity.list");
        assertThat(urlCaptor.getValue()).contains("access_token=token");
        assertThat((String) entityCaptor.getValue().getBody()).contains("\"page_size\":20");
    }

    @Test
    void post_shouldUseOverriddenAppId() {
        when(douyinConfig.getClientSecret()).thenReturn("secret123");
        when(douyinConfig.getBaseUrl()).thenReturn("https://openapi-fxg.jinritemai.com");
        when(douyinTokenService.getValidToken("custom-app")).thenReturn("custom-token");
        when(douyinRestTemplate.postForObject(any(String.class), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(Map.of("code", 10000));

        douyinApiClient.post("test.method", Map.of("appId", "custom-app"));

        verify(douyinTokenService).getValidToken("custom-app");
    }

    @Test
    void postWithoutAuth_shouldNotIncludeAccessToken() {
        when(douyinConfig.getAppId()).thenReturn("app123");
        when(douyinConfig.getClientSecret()).thenReturn("secret123");
        when(douyinConfig.getBaseUrl()).thenReturn("https://openapi-fxg.jinritemai.com");
        when(douyinRestTemplate.postForObject(any(String.class), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(Map.of("code", 10000));

        douyinApiClient.postWithoutAuth("buyin.materialsProductStatus", Map.of("products", java.util.List.of("https://a.com")));

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(douyinRestTemplate).postForObject(urlCaptor.capture(), any(HttpEntity.class), eq(Map.class));
        assertThat(urlCaptor.getValue()).contains("method=buyin.materialsProductStatus");
        assertThat(urlCaptor.getValue()).doesNotContain("access_token=");
        verify(douyinTokenService, never()).getValidToken(any());
    }

    @Test
    void post_shouldThrowWhenResponseIsNull() {
        when(douyinConfig.getAppId()).thenReturn("app123");
        when(douyinConfig.getClientSecret()).thenReturn("secret123");
        when(douyinConfig.getBaseUrl()).thenReturn("https://openapi-fxg.jinritemai.com");
        when(douyinTokenService.getValidToken("app123")).thenReturn("token");
        when(douyinRestTemplate.postForObject(any(String.class), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(null);

        assertThatThrownBy(() -> douyinApiClient.post("test", null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void post_shouldThrowDouyinApiExceptionWhenCodeNonZero() {
        when(douyinConfig.getAppId()).thenReturn("app123");
        when(douyinConfig.getClientSecret()).thenReturn("secret123");
        when(douyinConfig.getBaseUrl()).thenReturn("https://openapi-fxg.jinritemai.com");
        when(douyinTokenService.getValidToken("app123")).thenReturn("token");
        when(douyinRestTemplate.postForObject(any(String.class), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(Map.of("err_no", 10001, "err_msg", "bad"));

        assertThatThrownBy(() -> douyinApiClient.post("test", null))
                .isInstanceOf(DouyinApiException.class);
    }

    @Test
    void post_shouldNotMarkReauthorizeFor31012() {
        when(douyinConfig.getClientKey()).thenReturn("app123");
        when(douyinConfig.getClientSecret()).thenReturn("secret123");
        when(douyinConfig.getBaseUrl()).thenReturn("https://openapi-fxg.jinritemai.com");
        when(douyinTokenService.getValidToken("app123")).thenReturn("token");
        when(douyinRestTemplate.postForObject(any(String.class), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(Map.of("err_no", 31012, "err_msg", "token expired"));

        assertThatThrownBy(() -> douyinApiClient.post("test", null))
                .isInstanceOf(DouyinApiException.class);

        verify(douyinTokenService, never()).markReauthorizeRequired(any(), any());
    }

    @Test
    void post_shouldPreferClientKeyWhenNoExplicitAppId() {
        when(douyinConfig.getClientKey()).thenReturn("client-key-123");
        when(douyinConfig.getAppId()).thenReturn("app123");
        when(douyinConfig.getClientSecret()).thenReturn("secret123");
        when(douyinConfig.getBaseUrl()).thenReturn("https://openapi-fxg.jinritemai.com");
        when(douyinTokenService.getValidToken("client-key-123")).thenReturn("token");
        when(douyinRestTemplate.postForObject(any(String.class), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(Map.of("code", 10000));

        douyinApiClient.post("test.method", Map.of());

        verify(douyinTokenService).getValidToken("client-key-123");
    }

    @Test
    void post_shouldParseCodeFromCodeField() {
        when(douyinConfig.getAppId()).thenReturn("app123");
        when(douyinConfig.getClientSecret()).thenReturn("secret123");
        when(douyinConfig.getBaseUrl()).thenReturn("https://openapi-fxg.jinritemai.com");
        when(douyinTokenService.getValidToken("app123")).thenReturn("token");
        when(douyinRestTemplate.postForObject(any(String.class), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(Map.of("code", 10000, "message", "ok"));

        Map<String, Object> result = douyinApiClient.post("test", null);

        assertThat(result).containsEntry("code", 10000);
    }

    @Test
    void post_shouldPreferSubMsgWhenPresent() {
        when(douyinConfig.getAppId()).thenReturn("app123");
        when(douyinConfig.getClientSecret()).thenReturn("secret123");
        when(douyinConfig.getBaseUrl()).thenReturn("https://openapi-fxg.jinritemai.com");
        when(douyinTokenService.getValidToken("app123")).thenReturn("token");
        when(douyinRestTemplate.postForObject(any(String.class), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(Map.of(
                        "code", 40003,
                        "msg", "unknown error",
                        "sub_code", "isv.parameter-invalid:257",
                        "sub_msg", "参数校验失败",
                        "log_id", "202604220001"
                ));

        assertThatThrownBy(() -> douyinApiClient.post("test", null))
                .isInstanceOf(DouyinApiException.class)
                .extracting(ex -> ((DouyinApiException) ex).getErrorMsg())
                .isEqualTo("参数校验失败");
    }

    @Test
    void post_shouldNotLogOrThrowSensitiveRequestUrlWhenTransportFails() {
        when(douyinConfig.getAppId()).thenReturn("app123");
        when(douyinConfig.getClientSecret()).thenReturn("secret123");
        when(douyinConfig.getBaseUrl()).thenReturn("https://openapi-fxg.jinritemai.com");
        when(douyinTokenService.getValidToken("app123")).thenReturn("very-secret-token");
        when(douyinRestTemplate.postForObject(any(String.class), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new ResourceAccessException(
                        "I/O error on POST request for \"https://openapi-fxg.jinritemai.com/test/method"
                                + "?access_token=very-secret-token&sign=very-secret-sign\": Read timed out"));

        Logger logger = (Logger) LoggerFactory.getLogger(DouyinApiClient.class);
        Level originalLevel = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.setLevel(Level.ERROR);
        logger.addAppender(appender);
        try {
            assertThatThrownBy(() -> douyinApiClient.post("test.method", Map.of()))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("Douyin API request failed")
                    .satisfies(ex -> assertThat(ex.getCause()).isNull());

            String logText = appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .collect(Collectors.joining("\n"));
            assertThat(logText)
                    .contains("method=test.method")
                    .contains("exception=ResourceAccessException")
                    .doesNotContain("very-secret-token", "very-secret-sign", "access_token=", "sign=");
            assertThat(appender.list)
                    .allSatisfy(event -> assertThat(event.getThrowableProxy()).isNull());
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(originalLevel);
        }
    }
}
