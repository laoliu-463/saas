package com.colonel.saas.dto.product;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 快速寄样申请响应 DTO。
 * <p>
 * 返回快速寄样申请的执行结果，包含整体状态、外部平台对接情况、成功/失败计数
 * 以及每条达人的申请结果明细。
 * 关联业务领域：商品域（Product）、寄样域（Sample）。
 * </p>
 */
@Data
public class QuickSampleApplyResponse {

    /** 整体是否成功 */
    private boolean success;
    /** 外部平台（如抖音）寄样接口是否已启用 */
    private boolean externalEnabled;
    /** 外部平台寄样接口是否支持 */
    private boolean externalSupported;
    /** 是否已成功调用外部平台接口 */
    private boolean externalApplied;
    /** 外部平台网关状态 */
    private String gatewayStatus;
    /** 降级兜底策略类型 */
    private String fallbackType;
    /** 结果描述信息 */
    private String message;
    /** 成功申请的达人数量 */
    private int successCount;
    /** 申请失败的达人数量 */
    private int failureCount;
    /** 每条达人的申请结果明细列表 */
    private List<QuickSampleApplyItemResult> items = new ArrayList<>();

    /**
     * 快速寄样申请单项结果。
     */
    @Data
    public static class QuickSampleApplyItemResult {
        /** 达人 ID */
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
        /** 结果描述信息 */
        private String message;
    }
}
