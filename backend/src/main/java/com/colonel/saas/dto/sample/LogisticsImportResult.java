package com.colonel.saas.dto.sample;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.UUID;

@Value
@Builder
public class LogisticsImportResult {
    int total;
    int successCount;
    int failedCount;
    List<LogisticsImportItemResult> items;

    @Value
    @Builder
    public static class LogisticsImportItemResult {
        int rowNo;
        UUID sampleRequestId;
        String sampleNo;
        boolean success;
        String message;
    }
}
