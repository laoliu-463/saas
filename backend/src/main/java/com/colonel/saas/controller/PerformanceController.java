package com.colonel.saas.controller;

import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.common.base.BaseController;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.dto.performance.PerformanceBatchRequest;
import com.colonel.saas.dto.performance.PerformanceBatchResponse;
import com.colonel.saas.dto.performance.PerformanceDetailDTO;
import com.colonel.saas.dto.performance.PerformanceListQuery;
import com.colonel.saas.dto.performance.PerformancePageResponse;
import com.colonel.saas.dto.performance.PerformanceRecalculateMonthRequest;
import com.colonel.saas.dto.performance.PerformanceRecalculateMonthResponse;
import com.colonel.saas.dto.performance.PerformanceSummaryQuery;
import com.colonel.saas.dto.performance.PerformanceSummaryResponse;
import com.colonel.saas.service.ExclusiveMerchantQueryService;
import com.colonel.saas.service.OperationLogService;
import com.colonel.saas.service.PerformanceExportService;
import com.colonel.saas.service.PerformanceMonthRecalculationService;
import com.colonel.saas.service.PerformanceQueryService;
import com.colonel.saas.service.PerformanceSummaryService;
import com.colonel.saas.service.performance.PerformanceAccessContext;
import com.colonel.saas.service.performance.PerformanceAccessScope;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Validated
@Tag(name = "业绩域", description = "业绩查询、汇总、导出与管理员重算接口。")
@RestController
@RequestMapping("/performance")
@RequireRoles({
        RoleCodes.ADMIN,
        RoleCodes.OPS_STAFF,
        RoleCodes.BIZ_LEADER,
        RoleCodes.BIZ_STAFF,
        RoleCodes.CHANNEL_LEADER,
        RoleCodes.CHANNEL_STAFF,
        RoleCodes.COLONEL_LEADER
})
public class PerformanceController extends BaseController {

    private final PerformanceQueryService performanceQueryService;
    private final PerformanceSummaryService performanceSummaryService;
    private final PerformanceExportService performanceExportService;
    private final PerformanceMonthRecalculationService monthRecalculationService;
    private final OperationLogService operationLogService;

    public PerformanceController(
            PerformanceQueryService performanceQueryService,
            PerformanceSummaryService performanceSummaryService,
            PerformanceExportService performanceExportService,
            PerformanceMonthRecalculationService monthRecalculationService,
            OperationLogService operationLogService) {
        this.performanceQueryService = performanceQueryService;
        this.performanceSummaryService = performanceSummaryService;
        this.performanceExportService = performanceExportService;
        this.monthRecalculationService = monthRecalculationService;
        this.operationLogService = operationLogService;
    }

    @Operation(summary = "单笔订单业绩查询")
    @GetMapping("/{orderId}")
    public ApiResult<PerformanceDetailDTO> getByOrderId(
            @PathVariable String orderId,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope,
            @RequestAttribute(value = "roleCodes", required = false) List<String> roleCodes) {
        return ok(performanceQueryService.getByOrderId(
                orderId,
                PerformanceAccessContext.of(userId, deptId, dataScope, roleCodes)));
    }

    @Operation(summary = "批量订单业绩查询")
    @PostMapping("/batch")
    public ApiResult<PerformanceBatchResponse> batchGet(
            @RequestBody PerformanceBatchRequest request,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope,
            @RequestAttribute(value = "roleCodes", required = false) List<String> roleCodes) {
        return ok(performanceQueryService.batchGet(
                request == null ? List.of() : request.getOrderIds(),
                PerformanceAccessContext.of(userId, deptId, dataScope, roleCodes)));
    }

    @Operation(summary = "业绩列表分页查询")
    @GetMapping
    public ApiResult<PerformancePageResponse> list(
            @RequestParam(required = false) String orderId,
            @RequestParam(required = false) String productId,
            @RequestParam(required = false) String productName,
            @RequestParam(required = false) Long partnerId,
            @RequestParam(required = false) String partnerName,
            @RequestParam(required = false) String activityId,
            @RequestParam(required = false) UUID talentId,
            @RequestParam(required = false) UUID channelId,
            @RequestParam(required = false) UUID recruiterId,
            @RequestParam(required = false) String orderStatus,
            @RequestParam(required = false, defaultValue = "pay") String timeFilterType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime timeStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime timeEnd,
            @RequestParam(required = false, defaultValue = "both") String amountTrack,
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) long pageSize,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope,
            @RequestAttribute(value = "roleCodes", required = false) List<String> roleCodes) {
        PerformanceListQuery query = buildListQuery(
                orderId, productId, productName, partnerId, partnerName, activityId, talentId,
                channelId, recruiterId, orderStatus, timeFilterType, timeStart, timeEnd, amountTrack,
                page, pageSize, sortBy, sortOrder);
        return ok(performanceQueryService.list(
                query,
                PerformanceAccessContext.of(userId, deptId, dataScope, roleCodes)));
    }

    @Operation(summary = "业绩指标卡片汇总")
    @GetMapping("/summary")
    public ApiResult<PerformanceSummaryResponse> summary(
            @RequestParam(required = false, defaultValue = "pay") String timeFilterType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime timeStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime timeEnd,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) UUID channelId,
            @RequestParam(required = false) UUID recruiterId,
            @RequestParam(required = false) String activityId,
            @RequestParam(required = false) String productId,
            @RequestParam(required = false) String orderStatus,
            @RequestParam(required = false) Long partnerId,
            @RequestParam(required = false) UUID talentId,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope,
            @RequestAttribute(value = "roleCodes", required = false) List<String> roleCodes) {
        PerformanceSummaryQuery query = new PerformanceSummaryQuery();
        query.setTimeFilterType(timeFilterType);
        query.setTimeStart(resolveStart(timeStart, startDate));
        query.setTimeEnd(resolveEnd(timeEnd, endDate));
        query.setChannelId(channelId);
        query.setRecruiterId(recruiterId);
        query.setActivityId(activityId);
        query.setProductId(productId);
        query.setOrderStatus(orderStatus);
        query.setPartnerId(partnerId);
        query.setTalentId(talentId);
        return ok(performanceSummaryService.getSummary(
                query,
                PerformanceAccessContext.of(userId, deptId, dataScope, roleCodes)));
    }

    @Operation(summary = "业绩明细导出")
    @GetMapping("/export")
    @RequireRoles({RoleCodes.ADMIN, RoleCodes.BIZ_LEADER, RoleCodes.CHANNEL_LEADER, RoleCodes.COLONEL_LEADER})
    public void export(
            @RequestParam(required = false) String orderId,
            @RequestParam(required = false) String productId,
            @RequestParam(required = false) String productName,
            @RequestParam(required = false) Long partnerId,
            @RequestParam(required = false) String partnerName,
            @RequestParam(required = false) String activityId,
            @RequestParam(required = false) UUID talentId,
            @RequestParam(required = false) UUID channelId,
            @RequestParam(required = false) UUID recruiterId,
            @RequestParam(required = false) String orderStatus,
            @RequestParam(required = false, defaultValue = "pay") String timeFilterType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime timeStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime timeEnd,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false, defaultValue = "both") String amountTrack,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope,
            @RequestAttribute(value = "roleCodes", required = false) List<String> roleCodes,
            HttpServletResponse response) throws IOException {
        PerformanceAccessContext context = PerformanceAccessContext.of(userId, deptId, dataScope, roleCodes);
        if (!PerformanceAccessScope.canExport(context)) {
            throw BusinessException.forbidden("无权导出业绩明细");
        }
        PerformanceListQuery query = buildListQuery(
                orderId, productId, productName, partnerId, partnerName, activityId, talentId,
                channelId, recruiterId, orderStatus, timeFilterType,
                resolveStart(timeStart, startDate), resolveEnd(timeEnd, endDate),
                amountTrack, 1, PerformanceQueryService.EXPORT_MAX_ROWS, sortBy, sortOrder);
        byte[] bytes = performanceExportService.exportXlsx(query, context);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=\"performance-export.xlsx\"");
        response.getOutputStream().write(bytes);
        operationLogService.recordSystemAction(
                userId,
                "业绩域",
                "导出业绩明细",
                "GET",
                "performance_export",
                null,
                "performance-export.xlsx",
                "rows=" + (bytes.length > 0 ? "ok" : "0"));
    }

    @Operation(summary = "重算指定月份业绩（仅未结算订单）")
    @PostMapping("/recalculate-month")
    @RequireRoles({RoleCodes.ADMIN})
    public ApiResult<PerformanceRecalculateMonthResponse> recalculateMonth(
            @RequestBody PerformanceRecalculateMonthRequest request,
            @RequestAttribute("userId") UUID userId) {
        PerformanceRecalculateMonthResponse result = monthRecalculationService.recalculateMonth(
                request == null ? null : request.getMonth(),
                request == null ? null : request.getReason());
        operationLogService.recordSystemAction(
                userId,
                "业绩域",
                "重算指定月份业绩",
                "POST",
                "performance_recalculate_month",
                result.getJobId(),
                result.getMonth(),
                String.format("reason=%s, scanned=%d, upserted=%d",
                        request == null ? "" : request.getReason(),
                        result.getScanned(),
                        result.getUpserted()));
        return ok(result);
    }

    private PerformanceListQuery buildListQuery(
            String orderId,
            String productId,
            String productName,
            Long partnerId,
            String partnerName,
            String activityId,
            UUID talentId,
            UUID channelId,
            UUID recruiterId,
            String orderStatus,
            String timeFilterType,
            LocalDateTime timeStart,
            LocalDateTime timeEnd,
            String amountTrack,
            long page,
            long pageSize,
            String sortBy,
            String sortOrder) {
        PerformanceListQuery query = new PerformanceListQuery();
        query.setOrderId(orderId);
        query.setProductId(productId);
        query.setProductName(productName);
        query.setPartnerId(partnerId);
        query.setPartnerName(partnerName);
        query.setActivityId(activityId);
        query.setTalentId(talentId);
        query.setChannelId(channelId);
        query.setRecruiterId(recruiterId);
        query.setOrderStatus(orderStatus);
        query.setTimeFilterType(timeFilterType);
        query.setTimeStart(timeStart);
        query.setTimeEnd(timeEnd);
        query.setAmountTrack(amountTrack);
        query.setPage(page);
        query.setPageSize(pageSize);
        query.setSortBy(sortBy);
        query.setSortOrder(sortOrder);
        return query;
    }

    private LocalDateTime resolveStart(LocalDateTime timeStart, LocalDate startDate) {
        if (timeStart != null) {
            return timeStart;
        }
        return startDate == null ? null : startDate.atStartOfDay();
    }

    private LocalDateTime resolveEnd(LocalDateTime timeEnd, LocalDate endDate) {
        if (timeEnd != null) {
            return timeEnd;
        }
        return endDate == null ? null : endDate.plusDays(1).atStartOfDay();
    }
}
