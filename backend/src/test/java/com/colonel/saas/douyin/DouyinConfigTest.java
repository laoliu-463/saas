package com.colonel.saas.douyin;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

        config.douyinRestTemplate(builder);

        verify(builder).setConnectTimeout(Duration.ofSeconds(2));
        verify(builder).setReadTimeout(Duration.ofSeconds(4));
    }
}
