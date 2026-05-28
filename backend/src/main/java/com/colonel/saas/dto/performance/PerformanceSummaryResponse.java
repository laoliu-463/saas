package com.colonel.saas.dto.performance;

import lombok.Data;

/**
 * 业绩汇总响应 DTO。
 * <p>
 * 返回业绩汇总数据，包含预估轨道和生效轨道两条汇总维度的统计数据。
 * 关联业务领域：业绩域（Performance）。
 * </p>
 */
@Data
public class PerformanceSummaryResponse {
    /** 预估轨道汇总（基于预估值计算的统计） */
    private PerformanceTrackSummaryDTO estimate;
    /** 生效轨道汇总（基于实际结算值计算的统计） */
    private PerformanceTrackSummaryDTO effective;
}
