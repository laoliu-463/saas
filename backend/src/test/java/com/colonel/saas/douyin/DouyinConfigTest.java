package com.colonel.saas.douyin;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

class DouyinConfigTest {

    @Test
    void douyinRestTemplate_shouldUseShortDefaultTimeoutsForThirdPartyCalls() {
        DouyinConfig config = new DouyinConfig();
        RestTemplateBuilder builder = mock(RestTemplateBuilder.class);
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(builder.setConnectTimeout(Duration.ofSeconds(3))).thenReturn(builder);
        when(builder.setReadTimeout(Duration.ofSeconds(5))).thenReturn(builder);
        when(builder.build()).thenReturn(restTemplate);

        config.douyinRestTemplate(builder);

        verify(builder).setConnectTimeout(Duration.ofSeconds(3));
        verify(builder).setReadTimeout(Duration.ofSeconds(5));
    }

    @Test
    void douyinRestTemplate_shouldAllowTimeoutOverrides() {
        DouyinConfig config = new DouyinConfig();
        config.setConnectTimeout(Duration.ofSeconds(2));
        config.setReadTimeout(Duration.ofSeconds(4));
        RestTemplateBuilder builder = mock(RestTemplateBuilder.class);
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(builder.setConnectTimeout(Duration.ofSeconds(2))).thenReturn(builder);
        when(builder.setReadTimeout(Duration.ofSeconds(4))).thenReturn(builder);
        when(builder.build()).thenReturn(restTemplate);

        RestTemplate result = config.douyinRestTemplate(builder);

        verify(builder).setConnectTimeout(Duration.ofSeconds(2));
        verify(builder).setReadTimeout(Duration.ofSeconds(4));
        assertThat(result).isSameAs(restTemplate);
    }

    @Test
    void properties_shouldExposeAssignedValues() {
        DouyinConfig config = new DouyinConfig();
        config.setBaseUrl("https://example.test");
        config.setAppId("app-id");
        config.setClientKey("client-key");
        config.setClientSecret("client-secret");
        config.setSandbox(true);

        assertThat(config.getBaseUrl()).isEqualTo("https://example.test");
        assertThat(config.getAppId()).isEqualTo("app-id");
        assertThat(config.getClientKey()).isEqualTo("client-key");
        assertThat(config.getClientSecret()).isEqualTo("client-secret");
        assertThat(config.isSandbox()).isTrue();
    }
}
