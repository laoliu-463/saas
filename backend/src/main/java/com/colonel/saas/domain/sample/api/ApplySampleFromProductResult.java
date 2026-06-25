package com.colonel.saas.domain.sample.api;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 商品域快速寄样结果 — 由 {@link SampleApplicationPort} 返回给商品域。
 * <p>
 * 包含批量申请的汇总信息和每个达人的明细结果。
 * 商品域据此构建前端响应 DTO（{@code QuickSampleApplyResponse}），
 * 保持前端接口路径和响应结构不变。
 * </p>
 */
public class ApplySampleFromProductResult {

    /** 整体是否成功 */
    private boolean success;
    /** 成功申请的达人数量 */
    private int successCount;
    /** 申请失败的达人数量 */
    private int failureCount;
    /** 每条达人的申请结果明细列表 */
    private final List<TalentResult> items = new ArrayList<>();

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public int getSuccessCount() { return successCount; }
    public void setSuccessCount(int successCount) { this.successCount = successCount; }
    public int getFailureCount() { return failureCount; }
    public void setFailureCount(int failureCount) { this.failureCount = failureCount; }
    public List<TalentResult> getItems() { return items; }

    /**
     * 单个达人的寄样申请结果。
     */
    public static class TalentResult {
        /** 达人外部 ID（抖音 UID） */
        private String talentId;
        /** 该达人申请是否成功 */
        private boolean success;
        /** 创建的寄样申请 ID */
        private UUID sampleRequestId;
        /** 是否已调用外部平台接口 */
        private boolean externalApplied;
        /** 外部平台申请 ID */
        private String externalApplyId;
        /** 是否使用了降级兜底策略 */
        private boolean fallback;
        /** 外部平台网关状态 */
        private String gatewayStatus;
        /** 降级兜底策略类型 */
        private String fallbackType;
        /** 结果描述消息 */
        private String message;

        public String getTalentId() { return talentId; }
        public void setTalentId(String talentId) { this.talentId = talentId; }
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public UUID getSampleRequestId() { return sampleRequestId; }
        public void setSampleRequestId(UUID sampleRequestId) { this.sampleRequestId = sampleRequestId; }
        public boolean isExternalApplied() { return externalApplied; }
        public void setExternalApplied(boolean externalApplied) { this.externalApplied = externalApplied; }
        public String getExternalApplyId() { return externalApplyId; }
        public void setExternalApplyId(String externalApplyId) { this.externalApplyId = externalApplyId; }
        public boolean isFallback() { return fallback; }
        public void setFallback(boolean fallback) { this.fallback = fallback; }
        public String getGatewayStatus() { return gatewayStatus; }
        public void setGatewayStatus(String gatewayStatus) { this.gatewayStatus = gatewayStatus; }
        public String getFallbackType() { return fallbackType; }
        public void setFallbackType(String fallbackType) { this.fallbackType = fallbackType; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }
}
