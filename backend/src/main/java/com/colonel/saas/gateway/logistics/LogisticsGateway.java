package com.colonel.saas.gateway.logistics;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface LogisticsGateway {

    LogisticsResult createShipment(LogisticsCommand command);

    /**
     * @deprecated 快递鸟等物流服务商需要快递公司编码，请使用 {@link #queryTrack(String, String)}。
     */
    @Deprecated
    LogisticsStatusResult queryStatus(String trackingNo);

    LogisticsTrackResult queryTrack(String companyCode, String trackingNo);

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

    record LogisticsTrackResult(
            String companyCode,
            String trackingNo,
            boolean success,
            String reason,
            String externalState,
            String internalStatus,
            boolean signed,
            LocalDateTime signedAt,
            List<LogisticsTraceNode> traces,
            Map<String, Object> rawResponse) {
    }

    record LogisticsTraceNode(
            LocalDateTime acceptTime,
            String acceptStation,
            String remark) {
    }
}
