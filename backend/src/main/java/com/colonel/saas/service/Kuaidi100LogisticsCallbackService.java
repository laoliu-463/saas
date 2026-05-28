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

/**
 * 快递100物流轨迹回调处理服务。
 *
 * <p>负责接收快递100（Kuaidi100）推送的物流轨迹回调，完成签名验证、报文解析、
 * 物理轨迹节点落库、签收状态自动推进等全链路处理。</p>
 *
 * <ul>
 *   <li>接收快递100推送回调，使用MD5+盐值方案验证签名合法性</li>
 *   <li>解析回调报文，提取快递公司编码、物流单号、轨迹节点列表</li>
 *   <li>通过快递公司编码+物流单号匹配寄样请求记录</li>
 *   <li>基于SHA-256节点哈希实现轨迹节点幂等插入，防止重复落库</li>
 *   <li>当物流状态为"已签收"时，自动将寄样请求从待发货/运送中推进到待作业状态</li>
 *   <li>签收时触发{@link SampleDomainEventPublisher#publishSampleSigned}领域事件</li>
 *   <li>记录异常物流状态（异常件、拒签等）的最新上下文信息</li>
 * </ul>
 *
 * <p>架构角色：寄样域 - 物流回调处理器，属于仓储层之上、面向外部推送的事件接收服务。</p>
 *
 * <p>业务领域：寄样管理 → 物流轨迹追踪 → 签收状态自动流转。</p>
 *
 * <p>访问控制：此服务由Webhook回调控制器调用，依赖签名验证保障调用合法性。</p>
 *
 * @see com.colonel.saas.controller.SampleController
 * @see com.colonel.saas.service.SampleStatusLogService
 * @see com.colonel.saas.domain.sample.event.SampleDomainEventPublisher
 */
@Slf4j
@Service
public class Kuaidi100LogisticsCallbackService {

    /** 物流轨迹节点时间解析格式：yyyy-MM-dd HH:mm:ss */
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 寄样状态：待发货（值=2） */
    private static final int STATUS_PENDING_SHIP = 2;

    /** 寄样状态：运送中（值=3） */
    private static final int STATUS_SHIPPING = 3;

    /** 寄样状态：待作业（值=5），签收后自动推进至此状态 */
    private static final int STATUS_PENDING_HOMEWORK = 5;

    /** 物流服务提供商标识：快递100 */
    private static final String PROVIDER = "KUAIDI100";

    /** 物流配置属性，包含快递100回调盐值等配置 */
    private final LogisticsProperties properties;

    /** 寄样请求数据访问层 */
    private final SampleRequestMapper sampleRequestMapper;

    /** 寄样物流轨迹数据访问层 */
    private final SampleLogisticsTraceMapper sampleLogisticsTraceMapper;

    /** 寄样状态变更日志服务，用于记录签收状态流转 */
    private final SampleStatusLogService sampleStatusLogService;

    /** 寄样领域事件发布器，用于发布签收事件 */
    private final SampleDomainEventPublisher sampleDomainEventPublisher;

    /** Jackson JSON序列化器 */
    private final ObjectMapper objectMapper;

    /**
     * 构造函数，通过依赖注入组装所有协作者。
     *
     * @param properties                物流配置属性（含快递100回调盐值）
     * @param sampleRequestMapper       寄样请求Mapper
     * @param sampleLogisticsTraceMapper 物流轨迹节点Mapper
     * @param sampleStatusLogService    寄样状态变更日志服务
     * @param sampleDomainEventPublisher 寄样领域事件发布器
     * @param objectMapper              JSON序列化器
     */
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

    /**
     * 处理快递100物流轨迹回调请求。
     *
     * <p>处理流程：</p>
     * <ol>
     *   <li>第一步：校验回调参数完整性（param和sign均不能为空）</li>
     *   <li>第二步：使用MD5+盐值方案验证签名，拒绝非法请求</li>
     *   <li>第三步：解析JSON报文，提取lastResult中快递公司编码和物流单号</li>
     *   <li>第四步：通过快递公司编码+物流单号匹配系统内寄样请求记录</li>
     *   <li>第五步：更新寄样请求的物流状态字段（提供者、回调时间、状态码、状态名等）</li>
     *   <li>第六步：将轨迹节点列表通过SHA-256去重后插入物流轨迹表</li>
     *   <li>第七步：若为异常状态，记录最新异常上下文；若为签收状态，自动推进寄样状态</li>
     *   <li>第八步：乐观锁更新寄样请求记录，返回成功确认</li>
     * </ol>
     *
     * @param param 快递100回调原始JSON参数字符串
     * @param sign  快递100回调签名，使用MD5(param + salt)生成
     * @return 回调确认响应，包含处理结果、返回码和消息
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
