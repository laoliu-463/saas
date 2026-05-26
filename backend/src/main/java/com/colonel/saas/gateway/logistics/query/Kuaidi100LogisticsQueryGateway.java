package com.colonel.saas.gateway.logistics.query;

import com.colonel.saas.config.LogisticsProperties;
import com.colonel.saas.gateway.logistics.LogisticsGateway;
import com.colonel.saas.gateway.logistics.LogisticsTrackCommand;
import com.colonel.saas.gateway.logistics.kuaidi100.Kuaidi100LogisticsGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 快递100查询适配器。未配置凭证时返回 NOT_CONFIGURED，禁止伪造成功。
 */
@Slf4j
@Component
public class Kuaidi100LogisticsQueryGateway implements LogisticsQueryGateway {

    private static final Duration MIN_QUERY_INTERVAL = Duration.ofMinutes(30);

    private final LogisticsProperties properties;
    private final ObjectProvider<Kuaidi100LogisticsGateway> delegateProvider;
    private final ConcurrentMap<String, LocalDateTime> lastQueryAtByWaybill = new ConcurrentHashMap<>();

    public Kuaidi100LogisticsQueryGateway(
            LogisticsProperties properties,
            ObjectProvider<Kuaidi100LogisticsGateway> delegateProvider) {
        this.properties = properties;
        this.delegateProvider = delegateProvider;
    }

    @Override
    public LogisticsQueryResult query(String logisticsCompany, String trackingNo) {
        return query(LogisticsTrackCommand.builder()
                .companyCode(logisticsCompany)
                .trackingNo(trackingNo)
                .build());
    }

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

    @Override
    public boolean isSupported() {
        return isConfigured() && delegateProvider.getIfAvailable() != null;
    }

    @Override
    public String providerName() {
        return "KUAIDI100";
    }

    private boolean isConfigured() {
        LogisticsProperties.Kd100 kd100 = properties.getKd100();
        return kd100.isEnabled()
                && StringUtils.hasText(kd100.getCustomer())
                && StringUtils.hasText(kd100.getKey());
    }

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

    private LogisticsQueryResult throttleIfNeeded(LogisticsTrackCommand command) {
        String key = (command.getCompanyCode() + "::" + command.getTrackingNo()).toUpperCase();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lastQueryAt = lastQueryAtByWaybill.get(key);
        if (lastQueryAt != null && lastQueryAt.plus(MIN_QUERY_INTERVAL).isAfter(now)) {
            return LogisticsQueryResult.queryFailed(
                    providerName(),
                    command.getCompanyCode(),
                    command.getTrackingNo(),
                    "QUERY_THROTTLED",
                    "快递100要求同一运单查询间隔至少30分钟，请稍后再试");
        }
        lastQueryAtByWaybill.put(key, now);
        return null;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

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
