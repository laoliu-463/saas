package com.colonel.saas.dto.douyin;

import lombok.Builder;
import lombok.Data;

/**
 * 抖音快速寄样状态响应 DTO。
 * <p>
 * 返回抖音平台快速寄样接口的连接状态和可用性，用于前端展示快速寄样功能的健康状态。
 * 关联业务领域：抖音集成（Douyin）。
 * </p>
 */
@Data
@Builder
public class DouyinQuickSampleStatusResponse {
    /** 是否支持快速寄样功能 */
    private boolean supported;
    /** 当前连接状态 */
    private String status;
    /** 是否已通过真实 API 连接验证 */
    private boolean realConnected;
    /** 状态描述信息 */
    private String message;
    /** 是否启用降级兜底策略 */
    private boolean fallbackEnabled;
}
