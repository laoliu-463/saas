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
 * 快递鸟查询适配器。未配置凭证时返回 NOT_CONFIGURED，禁止伪造成功。
 */
@Slf4j
@Component
public class KuaidiNiaoLogisticsQueryGateway implements LogisticsQueryGateway {

    private final LogisticsProperties properties;
    private final LogisticsGateway delegate;

    public KuaidiNiaoLogisticsQueryGateway(LogisticsProperties properties, LogisticsGateway delegate) {
        this.properties = properties;
        this.delegate = delegate;
    }

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
            LogisticsGateway.LogisticsTrackResult track = delegate.queryTrack(company, trackingNo.trim());
            return mapTrack(track, company, trackingNo.trim());
        } catch (Exception ex) {
            log.warn("KuaidiNiao query failed trackingNo={}: {}", trackingNo, ex.getMessage());
            return LogisticsQueryResult.queryFailed(providerName(), company, trackingNo.trim(),
                    "QUERY_ERROR", ex.getMessage());
        }
    }

    @Override
    public boolean isSupported() {
        return isConfigured();
    }

    @Override
    public String providerName() {
        return "KUAIDINIAO";
    }

    private boolean isConfigured() {
        LogisticsProperties.Kdn kdn = properties.getKdn();
        return kdn.isEnabled()
                && StringUtils.hasText(kdn.getEbusinessId())
                && StringUtils.hasText(kdn.getApiKey());
    }

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
