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
import com.colonel.saas.domain.user.facade.UserDomainFacade;
import com.colonel.saas.domain.user.facade.dto.DepartmentOption;
import com.colonel.saas.config.DddRefactorProperties;
import com.colonel.saas.domain.order.facade.OrderDomainFacade;
import com.colonel.saas.domain.order.query.OrderDetailView;
import com.colonel.saas.domain.order.query.OrderQueryView;
import com.colonel.saas.domain.order.query.OrderListAssembler;
import com.colonel.saas.domain.order.query.OrderDetailAssembler;
import com.colonel.saas.service.AttributionService;
import com.colonel.saas.service.CommissionService;
import com.colonel.saas.service.DashboardService;
import com.colonel.saas.service.OperationLogService;
import com.colonel.saas.service.OrderAttributionReplayService;
import com.colonel.saas.service.Order1603SettlementDryRunService;
import com.colonel.saas.service.Order2704SettlementDryRunService;
import com.colonel.saas.service.Order6468PaginationDryRunService;
import com.colonel.saas.service.OrderQueryService;
import com.colonel.saas.service.OrderService;
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

/**
 * 订单管理控制器。
 * <p>
 * 负责订单域的全部 HTTP 接口，包括订单同步、分页查询、统计汇总、筛选项候选值、
 * 详情查询以及历史订单归因重算和业绩回填等运维操作。
 * </p>
 *
 * <ul>
 *   <li>订单同步：从抖店上游拉取并落库订单数据（仅管理员）</li>
 *   <li>订单列表：按多维筛选条件分页查询订单归因结果</li>
 *   <li>未归因订单：单独分页查询未归因订单，便于排查</li>
 *   <li>订单详情：查询单个订单的归因、推广映射、达人与寄样关联信息</li>
 *   <li>订单统计：按当前筛选条件统计总量、已归因、未归因与未归因原因分布</li>
 *   <li>筛选选项：返回订单页所需的全部筛选候选值（状态、商品、渠道、团长、部门等）</li>
 *   <li>归因重算：对已落库订单重新执行归因逻辑，用于补映射后的历史订单回放验证</li>
 *   <li>业绩回填：批量写入缺失的 performance_records 业绩记录</li>
 *   <li>提成计算：批量或单笔计算并持久化双轨提成到 performance_records</li>
 * </ul>
 *
 * <h3>架构角色</h3>
 * <p>本控制器属于订单域的入口层（API Gateway），通过构造器注入委托给多个领域服务完成业务逻辑，
 * 自身仅负责参数解析、数据范围权限注入和缓存失效管理。</p>
 *
 * <h3>API 路径前缀</h3>
 * <p>{@code /orders}</p>
 *
 * <h3>业务领域</h3>
 * <p>订单域 —— 订单归因、订单查询、订单同步、提成计算、业绩回填</p>
 *
 * <h3>访问控制</h3>
 * <p>类级别要求具备以下角色之一：BIZ_LEADER、BIZ_STAFF、CHANNEL_LEADER、CHANNEL_STAFF、ADMIN。
 * 部分写操作接口额外限定为 ADMIN 角色。数据范围（self / group / all）通过请求属性注入，
 * 在查询层自动拼接过滤条件。</p>
 *
 * @see com.colonel.saas.service.OrderSyncService 订单同步服务
 * @see com.colonel.saas.service.OrderQueryService 订单查询服务
 * @see com.colonel.saas.service.AttributionService 归因服务
 * @see com.colonel.saas.service.CommissionService 提成计算服务
 * @see com.colonel.saas.service.PerformanceBackfillService 业绩回填服务
 * @see com.colonel.saas.service.OrderAttributionReplayService 归因重算服务
 * @see com.colonel.saas.common.base.BaseController 基础控制器
 */
@Tag(name = "订单管理", description = "订单同步、列表、统计、筛选项与详情查询接口。")
@Validated
@RequireRoles({RoleCodes.BIZ_LEADER, RoleCodes.BIZ_STAFF, RoleCodes.CHANNEL_LEADER, RoleCodes.CHANNEL_STAFF, RoleCodes.ADMIN})
@RestController
@RequestMapping("/orders")
public class OrderController extends BaseController {

    /** 筛选项缓存过期时间：60 秒 */
    private static final Duration FILTER_OPTIONS_CACHE_TTL = Duration.ofSeconds(60);

    /** 筛选项缓存键前缀 */
    private static final String FILTER_OPTIONS_CACHE_PREFIX = "orders:filter-options:";

    /** Dashboard 汇总缓存键前缀 */
    private static final String DASHBOARD_SUMMARY_CACHE_PREFIX = "dashboard:summary:";

    /** Dashboard 指标缓存键前缀 */
    private static final String DASHBOARD_METRICS_CACHE_PREFIX = "dashboard:metrics:";

    /** 订单同步服务：从抖店上游拉取订单数据并落库 */
    private final OrderSyncService orderSyncService;

    /** 订单 MyBatis-Plus Mapper：直接数据库操作 */
    private final ColonelsettlementOrderMapper orderMapper;

    /** 订单查询服务：封装订单详情等复合查询逻辑 */
    private final OrderQueryService orderQueryService;

    /** 归因重算服务：对历史订单重新执行归因逻辑 */
    private final OrderAttributionReplayService orderAttributionReplayService;

    /** 操作日志服务：记录管理员手动操作的审计日志 */
    private final OperationLogService operationLogService;

    /** 短 TTL 缓存服务：用于筛选选项等高频读取场景的秒级缓存 */
    private final ShortTtlCacheService shortTtlCacheService;

    /** 提成计算服务：批量/单笔计算并持久化双轨提成 */
    private final CommissionService commissionService;

    /** 业绩回填服务：批量写入缺失的 performance_records */
    private final PerformanceBackfillService performanceBackfillService;

    /** 用户域 Facade：用于加载部门下拉选项 */
    private final UserDomainFacade userDomainFacade;

    /** 6468 历史订单分页 dry-run 服务：只读拉取上游并聚合候选口径 */
    private final Order6468PaginationDryRunService order6468PaginationDryRunService;

    /** 1603 结算口径 dry-run 服务：只读拉取上游并模拟双轨字段映射 */
    private final Order1603SettlementDryRunService order1603SettlementDryRunService;

    /** 2704 多结算订单 dry-run 服务：只读拉取上游并输出官方口径对照证据 */
    private final Order2704SettlementDryRunService order2704SettlementDryRunService;

    /**
     * 订单域查询服务：封装筛选条件构造与分页 / 统计聚合（t2-orders 抽 service）。
     * <p>
     * 历史实现中本 controller 自身持有 14 参数 {@code buildWrapper}、归因 / 时间 / 数据范围 / 诊断分类
     * 应用等私有方法，单测只能通过 {@code MockMvc} 端到端验证，wrapper 拼装逻辑被锁在 controller
     * 内部。现委托给 {@link OrderService}，controller 仍负责 HTTP 接入层（参数解析、缓存、审计日志），
     * 业务层逻辑可独立单测。
     * </p>
     */
    private final OrderService orderService;

    private final DddRefactorProperties dddRefactorProperties;
    private final OrderDomainFacade orderDomainFacade;

    public OrderController(
            OrderSyncService orderSyncService,
            ColonelsettlementOrderMapper orderMapper,
            OrderQueryService orderQueryService,
            OrderAttributionReplayService orderAttributionReplayService,
            OperationLogService operationLogService,
            ShortTtlCacheService shortTtlCacheService,
            CommissionService commissionService,
            PerformanceBackfillService performanceBackfillService,
            UserDomainFacade userDomainFacade,
            Order6468PaginationDryRunService order6468PaginationDryRunService,
            Order1603SettlementDryRunService order1603SettlementDryRunService,
            Order2704SettlementDryRunService order2704SettlementDryRunService,
            OrderService orderService,
            DddRefactorProperties dddRefactorProperties,
            OrderDomainFacade orderDomainFacade) {
        this.orderSyncService = orderSyncService;
        this.orderMapper = orderMapper;
        this.orderQueryService = orderQueryService;
        this.orderAttributionReplayService = orderAttributionReplayService;
        this.operationLogService = operationLogService;
        this.shortTtlCacheService = shortTtlCacheService;
        this.commissionService = commissionService;
        this.performanceBackfillService = performanceBackfillService;
        this.userDomainFacade = userDomainFacade;
        this.order6468PaginationDryRunService = order6468PaginationDryRunService;
        this.order1603SettlementDryRunService = order1603SettlementDryRunService;
        this.order2704SettlementDryRunService = order2704SettlementDryRunService;
        this.orderService = orderService;
        this.dddRefactorProperties = dddRefactorProperties;
        this.orderDomainFacade = orderDomainFacade;
    }

    /**
     * 手动同步订单。
     * <p>
     * 触发近实时订单同步，用于联调真实网关回流数据。仅管理员可执行。
     * </p>
     *
     * <ol>
     *   <li>第一步：补全请求参数，未传入时默认最近 30 天，仅用于审计记录</li>
     *   <li>第二步：委托 {@link OrderSyncService#syncInstituteOrdersHotRecent()} 执行 6468 近实时同步</li>
     *   <li>第四步：记录操作审计日志</li>
     *   <li>第五步：清除所有订单衍生缓存（筛选项、Dashboard 汇总与指标）</li>
     * </ol>
     *
     * @param request 同步时间范围请求体，可选；为空时默认最近 30 天，当前仅用于审计记录
     * @param userId  当前登录用户 ID（由拦截器注入）
     * @return 同步结果，包含 created/updated/attributed/unattributed/failed 计数
     */
    @Operation(summary = "手动同步订单", description = "触发 6468 近实时订单同步，用于联调真实网关回流数据。")
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
        OrderSyncService.SyncResult result = orderSyncService.syncInstituteOrdersHotRecent();
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

    /**
     * 按明确时间范围同步订单。
     * <p>
     * 用于历史窗口补偿和真实上游差异回灌。与 {@link #syncOrders(SyncRequest, UUID)} 不同，
     * 本入口会实际使用请求中的 startTime / endTime 调用同步服务。
     * </p>
     */
    @Operation(summary = "按时间范围同步订单", description = "按明确 startTime/endTime 调用真实上游同步，用于历史窗口补偿。")
    @RequireRoles({RoleCodes.ADMIN})
    @PostMapping("/sync-range")
    public ApiResult<OrderSyncService.SyncResult> syncOrdersByRange(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "订单同步时间范围，格式 yyyy-MM-dd HH:mm:ss。",
                    required = true,
                    content = @Content(examples = @ExampleObject(value = "{\"startTime\":\"2026-06-12 00:00:00\",\"endTime\":\"2026-06-13 00:00:00\"}"))
            )
            @RequestBody SyncRequest request,
            @RequestAttribute("userId") UUID userId) {
        if (request == null || !StringUtils.hasText(request.getStartTime()) || !StringUtils.hasText(request.getEndTime())) {
            throw new IllegalArgumentException("startTime and endTime are required");
        }
        long start = parseDateTime(request.getStartTime());
        long end = parseDateTime(request.getEndTime());
        if (start <= 0L || end <= 0L || start >= end) {
            throw new IllegalArgumentException("Invalid sync time range");
        }
        OrderSyncService.SyncResult result = orderSyncService.syncByTimeRange(start, end);
        operationLogService.recordSystemAction(
                userId,
                "订单归因",
                "按时间范围同步订单",
                "POST",
                "order_sync_range",
                null,
                request.getStartTime() + " ~ " + request.getEndTime(),
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

    /**
     * 6468 历史订单分页 dry-run。
     * <p>
     * 只读调用 buyin.instituteOrderColonel，按 6468 data.cursor 继续翻页，
     * 聚合不同候选口径并与 3739 基准对比。该接口不落库、不清缓存、不写操作日志。
     * </p>
     */
    @Operation(summary = "6468 订单分页 dry-run", description = "只读拉全 6468 cursor 分页并聚合候选口径，不写订单表。")
    @RequireRoles({RoleCodes.ADMIN})
    @PostMapping("/6468-pagination-dry-run")
    public ApiResult<Order6468PaginationDryRunService.DryRunResult> dryRun6468Pagination(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "6468 dry-run 请求。startTime/endTime 格式 yyyy-MM-dd HH:mm:ss。",
                    required = true,
                    content = @Content(examples = @ExampleObject(value = "{\"startTime\":\"2026-06-03 00:00:00\",\"endTime\":\"2026-06-06 13:30:00\",\"pageSize\":100,\"maxPages\":500,\"maxOrders\":50000}"))
            )
            @RequestBody Order6468PaginationDryRunRequest request) {
        Order6468PaginationDryRunRequest safeRequest =
                request == null ? new Order6468PaginationDryRunRequest() : request;
        long start = parseDateTime(safeRequest.getStartTime());
        long end = parseDateTime(safeRequest.getEndTime());
        long filterStart = StringUtils.hasText(safeRequest.getFilterStartTime())
                ? parseDateTime(safeRequest.getFilterStartTime())
                : start;
        long filterEnd = StringUtils.hasText(safeRequest.getFilterEndTime())
                ? parseDateTime(safeRequest.getFilterEndTime())
                : end;
        Order6468PaginationDryRunService.DryRunRequest command =
                new Order6468PaginationDryRunService.DryRunRequest(
                        start,
                        end,
                        safeRequest.getPageSize() == null ? 0 : safeRequest.getPageSize(),
                        safeRequest.getMaxPages() == null ? 0 : safeRequest.getMaxPages(),
                        safeRequest.getMaxOrders() == null ? 0 : safeRequest.getMaxOrders(),
                        filterStart,
                        filterEnd
                );
        return ok(order6468PaginationDryRunService.dryRun(command));
    }

    /**
     * 1603 结算口径 dry-run。
     * <p>
     * 只读调用 buyin.instituteOrderColonel，模拟结算字段映射并输出 warnings / confidence。
     * 该接口不落库、不触发归因、不发布订单事件、不清缓存、不写操作日志。
     * </p>
     */
    @Operation(summary = "1603 查询团长订单（结算口径）dry-run", description = "只读调用 1603 结算口径并模拟双轨字段映射，不写订单表。")
    @RequireRoles({RoleCodes.ADMIN})
    @PostMapping("/1603-settlement-dry-run")
    public ApiResult<Order1603SettlementDryRunService.DryRunResult> dryRun1603Settlement(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "1603 结算口径 dry-run 请求。startTime/endTime 格式 yyyy-MM-dd HH:mm:ss。",
                    required = true,
                    content = @Content(examples = @ExampleObject(value = "{\"startTime\":\"2026-06-03 00:00:00\",\"endTime\":\"2026-06-06 13:30:00\",\"timeType\":\"settle\",\"pageSize\":20,\"maxPages\":3,\"maxOrders\":100}"))
            )
            @RequestBody Order1603SettlementDryRunRequest request) {
        Order1603SettlementDryRunRequest safeRequest =
                request == null ? new Order1603SettlementDryRunRequest() : request;
        Order1603SettlementDryRunService.DryRunRequest command =
                new Order1603SettlementDryRunService.DryRunRequest(
                        safeRequest.getStartTime(),
                        safeRequest.getEndTime(),
                        safeRequest.getTimeType(),
                        safeRequest.getPageSize(),
                        safeRequest.getCursor(),
                        safeRequest.getMaxPages(),
                        safeRequest.getMaxOrders(),
                        safeRequest.getOrderIds()
                );
        return ok(order1603SettlementDryRunService.dryRun(command));
    }

    /**
     * 2704 多结算订单 dry-run。
     * <p>
     * 只读调用 buyin.colonelMultiSettlementOrders，聚合字段求和并与本地结算日订单做差异对照。
     * 该接口不落库、不触发归因、不发布订单事件、不清缓存、不写操作日志。
     * </p>
     */
    @Operation(summary = "2704 多结算订单 dry-run", description = "只读调用 2704 多结算订单接口，输出聚合、字段求和和 upstream/local 差异清单。")
    @RequireRoles({RoleCodes.ADMIN})
    @PostMapping("/2704-settlement-dry-run")
    public ApiResult<Order2704SettlementDryRunService.DryRunResult> dryRun2704Settlement(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "2704 结算口径 dry-run 请求。startTime/endTime 格式 yyyy-MM-dd HH:mm:ss。",
                    required = true,
                    content = @Content(examples = @ExampleObject(value = "{\"startTime\":\"2026-06-12 00:00:00\",\"endTime\":\"2026-06-13 00:00:00\",\"timeType\":\"settle\",\"pageSize\":100,\"maxPages\":500,\"maxOrders\":50000,\"maxDiffOrderIds\":500}"))
            )
            @RequestBody Order2704SettlementDryRunRequest request) {
        Order2704SettlementDryRunRequest safeRequest =
                request == null ? new Order2704SettlementDryRunRequest() : request;
        Order2704SettlementDryRunService.DryRunRequest command =
                new Order2704SettlementDryRunService.DryRunRequest(
                        safeRequest.getStartTime(),
                        safeRequest.getEndTime(),
                        safeRequest.getTimeType(),
                        safeRequest.getPageSize(),
                        safeRequest.getCursor(),
                        safeRequest.getMaxPages(),
                        safeRequest.getMaxOrders(),
                        safeRequest.getMaxDiffOrderIds(),
                        safeRequest.getOrderIds()
                );
        return ok(order2704SettlementDryRunService.dryRun(command));
    }

    /**
     * 重算历史订单归因。
     * <p>
     * 对已落库订单重新执行归因逻辑，用于补映射后的历史订单回放验证。默认只扫描未归因订单。
     * 支持 dry-run 模式（仅预演不落库），仅管理员可执行。
     * </p>
     *
     * <ol>
     *   <li>第一步：补全请求参数，默认空请求对象</li>
     *   <li>第二步：判断是否为 dry-run 模式</li>
     *   <li>第三步：委托 {@link OrderAttributionReplayService#replay} 执行归因重算</li>
     *   <li>第四步：记录操作审计日志（区分预览/实际执行）</li>
     *   <li>第五步：非 dry-run 模式下清除订单衍生缓存</li>
     * </ol>
     *
     * @param request 归因重算请求体，可选；支持指定 orderIds 或按未归因原因批量扫描
     * @param userId  当前登录用户 ID（由拦截器注入）
     * @return 重算结果，包含 scanned/attributed/unattributed/updated 计数
     */
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

    /**
     * 回填历史业绩记录。
     * <p>
     * 按订单号或结算时间范围批量写入 performance_records，仅管理员可执行。
     * 默认仅处理尚未生成业绩记录的订单（onlyMissing=true）。
     * </p>
     *
     * <ol>
     *   <li>第一步：补全请求参数，onlyMissing 默认为 true</li>
     *   <li>第二步：委托 {@link PerformanceBackfillService#backfill} 执行回填</li>
     *   <li>第三步：记录操作审计日志</li>
     *   <li>第四步：清除所有订单衍生缓存</li>
     * </ol>
     *
     * @param request 回填请求体，可选；支持指定 orderIds 或按结算时间范围扫描缺失记录
     * @param userId  当前登录用户 ID（由拦截器注入）
     * @return 回填结果，包含 scanned/upserted/failed/onlyMissing 信息
     */
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

    /**
     * 重算失效/退款订单上的过期有效业绩记录。
     */
    @Operation(summary = "重算失效订单过期业绩", description = "扫描 order_status=4/5 且 performance_records.is_valid=true 的订单并重算冲正。")
    @RequireRoles({RoleCodes.ADMIN})
    @PostMapping("/performance-reconcile-invalidated")
    public ApiResult<PerformanceBackfillService.BackfillResult> reconcileInvalidatedPerformance(
            @RequestBody(required = false) PerformanceReconcileRequest request,
            @RequestAttribute("userId") UUID userId) {
        PerformanceReconcileRequest safeRequest = request == null ? new PerformanceReconcileRequest() : request;
        PerformanceBackfillService.BackfillResult result =
                performanceBackfillService.reconcileInvalidatedPerformance(safeRequest.getLimit());
        operationLogService.recordSystemAction(
                userId,
                "订单业绩",
                "重算失效订单过期业绩",
                "POST",
                "performance_reconcile_invalidated",
                null,
                null,
                String.format("scanned=%d, upserted=%d, failed=%d", result.scanned(), result.upserted(), result.failed()));
        evictOrderDerivedCaches();
        return ok(result);
    }

    /**
     * 批量补全订单业绩（双轨提成计算）。
     * <p>
     * 按订单号批量计算并持久化双轨提成到 performance_records（Y-08）；
     * 取消/失效订单自动标记为冲正。
     * </p>
     *
     * <ol>
     *   <li>第一步：从请求中提取并去重订单号列表，过滤空白值</li>
     *   <li>第二步：订单号列表为空时直接返回空结果</li>
     *   <li>第三步：从数据库查询未删除的订单记录</li>
     *   <li>第四步：委托 {@link CommissionService#batchUpsertPerformanceRecords} 批量计算提成并持久化</li>
     * </ol>
     *
     * @param request 批量提成请求体，包含待补全业绩的订单号列表
     * @return 每个订单的提成计算结果列表
     */
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

    /**
     * 管理员单笔重算业绩。
     * <p>
     * 传入单个 orderId，重算并回写 performance_records（Y-09）。仅管理员可执行。
     * 若订单不存在或 orderId 为空，返回冲正标记结果。
     * </p>
     *
     * <ol>
     *   <li>第一步：校验 orderId 是否非空，为空则返回冲正结果</li>
     *   <li>第二步：按 orderId 查询单条未删除的订单记录</li>
     *   <li>第三步：订单不存在时返回冲正标记结果</li>
     *   <li>第四步：委托 {@link CommissionService#batchUpsertPerformanceRecords} 执行单笔提成计算</li>
     * </ol>
     *
     * @param orderId 订单 ID（请求参数）
     * @return 单笔订单的提成计算结果；订单不存在时返回冲正标记
     */
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

    /**
     * 获取订单列表（分页查询）。
     * <p>
     * 按多维筛选条件分页查询订单归因列表，用于订单主页面。
     * 支持按订单 ID、归因状态、未归因原因、活动、商品、渠道/团长关键字、
     * 订单状态、时间范围、诊断分类、部门等条件过滤。
     * </p>
     *
     * <ol>
     *   <li>第一步：构建分页参数和 LambdaQueryWrapper 查询条件</li>
     *   <li>第二步：排除 extra_data 大字段，减少网络传输</li>
     *   <li>第三步：根据用户数据范围（self/group/all）注入权限过滤条件</li>
     *   <li>第四步：按 updateTime 和 createTime 降序排列</li>
     *   <li>第五步：执行分页查询并规范化每行数据</li>
     * </ol>
     *
     * @param page                 页码，从 1 开始，最大 1000
     * @param size                 每页条数，最大 200
     * @param orderId              订单 ID 精确过滤
     * @param attributionStatus    归因状态过滤（ATTRIBUTED / UNATTRIBUTED / PARTIAL / FAILED）
     * @param unattributedReason   未归因原因过滤
     * @param activityId           活动 ID 过滤
     * @param productId            商品 ID 过滤
     * @param channelKeyword       渠道关键字（模糊匹配渠道名称或渠道 ID）
     * @param colonelKeyword       团长关键字（模糊匹配团长名称或团长 ID）
     * @param orderStatus          订单状态过滤
     * @param startTime            开始时间，格式 yyyy-MM-dd HH:mm:ss
     * @param endTime              结束时间，格式 yyyy-MM-dd HH:mm:ss
     * @param timeField            时间字段（createTime 或 settleTime，默认 createTime）
     * @param dashboardDiagnosis   Dashboard 诊断分类过滤
     * @param recruiterDeptIds     招商部门 ID 列表（CSV 格式）
     * @param channelDeptIds       渠道部门 ID 列表（CSV 格式）
     * @param userId               当前登录用户 ID
     * @param deptId               当前登录用户所属部门 ID
     * @param dataScope            数据范围枚举（PERSONAL / DEPT / ALL）
     * @return 订单分页结果，记录已排除 extra_data 字段
     */
    @Operation(summary = "获取订单列表", description = "分页查询订单归因列表，用于订单主页面。")
    @GetMapping
    public ApiResult<IPage<OrderQueryView>> getOrders(
            @Parameter(description = "页码，从 1 开始，最大 1000。") @RequestParam(name = "page", defaultValue = "1") @Min(1) @Max(1000) long page,
            @Parameter(description = "每页条数，最大 200。") @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(200) long size,
            @Parameter(description = "订单 ID。") @RequestParam(name = "orderId", required = false) String orderId,
            @Parameter(description = "归因状态，例如 ATTRIBUTED、UNATTRIBUTED。完整取值以代码常量为准。") @RequestParam(name = "attributionStatus", required = false) String attributionStatus,
            @Parameter(description = "未归因原因。完整取值以代码常量为准。") @RequestParam(name = "unattributedReason", required = false) String unattributedReason,
            @Parameter(description = "活动 ID。") @RequestParam(name = "activityId", required = false) String activityId,
            @Parameter(description = "商品 ID。") @RequestParam(name = "productId", required = false) String productId,
            @Parameter(description = "渠道关键字，可匹配渠道名称或渠道 ID。") @RequestParam(name = "channelKeyword", required = false) String channelKeyword,
            @Parameter(description = "团长关键字，可匹配团长名称或团长 ID。") @RequestParam(name = "colonelKeyword", required = false) String colonelKeyword,
            @Parameter(description = "订单状态。完整映射以代码标签函数为准。") @RequestParam(name = "orderStatus", required = false) Integer orderStatus,
            @Parameter(description = "开始时间，格式 yyyy-MM-dd HH:mm:ss。") @RequestParam(name = "startTime", required = false) String startTime,
            @Parameter(description = "结束时间，格式 yyyy-MM-dd HH:mm:ss。") @RequestParam(name = "endTime", required = false) String endTime,
            @Parameter(description = "时间字段，支持 createTime 或 settleTime。默认 createTime。") @RequestParam(name = "timeField", required = false) String timeField,
            @Parameter(description = "Dashboard 诊断分类过滤。") @RequestParam(name = "dashboardDiagnosis", required = false) String dashboardDiagnosis,
            @Parameter(description = "招商部门 ID 列表（dept_id IN ...），CSV 或重复同名参数；非法 UUID 将被忽略。") @RequestParam(name = "recruiterDeptIds", required = false) String recruiterDeptIds,
            @Parameter(description = "渠道部门 ID 列表（channel_dept_id IN ...），CSV 或重复同名参数；非法 UUID 将被忽略。") @RequestParam(name = "channelDeptIds", required = false) String channelDeptIds,
            @RequestAttribute(name = "userId", required = false) UUID userId,
            @RequestAttribute(name = "deptId", required = false) UUID deptId,
            @RequestAttribute(name = "dataScope", required = false) DataScope dataScope) {
        if (dddRefactorProperties.isEnabled() && dddRefactorProperties.getOrderApplication().isEnabled()) {
            IPage<OrderQueryView> dddResult = orderDomainFacade.getOrders(
                    page, size, orderId, attributionStatus, unattributedReason, activityId, productId,
                    channelKeyword, colonelKeyword, orderStatus, startTime, endTime, timeField,
                    dashboardDiagnosis, recruiterDeptIds, channelDeptIds, userId, deptId, dataScope
            );
            return ok(dddResult);
        }
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
        result.getRecords().forEach(orderService::normalizeOrderRow);
        orderService.enrichOrderList(result.getRecords());
        return ok(result.convert(OrderListAssembler::toView));
    }

    /**
     * 获取未归因订单（分页查询）。
     * <p>
     * 分页查询未归因订单列表，用于未归因排查。内部直接委托 {@link #getOrders}，
     * 强制设置归因状态为 UNATTRIBUTED，其余筛选条件与订单列表保持一致。
     * </p>
     *
     * <ol>
     *   <li>第一步：将归因状态强制设为 {@code STATUS_UNATTRIBUTED}</li>
     *   <li>第二步：委托 {@link #getOrders} 执行通用查询逻辑</li>
     * </ol>
     *
     * @param page                 页码，从 1 开始，最大 1000
     * @param size                 每页条数，最大 200
     * @param orderId              订单 ID 精确过滤
     * @param unattributedReason   未归因原因过滤
     * @param activityId           活动 ID 过滤
     * @param productId            商品 ID 过滤
     * @param channelKeyword       渠道关键字
     * @param colonelKeyword       团长关键字
     * @param orderStatus          订单状态过滤
     * @param startTime            开始时间
     * @param endTime              结束时间
     * @param timeField            时间字段
     * @param dashboardDiagnosis   Dashboard 诊断分类过滤
     * @param recruiterDeptIds     招商部门 ID 列表
     * @param channelDeptIds       渠道部门 ID 列表
     * @param userId               当前登录用户 ID
     * @param deptId               当前登录用户所属部门 ID
     * @param dataScope            数据范围枚举
     * @return 未归因订单分页结果
     */
    @Operation(summary = "获取未归因订单", description = "分页查询未归因订单列表，用于未归因排查。其余筛选条件与订单列表保持一致。")
    @GetMapping("/unattributed")
    public ApiResult<IPage<OrderQueryView>> getUnattributedOrders(
            @Parameter(description = "页码，从 1 开始，最大 1000。") @RequestParam(name = "page", defaultValue = "1") @Min(1) @Max(1000) long page,
            @Parameter(description = "每页条数，最大 200。") @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(200) long size,
            @Parameter(description = "订单 ID。") @RequestParam(name = "orderId", required = false) String orderId,
            @Parameter(description = "未归因原因。完整取值以代码常量为准。") @RequestParam(name = "unattributedReason", required = false) String unattributedReason,
            @Parameter(description = "活动 ID。") @RequestParam(name = "activityId", required = false) String activityId,
            @Parameter(description = "商品 ID。") @RequestParam(name = "productId", required = false) String productId,
            @Parameter(description = "渠道关键字，可匹配渠道名称或渠道 ID。") @RequestParam(name = "channelKeyword", required = false) String channelKeyword,
            @Parameter(description = "团长关键字，可匹配团长名称或团长 ID。") @RequestParam(name = "colonelKeyword", required = false) String colonelKeyword,
            @Parameter(description = "订单状态。完整映射以代码标签函数为准。") @RequestParam(name = "orderStatus", required = false) Integer orderStatus,
            @Parameter(description = "开始时间，格式 yyyy-MM-dd HH:mm:ss。") @RequestParam(name = "startTime", required = false) String startTime,
            @Parameter(description = "结束时间，格式 yyyy-MM-dd HH:mm:ss。") @RequestParam(name = "endTime", required = false) String endTime,
            @Parameter(description = "时间字段，支持 createTime 或 settleTime。默认 createTime。") @RequestParam(name = "timeField", required = false) String timeField,
            @Parameter(description = "Dashboard 诊断分类过滤。") @RequestParam(name = "dashboardDiagnosis", required = false) String dashboardDiagnosis,
            @Parameter(description = "招商部门 ID 列表（dept_id IN ...），CSV 或重复同名参数；非法 UUID 将被忽略。") @RequestParam(name = "recruiterDeptIds", required = false) String recruiterDeptIds,
            @Parameter(description = "渠道部门 ID 列表（channel_dept_id IN ...），CSV 或重复同名参数；非法 UUID 将被忽略。") @RequestParam(name = "channelDeptIds", required = false) String channelDeptIds,
            @RequestAttribute(name = "userId", required = false) UUID userId,
            @RequestAttribute(name = "deptId", required = false) UUID deptId,
            @RequestAttribute(name = "dataScope", required = false) DataScope dataScope) {
        if (dddRefactorProperties.isEnabled() && dddRefactorProperties.getOrderApplication().isEnabled()) {
            IPage<OrderQueryView> dddResult = orderDomainFacade.getOrders(
                    page, size, orderId, AttributionService.STATUS_UNATTRIBUTED, unattributedReason, activityId, productId,
                    channelKeyword, colonelKeyword, orderStatus, startTime, endTime, timeField,
                    dashboardDiagnosis, recruiterDeptIds, channelDeptIds, userId, deptId, dataScope
            );
            return ok(dddResult);
        }
        return getOrders(page, size, orderId, AttributionService.STATUS_UNATTRIBUTED, unattributedReason, activityId, productId, channelKeyword, colonelKeyword, orderStatus, startTime, endTime, timeField, dashboardDiagnosis, recruiterDeptIds, channelDeptIds, userId, deptId, dataScope);
    }

    /**
     * 获取订单详情。
     * <p>
     * 查询单个订单的完整信息，返回订单基础信息、归因结果、推广映射、达人与寄样关联信息。
     * 内部通过数据范围权限校验，确保当前用户有权访问该订单。
     * </p>
     *
     * <ol>
     *   <li>第一步：委托 {@link OrderQueryService#getOrderDetail} 查询订单详情</li>
     *   <li>第二步：服务内部完成数据范围权限校验</li>
     * </ol>
     *
     * @param orderId  订单 ID（路径变量）
     * @param userId   当前登录用户 ID
     * @param deptId   当前登录用户所属部门 ID
     * @param dataScope 数据范围枚举
     * @return 订单详情响应，包含归因结果、推广映射、达人与寄样关联等
     */
    @Operation(summary = "获取订单详情", description = "查询单个订单详情，返回订单基础信息、归因结果、推广映射、达人与寄样关联信息。")
    @GetMapping("/{orderId}")
    public ApiResult<OrderDetailView> getOrderDetail(
            @Parameter(description = "订单 ID。") @PathVariable("orderId") String orderId,
            @RequestAttribute(name = "userId", required = false) UUID userId,
            @RequestAttribute(name = "deptId", required = false) UUID deptId,
            @RequestAttribute(name = "dataScope", required = false) DataScope dataScope) {
        if (dddRefactorProperties.isEnabled() && dddRefactorProperties.getOrderApplication().isEnabled()) {
            return ok(orderDomainFacade.getOrderDetail(orderId, userId, deptId, dataScope));
        }
        OrderDetailResponse response = orderQueryService.getOrderDetail(orderId, userId, deptId, dataScope);
        return ok(OrderDetailAssembler.toView(response));
    }

    @Operation(summary = "获取订单统计", description = "按当前筛选条件统计订单总量、已归因数、未归因数与未归因原因分布。")
    @GetMapping("/stats")
    public ApiResult<OrderStats> getStats(
            @Parameter(description = "订单 ID。") @RequestParam(name = "orderId", required = false) String orderId,
            @Parameter(description = "归因状态，例如 ATTRIBUTED、UNATTRIBUTED。完整取值以代码常量为准。") @RequestParam(name = "attributionStatus", required = false) String attributionStatus,
            @Parameter(description = "未归因原因。完整取值以代码常量为准。") @RequestParam(name = "unattributedReason", required = false) String unattributedReason,
            @Parameter(description = "活动 ID。") @RequestParam(name = "activityId", required = false) String activityId,
            @Parameter(description = "商品 ID。") @RequestParam(name = "productId", required = false) String productId,
            @Parameter(description = "渠道关键字，可匹配渠道名称或渠道 ID。") @RequestParam(name = "channelKeyword", required = false) String channelKeyword,
            @Parameter(description = "团长关键字，可匹配团长名称或团长 ID。") @RequestParam(name = "colonelKeyword", required = false) String colonelKeyword,
            @Parameter(description = "订单状态。完整映射以代码标签函数为准。") @RequestParam(name = "orderStatus", required = false) Integer orderStatus,
            @Parameter(description = "开始时间，格式 yyyy-MM-dd HH:mm:ss。") @RequestParam(name = "startTime", required = false) String startTime,
            @Parameter(description = "结束时间，格式 yyyy-MM-dd HH:mm:ss。") @RequestParam(name = "endTime", required = false) String endTime,
            @Parameter(description = "时间字段，支持 createTime 或 settleTime。默认 createTime。") @RequestParam(name = "timeField", required = false) String timeField,
            @Parameter(description = "Dashboard 诊断分类过滤。") @RequestParam(name = "dashboardDiagnosis", required = false) String dashboardDiagnosis,
            @Parameter(description = "招商部门 ID 列表（dept_id IN ...），CSV 或重复同名参数；非法 UUID 将被忽略。") @RequestParam(name = "recruiterDeptIds", required = false) String recruiterDeptIds,
            @Parameter(description = "渠道部门 ID 列表（channel_dept_id IN ...），CSV 或重复同名参数；非法 UUID 将被忽略。") @RequestParam(name = "channelDeptIds", required = false) String channelDeptIds,
            @RequestAttribute(name = "userId", required = false) UUID userId,
            @RequestAttribute(name = "deptId", required = false) UUID deptId,
            @RequestAttribute(name = "dataScope", required = false) DataScope dataScope) {
        if (dddRefactorProperties.isEnabled() && dddRefactorProperties.getOrderApplication().isEnabled()) {
            return ok(orderDomainFacade.getStats(
                    orderId, attributionStatus, unattributedReason, activityId, productId,
                    channelKeyword, colonelKeyword, orderStatus, startTime, endTime, timeField,
                    dashboardDiagnosis, recruiterDeptIds, channelDeptIds, userId, deptId, dataScope
            ));
        }
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
            @Parameter(description = "筛选项检索关键字。") @RequestParam(name = "keyword", required = false) String keyword,
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
        // 同时列出父级部门（department）和对应业务组（recruiter_group / channel_group），
        // 兼容尚未创建子组的场景。order by sort_order, dept_name。
        // 元数据全量返回；订单维度的可见性由 applyDataScope 兜底，不会越权。
        options.setRecruiterDepartments(loadDeptOptions(DeptType.DEPARTMENT, DeptType.RECRUITER_GROUP));
        options.setChannelDepartments(loadDeptOptions(DeptType.DEPARTMENT, DeptType.CHANNEL_GROUP));
        return options;
        }));
    }

    private List<OptionItem> loadDeptOptions(String... deptTypes) {
        return userDomainFacade.listDepartments(java.util.Arrays.asList(deptTypes)).stream()
                .map(dept -> new OptionItem(
                        dept.id() == null ? "" : dept.id().toString(),
                        StringUtils.hasText(dept.deptName()) ? dept.deptName() : dept.deptCode()))
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
    public static class Order6468PaginationDryRunRequest {
        @Schema(description = "上游查询开始时间，格式 yyyy-MM-dd HH:mm:ss。", example = "2026-06-03 00:00:00")
        private String startTime;

        @Schema(description = "上游查询结束时间，格式 yyyy-MM-dd HH:mm:ss。", example = "2026-06-06 13:30:00")
        private String endTime;

        @Schema(description = "候选时间口径过滤开始时间；为空时使用 startTime。")
        private String filterStartTime;

        @Schema(description = "候选时间口径过滤结束时间；为空时使用 endTime。")
        private String filterEndTime;

        @Min(1)
        @Max(100)
        @Schema(description = "每页条数，默认 100，最大 100。")
        private Integer pageSize;

        @Min(1)
        @Max(500)
        @Schema(description = "最大页数，默认 500，最大 500。")
        private Integer maxPages;

        @Min(1)
        @Max(50000)
        @Schema(description = "最大订单行数，默认 50000，最大 50000。")
        private Integer maxOrders;
    }

    @Data
    public static class Order1603SettlementDryRunRequest {
        @Schema(description = "1603 查询开始时间，格式 yyyy-MM-dd HH:mm:ss。", example = "2026-06-03 00:00:00")
        private String startTime;

        @Schema(description = "1603 查询结束时间，格式 yyyy-MM-dd HH:mm:ss。", example = "2026-06-06 13:30:00")
        private String endTime;

        @Schema(description = "时间类型，默认 settle。")
        private String timeType;

        @Min(1)
        @Max(100)
        @Schema(description = "每页条数，默认 20，最大 100。")
        private Integer pageSize;

        @Schema(description = "游标，默认 0。")
        private String cursor;

        @Min(1)
        @Max(10)
        @Schema(description = "最大页数，默认 3，最大 10。")
        private Integer maxPages;

        @Min(1)
        @Max(500)
        @Schema(description = "最大订单行数，默认 100，最大 500。")
        private Integer maxOrders;

        @Schema(description = "订单号列表；1603 默认不强传给上游，仅用于 dry-run 请求回显为 warning。")
        private List<String> orderIds;
    }

    @Data
    public static class Order2704SettlementDryRunRequest {
        @Schema(description = "2704 查询开始时间，格式 yyyy-MM-dd HH:mm:ss。", example = "2026-06-12 00:00:00")
        private String startTime;

        @Schema(description = "2704 查询结束时间，格式 yyyy-MM-dd HH:mm:ss。", example = "2026-06-13 00:00:00")
        private String endTime;

        @Schema(description = "时间类型，默认 settle。")
        private String timeType;

        @Min(1)
        @Max(100)
        @Schema(description = "每页条数，默认 100，最大 100。")
        private Integer pageSize;

        @Schema(description = "游标，默认 0。")
        private String cursor;

        @Min(1)
        @Max(500)
        @Schema(description = "最大页数，默认 500，最大 500。")
        private Integer maxPages;

        @Min(1)
        @Max(50000)
        @Schema(description = "最大订单行数，默认 50000，最大 50000。")
        private Integer maxOrders;

        @Min(0)
        @Max(5000)
        @Schema(description = "返回差异订单号清单的最大数量，默认 500，最大 5000。")
        private Integer maxDiffOrderIds;

        @Schema(description = "订单号列表；为空时按时间范围查询。")
        private List<String> orderIds;
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
    public static class PerformanceReconcileRequest {
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
