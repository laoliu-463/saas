package com.colonel.saas.domain.logistics.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.common.exception.OptimisticLockSupport;
import com.colonel.saas.config.LogisticsProperties;
import com.colonel.saas.domain.sample.event.SampleDomainEventPublisher;
import com.colonel.saas.entity.SampleLogisticsTrace;
import com.colonel.saas.entity.SampleRequest;
import com.colonel.saas.mapper.SampleLogisticsTraceMapper;
import com.colonel.saas.mapper.SampleRequestMapper;
import com.colonel.saas.service.SampleStatusLogService;
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

/**
 * Kuaidi100 物流轨迹回调 Application Service (DDD-LOGISTICS Slice 3).
 *
 * <p>基于快递100官方回调 API 文档字段结构, 业务逻辑从
 * {@code service.Kuaidi100LogisticsCallbackService} 真实搬运 (无业务逻辑改动):
 * <ul>
 *   <li>{@link #handleCallback} —— 接收快递100回调, 验签 + 解析 + 幂等 + 状态流转 + 事件发布</li>
 * </ul>
 *
 * <p>官方 API 字段结构 (参考 Kuaidi100LogisticsGateway Javadoc):
 * <ul>
 *   <li>POST callback URL (form 表单): param + sign</li>
 *   <li>签名算法: MD5(param + salt) 转大写十六进制</li>
 *   <li>JSON 报文: {lastResult: {com, nu, state, message, data: [{ftime, time, context, location}]}}</li>
 *   <li>state 状态码: 0/1/7/8/10/11/12/13 (IN_TRANSIT) / 2/4/6/14 (EXCEPTION) / 3 (SIGNED) / 5 (DELIVERING)</li>
 * </ul>
 *
 * <p>Service 改 1-line 委派壳, helper 改 package-private 暴露给 ApplicationTest.</p>
 */
@Slf4j
@Service
public class Kuaidi100CallbackApplicationService {

    /** 物流轨迹节点时间解析格式: yyyy-MM-dd HH:mm:ss */
    static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 寄样状态: 待发货 (值=2) */
    static final int STATUS_PENDING_SHIP = 2;
    /** 寄样状态: 运送中 (值=3) */
    static final int STATUS_SHIPPING = 3;
    /** 寄样状态: 待作业 (值=5), 签收后自动推进至此状态 */
    static final int STATUS_PENDING_HOMEWORK = 5;

    /** 物流服务提供商标识: 快递100 */
    static final String PROVIDER = "KUAIDI100";

    private final LogisticsProperties properties;
    private final SampleRequestMapper sampleRequestMapper;
    private final SampleLogisticsTraceMapper sampleLogisticsTraceMapper;
    private final SampleStatusLogService sampleStatusLogService;
    private final SampleDomainEventPublisher sampleDomainEventPublisher;
    private final ObjectMapper objectMapper;

    public Kuaidi100CallbackApplicationService(
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

    /**
     * 处理快递100回调 (主流程, 真实搬运自 god service).
     *
     * <p>处理流程:
     * <ol>
     *   <li>校验回调参数完整性 (param + sign)</li>
     *   <li>MD5 签名验证 (param + salt)</li>
     *   <li>JSON 报文解析, 提取 lastResult</li>
     *   <li>校验快递公司 + 物流单号</li>
     *   <li>匹配寄样请求 (公司编码标准化 + LambdaQueryWrapper)</li>
     *   <li>更新寄样请求的物流状态字段 (8 个 setter)</li>
     *   <li>SHA-256 幂等插入轨迹节点</li>
     *   <li>异常状态记录 / 签收状态自动推进</li>
     *   <li>乐观锁更新寄样请求</li>
     * </ol>
     */
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
            payload = objectMapper.readValue(param, new TypeReference<Map<String, Object>>() {});
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

    public record CallbackAck(boolean result, String returnCode, String message) {}

    // ===== Internal helpers (package-private, 真实搬运自 god service) =====

    boolean verifySign(String param, String providedSign) {
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

    SampleRequest findMatchedSample(String company, String trackingNo) {
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

    void insertMissingTraceNodes(SampleRequest sample, String state, List<Map<String, Object>> nodes) {
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

    void applySigned(SampleRequest sample, LocalDateTime signedAt) {
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

    String nodeHash(SampleRequest sample, Map<String, Object> node) {
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

    LocalDateTime resolveSignedAt(List<Map<String, Object>> nodes, LocalDateTime fallback) {
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

    String statusName(String state) {
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

    boolean isExceptionState(String state) {
        return "2".equals(state) || "4".equals(state) || "6".equals(state) || "14".equals(state);
    }

    String latestContext(List<Map<String, Object>> nodes, String fallback) {
        if (nodes.isEmpty()) {
            return fallback;
        }
        String context = text(nodes.get(0).get("context"));
        return StringUtils.hasText(context) ? context : fallback;
    }

    LocalDateTime parseTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim(), TIME_FMT);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    String normalizeCompanyCode(String companyCode) {
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
    Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> ? (Map<String, Object>) value : null;
    }

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> asNodeList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(item -> (Map<String, Object>) item)
                .toList();
    }

    String firstText(Object... values) {
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

    String text(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    CallbackAck fail(String message) {
        return new CallbackAck(false, "500", message);
    }
}