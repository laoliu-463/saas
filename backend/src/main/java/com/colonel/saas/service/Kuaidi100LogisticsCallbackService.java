package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.common.exception.OptimisticLockSupport;
import com.colonel.saas.config.LogisticsProperties;
import com.colonel.saas.domain.sample.event.SampleDomainEventPublisher;
import com.colonel.saas.entity.SampleLogisticsTrace;
import com.colonel.saas.entity.SampleRequest;
import com.colonel.saas.mapper.SampleLogisticsTraceMapper;
import com.colonel.saas.mapper.SampleRequestMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
public class Kuaidi100LogisticsCallbackService {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int STATUS_PENDING_SHIP = 2;
    private static final int STATUS_SHIPPING = 3;
    private static final int STATUS_PENDING_HOMEWORK = 5;
    private static final String PROVIDER = "KUAIDI100";

    private final LogisticsProperties properties;
    private final SampleRequestMapper sampleRequestMapper;
    private final SampleLogisticsTraceMapper sampleLogisticsTraceMapper;
    private final SampleStatusLogService sampleStatusLogService;
    private final SampleDomainEventPublisher sampleDomainEventPublisher;
    private final ObjectMapper objectMapper;

    public Kuaidi100LogisticsCallbackService(
            LogisticsProperties properties,
            SampleRequestMapper sampleRequestMapper,
            SampleLogisticsTraceMapper sampleLogisticsTraceMapper,
            SampleStatusLogService sampleStatusLogService,
            SampleDomainEventPublisher sampleDomainEventPublisher,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.sampleRequestMapper = sampleRequestMapper;
        this.sampleLogisticsTraceMapper = sampleLogisticsTraceMapper;
        this.sampleStatusLogService = sampleStatusLogService;
        this.sampleDomainEventPublisher = sampleDomainEventPublisher;
        this.objectMapper = objectMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public CallbackAck handleCallback(String param, String sign) {
        if (!StringUtils.hasText(param) || !StringUtils.hasText(sign)) {
            return fail("缺少参数");
        }
        if (!verifySign(param, sign)) {
            return fail("签名错误");
        }

        Map<String, Object> payload;
        try {
            payload = objectMapper.readValue(param, new TypeReference<>() {
            });
        } catch (Exception e) {
            log.warn("Kuaidi100 callback param parse failed, bodyLength={}, exception={}",
                    param.length(), e.getClass().getSimpleName());
            return fail("参数解析失败");
        }

        Map<String, Object> lastResult = asMap(payload.get("lastResult"));
        if (lastResult == null) {
            return fail("缺少 lastResult");
        }
        String company = text(lastResult.get("com"));
        String trackingNo = text(lastResult.get("nu"));
        if (!StringUtils.hasText(company) || !StringUtils.hasText(trackingNo)) {
            return fail("缺少快递公司或物流单号");
        }

        SampleRequest sample = findMatchedSample(company, trackingNo);
        if (sample == null) {
            return fail("快递公司编码或单号与系统记录不一致");
        }

        LocalDateTime now = LocalDateTime.now();
        String state = text(lastResult.get("state"));
        String callbackStatus = text(payload.get("status"));
        String callbackMessage = firstText(payload.get("message"), lastResult.get("message"));
        sample.setLogisticsProvider(PROVIDER);
        sample.setLogisticsLastCallbackAt(now);
        sample.setLogisticsCallbackStatus(callbackStatus);
        sample.setLogisticsCallbackMessage(callbackMessage);
        sample.setLogisticsStatus(state);
        sample.setLogisticsStatusName(statusName(state));
        sample.setLogisticsRawPayload(payload);
        sample.setLogisticsLastError(null);

        List<Map<String, Object>> nodes = asNodeList(lastResult.get("data"));
        insertMissingTraceNodes(sample, state, nodes);
        if (isExceptionState(state)) {
            sample.setLogisticsExceptionReason(latestContext(nodes, callbackMessage));
        }
        if ("3".equals(state)) {
            applySigned(sample, resolveSignedAt(nodes, now));
        }
        OptimisticLockSupport.requireUpdated(sampleRequestMapper.updateById(sample));
        return new CallbackAck(true, "200", "success");
    }

    private boolean verifySign(String param, String providedSign) {
        String salt = properties.getKd100().getCallbackSalt();
        if (!StringUtils.hasText(salt)) {
            return false;
        }
        try {
            byte[] digest = MessageDigest.getInstance("MD5")
                    .digest((param + salt).getBytes(StandardCharsets.UTF_8));
            String expected = HexFormat.of().formatHex(digest).toUpperCase(Locale.ROOT);
            return expected.equals(providedSign.trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            log.warn("Kuaidi100 callback sign compute failed", e);
            return false;
        }
    }

    private SampleRequest findMatchedSample(String company, String trackingNo) {
        List<SampleRequest> samples = sampleRequestMapper.selectList(new LambdaQueryWrapper<SampleRequest>()
                .eq(SampleRequest::getTrackingNo, trackingNo.trim())
                .orderByDesc(SampleRequest::getUpdateTime));
        String normalizedCompany = normalizeCompanyCode(company);
        return samples.stream()
                .filter(Objects::nonNull)
                .filter(sample -> normalizedCompany.equals(normalizeCompanyCode(sample.getShipperCode())))
                .findFirst()
                .orElse(null);
    }

    private void insertMissingTraceNodes(SampleRequest sample, String state, List<Map<String, Object>> nodes) {
        if (nodes.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (Map<String, Object> node : nodes) {
            String nodeHash = nodeHash(sample, node);
            Long existing = sampleLogisticsTraceMapper.selectCount(new LambdaQueryWrapper<SampleLogisticsTrace>()
                    .eq(SampleLogisticsTrace::getSampleRequestId, sample.getId())
                    .eq(SampleLogisticsTrace::getNodeHash, nodeHash));
            if (existing != null && existing > 0) {
                continue;
            }
            SampleLogisticsTrace trace = new SampleLogisticsTrace();
            trace.setId(UUID.randomUUID());
            trace.setSampleRequestId(sample.getId());
            trace.setTrackingNo(sample.getTrackingNo());
            trace.setLogisticsCompany(sample.getShipperCode());
            trace.setStatusCode(state);
            trace.setStatusName(statusName(state));
            trace.setTraceTime(parseTime(firstText(node.get("ftime"), node.get("time"))));
            trace.setTraceContent(text(node.get("context")));
            trace.setLocation(text(node.get("location")));
            trace.setNodeHash(nodeHash);
            trace.setRawPayload(node);
            trace.setCreatedAt(now);
            sampleLogisticsTraceMapper.insert(trace);
        }
    }

    private void applySigned(SampleRequest sample, LocalDateTime signedAt) {
        Integer status = sample.getStatus();
        if (status == null || (status != STATUS_PENDING_SHIP && status != STATUS_SHIPPING)) {
            return;
        }
        int fromStatus = status;
        sample.setSignedAt(signedAt);
        sample.setDeliverTime(signedAt);
        sample.setStatus(STATUS_PENDING_HOMEWORK);
        UUID operatorId = sample.getUserId() != null ? sample.getUserId() : sample.getId();
        sampleStatusLogService.log(sample.getId(), fromStatus, STATUS_PENDING_HOMEWORK, operatorId, "快递100签收回调自动推进");
        sampleDomainEventPublisher.publishSampleSigned(sample, signedAt);
    }

    private String nodeHash(SampleRequest sample, Map<String, Object> node) {
        String raw = String.join("|",
                normalizeCompanyCode(sample.getShipperCode()),
                nullToEmpty(sample.getTrackingNo()),
                nullToEmpty(firstText(node.get("ftime"), node.get("time"))),
                nullToEmpty(text(node.get("context"))),
                nullToEmpty(text(node.get("location"))),
                nullToEmpty(text(node.get("statusCode"))));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("生成物流轨迹节点哈希失败", e);
        }
    }

    private LocalDateTime resolveSignedAt(List<Map<String, Object>> nodes, LocalDateTime fallback) {
        return nodes.stream()
                .filter(node -> {
                    String context = text(node.get("context"));
                    return StringUtils.hasText(context) && (context.contains("签收") || context.contains("已取件"));
                })
                .map(node -> parseTime(firstText(node.get("ftime"), node.get("time"))))
                .filter(Objects::nonNull)
                .findFirst()
                .orElseGet(() -> nodes.stream()
                        .map(node -> parseTime(firstText(node.get("ftime"), node.get("time"))))
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse(fallback));
    }

    private String statusName(String state) {
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

    private boolean isExceptionState(String state) {
        return "2".equals(state) || "4".equals(state) || "6".equals(state) || "14".equals(state);
    }

    private String latestContext(List<Map<String, Object>> nodes, String fallback) {
        if (nodes.isEmpty()) {
            return fallback;
        }
        String context = text(nodes.get(0).get("context"));
        return StringUtils.hasText(context) ? context : fallback;
    }

    private LocalDateTime parseTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim(), TIME_FMT);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private String normalizeCompanyCode(String companyCode) {
        if (!StringUtils.hasText(companyCode)) {
            return "";
        }
        String value = companyCode.trim().toUpperCase(Locale.ROOT);
        return switch (value) {
            case "SF", "SHUNFENG" -> "shunfeng";
            case "ZTO", "ZHONGTONG" -> "zhongtong";
            case "YTO", "YUANTONG" -> "yuantong";
            case "STO", "SHENTONG" -> "shentong";
            case "YD", "YUNDA" -> "yunda";
            case "EMS" -> "ems";
            case "JD", "JINGDONG" -> "jd";
            default -> companyCode.trim().toLowerCase(Locale.ROOT);
        };
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> ? (Map<String, Object>) value : null;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> asNodeList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(item -> (Map<String, Object>) item)
                .toList();
    }

    private String firstText(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object value : values) {
            String text = text(value);
            if (StringUtils.hasText(text)) {
                return text;
            }
        }
        return null;
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private CallbackAck fail(String message) {
        return new CallbackAck(false, "500", message);
    }

    public record CallbackAck(boolean result, String returnCode, String message) {
    }
}
