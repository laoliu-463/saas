package com.colonel.saas.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/douyin/webhooks")
public class DouyinWebhookController {

    private static final Logger log = LoggerFactory.getLogger(DouyinWebhookController.class);

    private final ObjectMapper objectMapper;
    private final String clientSecret;
    private final boolean verifySign;

    public DouyinWebhookController(
            ObjectMapper objectMapper,
            @Value("${douyin.app.client-secret:}") String clientSecret,
            @Value("${douyin.webhook.verify-sign:false}") boolean verifySign) {
        this.objectMapper = objectMapper;
        this.clientSecret = clientSecret;
        this.verifySign = verifySign;
    }

    @PostMapping(
            value = "/colonel-open-events",
            consumes = MediaType.ALL_VALUE,
            produces = MediaType.TEXT_PLAIN_VALUE
    )
    public ResponseEntity<String> colonelOpenEvent(
            @RequestHeader(value = "x-doudian-sign", required = false) String xDoudianSign,
            @RequestHeader(value = "sign", required = false) String sign,
            @RequestHeader(value = "x-sign", required = false) String xSign,
            @RequestBody(required = false) String rawBody) {
        String body = rawBody == null ? "" : rawBody;
        String providedSign = firstNonBlank(xDoudianSign, sign, xSign);
        if (verifySign && !verifySignature(body, providedSign)) {
            log.warn("Douyin webhook sign verify failed, providedSign={}", providedSign);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("invalid sign");
        }
        try {
            Map<?, ?> event = objectMapper.readValue(body, Map.class);
            log.info("Receive doudian_alliance_colonelOpenEvent: {}", event);
        } catch (Exception e) {
            log.warn("Douyin webhook payload parse failed, body={}", body);
        }
        // 抖店消息回调通常要求快速返回成功文案，避免重复重试
        return ResponseEntity.ok("success");
    }

    private boolean verifySignature(String body, String providedSign) {
        if (!StringUtils.hasText(providedSign) || !StringUtils.hasText(clientSecret)) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(clientSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
            String expectedSign = toHexLowercase(digest);
            return expectedSign.equals(providedSign.toLowerCase(Locale.ROOT));
        } catch (Exception e) {
            log.warn("Douyin webhook sign compute error", e);
            return false;
        }
    }

    private String toHexLowercase(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            if (StringUtils.hasText(v)) {
                return v.trim();
            }
        }
        return null;
    }
}
