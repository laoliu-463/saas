package com.colonel.saas.controller;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.colonel.saas.mapper.DouyinWebhookEventMapper;
import com.colonel.saas.service.DouyinWebhookEventService;
import com.colonel.saas.domain.order.application.OrderSyncService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.stream.Collectors;

import static org.mockito.Mockito.mock;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DouyinWebhookControllerTest {

    private static final String WEBHOOK_SIGNING_KEY = "example-signing-key";

    private MockMvc createMvc(boolean verifySign) {
        DouyinWebhookEventService eventService =
                new DouyinWebhookEventService(mock(DouyinWebhookEventMapper.class), new ObjectMapper(), mock(OrderSyncService.class));
        DouyinWebhookController controller =
                new DouyinWebhookController(new ObjectMapper(), eventService, WEBHOOK_SIGNING_KEY, verifySign);
        return MockMvcBuilders.standaloneSetup(controller).build();
    }

    private String sign(String body) throws NoSuchAlgorithmException, InvalidKeyException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(WEBHOOK_SIGNING_KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] digest = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(digest);
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }

    @Test
    void colonelOpenEvent_verifySignDisabled_returnsSuccess() throws Exception {
        MockMvc mockMvc = createMvc(false);

        mockMvc.perform(post("/douyin/webhooks/colonel-open-events")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(content().string("success"));
    }

    @Test
    void colonelOpenEvent_verifySignDisabled_emptyBody_returnsSuccess() throws Exception {
        MockMvc mockMvc = createMvc(false);

        mockMvc.perform(post("/douyin/webhooks/colonel-open-events")
                        .contentType("application/json"))
                .andExpect(status().isOk())
                .andExpect(content().string("success"));
    }

    @Test
    void colonelOpenEvent_verifySignDisabled_nullBody_returnsSuccess() throws Exception {
        MockMvc mockMvc = createMvc(false);

        mockMvc.perform(post("/douyin/webhooks/colonel-open-events")
                        .contentType("application/json")
                        .content(""))
                .andExpect(status().isOk())
                .andExpect(content().string("success"));
    }

    @Test
    void colonelOpenEvent_verifySignEnabled_validSignature_returnsSuccess() throws Exception {
        MockMvc mockMvc = createMvc(true);
        String body = "{\"event\":\"test\"}";
        String sig = sign(body);

        mockMvc.perform(post("/douyin/webhooks/colonel-open-events")
                        .header("x-doudian-sign", sig)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(content().string("success"));
    }

    @Test
    void colonelOpenEvent_verifySignEnabled_validSignatureWithSignHeader_returnsSuccess() throws Exception {
        MockMvc mockMvc = createMvc(true);
        String body = "{\"event\":\"test\"}";
        String sig = sign(body);

        mockMvc.perform(post("/douyin/webhooks/colonel-open-events")
                        .header("sign", sig)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(content().string("success"));
    }

    @Test
    void colonelOpenEvent_verifySignEnabled_validSignatureWithXSignHeader_returnsSuccess() throws Exception {
        MockMvc mockMvc = createMvc(true);
        String body = "{\"event\":\"test\"}";
        String sig = sign(body);

        mockMvc.perform(post("/douyin/webhooks/colonel-open-events")
                        .header("x-sign", sig)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(content().string("success"));
    }

    @Test
    void colonelOpenEvent_verifySignEnabled_invalidSignature_returns401() throws Exception {
        MockMvc mockMvc = createMvc(true);
        String body = "{\"event\":\"test\"}";

        mockMvc.perform(post("/douyin/webhooks/colonel-open-events")
                        .header("x-doudian-sign", "invalid-signature")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string("invalid sign"));
    }

    @Test
    void colonelOpenEvent_verifySignEnabled_missingSignature_returns401() throws Exception {
        MockMvc mockMvc = createMvc(true);
        String body = "{\"event\":\"test\"}";

        mockMvc.perform(post("/douyin/webhooks/colonel-open-events")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string("invalid sign"));
    }

    @Test
    void colonelOpenEvent_verifySignEnabled_emptyBody_validSignature_returnsSuccess() throws Exception {
        MockMvc mockMvc = createMvc(true);
        String body = "";
        String sig = sign(body);

        mockMvc.perform(post("/douyin/webhooks/colonel-open-events")
                        .header("x-doudian-sign", sig)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(content().string("success"));
    }

    @Test
    void colonelOpenEvent_verifySignEnabled_nullBody_validSignature_returnsSuccess() throws Exception {
        MockMvc mockMvc = createMvc(true);
        String body = "";
        String sig = sign(body);

        mockMvc.perform(post("/douyin/webhooks/colonel-open-events")
                        .header("x-doudian-sign", sig)
                        .contentType("application/json"))
                .andExpect(status().isOk())
                .andExpect(content().string("success"));
    }

    @Test
    void colonelOpenEvent_validJson_logsMetadataWithoutRawSensitivePayload() throws Exception {
        MockMvc mockMvc = createMvc(false);
        String body = """
                {"event":"doudian_alliance_colonelOpenEvent","data":{"mobile":"13900000000","access_token":"very-secret-token","sign":"very-secret-sign"}}
                """;
        Logger logger = (Logger) LoggerFactory.getLogger(DouyinWebhookController.class);
        Level originalLevel = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.setLevel(Level.INFO);
        logger.addAppender(appender);
        try {
            mockMvc.perform(post("/douyin/webhooks/colonel-open-events")
                            .contentType("application/json")
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(content().string("success"));

            String logText = appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .collect(Collectors.joining("\n"));
            assertThat(logText)
                    .contains("event=doudian_alliance_colonelOpenEvent")
                    .contains("bodyLength=")
                    .doesNotContain("13900000000", "example-token", "example-sign", "access_token", "sign=");
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(originalLevel);
        }
    }

    @Test
    void colonelOpenEvent_invalidJson_logsMetadataWithoutRawSensitivePayload() throws Exception {
        MockMvc mockMvc = createMvc(false);
        String body = "{bad-json, mobile=13900000000, access_token=example-token, sign=example-sign";
        Logger logger = (Logger) LoggerFactory.getLogger(DouyinWebhookController.class);
        Level originalLevel = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.setLevel(Level.WARN);
        logger.addAppender(appender);
        try {
            mockMvc.perform(post("/douyin/webhooks/colonel-open-events")
                            .contentType("application/json")
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(content().string("success"));

            String logText = appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .collect(Collectors.joining("\n"));
            assertThat(logText)
                    .contains("payload parse failed")
                    .contains("bodyLength=")
                    .doesNotContain("13900000000", "very-secret-token", "very-secret-sign", "access_token", "sign=");
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(originalLevel);
        }
    }
}
