package com.colonel.saas.dto.sample;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.UUID;

/**
 * 物流导入结果 DTO。
 * <p>
 * 返回物流信息批量导入操作的汇总统计及每条记录的处理结果。
 * 关联业务领域：寄样域（Sample）。
 * </p>
 */
@Value
@Builder
public class LogisticsImportResult {
    /** 总导入条数 */
    int total;
    /** 成功导入条数 */
    int successCount;
    /** 失败条数 */
    int failedCount;
    /** 每条记录的详细处理结果 */
    List<LogisticsImportItemResult> items;

    /**
     * 物流导入单项结果。
     */
    @Value
    @Builder
    public static class LogisticsImportItemResult {
        /** 原始行号 */
        int rowNo;
        /** 关联的寄样申请 ID */
        UUID sampleRequestId;
        /** 寄样单号 */
        String sampleNo;
        /** 该条记录是否导入成功 */
        boolean success;
        /** 处理结果说明信息 */
        String message;
    }
}
