package com.colonel.saas.gateway.logistics.kuaidi100;

import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.config.LogisticsProperties;
import com.colonel.saas.gateway.logistics.LogisticsGateway;
import com.colonel.saas.gateway.logistics.LogisticsSubscribeCommand;
import com.colonel.saas.gateway.logistics.LogisticsSubscribeResult;
import com.colonel.saas.gateway.logistics.LogisticsTrackCommand;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 快递100物流查询实现
 * 对接文档：https://www.kuaidi100.com/openapi/
 *
 * 实时查询接口：POST https://poll.kuaidi100.com/poll/query.do
 * 参数：customer + sign + param（application/x-www-form-urlencoded）。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "logistics.kd100", name = "enabled", havingValue = "true")
public class Kuaidi100LogisticsGateway implements LogisticsGateway {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Map<String, String> COMPANY_CODE_ALIASES = Map.ofEntries(
            Map.entry("SF", "shunfeng"),
            Map.entry("SHUNFENG", "shunfeng"),
            Map.entry("ZTO", "zhongtong"),
            Map.entry("ZHONGTONG", "zhongtong"),
            Map.entry("YTO", "yuantong"),
            Map.entry("YUANTONG", "yuantong"),
            Map.entry("STO", "shentong"),
            Map.entry("SHENTONG", "shentong"),
            Map.entry("YD", "yunda"),
            Map.entry("YUNDA", "yunda"),
            Map.entry("EMS", "ems"),
            Map.entry("JD", "jd"),
            Map.entry("JINGDONG", "jd")
    );

    private final RestTemplate restTemplate;
    private final LogisticsProperties properties;
    private final ObjectMapper objectMapper;

    public Kuaidi100LogisticsGateway(
            RestTemplate kuaidi100RestTemplate,
            LogisticsProperties properties,
            ObjectMapper objectMapper) {
        this.restTemplate = kuaidi100RestTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public LogisticsResult createShipment(LogisticsCommand command) {
        throw new UnsupportedOperationException("快递100即时查询API不支持创建发货");
    }

    @Override
    public LogisticsSubscribeResult subscribeTrack(LogisticsSubscribeCommand command) {
        if (command == null || !StringUtils.hasText(command.getTrackingNo())) {
            throw BusinessException.param("物流单号不能为空");
        }
        String trackingNo = command.getTrackingNo().trim();
        String companyCode = normalizeCompanyCode(command.getCompanyCode());
        if (!StringUtils.hasText(companyCode) || "auto".equals(companyCode)) {
            throw BusinessException.param("快递公司编码不能为空，请先识别快递公司");
        }
        LogisticsProperties.Kd100 kd100 = properties.getKd100();
        if (!StringUtils.hasText(kd100.getKey())
                || !StringUtils.hasText(kd100.getCallbackUrl())
                || !StringUtils.hasText(kd100.getCallbackSalt())) {
            throw BusinessException.param("快递100订阅配置不完整");
        }
        try {
            String param = buildSubscribeParam(command, companyCode, trackingNo);
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("schema", "json");
            params.add("param", param);

            String response = restTemplate.postForObject(kd100.getSubscribeEndpoint(), params, String.class);
            return parseSubscribeResponse(response, companyCode, trackingNo);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("快递100订阅失败, trackingNo={}, companyCode={}, error={}",
                    trackingNo, companyCode, e.getMessage(), e);
            throw BusinessException.external("快递100订阅失败: " + e.getMessage());
        }
    }

    @Override
    public LogisticsStatusResult queryStatus(String trackingNo) {
        throw BusinessException.param("请调用 queryTrack(companyCode, trackingNo)");
    }

    @Override
    public LogisticsTrackResult queryTrack(String companyCode, String trackingNo) {
        return queryTrack(LogisticsTrackCommand.of(companyCode, trackingNo));
    }

    @Override
    public LogisticsTrackResult queryTrack(LogisticsTrackCommand command) {
        if (command == null || !StringUtils.hasText(command.getTrackingNo())) {
            throw BusinessException.param("物流单号不能为空");
        }
        String trackingNo = command.getTrackingNo().trim();
        String companyCode = normalizeCompanyCode(command.getCompanyCode());
        if (!StringUtils.hasText(companyCode) || "auto".equals(companyCode)) {
            throw BusinessException.param("快递公司编码不能为空，请先识别快递公司");
        }
        String phone = trimToNull(command.getPhone());
        if (requiresPhone(companyCode) && !StringUtils.hasText(phone)) {
            throw BusinessException.param("顺丰/中通快递100查询需要收件人或寄件人手机号");
        }

        try {
            String param = buildParam(command, companyCode, trackingNo, phone);
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("customer", properties.getKd100().getCustomer());
            params.add("sign", generateSign(param));
            params.add("param", param);

            String response = restTemplate.postForObject(properties.getKd100().getEndpoint(), params, String.class);
            return parseTrackResponse(response, companyCode, trackingNo);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("快递100查询失败, trackingNo={}, companyCode={}, error={}",
                    trackingNo, companyCode, e.getMessage(), e);
            throw BusinessException.external("快递100查询失败: " + e.getMessage());
        }
    }

    private String buildParam(LogisticsTrackCommand command, String companyCode, String trackingNo, String phone) {
        try {
            Map<String, Object> param = new LinkedHashMap<>();
            param.put("com", companyCode);
            param.put("num", trackingNo);
            if (StringUtils.hasText(phone)) {
                param.put("phone", phone);
            }
            putIfPresent(param, "from", command.getFrom());
            putIfPresent(param, "to", command.getTo());
            param.put("resultv2", StringUtils.hasText(command.getResultV2()) ? command.getResultV2().trim() : "4");
            param.put("show", "0");
            param.put("order", "desc");
            param.put("lang", "zh");
            return objectMapper.writeValueAsString(param);
        } catch (Exception e) {
            throw BusinessException.param("构建查询参数失败", e);
        }
    }

    private String buildSubscribeParam(LogisticsSubscribeCommand command, String companyCode, String trackingNo) {
        try {
            LogisticsProperties.Kd100 kd100 = properties.getKd100();
            Map<String, Object> param = new LinkedHashMap<>();
            param.put("company", companyCode);
            param.put("number", trackingNo);
            param.put("key", kd100.getKey());

            Map<String, Object> parameters = new LinkedHashMap<>();
            parameters.put("callbackurl", kd100.getCallbackUrl().trim());
            parameters.put("salt", kd100.getCallbackSalt().trim());
            parameters.put("resultv2", StringUtils.hasText(kd100.getResultV2()) ? kd100.getResultV2().trim() : "4");
            putIfPresent(parameters, "phone", command.getPhone());
            putIfPresent(parameters, "from", command.getFrom());
            putIfPresent(parameters, "to", command.getTo());
            param.put("parameters", parameters);
            return objectMapper.writeValueAsString(param);
        } catch (Exception e) {
            throw BusinessException.param("构建订阅参数失败", e);
        }
    }

    private String generateSign(String param) {
        try {
            String raw = param + properties.getKd100().getKey() + properties.getKd100().getCustomer();
            byte[] digest = MessageDigest.getInstance("MD5").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).toUpperCase(Locale.ROOT);
        } catch (Exception e) {
            throw BusinessException.external("生成快递100签名失败", e);
        }
    }

    private LogisticsTrackResult parseTrackResponse(String json, String companyCode, String trackingNo) {
        if (json == null || json.isBlank()) {
            return failedResult(companyCode, trackingNo, "快递100返回空响应", null);
        }

        Kuaidi100Response resp;
        try {
            resp = objectMapper.readValue(json, Kuaidi100Response.class);
        } catch (Exception e) {
            log.error("解析快递100响应失败: {}", json, e);
            return failedResult(companyCode, trackingNo, "解析响应失败: " + e.getMessage(), null);
        }

        Map<String, Object> raw = objectMapper.convertValue(resp, Map.class);

        if (!"200".equals(resp.getStatus())) {
            return failedResult(
                    resp.getCompanyCode() != null ? resp.getCompanyCode() : companyCode,
                    resp.getTrackingNo() != null ? resp.getTrackingNo() : trackingNo,
                    resp.getMessage() != null ? resp.getMessage() : "查询失败",
                    raw);
        }

        String status = mapStatus(resp.getState());
        List<LogisticsTraceNode> traces = toTraceNodes(resp.getData());
        LocalDateTime signedAt = "SIGNED".equals(status) ? resolveSignedAt(traces) : null;

        return new LogisticsTrackResult(
                resp.getCompanyCode() != null ? resp.getCompanyCode() : companyCode,
                resp.getTrackingNo() != null ? resp.getTrackingNo() : trackingNo,
                true,
                null,
                resp.getState(),
                status,
                "SIGNED".equals(status),
                signedAt,
                traces,
                raw);
    }

    private LogisticsTrackResult failedResult(String companyCode, String trackingNo, String reason, Map<String, Object> raw) {
        return new LogisticsTrackResult(
                companyCode, trackingNo, false, reason,
                null, "FAILED", false, null, List.of(), raw != null ? raw : Map.of());
    }

    private LogisticsSubscribeResult parseSubscribeResponse(String json, String companyCode, String trackingNo) {
        if (json == null || json.isBlank()) {
            return failedSubscribeResult(companyCode, trackingNo, "EMPTY_RESPONSE", "快递100返回空响应", Map.of());
        }
        Map<String, Object> raw;
        try {
            raw = objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            log.error("解析快递100订阅响应失败: {}", json, e);
            return failedSubscribeResult(companyCode, trackingNo, "PARSE_ERROR", "解析响应失败: " + e.getMessage(), Map.of());
        }
        String returnCode = valueAsString(raw.get("returnCode"));
        String message = valueAsString(raw.get("message"));
        boolean accepted = "200".equals(returnCode) || "501".equals(returnCode);
        return LogisticsSubscribeResult.builder()
                .provider("KUAIDI100")
                .companyCode(companyCode)
                .trackingNo(trackingNo)
                .success(accepted)
                .returnCode(returnCode)
                .message(StringUtils.hasText(message) ? message : (accepted ? "订阅成功" : "订阅失败"))
                .rawResponse(raw)
                .build();
    }

    private LogisticsSubscribeResult failedSubscribeResult(
            String companyCode,
            String trackingNo,
            String returnCode,
            String message,
            Map<String, Object> raw) {
        return LogisticsSubscribeResult.builder()
                .provider("KUAIDI100")
                .companyCode(companyCode)
                .trackingNo(trackingNo)
                .success(false)
                .returnCode(returnCode)
                .message(message)
                .rawResponse(raw)
                .build();
    }

    private String mapStatus(String state) {
        if (!StringUtils.hasText(state)) {
            return "UNKNOWN";
        }
        return switch (state.trim()) {
            case "3" -> "SIGNED";
            case "5" -> "DELIVERING";
            case "2", "4", "6", "14" -> "EXCEPTION";
            case "0", "1", "7", "8", "10", "11", "12", "13" -> "IN_TRANSIT";
            default -> "UNKNOWN";
        };
    }

    private List<LogisticsTraceNode> toTraceNodes(List<Kuaidi100Response.TraceNode> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return List.of();
        }
        return nodes.stream()
                .filter(Objects::nonNull)
                .map(n -> new LogisticsTraceNode(
                        parseTime(StringUtils.hasText(n.getFormattedTime()) ? n.getFormattedTime() : n.getTime()),
                        n.getContext(),
                        n.getLocation()))
                .toList();
    }

    private LocalDateTime resolveSignedAt(List<LogisticsTraceNode> traces) {
        if (traces == null || traces.isEmpty()) {
            return null;
        }
        return traces.stream()
                .filter(t -> t.acceptStation() != null &&
                        (t.acceptStation().contains("签收") || t.acceptStation().contains("已取件")))
                .map(LogisticsTraceNode::acceptTime)
                .filter(Objects::nonNull)
                .findFirst()
                .orElseGet(() -> traces.get(0).acceptTime());
    }

    private LocalDateTime parseTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value, TIME_FMT);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private String normalizeCompanyCode(String companyCode) {
        if (!StringUtils.hasText(companyCode)) {
            return null;
        }
        String trimmed = companyCode.trim();
        String mapped = COMPANY_CODE_ALIASES.get(trimmed.toUpperCase(Locale.ROOT));
        return mapped != null ? mapped : trimmed.toLowerCase(Locale.ROOT);
    }

    private boolean requiresPhone(String companyCode) {
        return "shunfeng".equals(companyCode) || "zhongtong".equals(companyCode);
    }

    private void putIfPresent(Map<String, Object> param, String key, String value) {
        if (StringUtils.hasText(value)) {
            param.put(key, value.trim());
        }
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String valueAsString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
