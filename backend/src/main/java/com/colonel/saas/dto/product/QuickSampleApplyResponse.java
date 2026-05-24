package com.colonel.saas.dto.product;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
public class QuickSampleApplyResponse {

    private boolean success;
    private boolean externalEnabled;
    private boolean externalSupported;
    private boolean externalApplied;
    private String gatewayStatus;
    private String fallbackType;
    private String message;
    private int successCount;
    private int failureCount;
    private List<QuickSampleApplyItemResult> items = new ArrayList<>();

  /** @deprecated use items */
    @Deprecated
    public List<QuickSampleApplyItemResult> getResults() {
        return items;
    }

    @Deprecated
    public void setResults(List<QuickSampleApplyItemResult> results) {
        this.items = results;
    }

    @Data
    public static class QuickSampleApplyItemResult {
        private String talentId;
        private boolean success;
        private UUID sampleRequestId;
        private boolean externalApplied;
        private String externalApplyId;
        private boolean fallback;
        private String gatewayStatus;
        private String fallbackType;
        private String message;
    }
}
