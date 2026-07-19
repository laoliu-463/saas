package com.colonel.saas.gateway.logistics.query;

import com.colonel.saas.config.LogisticsProperties;
import com.colonel.saas.gateway.logistics.LogisticsGateway;
import com.colonel.saas.gateway.logistics.LogisticsTrackCommand;
import com.colonel.saas.gateway.logistics.kuaidi100.Kuaidi100LogisticsGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * 快递100 V2 统一查询适配器。
 *
 * <p>功能描述：包装 {@link Kuaidi100LogisticsGateway}（V1 网关），
 * 将其返回的 {@link LogisticsGateway.LogisticsTrackResult} 转换为
 * 统一的 {@link LogisticsQueryResult}，并增加以下能力：</p>
 *
 * <ul>
 *   <li>配置检查：未配置快递100凭证时返回 {@code NOT_CONFIGURED}，禁止伪造成功</li>
 *   <li>参数校验：快递公司编码、物流单号非空校验，顺丰/中通手机号强制校验</li>
 *   <li>频率节流：同一运单 31 分钟内禁止重复查询（Redis 跨实例状态）</li>
 *   <li>状态映射：将 V1 内部状态（IN_TRANSIT / SIGNED / EXCEPTION 等）映射为 V2 统一状态码</li>
 *   <li>轨迹转换：将 V1 {@link LogisticsGateway.LogisticsTraceNode} 转换为 V2 {@link LogisticsQueryResult.LogisticsTraceItem}</li>
 * </ul>
 *
 * <p>在架构中的角色：寄样域 / 物流适配层 / V2 查询网关，实现 {@link LogisticsQueryGateway} 统一接口。
 * 通过 {@link ObjectProvider} 延迟获取 {@link Kuaidi100LogisticsGateway}，
 * 在快递100未启用时优雅降级。</p>
 *
 * @see LogisticsQueryGateway           V2 统一查询网关接口
 * @see Kuaidi100LogisticsGateway       被包装的 V1 快递100网关
 * @see LogisticsQueryResult            V2 统一查询结果
 * @see LogisticsProperties.Kd100       快递100配置项
 */
@Slf4j
@Component
public class Kuaidi100LogisticsQueryGateway implements LogisticsQueryGateway {

    /**
     * 同一运单最小查询间隔（31 分钟），为上游 30 分钟限制保留调度抖动余量。
     */
    private static final Duration MIN_QUERY_INTERVAL = Duration.ofMinutes(31);
    private static final String THROTTLE_KEY_PREFIX = "logistics:kuaidi100:query-throttle:";

    /** 物流配置属性（用于检查快递100是否已配置） */
    private final LogisticsProperties properties;

    /**
     * V1 快递100网关的延迟提供器。
     * <p>使用 {@link ObjectProvider} 而非直接注入，以支持快递100未启用时的优雅降级。</p>
     */
    private final ObjectProvider<Kuaidi100LogisticsGateway> delegateProvider;

    /** Redis 跨实例限频状态；不可用时关闭查询，避免突破快递100限制。 */
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 构造函数，通过 Spring 依赖注入获取所有必需组件。
     *
     * @param properties       物流配置属性（用于检查快递100是否已配置）
     * @param delegateProvider V1 快递100网关的延迟提供器（快递100未启用时为 null）
     */
    public Kuaidi100LogisticsQueryGateway(
            LogisticsProperties properties,
            ObjectProvider<Kuaidi100LogisticsGateway> delegateProvider,
            RedisTemplate<String, Object> redisTemplate) {
        this.properties = properties;
        this.delegateProvider = delegateProvider;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 查询物流轨迹（简单参数版，委托给命令对象版）。
     *
     * @param logisticsCompany 快递公司编码
     * @param trackingNo       物流运单号
     * @return 统一格式的物流查询结果
     */
    @Override
    public LogisticsQueryResult query(String logisticsCompany, String trackingNo) {
        return query(LogisticsTrackCommand.builder()
                .companyCode(logisticsCompany)
                .trackingNo(trackingNo)
                .build());
    }

    /**
     * 查询物流轨迹（核心方法，接受命令对象）。
     * <p>
     * 完整的查询流程如下：
     * </p>
     * <ol>
     *   <li>第一步：检查快递100是否已配置（customer + key），未配置返回 NOT_CONFIGURED</li>
     *   <li>第二步：校验物流单号非空</li>
     *   <li>第三步：校验快递公司编码非空且不为 "AUTO"</li>
     *   <li>第四步：校验手机号（顺丰/中通强制要求）</li>
     *   <li>第五步：获取 V1 网关实例，不可用时返回 NOT_CONFIGURED</li>
     *   <li>第六步：标准化命令参数（trim、默认值等）</li>
     *   <li>第七步：频率节流检查（同一运单 31 分钟内禁止重复查询）</li>
     *   <li>第八步：委托 V1 网关查询轨迹</li>
     *   <li>第九步：调用 {@link #mapTrack} 将 V1 结果转换为 V2 统一格式</li>
     * </ol>
     *
     * @param command 物流查询命令对象
     * @return 统一格式的物流查询结果
     */
    @Override
    public LogisticsQueryResult query(LogisticsTrackCommand command) {
        String logisticsCompany = command == null ? null : command.getCompanyCode();
        String trackingNo = command == null ? null : command.getTrackingNo();
        if (!isConfigured()) {
            return LogisticsQueryResult.notConfigured(providerName(), logisticsCompany, trackingNo);
        }
        if (!StringUtils.hasText(trackingNo)) {
            return LogisticsQueryResult.queryFailed(providerName(), logisticsCompany, trackingNo,
                    "INVALID_PARAM", "物流单号不能为空");
        }
        if (!StringUtils.hasText(logisticsCompany) || "AUTO".equalsIgnoreCase(logisticsCompany.trim())) {
            return LogisticsQueryResult.queryFailed(providerName(), logisticsCompany, trackingNo.trim(),
                    "INVALID_PARAM", "快递公司编码不能为空");
        }
        if (requiresPhone(logisticsCompany) && (command == null || !StringUtils.hasText(command.getPhone()))) {
            return LogisticsQueryResult.queryFailed(providerName(), logisticsCompany.trim(), trackingNo.trim(),
                    "INVALID_PARAM", "顺丰/中通快递100查询需要收件人或寄件人手机号");
        }
        Kuaidi100LogisticsGateway delegate = delegateProvider.getIfAvailable();
        if (delegate == null) {
            return LogisticsQueryResult.notConfigured(providerName(), logisticsCompany.trim(), trackingNo.trim());
        }
        LogisticsTrackCommand normalized = normalizeCommand(command);
        LogisticsQueryResult throttled = throttleIfNeeded(normalized);
        if (throttled != null) {
            return throttled;
        }
        try {
            LogisticsGateway.LogisticsTrackResult track = delegate.queryTrack(normalized);
            return mapTrack(track, normalized.getCompanyCode(), normalized.getTrackingNo());
        } catch (Exception ex) {
            log.warn("Kuaidi100 query failed trackingNo={}: {}", trackingNo, ex.getMessage());
            return LogisticsQueryResult.queryFailed(providerName(), normalized.getCompanyCode(), normalized.getTrackingNo(),
                    "QUERY_ERROR", ex.getMessage());
        }
    }

    /**
     * 判断当前网关是否可用。
     * <p>需同时满足：快递100已配置（customer + key）且 V1 网关 Bean 可用。</p>
     *
     * @return true 表示可用，false 表示不可用
     */
    @Override
    public boolean isSupported() {
        return isConfigured() && delegateProvider.getIfAvailable() != null;
    }

    /**
     * 返回物流服务商名称标识。
     *
     * @return 固定返回 "KUAIDI100"
     */
    @Override
    public String providerName() {
        return "KUAIDI100";
    }

    /**
     * 检查快递100是否已配置必要凭证。
     * <p>需同时满足：enabled=true 且 customer 和 key 均非空。</p>
     *
     * @return true 表示已配置，false 表示未配置
     */
    private boolean isConfigured() {
        LogisticsProperties.Kd100 kd100 = properties.getKd100();
        return kd100.isEnabled()
                && StringUtils.hasText(kd100.getCustomer())
                && StringUtils.hasText(kd100.getKey());
    }

    /**
     * 标准化物流查询命令参数。
     * <p>对命令对象中的所有字段执行以下处理：</p>
     * <ol>
     *   <li>快递公司编码 {@code companyCode}：去除首尾空白</li>
     *   <li>物流单号 {@code trackingNo}：去除首尾空白</li>
     *   <li>手机号 {@code phone}：调用 {@link #trimToNull}，空白字符串转为 null</li>
     *   <li>发件地 {@code from}：调用 {@link #trimToNull}，空白字符串转为 null</li>
     *   <li>收件地 {@code to}：调用 {@link #trimToNull}，空白字符串转为 null</li>
     *   <li>结果版本 {@code resultV2}：若有值则 trim，若为空则默认为 "4"（快递100 V2 格式）</li>
     * </ol>
     *
     * @param command 原始物流查询命令对象（不可为 null）
     * @return 标准化后的新命令对象（与输入对象独立，不修改原对象）
     */
    private LogisticsTrackCommand normalizeCommand(LogisticsTrackCommand command) {
        return LogisticsTrackCommand.builder()
                .companyCode(command.getCompanyCode().trim())
                .trackingNo(command.getTrackingNo().trim())
                .phone(trimToNull(command.getPhone()))
                .from(trimToNull(command.getFrom()))
                .to(trimToNull(command.getTo()))
                .resultV2(StringUtils.hasText(command.getResultV2()) ? command.getResultV2().trim() : "4")
                .build();
    }

    /**
     * 频率节流检查：使用 Redis SET NX EX 保证所有实例共享同一 31 分钟窗口。
     * Redis 不可用时失败关闭，宁可跳过本轮，也不绕过上游限频。
     *
     * @param command 已标准化的物流查询命令对象
     * @return 若被节流返回 {@link LogisticsQueryResult}（错误），否则返回 {@code null} 表示可继续查询
     */
    private LogisticsQueryResult throttleIfNeeded(LogisticsTrackCommand command) {
        String identity = (command.getCompanyCode() + "::" + command.getTrackingNo()).toUpperCase();
        String key = THROTTLE_KEY_PREFIX + sha256(identity);
        try {
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                    key, Long.toString(System.currentTimeMillis()), MIN_QUERY_INTERVAL);
            if (Boolean.TRUE.equals(acquired)) {
                return null;
            }
            if (acquired == null) {
                throw new IllegalStateException("Redis SETNX returned null");
            }
            return LogisticsQueryResult.queryFailed(
                    providerName(),
                    command.getCompanyCode(),
                    command.getTrackingNo(),
                    "QUERY_THROTTLED",
                    "快递100同一运单查询安全间隔至少31分钟，本次已跳过");
        } catch (RuntimeException ex) {
            log.error("Kuaidi100 throttle state unavailable, key={}", key, ex);
            return LogisticsQueryResult.queryFailed(
                    providerName(),
                    command.getCompanyCode(),
                    command.getTrackingNo(),
                    "THROTTLE_STATE_UNAVAILABLE",
                    "物流限频状态不可用，本次查询已阻止");
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    /**
     * 将字符串去除首尾空白，若结果为空白则返回 {@code null}。
     * <p>用于 {@link #normalizeCommand} 方法中处理可选参数字段（手机号、发件地、收件地）。</p>
     *
     * @param value 原始字符串（可为 null）
     * @return trim 后的字符串，若为 null 或纯空白则返回 {@code null}
     */
    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /**
     * 判断指定快递公司是否强制要求提供手机号。
     * <p>快递100 API 对以下快递公司强制要求收件人或寄件人手机号：</p>
     * <ul>
     *   <li>顺丰快递：编码 "SF" 或 "SHUNFENG"</li>
     *   <li>中通快递：编码 "ZTO" 或 "ZHONGTONG"</li>
     * </ul>
     * <p>编码比较忽略大小写。若快递公司编码为空，返回 {@code false}。</p>
     *
     * @param companyCode 快递公司编码
     * @return {@code true} 表示需要手机号，{@code false} 表示不需要
     */
    private boolean requiresPhone(String companyCode) {
        if (!StringUtils.hasText(companyCode)) {
            return false;
        }
        String normalized = companyCode.trim().toUpperCase();
        return "SF".equals(normalized)
                || "SHUNFENG".equals(normalized)
                || "ZTO".equals(normalized)
                || "ZHONGTONG".equals(normalized);
    }

    /**
     * 将 V1 快递100网关返回的物流轨迹结果转换为 V2 统一查询结果。
     * <p>转换流程：</p>
     * <ol>
     *   <li>第一步：判断 V1 结果是否为 null 或查询失败，若是则返回 {@code UPSTREAM_FAILED} 错误</li>
     *   <li>第二步：调用 {@link #mapStatus} 将 V1 内部状态映射为 V2 {@link LogisticsStatusCode} 枚举</li>
     *   <li>第三步：遍历 V1 轨迹节点列表 {@link LogisticsGateway.LogisticsTraceNode}，
     *       逐条转换为 V2 {@link LogisticsQueryResult.LogisticsTraceItem}，
     *       字段映射：acceptTime→traceTime, acceptStation→traceContent, remark→location</li>
     *   <li>第四步：组装 V2 {@link LogisticsQueryResult}，包含供应商名、状态码、签收信息、轨迹列表、原始响应等</li>
     * </ol>
     *
     * @param track            V1 快递100网关返回的物流轨迹结果（可能为 null）
     * @param logisticsCompany 请求时的快递公司编码（V1 结果中可能为空，此时使用此值）
     * @param trackingNo       请求时的物流运单号
     * @return V2 统一格式的物流查询结果
     */
    private LogisticsQueryResult mapTrack(
            LogisticsGateway.LogisticsTrackResult track,
            String logisticsCompany,
            String trackingNo) {
        if (track == null || !track.success()) {
            return LogisticsQueryResult.queryFailed(
                    providerName(),
                    logisticsCompany,
                    trackingNo,
                    "UPSTREAM_FAILED",
                    track == null ? "空响应" : track.reason());
        }
        LogisticsStatusCode statusCode = mapStatus(track.internalStatus());
        List<LogisticsQueryResult.LogisticsTraceItem> traces = new ArrayList<>();
        if (track.traces() != null) {
            for (LogisticsGateway.LogisticsTraceNode node : track.traces()) {
                traces.add(LogisticsQueryResult.LogisticsTraceItem.builder()
                        .traceTime(node.acceptTime())
                        .traceContent(node.acceptStation())
                        .location(node.remark())
                        .build());
            }
        }
        return LogisticsQueryResult.builder()
                .success(true)
                .provider(providerName())
                .trackingNo(trackingNo)
                .logisticsCompany(StringUtils.hasText(track.companyCode()) ? track.companyCode() : logisticsCompany)
                .statusCode(statusCode)
                .statusName(statusCode.name())
                .signed(track.signed())
                .signedAt(track.signedAt())
                .traces(traces)
                .rawPayload(track.rawResponse() == null ? Map.of() : track.rawResponse())
                .queriedAt(LocalDateTime.now())
                .build();
    }

    /**
     * 将 V1 内部状态字符串映射为 V2 统一状态码枚举。
     * <p>使用 Java switch 表达式（箭头语法）进行映射，映射规则如下：</p>
     * <ul>
     *   <li>"SIGNED" → {@link LogisticsStatusCode#SIGNED}（已签收）</li>
     *   <li>"IN_TRANSIT" / "NO_TRACE" → {@link LogisticsStatusCode#IN_TRANSIT}（运输中 / 无轨迹）</li>
     *   <li>"DELIVERING" → {@link LogisticsStatusCode#DELIVERING}（派件中）</li>
     *   <li>"EXCEPTION" / "REJECTED" → {@link LogisticsStatusCode#REJECTED}（异常 / 拒收）</li>
     *   <li>"FAILED" → {@link LogisticsStatusCode#FAILED}（失败）</li>
     *   <li>"NOT_CONFIGURED" → {@link LogisticsStatusCode#NOT_CONFIGURED}（未配置）</li>
     *   <li>其他（含 null / 空字符串） → {@link LogisticsStatusCode#UNKNOWN}（未知）</li>
     * </ul>
     *
     * @param internalStatus V1 内部状态字符串（可为 null 或空）
     * @return V2 统一状态码枚举值，未匹配时返回 {@link LogisticsStatusCode#UNKNOWN}
     */
    private LogisticsStatusCode mapStatus(String internalStatus) {
        if (!StringUtils.hasText(internalStatus)) {
            return LogisticsStatusCode.UNKNOWN;
        }
        return switch (internalStatus.toUpperCase()) {
            case "SIGNED" -> LogisticsStatusCode.SIGNED;
            case "IN_TRANSIT", "NO_TRACE" -> LogisticsStatusCode.IN_TRANSIT;
            case "DELIVERING" -> LogisticsStatusCode.DELIVERING;
            case "EXCEPTION", "REJECTED" -> LogisticsStatusCode.REJECTED;
            case "FAILED" -> LogisticsStatusCode.FAILED;
            case "NOT_CONFIGURED" -> LogisticsStatusCode.NOT_CONFIGURED;
            default -> LogisticsStatusCode.UNKNOWN;
        };
    }
}
