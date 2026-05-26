package com.colonel.saas.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.common.base.BaseController;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.constant.DeptType;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.dto.order.OrderDetailResponse;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.entity.SysDept;
import com.colonel.saas.mapper.ColonelsettlementOrderMapper;
import com.colonel.saas.mapper.SysDeptMapper;
import com.colonel.saas.service.AttributionService;
import com.colonel.saas.service.CommissionService;
import com.colonel.saas.service.DashboardService;
import com.colonel.saas.service.OperationLogService;
import com.colonel.saas.service.OrderAttributionReplayService;
import com.colonel.saas.service.OrderQueryService;
import com.colonel.saas.service.OrderSyncService;
import com.colonel.saas.service.PerformanceBackfillService;
import com.colonel.saas.service.ShortTtlCacheService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Tag(name = "订单管理", description = "订单同步、列表、统计、筛选项与详情查询接口。")
@Validated
@RequireRoles({RoleCodes.BIZ_LEADER, RoleCodes.BIZ_STAFF, RoleCodes.CHANNEL_LEADER, RoleCodes.CHANNEL_STAFF, RoleCodes.ADMIN})
@RestController
@RequestMapping("/orders")
public class OrderController extends BaseController {

    private static final Duration FILTER_OPTIONS_CACHE_TTL = Duration.ofSeconds(60);
    private static final String FILTER_OPTIONS_CACHE_PREFIX = "orders:filter-options:";
    private static final String DASHBOARD_SUMMARY_CACHE_PREFIX = "dashboard:summary:";
    private static final String DASHBOARD_METRICS_CACHE_PREFIX = "dashboard:metrics:";

    private final OrderSyncService orderSyncService;
    private final ColonelsettlementOrderMapper orderMapper;
    private final OrderQueryService orderQueryService;
    private final OrderAttributionReplayService orderAttributionReplayService;
    private final OperationLogService operationLogService;
    private final ShortTtlCacheService shortTtlCacheService;
    private final CommissionService commissionService;
    private final PerformanceBackfillService performanceBackfillService;
    private final SysDeptMapper sysDeptMapper;

    public OrderController(
            OrderSyncService orderSyncService,
            ColonelsettlementOrderMapper orderMapper,
            OrderQueryService orderQueryService,
            OrderAttributionReplayService orderAttributionReplayService,
            OperationLogService operationLogService,
            ShortTtlCacheService shortTtlCacheService,
            CommissionService commissionService,
            PerformanceBackfillService performanceBackfillService,
            SysDeptMapper sysDeptMapper) {
        this.orderSyncService = orderSyncService;
        this.orderMapper = orderMapper;
        this.orderQueryService = orderQueryService;
        this.orderAttributionReplayService = orderAttributionReplayService;
        this.operationLogService = operationLogService;
        this.shortTtlCacheService = shortTtlCacheService;
        this.commissionService = commissionService;
        this.performanceBackfillService = performanceBackfillService;
        this.sysDeptMapper = sysDeptMapper;
    }

    @Operation(summary = "手动同步订单", description = "按时间范围触发订单同步，用于补拉订单或联调真实网关回流数据。")
    @RequireRoles({RoleCodes.ADMIN})
    @PostMapping("/sync")
    public ApiResult<OrderSyncService.SyncResult> syncOrders(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "订单同步时间范围。",
                    required = false,
                    content = @Content(examples = @ExampleObject(value = "{\"startTime\":\"2026-04-01 00:00:00\",\"endTime\":\"2026-04-28 23:59:59\"}"))
            )
            @RequestBody(required = false) SyncRequest request,
            @RequestAttribute("userId") UUID userId) {
        SyncRequest safeRequest = request == null ? defaultSyncRequest() : request;
        long start = parseDateTime(safeRequest.getStartTime());
        long end = parseDateTime(safeRequest.getEndTime());
        OrderSyncService.SyncResult result = orderSyncService.syncByTimeRange(start, end);
        operationLogService.recordSystemAction(
                userId,
                "订单归因",
                "手动同步订单",
                "POST",
                "order_sync",
                null,
                safeRequest.getStartTime() + " ~ " + safeRequest.getEndTime(),
                String.format(
                        "created=%d, updated=%d, attributed=%d, unattributed=%d, failed=%d",
                        result.created(),
                        result.updated(),
                        result.attributed(),
                        result.unattributed(),
                        result.failed()));
        evictOrderDerivedCaches();
        return ok(result);
    }

    @Operation(summary = "重算历史订单归因", description = "对已落库订单重新执行归因逻辑，用于补映射后的历史订单回放验证。默认只扫描未归因订单。")
    @RequireRoles({RoleCodes.ADMIN})
    @PostMapping("/replay-attribution")
    public ApiResult<OrderAttributionReplayService.ReplayResult> replayAttribution(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "历史订单重算请求。可指定 orderIds 或按未归因原因批量扫描。",
                    required = false,
                    content = @Content(examples = @ExampleObject(value = "{\"reason\":\"COLONEL_MAPPING_NOT_FOUND\",\"limit\":50,\"dryRun\":true}"))
            )
            @RequestBody(required = false) ReplayAttributionRequest request,
            @RequestAttribute("userId") UUID userId) {
        ReplayAttributionRequest safeRequest = request == null ? new ReplayAttributionRequest() : request;
        boolean dryRun = Boolean.TRUE.equals(safeRequest.getDryRun());
        OrderAttributionReplayService.ReplayResult result = orderAttributionReplayService.replay(
                safeRequest.getOrderIds(),
                safeRequest.getReason(),
                safeRequest.getLimit(),
                dryRun
        );
        operationLogService.recordSystemAction(
                userId,
                "订单归因",
                dryRun ? "重算历史订单归因(预览)" : "重算历史订单归因",
                "POST",
                "order_attribution",
                safeRequest.getReason(),
                dryRun ? "dry-run" : "apply",
                String.format(
                        "scanned=%d, attributed=%d, unattributed=%d, updated=%d, dryRun=%s",
                        result.scanned(),
                        result.attributed(),
                        result.unattributed(),
                        result.updated(),
                        dryRun));
        if (!dryRun) {
            evictOrderDerivedCaches();
        }
        return ok(result);
    }

    @Operation(summary = "回填历史业绩记录", description = "按订单号或结算时间范围批量写入 performance_records，默认仅处理尚未生成业绩记录的订单。")
    @RequireRoles({RoleCodes.ADMIN})
    @PostMapping("/performance-backfill")
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
                "订单业绩",
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
        evictOrderDerivedCaches();
        return ok(result);
    }

    @Operation(summary = "批量补全订单业绩", description = "按订单号批量计算并持久化双轨提成到 performance_records（Y-08）；取消/失效订单标记为冲正。")
    @PostMapping("/commission-batch")
    public ApiResult<List<CommissionService.OrderCommissionItem>> batchFillCommission(
            @RequestBody OrderCommissionBatchRequest request) {
        List<String> orderIds = request == null || request.getOrderIds() == null
                ? List.of()
                : request.getOrderIds().stream().filter(StringUtils::hasText).map(String::trim).distinct().toList();
        if (orderIds.isEmpty()) {
            return ok(List.of());
        }
        List<ColonelsettlementOrder> orders = orderMapper.selectList(new LambdaQueryWrapper<ColonelsettlementOrder>()
                .in(ColonelsettlementOrder::getOrderId, orderIds)
                .eq(ColonelsettlementOrder::getDeleted, 0));
        return ok(commissionService.batchUpsertPerformanceRecords(orders));
    }

    @Operation(summary = "管理员单笔重算业绩", description = "传入单个 orderId，重算并回写 performance_records（Y-09）。需 ADMIN 权限。")
    @PostMapping("/commission-recalculate")
    @RequireRoles({RoleCodes.ADMIN})
    public ApiResult<CommissionService.OrderCommissionItem> recalculateSingle(
            @RequestParam("orderId") String orderId) {
        if (!StringUtils.hasText(orderId)) {
            return ok(CommissionService.OrderCommissionItem.reversed(null));
        }
        ColonelsettlementOrder order = orderMapper.selectOne(new LambdaQueryWrapper<ColonelsettlementOrder>()
                .eq(ColonelsettlementOrder::getOrderId, orderId.trim())
                .eq(ColonelsettlementOrder::getDeleted, 0)
                .last("limit 1"));
        if (order == null) {
            return ok(CommissionService.OrderCommissionItem.reversed(orderId));
        }
        List<CommissionService.OrderCommissionItem> results =
                commissionService.batchUpsertPerformanceRecords(List.of(order));
        return ok(results.isEmpty() ? CommissionService.OrderCommissionItem.reversed(orderId) : results.get(0));
    }

    @Operation(summary = "获取订单列表", description = "分页查询订单归因列表，用于订单主页面。")
    @GetMapping
    public ApiResult<IPage<ColonelsettlementOrder>> getOrders(
            @Parameter(description = "页码，从 1 开始，最大 1000。") @RequestParam(defaultValue = "1") @Min(1) @Max(1000) long page,
            @Parameter(description = "每页条数，最大 200。") @RequestParam(defaultValue = "20") @Min(1) @Max(200) long size,
            @Parameter(description = "订单 ID。") @RequestParam(required = false) String orderId,
            @Parameter(description = "归因状态，例如 ATTRIBUTED、UNATTRIBUTED。完整取值以代码常量为准。") @RequestParam(required = false) String attributionStatus,
            @Parameter(description = "未归因原因。完整取值以代码常量为准。") @RequestParam(required = false) String unattributedReason,
            @Parameter(description = "活动 ID。") @RequestParam(required = false) String activityId,
            @Parameter(description = "商品 ID。") @RequestParam(required = false) String productId,
            @Parameter(description = "渠道关键字，可匹配渠道名称或渠道 ID。") @RequestParam(required = false) String channelKeyword,
            @Parameter(description = "团长关键字，可匹配团长名称或团长 ID。") @RequestParam(required = false) String colonelKeyword,
            @Parameter(description = "订单状态。完整映射以代码标签函数为准。") @RequestParam(required = false) Integer orderStatus,
            @Parameter(description = "开始时间，格式 yyyy-MM-dd HH:mm:ss。") @RequestParam(required = false) String startTime,
            @Parameter(description = "结束时间，格式 yyyy-MM-dd HH:mm:ss。") @RequestParam(required = false) String endTime,
            @Parameter(description = "时间字段，支持 createTime 或 settleTime。默认 createTime。") @RequestParam(required = false) String timeField,
            @Parameter(description = "Dashboard 诊断分类过滤。") @RequestParam(required = false) String dashboardDiagnosis,
            @Parameter(description = "招商部门 ID 列表（dept_id IN ...），CSV 或重复同名参数；非法 UUID 将被忽略。") @RequestParam(required = false) String recruiterDeptIds,
            @Parameter(description = "渠道部门 ID 列表（channel_dept_id IN ...），CSV 或重复同名参数；非法 UUID 将被忽略。") @RequestParam(required = false) String channelDeptIds,
            @RequestAttribute(name = "userId", required = false) UUID userId,
            @RequestAttribute(name = "deptId", required = false) UUID deptId,
            @RequestAttribute(name = "dataScope", required = false) DataScope dataScope) {
        Page<ColonelsettlementOrder> query = new Page<>(page, size);
        LambdaQueryWrapper<ColonelsettlementOrder> wrapper = buildWrapper(
                orderId,
                attributionStatus,
                unattributedReason,
                activityId,
                productId,
                channelKeyword,
                colonelKeyword,
                orderStatus,
                startTime,
                endTime,
                timeField,
                dashboardDiagnosis,
                parseUuidCsv(recruiterDeptIds),
                parseUuidCsv(channelDeptIds)
        );
        selectOrderListColumns(wrapper);
        applyDataScope(wrapper, userId, deptId, dataScope);
        wrapper.orderByDesc(ColonelsettlementOrder::getUpdateTime)
                .orderByDesc(ColonelsettlementOrder::getCreateTime);
        IPage<ColonelsettlementOrder> result = orderMapper.selectPage(query, wrapper);
        result.getRecords().forEach(this::normalizeOrderRow);
        return ok(result);
    }

    @Operation(summary = "获取未归因订单", description = "分页查询未归因订单列表，用于未归因排查。其余筛选条件与订单列表保持一致。")
    @GetMapping("/unattributed")
    public ApiResult<IPage<ColonelsettlementOrder>> getUnattributedOrders(
            @Parameter(description = "页码，从 1 开始，最大 1000。") @RequestParam(defaultValue = "1") @Min(1) @Max(1000) long page,
            @Parameter(description = "每页条数，最大 200。") @RequestParam(defaultValue = "20") @Min(1) @Max(200) long size,
            @Parameter(description = "订单 ID。") @RequestParam(required = false) String orderId,
            @Parameter(description = "未归因原因。完整取值以代码常量为准。") @RequestParam(required = false) String unattributedReason,
            @Parameter(description = "活动 ID。") @RequestParam(required = false) String activityId,
            @Parameter(description = "商品 ID。") @RequestParam(required = false) String productId,
            @Parameter(description = "渠道关键字，可匹配渠道名称或渠道 ID。") @RequestParam(required = false) String channelKeyword,
            @Parameter(description = "团长关键字，可匹配团长名称或团长 ID。") @RequestParam(required = false) String colonelKeyword,
            @Parameter(description = "订单状态。完整映射以代码标签函数为准。") @RequestParam(required = false) Integer orderStatus,
            @Parameter(description = "开始时间，格式 yyyy-MM-dd HH:mm:ss。") @RequestParam(required = false) String startTime,
            @Parameter(description = "结束时间，格式 yyyy-MM-dd HH:mm:ss。") @RequestParam(required = false) String endTime,
            @Parameter(description = "时间字段，支持 createTime 或 settleTime。默认 createTime。") @RequestParam(required = false) String timeField,
            @Parameter(description = "Dashboard 诊断分类过滤。") @RequestParam(required = false) String dashboardDiagnosis,
            @Parameter(description = "招商部门 ID 列表（dept_id IN ...），CSV 或重复同名参数；非法 UUID 将被忽略。") @RequestParam(required = false) String recruiterDeptIds,
            @Parameter(description = "渠道部门 ID 列表（channel_dept_id IN ...），CSV 或重复同名参数；非法 UUID 将被忽略。") @RequestParam(required = false) String channelDeptIds,
            @RequestAttribute(name = "userId", required = false) UUID userId,
            @RequestAttribute(name = "deptId", required = false) UUID deptId,
            @RequestAttribute(name = "dataScope", required = false) DataScope dataScope) {
        return getOrders(page, size, orderId, AttributionService.STATUS_UNATTRIBUTED, unattributedReason, activityId, productId, channelKeyword, colonelKeyword, orderStatus, startTime, endTime, timeField, dashboardDiagnosis, recruiterDeptIds, channelDeptIds, userId, deptId, dataScope);
    }

    @Operation(summary = "获取订单详情", description = "查询单个订单详情，返回订单基础信息、归因结果、推广映射、达人与寄样关联信息。")
    @GetMapping("/{orderId}")
    public ApiResult<OrderDetailResponse> getOrderDetail(
            @Parameter(description = "订单 ID。") @PathVariable String orderId,
            @RequestAttribute(name = "userId", required = false) UUID userId,
            @RequestAttribute(name = "deptId", required = false) UUID deptId,
            @RequestAttribute(name = "dataScope", required = false) DataScope dataScope) {
        return ok(orderQueryService.getOrderDetail(orderId, userId, deptId, dataScope));
    }

    @Operation(summary = "获取订单统计", description = "按当前筛选条件统计订单总量、已归因数、未归因数与未归因原因分布。")
    @GetMapping("/stats")
    public ApiResult<OrderStats> getStats(
            @Parameter(description = "订单 ID。") @RequestParam(required = false) String orderId,
            @Parameter(description = "归因状态，例如 ATTRIBUTED、UNATTRIBUTED。完整取值以代码常量为准。") @RequestParam(required = false) String attributionStatus,
            @Parameter(description = "未归因原因。完整取值以代码常量为准。") @RequestParam(required = false) String unattributedReason,
            @Parameter(description = "活动 ID。") @RequestParam(required = false) String activityId,
            @Parameter(description = "商品 ID。") @RequestParam(required = false) String productId,
            @Parameter(description = "渠道关键字，可匹配渠道名称或渠道 ID。") @RequestParam(required = false) String channelKeyword,
            @Parameter(description = "团长关键字，可匹配团长名称或团长 ID。") @RequestParam(required = false) String colonelKeyword,
            @Parameter(description = "订单状态。完整映射以代码标签函数为准。") @RequestParam(required = false) Integer orderStatus,
            @Parameter(description = "开始时间，格式 yyyy-MM-dd HH:mm:ss。") @RequestParam(required = false) String startTime,
            @Parameter(description = "结束时间，格式 yyyy-MM-dd HH:mm:ss。") @RequestParam(required = false) String endTime,
            @Parameter(description = "时间字段，支持 createTime 或 settleTime。默认 createTime。") @RequestParam(required = false) String timeField,
            @Parameter(description = "Dashboard 诊断分类过滤。") @RequestParam(required = false) String dashboardDiagnosis,
            @Parameter(description = "招商部门 ID 列表（dept_id IN ...），CSV 或重复同名参数；非法 UUID 将被忽略。") @RequestParam(required = false) String recruiterDeptIds,
            @Parameter(description = "渠道部门 ID 列表（channel_dept_id IN ...），CSV 或重复同名参数；非法 UUID 将被忽略。") @RequestParam(required = false) String channelDeptIds,
            @RequestAttribute(name = "userId", required = false) UUID userId,
            @RequestAttribute(name = "deptId", required = false) UUID deptId,
            @RequestAttribute(name = "dataScope", required = false) DataScope dataScope) {
        List<UUID> parsedRecruiterDeptIds = parseUuidCsv(recruiterDeptIds);
        List<UUID> parsedChannelDeptIds = parseUuidCsv(channelDeptIds);
        QueryWrapper<ColonelsettlementOrder> statusWrapper = buildStatsWrapper(
                orderId,
                attributionStatus,
                unattributedReason,
                activityId,
                productId,
                channelKeyword,
                colonelKeyword,
                orderStatus,
                startTime,
                endTime,
                timeField,
                dashboardDiagnosis,
                parsedRecruiterDeptIds,
                parsedChannelDeptIds
        );
        applyQueryDataScope(statusWrapper, userId, deptId, dataScope);
        OrderStats stats = new OrderStats();
        stats.setLastSyncTime(orderSyncService.getLastSyncTime());
        statusWrapper.select("attribution_status AS attributionStatus", "COUNT(*) AS total")
                .groupBy("attribution_status");

        long totalOrders = 0L;
        long attributedOrders = 0L;
        long unattributedOrders = 0L;
        long partialOrders = 0L;
        for (Map<String, Object> row : orderMapper.selectMaps(statusWrapper)) {
            String status = asText(readValue(row, "attributionStatus"));
            long count = asLong(readValue(row, "total"));
            totalOrders += count;
            if (AttributionService.STATUS_ATTRIBUTED.equals(status)) {
                attributedOrders += count;
            }
            if (AttributionService.STATUS_UNATTRIBUTED.equals(status)) {
                unattributedOrders += count;
            }
            if ("PARTIAL".equals(status)) {
                partialOrders += count;
            }
        }

        QueryWrapper<ColonelsettlementOrder> reasonWrapper = buildStatsWrapper(
                orderId,
                attributionStatus,
                unattributedReason,
                activityId,
                productId,
                channelKeyword,
                colonelKeyword,
                orderStatus,
                startTime,
                endTime,
                timeField,
                dashboardDiagnosis,
                parsedRecruiterDeptIds,
                parsedChannelDeptIds
        );
        applyQueryDataScope(reasonWrapper, userId, deptId, dataScope);
        reasonWrapper.eq("attribution_status", AttributionService.STATUS_UNATTRIBUTED)
                .isNotNull("attribution_remark")
                .select("attribution_remark AS reason", "COUNT(*) AS total")
                .groupBy("attribution_remark");

        List<ReasonCount> reasonCounts = new ArrayList<>();
        long syncFailedOrders = 0L;
        for (Map<String, Object> row : orderMapper.selectMaps(reasonWrapper)) {
            String reason = asText(readValue(row, "reason"));
            long count = asLong(readValue(row, "total"));
            if (!StringUtils.hasText(reason)) {
                continue;
            }
            reasonCounts.add(new ReasonCount(reason, count));
            if (AttributionService.REASON_SYNC_FAILED.equals(reason)) {
                syncFailedOrders += count;
            }
        }

        stats.setTotalOrders(totalOrders);
        stats.setAttributedOrders(attributedOrders);
        stats.setUnattributedOrders(unattributedOrders);
        stats.setPartialOrders(partialOrders);
        stats.setSyncFailedOrders(syncFailedOrders);
        stats.setUnattributedReasons(reasonCounts.stream()
                .sorted(Comparator.comparingLong(ReasonCount::count).reversed())
                .toList());
        return ok(stats);
    }

    @Operation(summary = "获取订单筛选选项", description = "返回订单页所需的筛选项候选值，包括状态、未归因原因、商品、渠道与团长。")
    @GetMapping("/filter-options")
    public ApiResult<OrderFilterOptions> getFilterOptions(
            @Parameter(description = "筛选项检索关键字。") @RequestParam(required = false) String keyword,
            @RequestAttribute(name = "userId", required = false) UUID userId,
            @RequestAttribute(name = "deptId", required = false) UUID deptId,
            @RequestAttribute(name = "dataScope", required = false) DataScope dataScope) {
        String cacheKey = FILTER_OPTIONS_CACHE_PREFIX + cacheKey(keyword, userId, deptId, dataScope);
        return ok(shortTtlCacheService.get(cacheKey, FILTER_OPTIONS_CACHE_TTL, () -> {
        QueryConditions conditions = new QueryConditions(keyword);
        OrderFilterOptions options = new OrderFilterOptions();

        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ColonelsettlementOrder> statusWrapper = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ColonelsettlementOrder>()
                .select("distinct order_status as value")
                .isNotNull("order_status")
                .orderByAsc("order_status")
                .last("limit 20");
        applyQueryDataScope(statusWrapper, userId, deptId, dataScope);
        options.setOrderStatuses(orderMapper.selectMaps(statusWrapper)
                .stream()
                .map(row -> toOrderStatusOption(row.get("value")))
                .filter(Objects::nonNull)
                .toList());

        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ColonelsettlementOrder> attrStatusWrapper = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ColonelsettlementOrder>()
                .select("distinct attribution_status as value")
                .isNotNull("attribution_status")
                .orderByAsc("attribution_status")
                .last("limit 20");
        applyQueryDataScope(attrStatusWrapper, userId, deptId, dataScope);
        options.setAttributionStatuses(orderMapper.selectMaps(attrStatusWrapper)
                .stream()
                .map(row -> toStatusOption(asText(row.get("value"))))
                .filter(Objects::nonNull)
                .toList());

        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ColonelsettlementOrder> reasonWrapper = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ColonelsettlementOrder>()
                .select("distinct attribution_remark as value")
                .eq("attribution_status", AttributionService.STATUS_UNATTRIBUTED)
                .isNotNull("attribution_remark")
                .orderByAsc("attribution_remark")
                .last("limit 50");
        applyQueryDataScope(reasonWrapper, userId, deptId, dataScope);
        options.setUnattributedReasons(orderMapper.selectMaps(reasonWrapper)
                .stream()
                .map(row -> toReasonOption(asText(row.get("value"))))
                .filter(Objects::nonNull)
                .toList());

        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ColonelsettlementOrder> productWrapper = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ColonelsettlementOrder>()
                .select("distinct product_id as value", "product_name as label")
                .isNotNull("product_id")
                .and(conditions.hasKeyword(), wrapper -> wrapper
                        .like("product_name", conditions.keyword())
                        .or()
                        .like("product_id", conditions.keyword()))
                .last("limit 50");
        applyQueryDataScope(productWrapper, userId, deptId, dataScope);
        options.setProducts(orderMapper.selectMaps(productWrapper)
                .stream()
                .map(this::toOptionItem)
                .filter(item -> StringUtils.hasText(item.value()))
                .toList());

        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ColonelsettlementOrder> channelWrapper = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ColonelsettlementOrder>()
                .select("distinct channel_user_name as value", "channel_user_name as label")
                .isNotNull("channel_user_name")
                .and(conditions.hasKeyword(), wrapper -> wrapper.like("channel_user_name", conditions.keyword()))
                .last("limit 50");
        applyQueryDataScope(channelWrapper, userId, deptId, dataScope);
        options.setChannels(orderMapper.selectMaps(channelWrapper)
                .stream()
                .map(this::toOptionItem)
                .filter(item -> StringUtils.hasText(item.value()))
                .toList());

        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ColonelsettlementOrder> colonelWrapper = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ColonelsettlementOrder>()
                .select("distinct colonel_user_name as value", "colonel_user_name as label")
                .isNotNull("colonel_user_name")
                .and(conditions.hasKeyword(), wrapper -> wrapper.like("colonel_user_name", conditions.keyword()))
                .last("limit 50");
        applyQueryDataScope(colonelWrapper, userId, deptId, dataScope);
        options.setColonels(orderMapper.selectMaps(colonelWrapper)
                .stream()
                .map(this::toOptionItem)
                .filter(item -> StringUtils.hasText(item.value()))
                .toList());

        // 部门下拉：按 dept_type 拆成"招商部门 / 渠道部门"两组。
        // 这里只列出 status=1 且未删除的业务组，order by sort_order, dept_name。
        // 元数据全量返回；订单维度的可见性由 applyDataScope 兜底，不会越权。
        options.setRecruiterDepartments(loadDeptOptions(DeptType.RECRUITER_GROUP));
        options.setChannelDepartments(loadDeptOptions(DeptType.CHANNEL_GROUP));
        return options;
        }));
    }

    private List<OptionItem> loadDeptOptions(String deptType) {
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SysDept> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SysDept>()
                        .eq("deleted", 0)
                        .eq("status", 1)
                        .eq("dept_type", deptType)
                        .orderByAsc("sort_order")
                        .orderByAsc("dept_name");
        return sysDeptMapper.selectList(wrapper).stream()
                .map(dept -> new OptionItem(
                        dept.getId() == null ? "" : dept.getId().toString(),
                        StringUtils.hasText(dept.getDeptName()) ? dept.getDeptName() : dept.getDeptCode()))
                .filter(item -> StringUtils.hasText(item.value()))
                .toList();
    }

    private LambdaQueryWrapper<ColonelsettlementOrder> buildWrapper(
            String orderId,
            String attributionStatus,
            String unattributedReason,
            String activityId,
            String productId,
            String channelKeyword,
            String colonelKeyword,
            Integer orderStatus,
            String startTime,
            String endTime,
            String timeField,
            String dashboardDiagnosis,
            List<UUID> recruiterDeptIds,
            List<UUID> channelDeptIds) {
        LocalDateTime start = parseLocalDateTime(startTime);
        LocalDateTime end = parseLocalDateTime(endTime);
        List<UUID> normalizedRecruiterDeptIds = normalizeUuidList(recruiterDeptIds);
        List<UUID> normalizedChannelDeptIds = normalizeUuidList(channelDeptIds);
        LambdaQueryWrapper<ColonelsettlementOrder> wrapper = new LambdaQueryWrapper<ColonelsettlementOrder>()
                .eq(ColonelsettlementOrder::getDeleted, 0)
                .eq(StringUtils.hasText(orderId), ColonelsettlementOrder::getOrderId, orderId)
                .eq(StringUtils.hasText(unattributedReason), ColonelsettlementOrder::getAttributionRemark, unattributedReason)
                .eq(StringUtils.hasText(activityId), ColonelsettlementOrder::getActivityId, activityId)
                .eq(StringUtils.hasText(productId), ColonelsettlementOrder::getProductId, productId)
                .eq(orderStatus != null, ColonelsettlementOrder::getOrderStatus, orderStatus)
                .in(!normalizedRecruiterDeptIds.isEmpty(), ColonelsettlementOrder::getDeptId, normalizedRecruiterDeptIds)
                .in(!normalizedChannelDeptIds.isEmpty(), ColonelsettlementOrder::getChannelDeptId, normalizedChannelDeptIds)
                .and(StringUtils.hasText(channelKeyword), nested -> nested
                        .like(ColonelsettlementOrder::getChannelUserName, channelKeyword)
                        .or()
                        .like(ColonelsettlementOrder::getChannelUserId, channelKeyword))
                .and(StringUtils.hasText(colonelKeyword), nested -> nested
                        .like(ColonelsettlementOrder::getColonelUserName, colonelKeyword)
                        .or()
                        .like(ColonelsettlementOrder::getColonelUserId, colonelKeyword));
        applyAttributionStatusFilter(wrapper, attributionStatus);
        applyTimeRange(wrapper, resolveTimeField(timeField), start, end);
        applyDashboardDiagnosisFilter(wrapper, null, dashboardDiagnosis);
        return wrapper;
    }

    /**
     * 仅保留非空 UUID，避免 IN 子句被空集合拖成 1=0 或抛 NPE。
     */
    private static List<UUID> normalizeUuidList(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream().filter(Objects::nonNull).distinct().toList();
    }

    /**
     * 把前端 CSV 形式（"a,b,c"）或重复同名参数（Spring 自动合并为 "a,b,c"）解析成 UUID 列表。
     * 非法 UUID 直接忽略，不向用户抛 400 —— 业务上等价于"该值没生效"，前端筛选不应因为一个脏值彻底失败。
     */
    private static List<UUID> parseUuidCsv(String csv) {
        if (!StringUtils.hasText(csv)) {
            return List.of();
        }
        List<UUID> result = new ArrayList<>();
        for (String token : csv.split(",")) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                result.add(UUID.fromString(trimmed));
            } catch (IllegalArgumentException ignored) {
                // 非法 UUID 跳过，不影响其他合法值
            }
        }
        return result;
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

    private void evictOrderDerivedCaches() {
        shortTtlCacheService.evictByPrefix(DASHBOARD_SUMMARY_CACHE_PREFIX);
        shortTtlCacheService.evictByPrefix(DASHBOARD_METRICS_CACHE_PREFIX);
        shortTtlCacheService.evictByPrefix(FILTER_OPTIONS_CACHE_PREFIX);
    }

    private QueryWrapper<ColonelsettlementOrder> buildStatsWrapper(
            String orderId,
            String attributionStatus,
            String unattributedReason,
            String activityId,
            String productId,
            String channelKeyword,
            String colonelKeyword,
            Integer orderStatus,
            String startTime,
            String endTime,
            String timeField,
            String dashboardDiagnosis,
            List<UUID> recruiterDeptIds,
            List<UUID> channelDeptIds) {
        LocalDateTime start = parseLocalDateTime(startTime);
        LocalDateTime end = parseLocalDateTime(endTime);
        List<UUID> normalizedRecruiterDeptIds = normalizeUuidList(recruiterDeptIds);
        List<UUID> normalizedChannelDeptIds = normalizeUuidList(channelDeptIds);
        QueryWrapper<ColonelsettlementOrder> wrapper = new QueryWrapper<>();
        wrapper.eq("deleted", 0)
                .eq(StringUtils.hasText(orderId), "order_id", orderId)
                .eq(StringUtils.hasText(unattributedReason), "attribution_remark", unattributedReason)
                .eq(StringUtils.hasText(activityId), "colonel_activity_id", activityId)
                .eq(StringUtils.hasText(productId), "product_id", productId)
                .eq(orderStatus != null, "order_status", orderStatus)
                .in(!normalizedRecruiterDeptIds.isEmpty(), "dept_id", normalizedRecruiterDeptIds)
                .in(!normalizedChannelDeptIds.isEmpty(), "channel_dept_id", normalizedChannelDeptIds)
                .and(StringUtils.hasText(channelKeyword), nested -> nested
                        .like("channel_user_name", channelKeyword)
                        .or()
                        .like("channel_user_id", channelKeyword))
                .and(StringUtils.hasText(colonelKeyword), nested -> nested
                        .like("colonel_user_name", colonelKeyword)
                        .or()
                        .like("colonel_user_id", colonelKeyword));
        applyAttributionStatusFilter(wrapper, attributionStatus);
        applyTimeRange(wrapper, resolveTimeField(timeField), start, end);
        applyDashboardDiagnosisFilter(wrapper, null, dashboardDiagnosis);
        return wrapper;
    }

    private void applyAttributionStatusFilter(
            LambdaQueryWrapper<ColonelsettlementOrder> wrapper,
            String attributionStatus) {
        if (wrapper == null || !StringUtils.hasText(attributionStatus)) {
            return;
        }
        if (AttributionService.STATUS_UNATTRIBUTED.equals(attributionStatus)) {
            wrapper.and(nested -> nested
                    .eq(ColonelsettlementOrder::getAttributionStatus, AttributionService.STATUS_UNATTRIBUTED)
                    .or()
                    .isNull(ColonelsettlementOrder::getAttributionStatus));
            return;
        }
        wrapper.eq(ColonelsettlementOrder::getAttributionStatus, attributionStatus);
    }

    private void applyAttributionStatusFilter(
            QueryWrapper<ColonelsettlementOrder> wrapper,
            String attributionStatus) {
        if (wrapper == null || !StringUtils.hasText(attributionStatus)) {
            return;
        }
        if (AttributionService.STATUS_UNATTRIBUTED.equals(attributionStatus)) {
            wrapper.and(nested -> nested
                    .eq("attribution_status", AttributionService.STATUS_UNATTRIBUTED)
                    .or()
                    .isNull("attribution_status"));
            return;
        }
        wrapper.eq("attribution_status", attributionStatus);
    }

    private void applyTimeRange(LambdaQueryWrapper<ColonelsettlementOrder> wrapper, String timeField, LocalDateTime start, LocalDateTime end) {
        if ("settle_time".equals(timeField)) {
            wrapper.ge(start != null, ColonelsettlementOrder::getSettleTime, start)
                    .le(end != null, ColonelsettlementOrder::getSettleTime, end);
            return;
        }
        wrapper.ge(start != null, ColonelsettlementOrder::getCreateTime, start)
                .le(end != null, ColonelsettlementOrder::getCreateTime, end);
    }

    private void applyTimeRange(QueryWrapper<ColonelsettlementOrder> wrapper, String timeField, LocalDateTime start, LocalDateTime end) {
        wrapper.ge(start != null, timeField, start)
                .le(end != null, timeField, end);
    }

    private void selectOrderListColumns(LambdaQueryWrapper<ColonelsettlementOrder> wrapper) {
        if (wrapper == null) {
            return;
        }
        wrapper.select(ColonelsettlementOrder.class, field -> !"extra_data".equals(field.getColumn()));
    }

    private String resolveTimeField(String timeField) {
        return "settleTime".equalsIgnoreCase(timeField) ? "settle_time" : "create_time";
    }

    private void applyDashboardDiagnosisFilter(
            LambdaQueryWrapper<ColonelsettlementOrder> wrapper,
            String alias,
            String dashboardDiagnosis) {
        if (wrapper == null || !StringUtils.hasText(dashboardDiagnosis)) {
            return;
        }
        String prefix = StringUtils.hasText(alias) ? alias + "." : "colonelsettlement_order.";
        applyDiagnosisSql(wrapper, prefix, dashboardDiagnosis.trim());
    }

    private void applyDashboardDiagnosisFilter(
            QueryWrapper<ColonelsettlementOrder> wrapper,
            String alias,
            String dashboardDiagnosis) {
        if (wrapper == null || !StringUtils.hasText(dashboardDiagnosis)) {
            return;
        }
        String prefix = StringUtils.hasText(alias) ? alias + "." : "colonelsettlement_order.";
        applyDiagnosisSql(wrapper, prefix, dashboardDiagnosis.trim());
    }

    private void applyDiagnosisSql(LambdaQueryWrapper<ColonelsettlementOrder> wrapper, String prefix, String diagnosis) {
        String normalizedDiagnosis = DashboardService.normalizeDiagnosisCategory(diagnosis);
        if (!StringUtils.hasText(normalizedDiagnosis)) {
            return;
        }
        String safePrefix = sanitizeDiagnosisSqlPrefix(prefix);
        String categorySql = DashboardService.diagnosisCategoryCaseSql(
                safePrefix + "colonel_activity_id",
                safePrefix + "second_colonel_activity_id",
                safePrefix + "product_id",
                safePrefix + "create_time",
                safePrefix + "colonel_buyin_id",
                safePrefix + "attribution_status",
                safePrefix + "attribution_remark"
        );
        wrapper.apply("(" + categorySql + ") = {0}", normalizedDiagnosis);
    }

    private void applyDiagnosisSql(QueryWrapper<ColonelsettlementOrder> wrapper, String prefix, String diagnosis) {
        String normalizedDiagnosis = DashboardService.normalizeDiagnosisCategory(diagnosis);
        if (!StringUtils.hasText(normalizedDiagnosis)) {
            return;
        }
        String safePrefix = sanitizeDiagnosisSqlPrefix(prefix);
        String categorySql = DashboardService.diagnosisCategoryCaseSql(
                safePrefix + "colonel_activity_id",
                safePrefix + "second_colonel_activity_id",
                safePrefix + "product_id",
                safePrefix + "create_time",
                safePrefix + "colonel_buyin_id",
                safePrefix + "attribution_status",
                safePrefix + "attribution_remark"
        );
        wrapper.apply("(" + categorySql + ") = {0}", normalizedDiagnosis);
    }

    private String sanitizeDiagnosisSqlPrefix(String prefix) {
        if (!StringUtils.hasText(prefix)) {
            return "colonelsettlement_order.";
        }
        String normalized = prefix.trim();
        if ("colonelsettlement_order.".equals(normalized) || "fo.".equals(normalized)) {
            return normalized;
        }
        return "colonelsettlement_order.";
    }

    private void applyDataScope(
            LambdaQueryWrapper<ColonelsettlementOrder> wrapper,
            UUID userId,
            UUID deptId,
            DataScope dataScope) {
        if (wrapper == null || dataScope == null) {
            return;
        }
        switch (dataScope) {
            case PERSONAL -> {
                if (userId != null) {
                    wrapper.eq(ColonelsettlementOrder::getUserId, userId);
                }
            }
            case DEPT -> {
                if (deptId != null) {
                    wrapper.eq(ColonelsettlementOrder::getDeptId, deptId);
                }
            }
            case ALL -> {
                // no filter
            }
        }
    }

    private void applyQueryDataScope(
            com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ColonelsettlementOrder> wrapper,
            UUID userId,
            UUID deptId,
            DataScope dataScope) {
        if (wrapper == null || dataScope == null) {
            return;
        }
        switch (dataScope) {
            case PERSONAL -> {
                if (userId != null) {
                    wrapper.eq("user_id", userId);
                }
            }
            case DEPT -> {
                if (deptId != null) {
                    wrapper.eq("dept_id", deptId);
                }
            }
            case ALL -> {
                // no filter
            }
        }
    }

    private void normalizeOrderRow(ColonelsettlementOrder order) {
        order.setUnattributedReason(order.getAttributionRemark());
    }

    private OptionItem toOptionItem(Map<String, Object> row) {
        String value = asText(row.get("value"));
        String label = row.get("label") == null ? value : String.valueOf(row.get("label"));
        return new OptionItem(value, StringUtils.hasText(label) ? label : value);
    }

    private OptionItem toOrderStatusOption(Object rawValue) {
        if (rawValue == null) {
            return null;
        }
        Integer value;
        if (rawValue instanceof Number number) {
            value = number.intValue();
        } else {
            try {
                value = Integer.parseInt(String.valueOf(rawValue));
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return new OptionItem(String.valueOf(value), orderStatusLabel(value));
    }

    private OptionItem toStatusOption(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return new OptionItem(value, attributionStatusLabel(value));
    }

    private OptionItem toReasonOption(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return new OptionItem(value, unattributedReasonLabel(value));
    }

    private String asText(Object raw) {
        return raw == null ? null : String.valueOf(raw);
    }

    private Object readValue(Map<String, Object> row, String key) {
        if (row == null || row.isEmpty() || key == null) {
            return null;
        }
        if (row.containsKey(key)) {
            return row.get(key);
        }
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (key.equalsIgnoreCase(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private long asLong(Object raw) {
        if (raw instanceof Number number) {
            return number.longValue();
        }
        if (raw == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(raw));
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private String orderStatusLabel(Integer value) {
        if (value == null) {
            return "";
        }
        return switch (value) {
            case 1 -> "已下单";
            case 2 -> "已发货";
            case 3 -> "已完成";
            case 4 -> "已取消";
            default -> "状态" + value;
        };
    }

    private String attributionStatusLabel(String value) {
        return switch (value) {
            case AttributionService.STATUS_ATTRIBUTED -> "已确认业绩";
            case AttributionService.STATUS_UNATTRIBUTED -> "待排查订单";
            case "PARTIAL" -> "部分归因";
            case "FAILED" -> "同步/归因失败";
            default -> value;
        };
    }

    private String unattributedReasonLabel(String value) {
        return switch (value) {
            case AttributionService.REASON_NO_PICK_SOURCE, "订单未携带推广参数" -> "订单未携带推广参数";
            case AttributionService.REASON_MAPPING_NOT_FOUND, "pick_source 未匹配到有效归因映射" -> "未找到对应推广链接";
            case AttributionService.REASON_COLONEL_MAPPING_NOT_FOUND -> "原生团长订单未找到归因映射";
            case AttributionService.REASON_COLONEL_MAPPING_AMBIGUOUS -> "原生团长订单命中多条归因映射";
            case AttributionService.REASON_TALENT_CLAIM_OWNER_CONFLICT -> "归因负责人和达人认领人不一致";
            case AttributionService.REASON_PRODUCT_NOT_FOUND -> "未匹配到本地商品库";
            case AttributionService.REASON_ACTIVITY_NOT_FOUND -> "商品未关联活动";
            case AttributionService.REASON_CHANNEL_NOT_FOUND -> "未匹配到渠道负责人";
            case AttributionService.REASON_SYNC_FAILED, "订单同步失败" -> "订单同步失败";
            case AttributionService.REASON_ATTRIBUTED -> "已确认业绩";
            default -> value;
        };
    }

    private long parseDateTime(String text) {
        if (text == null) return 0L;
        try {
            return LocalDateTime.parse(text, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    .toEpochSecond(ZoneOffset.ofHours(8));
        } catch (Exception e) {
            return 0L;
        }
    }

    private SyncRequest defaultSyncRequest() {
        LocalDateTime now = LocalDateTime.now();
        SyncRequest request = new SyncRequest();
        request.setStartTime(now.minusDays(30).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        request.setEndTime(now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        return request;
    }

    private LocalDateTime parseLocalDateTime(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return LocalDateTime.parse(text, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (Exception e) {
            return null;
        }
    }

    @Data
    public static class SyncRequest {
        @Schema(description = "开始时间，格式 yyyy-MM-dd HH:mm:ss。", example = "2026-04-01 00:00:00")
        private String startTime;

        @Schema(description = "结束时间，格式 yyyy-MM-dd HH:mm:ss。", example = "2026-04-28 23:59:59")
        private String endTime;
    }

    @Data
    public static class OrderCommissionBatchRequest {
        @Schema(description = "待补全业绩的订单号列表。")
        private List<String> orderIds;
    }

    @Data
    public static class ReplayAttributionRequest {
        @Schema(description = "指定需要重算的订单号列表；为空时按未归因订单筛选。")
        private List<String> orderIds;

        @Schema(description = "未归因原因筛选，例如 COLONEL_MAPPING_NOT_FOUND。仅在未指定 orderIds 时生效。")
        private String reason;

        @Schema(description = "批量扫描上限，默认 50，最大 200。仅在未指定 orderIds 时生效。")
        private Integer limit;

        @Schema(description = "是否仅预演不落库。默认 false。")
        private Boolean dryRun;
    }

    @Data
    public static class PerformanceBackfillRequest {
        @Schema(description = "指定需要回填的订单号列表；为空时按时间范围与 onlyMissing 扫描。")
        private List<String> orderIds;

        @Schema(description = "结算开始时间，格式 yyyy-MM-dd HH:mm:ss。")
        private String startTime;

        @Schema(description = "结算结束时间，格式 yyyy-MM-dd HH:mm:ss。")
        private String endTime;

        @Schema(description = "批量扫描上限，默认 200，最大 2000。仅在未指定 orderIds 时生效。")
        private Integer limit;

        @Schema(description = "是否仅处理尚未生成 performance_records 的订单。默认 true。")
        private Boolean onlyMissing;
    }

    @Data
    public static class OrderStats {
        private Long totalOrders;
        private Long attributedOrders;
        private Long unattributedOrders;
        private Long partialOrders;
        private Long syncFailedOrders;
        private LocalDateTime lastSyncTime;
        private List<ReasonCount> unattributedReasons;
    }

    public record ReasonCount(String reason, Long count) {
    }

    @Data
    public static class OrderFilterOptions {
        private List<OptionItem> orderStatuses;
        private List<OptionItem> attributionStatuses;
        private List<OptionItem> unattributedReasons;
        private List<OptionItem> products;
        private List<OptionItem> channels;
        private List<OptionItem> colonels;
        // 部门下拉（多选过滤用）。
        // value = sys_dept.id (UUID 字符串)，label = sys_dept.dept_name。
        // 注意：部门列表本身全量返回（不按 DataScope 过滤元数据），
        // 订单查询的可见性仍由 applyDataScope(...) 兜底，避免越权。
        private List<OptionItem> recruiterDepartments;
        private List<OptionItem> channelDepartments;
    }

    public record OptionItem(String value, String label) {
    }

    private record QueryConditions(String keyword) {
        boolean hasKeyword() {
            return StringUtils.hasText(keyword);
        }
    }
}
