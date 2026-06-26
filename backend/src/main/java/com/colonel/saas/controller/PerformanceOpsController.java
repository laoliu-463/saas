package com.colonel.saas.controller;

import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.common.base.BaseController;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.config.OrderDerivedCacheKeys;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.domain.order.facade.OrderReadFacade;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.service.CommissionService;
import com.colonel.saas.service.OperationLogService;
import com.colonel.saas.service.PerformanceBackfillService;
import com.colonel.saas.service.ShortTtlCacheService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * 业绩运维控制器。
 *
 * <p>负责历史业绩回填、失效订单冲正重算和提成记录补算等写侧运维入口。
 * 旧 {@code /orders/...} 路径保留为兼容入口，但控制器归属业绩域。</p>
 */
@Validated
@Tag(name = "业绩运维", description = "业绩回填、冲正重算与兼容运维入口。")
@RequireRoles({RoleCodes.ADMIN})
@RestController
public class PerformanceOpsController extends BaseController {

    private static final String DASHBOARD_SUMMARY_CACHE_PREFIX = OrderDerivedCacheKeys.DASHBOARD_SUMMARY_PREFIX;
    private static final String DASHBOARD_METRICS_CACHE_PREFIX = OrderDerivedCacheKeys.DASHBOARD_METRICS_PREFIX;
    private static final String ORDER_STATS_CACHE_PREFIX = OrderDerivedCacheKeys.ORDER_STATS_PREFIX;
    private static final String FILTER_OPTIONS_CACHE_PREFIX = OrderDerivedCacheKeys.FILTER_OPTIONS_PREFIX;
    private static final DateTimeFormatter REQUEST_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final OrderReadFacade orderReadFacade;
    private final CommissionService commissionService;
    private final PerformanceBackfillService performanceBackfillService;
    private final OperationLogService operationLogService;
    private final ShortTtlCacheService shortTtlCacheService;

    public PerformanceOpsController(
            OrderReadFacade orderReadFacade,
            CommissionService commissionService,
            PerformanceBackfillService performanceBackfillService,
            OperationLogService operationLogService,
            ShortTtlCacheService shortTtlCacheService) {
        this.orderReadFacade = orderReadFacade;
        this.commissionService = commissionService;
        this.performanceBackfillService = performanceBackfillService;
        this.operationLogService = operationLogService;
        this.shortTtlCacheService = shortTtlCacheService;
    }

    @Operation(summary = "回填历史业绩记录", description = "按订单号或结算时间范围批量写入 performance_records，默认仅处理尚未生成业绩记录的订单。")
    @PostMapping({"/performance/backfill", "/orders/performance-backfill"})
    public ApiResult<PerformanceBackfillService.BackfillResult> performanceBackfill(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "历史业绩回填请求。可指定 orderIds，或按结算时间范围扫描缺失记录。",
                    required = false,
                    content = @Content(examples = @ExampleObject(value = "{\"onlyMissing\":true,\"limit\":200}"))
            )
            @RequestBody(required = false) PerformanceBackfillRequest request,
            @RequestAttribute("userId") UUID userId) {
        PerformanceBackfillRequest safeRequest = request == null ? new PerformanceBackfillRequest() : request;
        boolean onlyMissing = safeRequest.getOnlyMissing() == null || Boolean.TRUE.equals(safeRequest.getOnlyMissing());
        PerformanceBackfillService.BackfillResult result = performanceBackfillService.backfill(
                safeRequest.getOrderIds(),
                parseLocalDateTime(safeRequest.getStartTime()),
                parseLocalDateTime(safeRequest.getEndTime()),
                safeRequest.getLimit(),
                onlyMissing);
        operationLogService.recordSystemAction(
                userId,
                "业绩运维",
                "回填历史业绩记录",
                "POST",
                "performance_backfill",
                null,
                safeRequest.getStartTime() + " ~ " + safeRequest.getEndTime(),
                String.format(
                        "scanned=%d, upserted=%d, failed=%d, onlyMissing=%s",
                        result.scanned(),
                        result.upserted(),
                        result.failed(),
                        result.onlyMissing()));
        evictDerivedCaches();
        return ok(result);
    }

    @Operation(summary = "重算失效订单过期业绩", description = "扫描 order_status=4/5 且 performance_records.is_valid=true 的订单并重算冲正。")
    @PostMapping({"/performance/reconcile-invalidated", "/orders/performance-reconcile-invalidated"})
    public ApiResult<PerformanceBackfillService.BackfillResult> reconcileInvalidatedPerformance(
            @RequestBody(required = false) PerformanceReconcileRequest request,
            @RequestAttribute("userId") UUID userId) {
        PerformanceReconcileRequest safeRequest = request == null ? new PerformanceReconcileRequest() : request;
        PerformanceBackfillService.BackfillResult result =
                performanceBackfillService.reconcileInvalidatedPerformance(safeRequest.getLimit());
        operationLogService.recordSystemAction(
                userId,
                "业绩运维",
                "重算失效订单过期业绩",
                "POST",
                "performance_reconcile_invalidated",
                null,
                null,
                String.format("scanned=%d, upserted=%d, failed=%d", result.scanned(), result.upserted(), result.failed()));
        evictDerivedCaches();
        return ok(result);
    }

    @Operation(summary = "批量补全订单业绩", description = "按订单号批量计算并持久化双轨提成到 performance_records；取消/失效订单标记为冲正。")
    @PostMapping({"/performance/commission-batch", "/orders/commission-batch"})
    public ApiResult<List<CommissionService.OrderCommissionItem>> batchFillCommission(
            @RequestBody OrderCommissionBatchRequest request) {
        List<String> orderIds = normalizeOrderIds(request == null ? null : request.getOrderIds());
        if (orderIds.isEmpty()) {
            return ok(List.of());
        }
        List<ColonelsettlementOrder> orders = orderReadFacade.findByOrderIds(orderIds);
        List<CommissionService.OrderCommissionItem> results =
                commissionService.batchUpsertPerformanceRecords(orders);
        evictDerivedCaches();
        return ok(results);
    }

    @Operation(summary = "管理员单笔重算业绩", description = "传入单个 orderId，重算并回写 performance_records。需 ADMIN 权限。")
    @PostMapping({"/performance/commission-recalculate", "/orders/commission-recalculate"})
    public ApiResult<CommissionService.OrderCommissionItem> recalculateSingle(
            @RequestParam("orderId") String orderId) {
        if (!StringUtils.hasText(orderId)) {
            return ok(CommissionService.OrderCommissionItem.reversed(null));
        }
        String normalizedOrderId = orderId.trim();
        ColonelsettlementOrder order = orderReadFacade.findByOrderIds(List.of(normalizedOrderId))
                .stream()
                .findFirst()
                .orElse(null);
        if (order == null) {
            return ok(CommissionService.OrderCommissionItem.reversed(normalizedOrderId));
        }
        List<CommissionService.OrderCommissionItem> results =
                commissionService.batchUpsertPerformanceRecords(List.of(order));
        evictDerivedCaches();
        return ok(results.isEmpty()
                ? CommissionService.OrderCommissionItem.reversed(normalizedOrderId)
                : results.get(0));
    }

    private List<String> normalizeOrderIds(List<String> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return List.of();
        }
        return orderIds.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
    }

    private LocalDateTime parseLocalDateTime(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return LocalDateTime.parse(text, REQUEST_TIME_FORMATTER);
        } catch (Exception e) {
            return null;
        }
    }

    private void evictDerivedCaches() {
        shortTtlCacheService.evictByPrefix(DASHBOARD_SUMMARY_CACHE_PREFIX);
        shortTtlCacheService.evictByPrefix(DASHBOARD_METRICS_CACHE_PREFIX);
        shortTtlCacheService.evictByPrefix(ORDER_STATS_CACHE_PREFIX);
        shortTtlCacheService.evictByPrefix(FILTER_OPTIONS_CACHE_PREFIX);
    }

    @Data
    public static class OrderCommissionBatchRequest {
        @Schema(description = "待补全业绩的订单号列表。")
        private List<String> orderIds;
    }

    @Data
    public static class PerformanceReconcileRequest {
        @Min(1)
        @Max(2000)
        @Schema(description = "批量扫描上限，默认 200，最大 2000。")
        private Integer limit;
    }

    @Data
    public static class PerformanceBackfillRequest {
        @Schema(description = "指定需要回填的订单号列表；为空时按时间范围与 onlyMissing 扫描。")
        private List<String> orderIds;

        @Schema(description = "结算开始时间，格式 yyyy-MM-dd HH:mm:ss。")
        private String startTime;

        @Schema(description = "结算结束时间，格式 yyyy-MM-dd HH:mm:ss。")
        private String endTime;

        @Min(1)
        @Max(2000)
        @Schema(description = "批量扫描上限，默认 200，最大 2000。仅在未指定 orderIds 时生效。")
        private Integer limit;

        @Schema(description = "是否仅处理尚未生成 performance_records 的订单。默认 true。")
        private Boolean onlyMissing;
    }
}
