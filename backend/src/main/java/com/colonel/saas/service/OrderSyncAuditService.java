package com.colonel.saas.service;

import com.colonel.saas.common.web.RequestIdContext;
import com.colonel.saas.entity.OperationLog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** 记录订单同步批次摘要，避免审计故障中断订单同步主链。 */
@Slf4j
@Service
public class OrderSyncAuditService {

    private final OperationLogService operationLogService;

    public OrderSyncAuditService(OperationLogService operationLogService) {
        this.operationLogService = operationLogService;
    }

    /** 单次批量同步只记录一条汇总日志，且仅在有数据变化或失败时写入。 */
    void recordSyncSummary(
            String logName,
            String api,
            String mode,
            String timeType,
            OrderSyncService.SyncResult result) {
        recordSyncSummary(logName, api, mode, timeType, result, null, null);
    }

    void recordSyncSummary(
            String logName,
            String api,
            String mode,
            String timeType,
            OrderSyncService.SyncResult result,
            String terminalErrorCode,
            String terminalErrorMessage) {
        if (result == null || result.locked()
                || (!StringUtils.hasText(terminalErrorCode)
                && result.created() == 0 && result.updated() == 0 && result.failed() == 0)) {
            return;
        }
        OperationLog audit = new OperationLog();
        audit.setModule("订单同步");
        audit.setAction("批量同步汇总");
        audit.setRequestMethod("SYSTEM");
        audit.setTargetType("order-sync-batch");
        audit.setTargetId(mode + ":" + result.startTime() + ":" + result.endTime());
        audit.setTargetName(logName);
        audit.setContent("订单批量同步汇总");
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("api", api);
        summary.put("mode", mode);
        summary.put("timeType", timeType);
        summary.put("pages", result.pages());
        summary.put("totalFetched", result.totalFetched());
        summary.put("uniqueOrders", result.uniqueOrders());
        summary.put("created", result.created());
        summary.put("updated", result.updated());
        summary.put("attributed", result.attributed());
        summary.put("unattributed", result.unattributed());
        summary.put("failed", result.failed());
        summary.put("stopReason", result.stopReason());
        audit.setResponseBody(summary);
        String traceId = RequestIdContext.current();
        audit.setTraceId(StringUtils.hasText(traceId) ? traceId : "order-sync-" + UUID.randomUUID());
        if (StringUtils.hasText(terminalErrorCode)) {
            audit.setErrorCode(terminalErrorCode);
            audit.setErrorMessage(StringUtils.hasText(terminalErrorMessage)
                    ? terminalErrorMessage
                    : "订单同步异常终止");
        } else if (result.failed() > 0) {
            audit.setErrorCode("ORDER_SYNC_PARTIAL_FAILURE");
            audit.setErrorMessage(result.failed() + " 条订单处理失败");
        }
        try {
            operationLogService.record(audit);
        } catch (RuntimeException ex) {
            log.warn("Order sync summary audit failed, mode={}, range=[{}, {}]",
                    mode, result.startTime(), result.endTime(), ex);
        }
    }
}
