package com.colonel.saas.controller;

import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.common.base.BaseController;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.common.result.PageResult;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.service.DashboardService;
import com.colonel.saas.service.ShortTtlCacheService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Tag(name = "数据看板", description = "首页看板与归因概览接口。")
@Validated
@RequireRoles({RoleCodes.BIZ_LEADER, RoleCodes.BIZ_STAFF, RoleCodes.CHANNEL_LEADER, RoleCodes.CHANNEL_STAFF})
@RestController
@RequestMapping("/dashboard")
public class DashboardController extends BaseController {

    private static final Duration SUMMARY_CACHE_TTL = Duration.ofSeconds(30);
    private static final String SUMMARY_CACHE_PREFIX = "dashboard:summary:";

    private final DashboardService dashboardService;
    private final ShortTtlCacheService shortTtlCacheService;

    public DashboardController(DashboardService dashboardService, ShortTtlCacheService shortTtlCacheService) {
        this.dashboardService = dashboardService;
        this.shortTtlCacheService = shortTtlCacheService;
    }

    @Operation(summary = "获取归因概览数据", description = "查询首页归因概览看板数据，可按时间范围筛选。该接口用于首页看板，不等同于数据页指标接口。")
    @GetMapping("/summary")
    public ApiResult<DashboardService.Summary> getSummary(
            @Parameter(description = "开始时间，格式 yyyy-MM-dd HH:mm:ss。") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @Parameter(description = "结束时间，格式 yyyy-MM-dd HH:mm:ss。") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestAttribute(name = "userId", required = false) UUID userId,
            @RequestAttribute(name = "deptId", required = false) UUID deptId,
            @RequestAttribute(name = "dataScope", required = false) DataScope dataScope) {
        String cacheKey = SUMMARY_CACHE_PREFIX + cacheKey(startTime, endTime, userId, deptId, dataScope);
        return ok(shortTtlCacheService.get(
                cacheKey,
                SUMMARY_CACHE_TTL,
                () -> dashboardService.getSummary(startTime, endTime, userId, deptId, dataScope)
        ));
    }

    @Operation(summary = "获取活动商品维度归因聚合", description = "按 activity_id + product_id 聚合订单、商品事实层和映射事实层，用于 Dashboard 穿透和解释层辅助排查。")
    @GetMapping("/activity-products")
    public ApiResult<PageResult<DashboardService.ActivityProductItem>> getActivityProducts(
            @Parameter(description = "页码，从 1 开始，最大 1000。") @RequestParam(defaultValue = "1") @Min(1) @Max(1000) long page,
            @Parameter(description = "每页条数，最大 200。") @RequestParam(defaultValue = "20") @Min(1) @Max(200) long size,
            @Parameter(description = "开始时间，格式 yyyy-MM-dd HH:mm:ss。") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @Parameter(description = "结束时间，格式 yyyy-MM-dd HH:mm:ss。") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestAttribute(name = "userId", required = false) UUID userId,
            @RequestAttribute(name = "deptId", required = false) UUID deptId,
            @RequestAttribute(name = "dataScope", required = false) DataScope dataScope) {
        DashboardService.ActivityProductPage result =
                dashboardService.getActivityProductBreakdown(startTime, endTime, userId, deptId, dataScope, page, size);
        PageResult<DashboardService.ActivityProductItem> pageResult = new PageResult<>();
        pageResult.setTotal(result.total());
        pageResult.setPage(result.page());
        pageResult.setSize(result.size());
        pageResult.setRecords(result.records());
        return ok(pageResult);
    }

    private String cacheKey(Object... values) {
        StringBuilder builder = new StringBuilder();
        for (Object value : values) {
            if (builder.length() > 0) {
                builder.append('|');
            }
            builder.append(value == null ? "" : value);
        }
        return builder.toString();
    }
}
