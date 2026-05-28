package com.colonel.saas.gateway.logistics.query;

import com.colonel.saas.config.LogisticsProperties;
import com.colonel.saas.gateway.logistics.LogisticsGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 快递鸟物流查询适配器。
 * <p>
 * 实现 {@link LogisticsQueryGateway} 接口，将快递鸟的 {@link LogisticsGateway} 结果
 * 转换为统一的 {@link LogisticsQueryResult}。
 * </p>
 *
 * <h3>安全策略</h3>
 * <ul>
 *   <li>未配置凭证（ebusinessId / apiKey）时，返回 NOT_CONFIGURED，禁止伪造成功</li>
 *   <li>查询异常时返回 FAILED 状态，不向上层抛出异常</li>
 * </ul>
 *
 * <h3>依赖关系</h3>
 * <p>
 * 委托 {@link LogisticsGateway}（即 {@link com.colonel.saas.gateway.logistics.kdniao.KdniaoLogisticsGateway}）
 * 执行实际的 API 调用，自身只做结果映射和错误兜底。
 * </p>
 */
@Slf4j
@Component
public class KuaidiNiaoLogisticsQueryGateway implements LogisticsQueryGateway {

    private final LogisticsProperties properties;
    /** 委托的真实快递鸟网关实现 */
    private final LogisticsGateway delegate;

    /**
     * 构造快递鸟查询适配器。
     *
     * @param properties 物流配置属性（包含快递鸟凭证 kdn.*）
     * @param delegate   快递鸟真实网关实现（由 Spring 条件注入）
     */
    public KuaidiNiaoLogisticsQueryGateway(LogisticsProperties properties, LogisticsGateway delegate) {
        this.properties = properties;
        this.delegate = delegate;
    }

    /**
     * 执行快递鸟物流轨迹查询。
     * <p>
     * 流程：凭证校验 -> 参数校验 -> 委托查询 -> 结果映射。
     * 查询失败时返回包含错误信息的结果对象，不向上抛出异常。
     * </p>
     */
    @Override
    public LogisticsQueryResult query(String logisticsCompany, String trackingNo) {
        if (!isConfigured()) {
            return LogisticsQueryResult.notConfigured(providerName(), logisticsCompany, trackingNo);
        }
        if (!StringUtils.hasText(trackingNo)) {
            return LogisticsQueryResult.queryFailed(providerName(), logisticsCompany, trackingNo,
                    "INVALID_PARAM", "物流单号不能为空");
        }
        String company = StringUtils.hasText(logisticsCompany) ? logisticsCompany.trim() : "AUTO";
        try {
            // 委托快递鸟真实网关执行 API 调用
            LogisticsGateway.LogisticsTrackResult track = delegate.queryTrack(company, trackingNo.trim());
            return mapTrack(track, company, trackingNo.trim());
        } catch (Exception ex) {
            log.warn("KuaidiNiao query failed trackingNo={}: {}", trackingNo, ex.getMessage());
            return LogisticsQueryResult.queryFailed(providerName(), company, trackingNo.trim(),
                    "QUERY_ERROR", ex.getMessage());
        }
    }

    /** 凭证已配置则认为可用 */
    @Override
    public boolean isSupported() {
        return isConfigured();
    }

    @Override
    public String providerName() {
        return "KUAIDINIAO";
    }

    /**
     * 检查快递鸟凭证是否已配置。
     * 需要 enabled=true 且 ebusinessId 和 apiKey 非空。
     */
    private boolean isConfigured() {
        LogisticsProperties.Kdn kdn = properties.getKdn();
        return kdn.isEnabled()
                && StringUtils.hasText(kdn.getEbusinessId())
                && StringUtils.hasText(kdn.getApiKey());
    }

    /**
     * 将快递鸟 {@link LogisticsGateway.LogisticsTrackResult} 映射为统一的 {@link LogisticsQueryResult}。
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
     * 将快递鸟内部状态字符串映射为统一状态码。
     * <p>
     * 映射规则：SIGNED -> SIGNED, IN_TRANSIT/NO_TRACE -> IN_TRANSIT,
     * DELIVERING -> DELIVERING, EXCEPTION/REJECTED -> REJECTED, FAILED -> FAILED。
     * </p>
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
