package com.colonel.saas.dto.performance;

import lombok.Data;

/**
 * 业绩月度重算请求 DTO。
 * <p>
 * 用于触发指定月份的业绩数据重新计算，通常在归属规则变更或数据修正后使用。
 * 关联业务领域：业绩域（Performance）。
 * </p>
 */
@Data
public class PerformanceRecalculateMonthRequest {
    /** 待重算的月份（格式：yyyy-MM） */
    private String month;
    /** 重算原因说明（用于审计追溯） */
    private String reason;
}
