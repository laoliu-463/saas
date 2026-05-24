package com.colonel.saas.gateway.logistics.query;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Value
@Builder
public class LogisticsQueryResult {
    boolean success;
    String provider;
    String trackingNo;
    String logisticsCompany;
    LogisticsStatusCode statusCode;
    String statusName;
    boolean signed;
    LocalDateTime signedAt;
    List<LogisticsTraceItem> traces;
    String errorCode;
    String errorMessage;
    Map<String, Object> rawPayload;
    LocalDateTime queriedAt;

    @Value
    @Builder
    public static class LogisticsTraceItem {
        LocalDateTime traceTime;
        String traceContent;
        String location;
    }

    public static LogisticsQueryResult notConfigured(String provider, String logisticsCompany, String trackingNo) {
        return LogisticsQueryResult.builder()
                .success(false)
                .provider(provider)
                .trackingNo(trackingNo)
                .logisticsCompany(logisticsCompany)
                .statusCode(LogisticsStatusCode.NOT_CONFIGURED)
                .statusName("未配置")
                .signed(false)
                .traces(List.of())
                .errorCode("NOT_CONFIGURED")
                .errorMessage("物流查询 provider 未配置或凭证缺失")
                .rawPayload(Map.of())
                .queriedAt(LocalDateTime.now())
                .build();
    }

    public static LogisticsQueryResult queryFailed(
            String provider,
            String logisticsCompany,
            String trackingNo,
            String errorCode,
            String errorMessage) {
        return LogisticsQueryResult.builder()
                .success(false)
                .provider(provider)
                .trackingNo(trackingNo)
                .logisticsCompany(logisticsCompany)
                .statusCode(LogisticsStatusCode.ERROR)
                .statusName("查询失败")
                .signed(false)
                .traces(List.of())
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .rawPayload(Map.of())
                .queriedAt(LocalDateTime.now())
                .build();
    }
}
