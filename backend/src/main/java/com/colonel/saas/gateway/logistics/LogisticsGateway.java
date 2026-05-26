package com.colonel.saas.gateway.logistics;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface LogisticsGateway {

    LogisticsResult createShipment(LogisticsCommand command);

    default LogisticsSubscribeResult subscribeTrack(LogisticsSubscribeCommand command) {
        throw new UnsupportedOperationException("当前物流网关不支持轨迹订阅");
    }

    /**
     * @deprecated 快递鸟等物流服务商需要快递公司编码，请使用 {@link #queryTrack(String, String)}。
     */
    @Deprecated
    LogisticsStatusResult queryStatus(String trackingNo);

    LogisticsTrackResult queryTrack(String companyCode, String trackingNo);

    default LogisticsTrackResult queryTrack(LogisticsTrackCommand command) {
        if (command == null) {
            return queryTrack(null, null);
        }
        return queryTrack(command.getCompanyCode(), command.getTrackingNo());
    }

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
