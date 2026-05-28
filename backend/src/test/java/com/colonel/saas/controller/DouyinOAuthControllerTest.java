package com.colonel.saas.controller;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.colonel.saas.douyin.DouyinOAuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DouyinOAuthControllerTest {

    @Mock
    private DouyinOAuthService douyinOAuthService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new DouyinOAuthController(douyinOAuthService)).build();
    }

    @Test
    void authorizeUrl_shouldReturnAuthorizePayload() throws Exception {
        when(douyinOAuthService.createAuthorizeUrl("app-1"))
                .thenReturn(new DouyinOAuthService.AuthorizeUrlResult(
                        "https://op.jinritemai.com/oauth2/authorize?state=s1",
                        "s1",
                        "http://localhost:8081/api/douyin/oauth/callback",
                        "https://buyin.jinritemai.com/dashboard/institution/power-manage"
                ));

        mockMvc.perform(get("/douyin/oauth/authorize-url").param("appId", "app-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.authorizeUrl").value("https://op.jinritemai.com/oauth2/authorize?state=s1"))
                .andExpect(jsonPath("$.data.state").value("s1"))
                .andExpect(jsonPath("$.data.powerManageUrl").value("https://buyin.jinritemai.com/dashboard/institution/power-manage"));
    }

    @Test
    void callback_shouldRedirectToSuccessUrl() throws Exception {
        when(douyinOAuthService.handleCallback("code-1", "state-1"))
                .thenReturn("http://localhost:3001/system/douyin?oauth=success");

        mockMvc.perform(get("/douyin/oauth/callback")
                        .param("code", "code-1")
                        .param("state", "state-1"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "http://localhost:3001/system/douyin?oauth=success"));

        verify(douyinOAuthService).handleCallback("code-1", "state-1");
    }

    @Test
    void callback_shouldRedirectToFailureUrlWhenExchangeFails() throws Exception {
        when(douyinOAuthService.handleCallback("bad-code", "bad-state"))
                .thenThrow(new IllegalStateException("upstream rejected code"));
        when(douyinOAuthService.failureRedirectUrl())
                .thenReturn("http://localhost:3001/system/douyin?oauth=failed");

        mockMvc.perform(get("/douyin/oauth/callback")
                        .param("code", "bad-code")
                        .param("state", "bad-state"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "http://localhost:3001/system/douyin?oauth=failed"));
    }

    @Test
    void callbackFailure_shouldNotLogSensitiveOAuthCodeFromExceptionMessage() throws Exception {
        when(douyinOAuthService.handleCallback("code-secret-123", "state-1"))
                .thenThrow(new IllegalStateException("upstream rejected code code-secret-123 access_token=secret-token"));
        when(douyinOAuthService.failureRedirectUrl())
                .thenReturn("http://localhost:3001/system/douyin?oauth=failed");
        Logger logger = (Logger) LoggerFactory.getLogger(DouyinOAuthController.class);
        Level originalLevel = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.setLevel(Level.WARN);
        logger.addAppender(appender);

        try {
            mockMvc.perform(get("/douyin/oauth/callback")
                            .param("code", "code-secret-123")
                            .param("state", "state-1"))
                    .andExpect(status().isFound())
                    .andExpect(header().string("Location", "http://localhost:3001/system/douyin?oauth=failed"));

            String logText = appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .collect(Collectors.joining("\n"));
            assertThat(logText)
                    .contains("Douyin OAuth callback failed")
                    .doesNotContain("code-secret-123", "secret-token", "access_token=");
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(originalLevel);
        }
    }
}
