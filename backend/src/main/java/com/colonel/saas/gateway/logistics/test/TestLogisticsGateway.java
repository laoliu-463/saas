package com.colonel.saas.gateway.logistics.test;

import com.colonel.saas.gateway.logistics.LogisticsGateway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

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
        return new LogisticsStatusResult(
                trackingNo,
                "演示物流-顺丰模拟",
                "DELIVERED",
                "演示物流状态已更新",
                LocalDateTime.now()
        );
    }
}


