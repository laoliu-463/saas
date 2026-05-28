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

/**
 * 数据看板控制器.
 *
 * <p>提供首页看板归因概览数据，包括订单汇总、金额统计与活动商品维度聚合，
 * 属于分析域。看板数据使用 Redis 短 TTL 缓存（30 秒）以平衡实时性与性能。</p>
 *
 * <p>API 路径前缀：{@code /dashboard}</p>
 *
 * <p>访问权限：业务负责人、业务人员、渠道负责人、渠道人员。</p>
 *
 * @see DashboardService
 * @see ShortTtlCacheService
 */
@Tag(name = "数据看板", description = "首页看板与归因概览接口。")
@Validated
@RequireRoles({RoleCodes.BIZ_LEADER, RoleCodes.BIZ_STAFF, RoleCodes.CHANNEL_LEADER, RoleCodes.CHANNEL_STAFF})
@RestController
@RequestMapping("/dashboard")
public class DashboardController extends BaseController {

    /** 归因概览缓存 TTL：30 秒 */
    private static final Duration SUMMARY_CACHE_TTL = Duration.ofSeconds(30);
    /** 归因概览缓存键前缀 */
    private static final String SUMMARY_CACHE_PREFIX = "dashboard:summary:";

    /** 看板服务，负责数据聚合查询 */
    private final DashboardService dashboardService;
    /** 短 TTL 缓存服务，用于看板数据的 Redis 缓存 */
    private final ShortTtlCacheService shortTtlCacheService;

    /**
     * 构造注入.
     *
     * @param dashboardService    看板服务
     * @param shortTtlCacheService 短 TTL 缓存服务
     */
    public DashboardController(DashboardService dashboardService, ShortTtlCacheService shortTtlCacheService) {
        this.dashboardService = dashboardService;
        this.shortTtlCacheService = shortTtlCacheService;
    }

    /**
     * 获取归因概览数据.
     *
     * <p>查询首页归因概览看板数据，支持按时间范围筛选。
     * 结果缓存 30 秒以降低数据库压力。该接口用于首页看板展示，
     * 不等同于数据页的详细指标接口。</p>
     *
     * @param startTime 开始时间（可选，格式 yyyy-MM-dd HH:mm:ss）
     * @param endTime   结束时间（可选，格式 yyyy-MM-dd HH:mm:ss）
     * @param userId    当前登录用户 ID（可选）
     * @param deptId    当前用户所属部门 ID（可选）
     * @param dataScope 当前用户数据范围枚举（可选）
     * @return 归因概览汇总数据
     */
    @Operation(summary = "获取归因概览数据", description = "查询首页归因概览看板数据，可按时间范围筛选。该接口用于首页看板，不等同于数据页指标接口。")
    @GetMapping("/summary")
    public ApiResult<DashboardService.Summary> getSummary(
            @Parameter(description = "开始时间，格式 yyyy-MM-dd HH:mm:ss。") @RequestParam(name = "startTime", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @Parameter(description = "结束时间，格式 yyyy-MM-dd HH:mm:ss。") @RequestParam(name = "endTime", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestAttribute(name = "userId", required = false) UUID userId,
            @RequestAttribute(name = "deptId", required = false) UUID deptId,
            @RequestAttribute(name = "dataScope", required = false) DataScope dataScope) {
        // 组合缓存键：前缀 + 各参数拼接
        String cacheKey = SUMMARY_CACHE_PREFIX + cacheKey(startTime, endTime, userId, deptId, dataScope);
        // 优先从 Redis 缓存获取，缓存未命中时回调查询
        return ok(shortTtlCacheService.get(
                cacheKey,
                SUMMARY_CACHE_TTL,
                () -> dashboardService.getSummary(startTime, endTime, userId, deptId, dataScope)
        ));
    }

    /**
     * 获取活动商品维度归因聚合.
     *
     * <p>按 activity_id + product_id 聚合订单、商品事实层和映射事实层，
     * 用于 Dashboard 穿透和解释层辅助排查，支持分页。</p>
     *
     * @param page      页码，从 1 开始，最大 1000
     * @param size      每页条数，最大 200
     * @param startTime 开始时间（可选，格式 yyyy-MM-dd HH:mm:ss）
     * @param endTime   结束时间（可选，格式 yyyy-MM-dd HH:mm:ss）
     * @param userId    当前登录用户 ID（可选）
     * @param deptId    当当前用户所属部门 ID（可选）
     * @param dataScope 当前用户数据范围枚举（可选）
     * @return 活动商品维度的分页聚合数据
     */
    @Operation(summary = "获取活动商品维度归因聚合", description = "按 activity_id + product_id 聚合订单、商品事实层和映射事实层，用于 Dashboard 穿透和解释层辅助排查。")
    @GetMapping("/activity-products")
    public ApiResult<PageResult<DashboardService.ActivityProductItem>> getActivityProducts(
            @Parameter(description = "页码，从 1 开始，最大 1000。") @RequestParam(name = "page", defaultValue = "1") @Min(1) @Max(1000) long page,
            @Parameter(description = "每页条数，最大 200。") @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(200) long size,
            @Parameter(description = "开始时间，格式 yyyy-MM-dd HH:mm:ss。") @RequestParam(name = "startTime", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @Parameter(description = "结束时间，格式 yyyy-MM-dd HH:mm:ss。") @RequestParam(name = "endTime", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestAttribute(name = "userId", required = false) UUID userId,
            @RequestAttribute(name = "deptId", required = false) UUID deptId,
            @RequestAttribute(name = "dataScope", required = false) DataScope dataScope) {
        // 调用看板服务查询活动商品维度聚合数据
        DashboardService.ActivityProductPage result =
                dashboardService.getActivityProductBreakdown(startTime, endTime, userId, deptId, dataScope, page, size);
        // 将服务层分页结果转换为统一的 PageResult 响应
        PageResult<DashboardService.ActivityProductItem> pageResult = new PageResult<>();
        pageResult.setTotal(result.total());
        pageResult.setPage(result.page());
        pageResult.setSize(result.size());
        pageResult.setRecords(result.records());
        return ok(pageResult);
    }

    /**
     * 构建缓存键.
     *
     * <p>将多个参数值用管道符（{@code |}）拼接为缓存键字符串。
     * null 值作为空字符串处理。</p>
     *
     * @param values 缓存键参数值列表
     * @return 拼接后的缓存键字符串
     */
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
