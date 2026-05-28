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

/**
 * 抖音 Webhook 回调控制器
 * <p>
 * 接收抖店联盟团长开放事件的回调通知。该控制器负责：
 * <ul>
 *   <li>接收抖店平台推送的事件回调请求</li>
 *   <li>对回调请求进行 HMAC-SHA256 签名校验，确保请求来源合法</li>
 *   <li>将事件按事件键幂等落库，防止重复消费</li>
 *   <li>快速返回成功响应，避免上游重复重试</li>
 * </ul>
 * </p>
 *
 * <p>API 路径前缀：{@code /douyin/webhooks}</p>
 * <p>所属业务领域：抖音联调（Webhook 事件回调）</p>
 * <p>访问权限：外部回调接口，通过签名验证保证安全性</p>
 *
 * @see com.colonel.saas.service.DouyinWebhookEventService
 */
@Slf4j
@RestController
@RequestMapping("/douyin/webhooks")
@Tag(name = "抖音联调")
public class DouyinWebhookController {

    /** Jackson 对象映射器，用于解析回调请求体 JSON */
    private final ObjectMapper objectMapper;

    /** Webhook 事件服务，负责事件的幂等落库与消费状态推进 */
    private final DouyinWebhookEventService webhookEventService;

    /** 抖音应用客户端密钥，用于 HMAC-SHA256 签名校验 */
    private final String clientSecret;

    /** 是否启用签名校验开关，通过配置项 douyin.webhook.verify-sign 控制 */
    private final boolean verifySign;

    /**
     * 构造函数，注入所有依赖
     *
     * @param objectMapper         JSON 对象映射器
     * @param webhookEventService  Webhook 事件处理服务
     * @param clientSecret         抖音应用客户端密钥，从配置项 douyin.app.client-secret 读取
     * @param verifySign           是否启用签名校验，从配置项 douyin.webhook.verify-sign 读取，默认 true
     */
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

    /**
     * 接收团长开放事件回调
     * <p>
     * 处理抖店联盟团长开放事件的 Webhook 回调。处理流程如下：
     * <ol>
     *   <li>从请求头中提取签名（兼容 x-doudian-sign、sign、x-sign 三种签名头）</li>
     *   <li>若启用签名校验，使用 HMAC-SHA256 算法验证签名的合法性</li>
     *   <li>尝试解析回调请求体的 JSON 内容，记录事件名称</li>
     *   <li>调用事件服务将事件按事件键幂等落库，推进本地消费状态</li>
     *   <li>快速返回 "success" 文本响应，避免抖音平台重复重试</li>
     * </ol>
     * </p>
     *
     * <p>HTTP 方法：POST</p>
     * <p>请求路径：{@code /douyin/webhooks/colonel-open-events}</p>
     * <p>Content-Type：接受所有类型（&#42;/&#42;）</p>
     * <p>响应 Content-Type：{@code text/plain}</p>
     *
     * @param xDoudianSign 抖店回调签名头 {@code x-doudian-sign}（可选）
     * @param sign         兼容签名头 {@code sign}（可选）
     * @param xSign        兼容签名头 {@code x-sign}（可选）
     * @param rawBody      抖店回调原始请求体 JSON 字符串（可选）
     * @return 成功时返回 "success"（HTTP 200），签名校验失败返回 "invalid sign"（HTTP 401），
     *         事件落库失败返回 "receive failed"（HTTP 500）
     */
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
        // 处理请求体为空的情况
        String body = rawBody == null ? "" : rawBody;
        // 从三个可能的签名头中取第一个非空值
        String providedSign = firstNonBlank(xDoudianSign, sign, xSign);

        // 第一步：签名校验
        if (verifySign && !verifySignature(body, providedSign)) {
            log.warn("Douyin webhook sign verify failed, providedSignPresent={}, providedSignLength={}, bodyLength={}",
                    StringUtils.hasText(providedSign),
                    providedSign == null ? 0 : providedSign.length(),
                    body.length());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("invalid sign");
        }

        // 第二步：尝试解析事件内容（仅用于日志记录，不影响后续流程）
        try {
            Map<?, ?> event = objectMapper.readValue(body, Map.class);
            log.info("Receive douyin webhook event, event={}, bodyLength={}", safeEventName(event), body.length());
        } catch (Exception e) {
            log.warn("Douyin webhook payload parse failed, bodyLength={}, exception={}",
                    body.length(), e.getClass().getSimpleName());
        }

        // 第三步：幂等落库并推进消费状态
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

    /**
     * 安全地提取事件名称
     * <p>
     * 从回调事件 Map 中提取 "event" 字段值，进行长度和字符合法性校验，
     * 防止日志注入攻击。仅允许字母、数字、点、下划线、冒号和连字符。
     * </p>
     *
     * @param event 回调事件 Map 对象
     * @return 合法的事件名称字符串，非法值返回 "unknown"
     */
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

    /**
     * 验证回调请求的 HMAC-SHA256 签名
     * <p>
     * 使用配置的 clientSecret 作为密钥，对请求体进行 HMAC-SHA256 签名计算，
     * 然后与请求头中提供的签名进行比较（不区分大小写）。
     * </p>
     *
     * @param body         回调请求体原始字符串
     * @param providedSign 请求头中提供的签名值
     * @return 签名校验通过返回 true，否则返回 false
     */
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

    /**
     * 将字节数组转换为小写十六进制字符串
     *
     * @param bytes 字节数组
     * @return 小写十六进制字符串表示
     */
    private String toHexLowercase(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * 从多个字符串中返回第一个非空非空白的值
     * <p>
     * 用于从多个兼容签名头中提取有效的签名值。
     * </p>
     *
     * @param values 待检查的字符串数组
     * @return 第一个非空非空白的字符串，全部为空时返回 null
     */
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
