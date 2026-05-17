package com.colonel.saas.gateway.logistics.test;

import com.colonel.saas.gateway.logistics.LogisticsGateway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "app.test", name = "enabled", havingValue = "true")
public class TestLogisticsGateway implements LogisticsGateway {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Override
    public LogisticsResult createShipment(LogisticsCommand command) {
        LocalDateTime now = LocalDateTime.now();
        String suffix = String.format("%04d", Math.abs(command.sampleRequestId().hashCode()) % 10_000);
        String trackingNo = "TEST-SF-" + now.format(DATE) + "-" + suffix;
        return new LogisticsResult(trackingNo, "演示物流-顺丰模拟", "SHIPPING", now);
    }

    @Override
    public LogisticsStatusResult queryStatus(String trackingNo) {
        LogisticsTrackResult track = queryTrack("SF", trackingNo);
        return new LogisticsStatusResult(
                trackingNo,
                "演示物流-顺丰模拟",
                track.internalStatus(),
                track.reason() == null ? "演示物流状态已更新" : track.reason(),
                track.signedAt() == null ? LocalDateTime.now() : track.signedAt()
        );
    }

    @Override
    public LogisticsTrackResult queryTrack(String companyCode, String trackingNo) {
        LocalDateTime now = LocalDateTime.now();
        String normalizedCompany = companyCode == null || companyCode.isBlank() ? "SF" : companyCode;
        if (trackingNo != null && trackingNo.contains("FAILED")) {
            return new LogisticsTrackResult(
                    normalizedCompany,
                    trackingNo,
                    false,
                    "模拟查询失败",
                    null,
                    "FAILED",
                    false,
                    null,
                    List.of(),
                    Map.of("Success", false));
        }
        if (trackingNo != null && trackingNo.contains("EXCEPTION")) {
            return new LogisticsTrackResult(
                    normalizedCompany,
                    trackingNo,
                    true,
                    null,
                    "4",
                    "EXCEPTION",
                    false,
                    null,
                    List.of(new LogisticsTraceNode(now.minusHours(1), "快件运输异常，等待人工处理", null)),
                    Map.of("Success", true, "State", "4"));
        }
        if (trackingNo != null && trackingNo.contains("IN_TRANSIT")) {
            return new LogisticsTrackResult(
                    normalizedCompany,
                    trackingNo,
                    true,
                    null,
                    "2",
                    "IN_TRANSIT",
                    false,
                    null,
                    List.of(new LogisticsTraceNode(now.minusHours(2), "快件已揽收，正在运输中", null)),
                    Map.of("Success", true, "State", "2"));
        }
        if (trackingNo != null && trackingNo.contains("NO_TRACE")) {
            return new LogisticsTrackResult(
                    normalizedCompany,
                    trackingNo,
                    true,
                    "暂无轨迹信息",
                    null,
                    "NO_TRACE",
                    false,
                    null,
                    List.of(),
                    Map.of("Success", true));
        }
        return new LogisticsTrackResult(
                normalizedCompany,
                trackingNo,
                true,
                null,
                "3",
                "SIGNED",
                true,
                now,
                List.of(new LogisticsTraceNode(now, "派件已签收[演示城市]", null)),
                Map.of("Success", true, "State", "3"));
    }
}


