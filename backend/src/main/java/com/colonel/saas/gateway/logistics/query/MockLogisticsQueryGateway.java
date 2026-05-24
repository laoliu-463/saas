package com.colonel.saas.gateway.logistics.query;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * local-mock / test 物流查询：按单号前缀返回不同状态，不请求真实外部 API。
 */
@Component
public class MockLogisticsQueryGateway implements LogisticsQueryGateway {

    @Override
    public LogisticsQueryResult query(String logisticsCompany, String trackingNo) {
        String company = StringUtils.hasText(logisticsCompany) ? logisticsCompany.trim() : "MOCK";
        String no = trackingNo == null ? "" : trackingNo.trim();
        if (!StringUtils.hasText(no)) {
            return LogisticsQueryResult.queryFailed(providerName(), company, no, "INVALID_PARAM", "物流单号不能为空");
        }
        LocalDateTime now = LocalDateTime.now();
        String upper = no.toUpperCase(Locale.ROOT);
        if (upper.startsWith("FAIL") || upper.contains("FAILED")) {
            return LogisticsQueryResult.queryFailed(providerName(), company, no, "MOCK_FAILED", "模拟查询失败");
        }
        if (upper.startsWith("TRANSIT") || upper.contains("IN_TRANSIT")) {
            return buildSuccess(company, no, LogisticsStatusCode.IN_TRANSIT, "运输中", false, null,
                    List.of(trace(now.minusHours(2), "快件已揽收，运输中", "演示城市")));
        }
        if (upper.startsWith("DELIVER") || upper.contains("DELIVERING")) {
            return buildSuccess(company, no, LogisticsStatusCode.DELIVERING, "派送中", false, null,
                    List.of(trace(now.minusHours(1), "快件派送中", "演示城市")));
        }
        if (upper.startsWith("REJECT") || upper.contains("REJECTED")) {
            return buildSuccess(company, no, LogisticsStatusCode.REJECTED, "拒收", false, null,
                    List.of(trace(now, "快件被拒收", "演示城市")));
        }
        if (upper.startsWith("SIGN") || upper.contains("SIGNED") || upper.startsWith("MOCK")) {
            return buildSuccess(company, no, LogisticsStatusCode.SIGNED, "已签收", true, now,
                    List.of(trace(now, "派件已签收[演示]", "演示城市")));
        }
        return buildSuccess(company, no, LogisticsStatusCode.IN_TRANSIT, "运输中", false, null,
                List.of(trace(now.minusHours(1), "快件运输中", "演示城市")));
    }

    @Override
    public boolean isSupported() {
        return true;
    }

    @Override
    public String providerName() {
        return "MOCK";
    }

    private LogisticsQueryResult buildSuccess(
            String company,
            String trackingNo,
            LogisticsStatusCode statusCode,
            String statusName,
            boolean signed,
            LocalDateTime signedAt,
            List<LogisticsQueryResult.LogisticsTraceItem> traces) {
        return LogisticsQueryResult.builder()
                .success(true)
                .provider(providerName())
                .trackingNo(trackingNo)
                .logisticsCompany(company)
                .statusCode(statusCode)
                .statusName(statusName)
                .signed(signed)
                .signedAt(signedAt)
                .traces(traces)
                .rawPayload(Map.of("mock", true, "provider", providerName()))
                .queriedAt(LocalDateTime.now())
                .build();
    }

    private LogisticsQueryResult.LogisticsTraceItem trace(LocalDateTime time, String content, String location) {
        return LogisticsQueryResult.LogisticsTraceItem.builder()
                .traceTime(time)
                .traceContent(content)
                .location(location)
                .build();
    }
}
