package com.colonel.saas.domain.product.port;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 商品域快速寄样委派结果 — 由 {@link ProductSampleApplicationPort} 返回给商品域。
 */
public class QuickSampleApplyPortResult {

    private boolean success;
    private int successCount;
    private int failureCount;
    private final List<TalentResult> items = new ArrayList<>();

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public int getSuccessCount() {
        return successCount;
    }

    public void setSuccessCount(int successCount) {
        this.successCount = successCount;
    }

    public int getFailureCount() {
        return failureCount;
    }

    public void setFailureCount(int failureCount) {
        this.failureCount = failureCount;
    }

    public List<TalentResult> getItems() {
        return items;
    }

    public static class TalentResult {
        private String talentId;
        private boolean success;
        private UUID sampleRequestId;
        private boolean externalApplied;
        private String externalApplyId;
        private boolean fallback;
        private String gatewayStatus;
        private String fallbackType;
        private String message;

        public String getTalentId() {
            return talentId;
        }

        public void setTalentId(String talentId) {
            this.talentId = talentId;
        }

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public UUID getSampleRequestId() {
            return sampleRequestId;
        }

        public void setSampleRequestId(UUID sampleRequestId) {
            this.sampleRequestId = sampleRequestId;
        }

        public boolean isExternalApplied() {
            return externalApplied;
        }

        public void setExternalApplied(boolean externalApplied) {
            this.externalApplied = externalApplied;
        }

        public String getExternalApplyId() {
            return externalApplyId;
        }

        public void setExternalApplyId(String externalApplyId) {
            this.externalApplyId = externalApplyId;
        }

        public boolean isFallback() {
            return fallback;
        }

        public void setFallback(boolean fallback) {
            this.fallback = fallback;
        }

        public String getGatewayStatus() {
            return gatewayStatus;
        }

        public void setGatewayStatus(String gatewayStatus) {
            this.gatewayStatus = gatewayStatus;
        }

        public String getFallbackType() {
            return fallbackType;
        }

        public void setFallbackType(String fallbackType) {
            this.fallbackType = fallbackType;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}
