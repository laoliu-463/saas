package com.colonel.saas.dto.performance;

import lombok.Data;

/**
 * 业绩月度重算响应 DTO。
 * <p>
 * 返回月度业绩重算任务的执行结果，包含任务 ID、状态以及扫描/更新/跳过的记录统计。
 * 关联业务领域：业绩域（Performance）。
 * </p>
 */
@Data
public class PerformanceRecalculateMonthResponse {
    /** 重算任务 ID */
    private String jobId;
    /** 任务状态（如 running、completed、failed） */
    private String status;
    /** 重算的月份（格式：yyyy-MM） */
    private String month;
    /** 扫描的记录总数 */
    private int scanned;
    /** 成功插入/更新的记录数 */
    private int upserted;
    /** 跳过的已结算记录数 */
    private int skippedSettled;
}
