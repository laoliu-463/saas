package com.colonel.saas.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.colonel.saas.service.DouyinWebhookEventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
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
@Tag(name = "抖音联调")
public class DouyinWebhookController {

    private final ObjectMapper objectMapper;
    private final DouyinWebhookEventService webhookEventService;
    private final String clientSecret;
    private final boolean verifySign;

    public DouyinWebhookController(
            ObjectMapper objectMapper,
            DouyinWebhookEventService webhookEventService,
            @Value("${douyin.app.client-secret:}") String clientSecret,
            @Value("${douyin.webhook.verify-sign:true}") boolean verifySign) {
        this.objectMapper = objectMapper;
        this.webhookEventService = webhookEventService;
        this.clientSecret = clientSecret;
        this.verifySign = verifySign;
    }

    @Operation(
            summary = "[联调] 接收团长开放事件回调",
            description = "接收抖店联盟团长开放事件回调。该接口会先做签名校验，再按事件键幂等落库并推进本地消费状态。"
    )
    @PostMapping(
            value = "/colonel-open-events",
            consumes = MediaType.ALL_VALUE,
            produces = MediaType.TEXT_PLAIN_VALUE
    )
    public ResponseEntity<String> colonelOpenEvent(
            @Parameter(description = "抖店回调签名头 x-doudian-sign。") @RequestHeader(value = "x-doudian-sign", required = false) String xDoudianSign,
            @Parameter(description = "兼容签名头 sign。") @RequestHeader(value = "sign", required = false) String sign,
            @Parameter(description = "兼容签名头 x-sign。") @RequestHeader(value = "x-sign", required = false) String xSign,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "抖店回调原始请求体。",
                    content = @Content(examples = @ExampleObject(value = "{\"event\":\"doudian_alliance_colonelOpenEvent\",\"data\":{}}"))
            )
            @RequestBody(required = false) String rawBody) {
        String body = rawBody == null ? "" : rawBody;
        String providedSign = firstNonBlank(xDoudianSign, sign, xSign);
        if (verifySign && !verifySignature(body, providedSign)) {
            log.warn("Douyin webhook sign verify failed, providedSignPresent={}, providedSignLength={}, bodyLength={}",
                    StringUtils.hasText(providedSign),
                    providedSign == null ? 0 : providedSign.length(),
                    body.length());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("invalid sign");
        }
        try {
            Map<?, ?> event = objectMapper.readValue(body, Map.class);
            log.info("Receive douyin webhook event, event={}, bodyLength={}", safeEventName(event), body.length());
        } catch (Exception e) {
            log.warn("Douyin webhook payload parse failed, bodyLength={}, exception={}",
                    body.length(), e.getClass().getSimpleName());
        }
        try {
            DouyinWebhookEventService.CaptureResult result = webhookEventService.captureColonelOpenEvent(body);
            log.info("Douyin webhook captured, event={}, status={}, duplicate={}, bodyLength={}",
                    result.eventType(), result.status(), result.duplicate(), body.length());
        } catch (Exception e) {
            log.warn("Douyin webhook capture failed, bodyLength={}, exception={}",
                    body.length(), e.getClass().getSimpleName());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("receive failed");
        }
        // 已完成幂等落库后再快速返回 success，避免上游重复重试。
        return ResponseEntity.ok("success");
    }

    private String safeEventName(Map<?, ?> event) {
        Object eventName = event.get("event");
        if (eventName == null) {
            return "unknown";
        }
        String value = String.valueOf(eventName).trim();
        if (!StringUtils.hasText(value) || value.length() > 128 || !value.matches("[A-Za-z0-9._:-]+")) {
            return "unknown";
        }
        return value;
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
