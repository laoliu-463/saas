package com.colonel.saas.gateway.logistics.mock;

import com.colonel.saas.gateway.logistics.LogisticsGateway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
@ConditionalOnProperty(prefix = "app.mock", name = "enabled", havingValue = "true")
public class MockLogisticsGateway implements LogisticsGateway {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Override
    public LogisticsResult createShipment(LogisticsCommand command) {
        LocalDateTime now = LocalDateTime.now();
        String suffix = String.format("%04d", Math.abs(command.sampleRequestId().hashCode()) % 10_000);
        String trackingNo = "MOCK-SF-" + now.format(DATE) + "-" + suffix;
        return new LogisticsResult(trackingNo, "MockExpress", "SHIPPING", now);
    }

    @Override
    public LogisticsStatusResult queryStatus(String trackingNo) {
        return new LogisticsStatusResult(
                trackingNo,
                "MockExpress",
                "DELIVERED",
                "mock logistics status ready",
                LocalDateTime.now()
        );
    }
}
