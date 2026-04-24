package com.colonel.saas.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DouyinWebhookControllerTest {

    private static final String CLIENT_SECRET = "test-client-secret";

    private MockMvc createMvc(boolean verifySign) {
        DouyinWebhookController controller =
                new DouyinWebhookController(new ObjectMapper(), CLIENT_SECRET, verifySign);
        return MockMvcBuilders.standaloneSetup(controller).build();
    }

    private String sign(String body) throws NoSuchAlgorithmException, InvalidKeyException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(CLIENT_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
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
}
