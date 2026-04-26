package com.colonel.saas.gateway.logistics;

import java.time.LocalDateTime;
import java.util.UUID;

public interface LogisticsGateway {

    LogisticsResult createShipment(LogisticsCommand command);

    LogisticsStatusResult queryStatus(String trackingNo);

    record LogisticsCommand(
            UUID sampleRequestId,
            String productId,
            String recipientName,
            String recipientPhone,
            String recipientAddress) {
    }

    record LogisticsResult(
            String trackingNo,
            String company,
            String status,
            LocalDateTime shipTime) {
    }

    record LogisticsStatusResult(
            String trackingNo,
            String company,
            String status,
            String message,
            LocalDateTime updateTime) {
    }
}
