package com.colonel.saas.service.data;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.common.base.BaseController;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.common.result.PageResult;
import com.colonel.saas.common.time.AppZone;
import com.colonel.saas.config.DddRefactorProperties;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.domain.order.facade.DataOrderQueryFacade;
import com.colonel.saas.domain.performance.facade.ExclusiveMerchantReadFacade;
import com.colonel.saas.domain.performance.facade.OrderPerformanceQueryFacade;
import com.colonel.saas.domain.product.facade.ProductActivityReadFacade;
import com.colonel.saas.domain.talent.facade.ExclusiveTalentReadFacade;
import com.colonel.saas.domain.user.facade.UserDomainFacade;
import com.colonel.saas.domain.user.policy.DataScopeResolver;
import com.colonel.saas.dto.performance.OrderPerformanceBatchResponse;
import com.colonel.saas.dto.performance.OrderPerformanceDTO;
import com.colonel.saas.entity.ColonelsettlementActivity;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.entity.ExclusiveMerchant;
import com.colonel.saas.entity.ExclusiveTalent;
import com.colonel.saas.entity.SysUser;
import com.colonel.saas.vo.data.OrderDetailVO;
import com.colonel.saas.service.CommissionService;
import com.colonel.saas.service.PerformanceMetricsQueryService;
import com.colonel.saas.service.ShortTtlCacheService;
import com.colonel.saas.domain.performance.policy.PerformanceAccessContext;
import com.colonel.saas.vo.ExclusiveMerchantStatusVO;
import com.colonel.saas.vo.ExclusiveTalentStatusVO;
import com.colonel.saas.vo.data.DualTrackMetricsVO;
import com.colonel.saas.vo.data.MetricsVO;
import com.colonel.saas.vo.data.OrderSummaryRowVO;
import com.colonel.saas.vo.data.OrderSummaryVO;
import com.colonel.saas.vo.data.OrderVO;
import com.colonel.saas.vo.data.TrendPointVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 数据平台控制器 (god service - 边缘服务, 不再 DDD 切片).
 *
 * <p><strong>当前状态 (2026-07-14):</strong></p>
 * <ul>
 *   <li>2455 行, 跨域聚合服务 (跨 colonel / order / product / talent 域)</li>
 *   <li>不切理由: 跨域聚合 + 单一职责已明确, 不再 DDD 切片</li>
 * </ul>
 * 数据平台控制器。
 * <p>
 * 负责提供数据页面专用的查询、统计、导出与运营监控接口，服务于前端数据看板与分析模块。
 * </p>
 * <ul>
 *   <li>订单数据页：分页查询订单列表、按日汇总订单明细与提成统计</li>
 *   <li>核心指标看板：查询首页核心指标（今日/趋势/双轨），支持 30 秒短 TTL 缓存</li>
 *   <li>CSV 导出：批量分页导出订单数据与活动列表，UTF-8 BOM 编码</li>
 *   <li>运营监控：分页查询达人/商家独家状态，支持按月份、关键字、状态筛选</li>
 * </ul>
 * <p>
 * <b>访问控制：</b>类级别要求 BIZ_LEADER / BIZ_STAFF / CHANNEL_LEADER / CHANNEL_STAFF 角色；
 * 导出与运营监控接口额外要求 ADMIN / BIZ_LEADER / CHANNEL_LEADER。
 * </p>
 * <p>
 * <b>数据范围：</b>通过 {@link DataScope} 注入实现行级数据过滤（PERSONAL / DEPT / ALL），
 * 所有查询均基于当前用户的 userId、deptId 与 dataScope 构建作用域。
 * </p>
 * <p>
 * <b>双轨模型：</b>核心指标支持两条时间轨道并行——
 * 结算时间（settleTime）用于已结算口径，创建时间（createTime）用于预估口径。
 * </p>
 *
 * @see com.colonel.saas.service.CommissionService 提成计算服务
 * @see com.colonel.saas.service.PerformanceMetricsQueryService 业绩指标聚合查询
 * @see com.colonel.saas.service.ShortTtlCacheService 短 TTL 缓存服务
 * @see DataOrderQueryFacade 订单事实只读门面
 * @see com.colonel.saas.common.enums.DataScope 数据范围枚举
 */
public class DataApplicationService extends BaseController {

    /** 导出批次大小：每批次从数据库查询 2000 条记录写入 CSV */
    private static final long EXPORT_BATCH_SIZE = 2000L;

    /** 核心指标缓存 TTL：30 秒，避免高并发下频繁查询数据库 */
    private static final Duration METRICS_CACHE_TTL = Duration.ofSeconds(30);

    /** 指标缓存键前缀，格式：dashboard:metrics:{track}:{scope}:{id} */
    private static final String METRICS_CACHE_PREFIX = "dashboard:metrics:";

    /** 订单汇总缓存 TTL：30 秒，与核心指标一致，避免重复实时聚合 */
    private static final Duration ORDER_SUMMARY_CACHE_TTL = Duration.ofSeconds(30);

    /** 订单汇总缓存键前缀，格式：dashboard:order-summary:{17 维} */
    private static final String ORDER_SUMMARY_CACHE_PREFIX = "dashboard:order-summary:";

    /** 退款订单识别条件：抖店退款状态或退款流转节点。 */
    private static final String REFUND_ORDER_PREDICATE = "(order_status = 5 OR UPPER(COALESCE(flow_point, '')) = 'REFUND')";

    /** 预估轨退款服务费口径：按预估服务费收益统计，扣除预估技术服务费与服务费支出。 */
    private static final String REFUND_ESTIMATE_SERVICE_FEE_EXPRESSION =
            "GREATEST(COALESCE(estimate_service_fee, 0) - COALESCE(estimate_tech_service_fee, 0) - COALESCE(estimate_service_fee_expense, 0), 0)";

    /** 结算轨退款服务费口径：按结算服务费收益统计，结算技术服务费仅展示不扣减。 */
    private static final String REFUND_EFFECTIVE_SERVICE_FEE_EXPRESSION =
            "GREATEST(COALESCE(NULLIF(effective_service_fee, 0), estimate_service_fee, 0) - COALESCE(effective_service_fee_expense, 0), 0)";

    /** 上游订单时间字符串常见格式。 */
    private static final DateTimeFormatter UPSTREAM_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 订单事实只读门面，负责订单表的基础查询与分页（含数据范围过滤） */
    private final DataOrderQueryFacade dataOrderQueryFacade;

    /** 提成计算服务，负责按活动桶计算团长/渠道/达人三方提成 */
    private final CommissionService commissionService;

    /** 达人域只读门面，负责独家达人监控数据查询 */
    private final ExclusiveTalentReadFacade exclusiveTalentReadFacade;

    /** 业绩域只读门面，负责独家商家监控数据查询 */
    private final ExclusiveMerchantReadFacade exclusiveMerchantReadFacade;

    /** 商品域活动只读门面，负责活动名称与活动列表导出查询 */
    private final ProductActivityReadFacade productActivityReadFacade;

    /** 短 TTL 缓存服务，用于指标缓存与筛选项缓存 */
    private final ShortTtlCacheService shortTtlCacheService;

    /** 业绩指标聚合查询服务，负责用订单事实补齐核心指标并关联 performance_records 业绩字段 */
    private final PerformanceMetricsQueryService performanceMetricsQueryService;

    /** 订单业绩查询门面，负责订单列表/详情的业绩补全 */
    private final OrderPerformanceQueryFacade orderPerformanceQueryFacade;

    /** JDBC 模板，用于复杂聚合查询（如 service profit 联合查询） */
    private final JdbcTemplate jdbcTemplate;

    /** 用户门面，负责查询渠道/招商负责人展示名称 */
    private final UserDomainFacade userDomainFacade;

    /** 用户域数据范围解析器，负责解释 PERSONAL / DEPT / ALL 的过滤决策 */
    private final DataScopeResolver dataScopeResolver;

    /** DDD 重构灰度开关，默认关闭以保持 Legacy 行为。 */
    private final DddRefactorProperties dddRefactorProperties;

    /**
     * 构造注入所有依赖服务与只读门面。
     *
     * @param dataOrderQueryFacade           订单事实只读门面
     * @param commissionService              提成计算服务
     * @param exclusiveTalentReadFacade      独家达人只读门面
     * @param exclusiveMerchantReadFacade    独家商家只读门面
     * @param productActivityReadFacade      活动只读门面
     * @param shortTtlCacheService           短 TTL 缓存服务
     * @param performanceMetricsQueryService 业绩指标聚合查询服务
     */
    public DataApplicationService(
            DataOrderQueryFacade dataOrderQueryFacade,
            CommissionService commissionService,
            ExclusiveTalentReadFacade exclusiveTalentReadFacade,
            ExclusiveMerchantReadFacade exclusiveMerchantReadFacade,
            ProductActivityReadFacade productActivityReadFacade,
            ShortTtlCacheService shortTtlCacheService,
            PerformanceMetricsQueryService performanceMetricsQueryService,
            OrderPerformanceQueryFacade orderPerformanceQueryFacade,
            UserDomainFacade userDomainFacade,
            DataScopeResolver dataScopeResolver,
            DddRefactorProperties dddRefactorProperties,
            JdbcTemplate jdbcTemplate) {
        this.dataOrderQueryFacade = dataOrderQueryFacade;
        this.commissionService = commissionService;
        this.exclusiveTalentReadFacade = exclusiveTalentReadFacade;
        this.exclusiveMerchantReadFacade = exclusiveMerchantReadFacade;
        this.productActivityReadFacade = productActivityReadFacade;
        this.shortTtlCacheService = shortTtlCacheService;
        this.performanceMetricsQueryService = performanceMetricsQueryService;
        this.orderPerformanceQueryFacade = orderPerformanceQueryFacade;
        this.userDomainFacade = userDomainFacade;
        this.dataScopeResolver = dataScopeResolver;
        this.dddRefactorProperties = dddRefactorProperties;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 订单分页查询（数据页）。
     * <p>
     * 为数据页面提供订单列表分页查询，支持多维度筛选与数据范围过滤。
     * 该接口服务于数据分析页面，与订单管理接口 {@link OrderController#getOrders} 不同。
     * </p>
     * <ol>
     *   <li>解析时间范围：未传入 startDate/endDate 时默认近 30 天</li>
     *   <li>构建筛选条件 QueryWrapper，应用各维度筛选参数</li>
     *   <li>应用当前用户的数据范围（PERSONAL / DEPT / ALL）</li>
     *   <li>执行分页查询，将实体转换为 OrderVO 返回</li>
     * </ol>
     *
     * @param page                页码，从 1 开始，最大 1000
     * @param size                每页条数，最大 200
     * @param orderId             订单号，支持模糊匹配
     * @param status              订单状态：ORDERED / SHIPPED / FINISHED / CANCELLED
     * @param talentId            达人 ID（UUID），精确匹配
     * @param merchantId          商家 merchant_id，精确匹配
     * @param productId           商品 ID，精确匹配
     * @param productName         商品名称/标题，模糊匹配
     * @param shopName            店铺名称，模糊匹配
     * @param talentName          达人昵称，模糊匹配
     * @param colonelName         团长/招商负责人名称，模糊匹配
     * @param channelName         渠道负责人名称，模糊匹配
     * @param colonelActivityId   团长活动 ID，精确匹配
     * @param recruitType         招商类型：MERCHANT（商家型招商单）或 PROMOTION（推广单）
     * @param startDate           开始日期，格式 yyyy-MM-dd
     * @param endDate             结束日期，格式 yyyy-MM-dd
     * @param timeField           时间字段：createTime（默认）或 settleTime
     * @param userId              当前用户 ID（从请求属性注入）
     * @param deptId              当前用户部门 ID（从请求属性注入）
     * @param dataScope           数据范围（从请求属性注入）
     * @return 订单分页结果，包含 OrderVO 列表与分页信息
     */
    @Operation(summary = "订单分页", description = "分页查询数据页订单列表。该接口服务于数据分析页面，不等同于订单主链路接口。")
    @GetMapping("/data/orders")
    public ApiResult<PageResult<OrderVO>> getOrderPage(
            @Parameter(description = "页码，从 1 开始，最大 1000。") @RequestParam(defaultValue = "1") @Min(1) @Max(1000) long page,
            @Parameter(description = "每页条数。") @RequestParam(defaultValue = "20") @Min(1) @Max(200) long size,
            @Parameter(description = "订单号，支持模糊匹配。") @RequestParam(required = false) String orderId,
            @Parameter(description = "订单状态，支持 ORDERED、SHIPPED、FINISHED、CANCELLED。") @RequestParam(required = false) String status,
            @Parameter(description = "达人 ID（UUID），精确匹配。") @RequestParam(required = false) UUID talentId,
            @Parameter(description = "商家 merchant_id（字符串），精确匹配。") @RequestParam(required = false) String merchantId,
            @Parameter(description = "商品 ID，精确匹配。") @RequestParam(required = false) String productId,
            @Parameter(description = "商品名称/标题，模糊匹配。") @RequestParam(required = false) String productName,
            @Parameter(description = "店铺名称，模糊匹配。") @RequestParam(required = false) String shopName,
            @Parameter(description = "达人昵称，模糊匹配。") @RequestParam(required = false) String talentName,
            @Parameter(description = "团长/招商负责人名称，模糊匹配。") @RequestParam(required = false) String colonelName,
            @Parameter(description = "渠道负责人名称，模糊匹配。") @RequestParam(required = false) String channelName,
            @Parameter(description = "团长活动 ID，精确匹配。") @RequestParam(required = false) String colonelActivityId,
            @Parameter(description = "招商类型：MERCHANT（商家型招商单） 或 PROMOTION（推广单）。") @RequestParam(required = false) String recruitType,
            @Parameter(description = "开始日期，格式 yyyy-MM-dd。") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "结束日期，格式 yyyy-MM-dd。") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @Parameter(description = "时间字段：createTime（默认）或 settleTime。") @RequestParam(required = false) String timeField,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope) {
        // 第一步：解析时间范围，未传入则默认近 30 天
        LocalDateTime start = startDate == null
                ? LocalDate.now().minusDays(30).atStartOfDay()
                : startDate.atStartOfDay();
        LocalDateTime end = endDate == null
                ? LocalDate.now().plusDays(1).atStartOfDay()
                : endDate.plusDays(1).atStartOfDay();

        // 第二步：构建筛选条件 QueryWrapper，应用各维度筛选参数与数据范围
        QueryWrapper<ColonelsettlementOrder> wrapper = buildOrderFilterWrapper(
                true,
                timeField,
                start,
                end,
                orderId,
                status,
                talentId,
                merchantId,
                productId,
                productName,
                shopName,
                talentName,
                colonelName,
                channelName,
                colonelActivityId,
                recruitType,
                userId,
                deptId,
                dataScope);

        // 第三步：执行分页查询，数据范围已在 wrapper 中显式追加
        IPage<ColonelsettlementOrder> orderPage = dataOrderQueryFacade.findPageWithScope(new Page<>(page, size), wrapper);

        // 第四步：将实体列表转换为 OrderVO，保留分页元信息
        Page<OrderVO> voPage = new Page<>(orderPage.getCurrent(), orderPage.getSize(), orderPage.getTotal());
        voPage.setRecords(orderPage.getRecords().stream().map(this::toOrderVO).toList());
        return okPage(voPage);
    }

    /**
     * 订单分页查询的简化重载版本。
     * <p>
     * 去除 productId、talentName、colonelName、channelName 等可选参数，
     * 内部委托给完整版 {@link #getOrderPage}。
     * </p>
     *
     * @param page              页码
     * @param size              每页条数
     * @param orderId           订单号
     * @param status            订单状态
     * @param talentId          达人 ID
     * @param merchantId        商家 ID
     * @param productName       商品名称
     * @param shopName          店铺名称
     * @param colonelActivityId 团长活动 ID
     * @param recruitType       招商类型
     * @param startDate         开始日期
     * @param endDate           结束日期
     * @param timeField         时间字段
     * @param userId            当前用户 ID
     * @param deptId            当前用户部门 ID
     * @param dataScope         数据范围
     * @return 订单分页结果
     */
    public ApiResult<PageResult<OrderVO>> getOrderPage(
            long page,
            long size,
            String orderId,
            String status,
            UUID talentId,
            String merchantId,
            String productName,
            String shopName,
            String colonelActivityId,
            String recruitType,
            LocalDate startDate,
            LocalDate endDate,
            String timeField,
            UUID userId,
            UUID deptId,
            DataScope dataScope) {
        return getOrderPage(
                page,
                size,
                orderId,
                status,
                talentId,
                merchantId,
                null,
                productName,
                shopName,
                null,
                null,
                null,
                colonelActivityId,
                recruitType,
                startDate,
                endDate,
                timeField,
                userId,
                deptId,
                dataScope);
    }

    /**
     * 订单明细分页查询（数据页）。
     * <p>
     * 返回逐订单粒度的明细数据，聚合订单事实与业绩域提成数据。
     * 每页查询订单后，批量关联 performance_records 获取提成字段。
     * </p>
     */
    @Operation(summary = "订单明细分页", description = "分页查询订单明细，含业绩提成双轨金额。")
    public ApiResult<PageResult<OrderDetailVO>> getOrderDetailPage(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") @Min(1) @Max(1000) long page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "20") @Min(1) @Max(200) long size,
            @Parameter(description = "订单号") @RequestParam(required = false) String orderId,
            @Parameter(description = "订单状态") @RequestParam(required = false) String status,
            @Parameter(description = "达人 ID") @RequestParam(required = false) UUID talentId,
            @Parameter(description = "商家 ID") @RequestParam(required = false) String merchantId,
            @Parameter(description = "商品 ID") @RequestParam(required = false) String productId,
            @Parameter(description = "商品名称") @RequestParam(required = false) String productName,
            @Parameter(description = "店铺名称") @RequestParam(required = false) String shopName,
            @Parameter(description = "达人昵称") @RequestParam(required = false) String talentName,
            @Parameter(description = "团长名称") @RequestParam(required = false) String colonelName,
            @Parameter(description = "渠道名称") @RequestParam(required = false) String channelName,
            @Parameter(description = "活动 ID") @RequestParam(required = false) String colonelActivityId,
            @Parameter(description = "活动名称") @RequestParam(required = false) String activityName,
            @Parameter(description = "合作方 ID") @RequestParam(required = false) String partnerId,
            @Parameter(description = "合作方名称") @RequestParam(required = false) String partnerName,
            @Parameter(description = "招商名称") @RequestParam(required = false) String recruiterName,
            @Parameter(description = "招商类型") @RequestParam(required = false) String recruitType,
            @Parameter(description = "开始日期") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "结束日期") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @Parameter(description = "时间字段") @RequestParam(required = false) String timeField,
            @Parameter(description = "招商部门 ID 列表") @RequestParam(required = false) String recruiterDeptIds,
            @Parameter(description = "渠道部门 ID 列表") @RequestParam(required = false) String channelDeptIds,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope,
            List<String> roleCodes) {

        LocalDateTime start = startDate == null
                ? LocalDate.now().minusDays(30).atStartOfDay()
                : startDate.atStartOfDay();
        LocalDateTime end = endDate == null
                ? LocalDate.now().plusDays(1).atStartOfDay()
                : endDate.plusDays(1).atStartOfDay();

        String effectivePartnerId = firstText(partnerId, merchantId);
        String effectiveRecruiterName = firstText(recruiterName, colonelName);
        QueryWrapper<ColonelsettlementOrder> wrapper = buildOrderFilterWrapper(
                true, timeField, start, end, orderId, status, talentId, effectivePartnerId,
                productId, productName, shopName, talentName, null, null,
                colonelActivityId, recruitType, userId, deptId, DataScope.ALL);
        applyOrderDataScope(wrapper, true, userId, deptId, dataScope, roleCodes);
        applyOrderDetailExtraFilters(wrapper, true, activityName, effectivePartnerId, partnerName, channelName, effectiveRecruiterName);
        applyDeptIdFilters(wrapper, true, parseUuidCsv(recruiterDeptIds), parseUuidCsv(channelDeptIds));

        IPage<ColonelsettlementOrder> orderPage = dataOrderQueryFacade.findPageWithScope(new Page<>(page, size), wrapper);
        List<ColonelsettlementOrder> orders = orderPage.getRecords();

        if (orders.isEmpty()) {
            Page<OrderDetailVO> emptyPage = new Page<>(orderPage.getCurrent(), orderPage.getSize(), orderPage.getTotal());
            emptyPage.setRecords(List.of());
            return okPage(emptyPage);
        }

        // 批量查询业绩记录
        List<String> orderIds = orders.stream()
                .map(ColonelsettlementOrder::getOrderId)
                .filter(StringUtils::hasText)
                .toList();
        Map<String, OrderPerformanceDTO> perfMap = loadPerformanceMap(
                orderIds,
                PerformanceAccessContext.of(userId, deptId, dataScope, List.of()));

        // 批量查询活动名称
        Map<String, String> activityNameMap = loadActivityNameMap(orders);

        // 批量查询用户展示名称（渠道 + 招商）
        Map<UUID, String> userNameMap = loadUserNameMap(perfMap.values());

        // 组装 VO
        Page<OrderDetailVO> voPage = new Page<>(orderPage.getCurrent(), orderPage.getSize(), orderPage.getTotal());
        voPage.setRecords(orders.stream().map(order -> {
            OrderPerformanceDTO perf = perfMap.get(order.getOrderId());
            return toOrderDetailVO(order, perf, activityNameMap, userNameMap);
        }).toList());
        return okPage(voPage);
    }

    /**
     * 批量加载订单业绩，构建 orderId → OrderPerformanceDTO 映射。
     */
    private Map<String, OrderPerformanceDTO> loadPerformanceMap(
            List<String> orderIds,
            PerformanceAccessContext context) {
        if (orderIds == null || orderIds.isEmpty()) {
            return Map.of();
        }
        OrderPerformanceBatchResponse response = orderPerformanceQueryFacade.batchGetOrderPerformance(orderIds, context);
        List<OrderPerformanceDTO> records = response == null || response.getItems() == null
                ? List.of()
                : response.getItems();
        Map<String, OrderPerformanceDTO> map = new LinkedHashMap<>();
        for (OrderPerformanceDTO r : records) {
            if (r.getOrderId() != null && Boolean.TRUE.equals(r.getIsValid())) {
                map.put(r.getOrderId(), r);
            }
        }
        return map;
    }

    /**
     * 批量加载活动名称映射 activityId → activityName。
     */
    private Map<String, String> loadActivityNameMap(List<ColonelsettlementOrder> orders) {
        List<String> activityIds = orders.stream()
                .map(ColonelsettlementOrder::getActivityId)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (activityIds.isEmpty()) {
            return Map.of();
        }
        List<ColonelsettlementActivity> activities = productActivityReadFacade.selectNamesByActivityIds(activityIds);
        Map<String, String> map = new LinkedHashMap<>();
        for (ColonelsettlementActivity a : activities) {
            if (a.getActivityId() != null && a.getName() != null) {
                map.put(a.getActivityId(), a.getName());
            }
        }
        return map;
    }

    /**
     * 批量加载用户展示名称映射 userId → displayName。
     */
    private Map<UUID, String> loadUserNameMap(Collection<OrderPerformanceDTO> records) {
        Set<UUID> userIds = new HashSet<>();
        for (OrderPerformanceDTO r : records) {
            UUID finalChannelId = parseUuid(r.getFinalChannelId());
            UUID finalRecruiterId = parseUuid(r.getFinalRecruiterId());
            if (finalChannelId != null) userIds.add(finalChannelId);
            if (finalRecruiterId != null) userIds.add(finalRecruiterId);
        }
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userDomainFacade.loadUserDisplayNamesByIds(userIds);
    }

    private UUID parseUuid(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    /**
     * 将订单实体 + 业绩记录合并为 OrderDetailVO。
     */
    private OrderDetailVO toOrderDetailVO(
            ColonelsettlementOrder order,
            OrderPerformanceDTO perf,
            Map<String, String> activityNameMap,
            Map<UUID, String> userNameMap) {
        OrderDetailVO vo = new OrderDetailVO();

        // 订单基本信息
        vo.setOrderId(StringUtils.hasText(order.getOrderId()) ? order.getOrderId() : String.valueOf(order.getId()));
        vo.setOrderStatus(order.getOrderStatus());
        vo.setOrderStatusText(toDetailOrderStatusText(order.getOrderStatus()));
        vo.setOrderTypeText(toOrderTypeText(order.getOrderType()));

        // 活动
        String activityId = order.getActivityId();
        vo.setActivityId(activityId);
        vo.setActivityName(StringUtils.hasText(activityId) ? activityNameMap.getOrDefault(activityId, null) : null);
        vo.setContentTypeText(order.getContentTypeText());

        // 商品
        vo.setProductId(order.getProductId());
        vo.setProductName(StringUtils.hasText(order.getProductTitle()) ? order.getProductTitle() : order.getProductName());
        vo.setProductImage(StringUtils.hasText(order.getProductImage()) ? order.getProductImage() : order.getProductPic());
        vo.setProductQuantity(order.getProductQuantity() != null ? order.getProductQuantity() : order.getItemNum());
        vo.setCommissionRate(order.getCommissionRate());
        vo.setServiceFeeRate(order.getServiceFeeRate());

        // 合作方
        vo.setPartnerId(order.getShopId() != null ? String.valueOf(order.getShopId()) : null);
        vo.setPartnerName(order.getShopName());
        vo.setColonelName(order.getColonelUserName());

        // 推广者
        vo.setTalentId(order.getTalentId() != null ? order.getTalentId().toString() : null);
        vo.setTalentName(StringUtils.hasText(order.getTalentName()) ? order.getTalentName()
                : pickText(order.getExtraData(), "talentName", "talent_nickname", "author_name"));
        vo.setTalentDouyinId(pickText(order.getExtraData(), "talent_unique_id", "author_id", "douyin_id"));
        vo.setVideoId(StringUtils.hasText(order.getAwemeId()) ? order.getAwemeId()
                : pickText(order.getExtraData(), "aweme_id", "video_id"));

        // 渠道/招商：优先从业绩记录获取最终归属
        if (perf != null) {
            UUID finalChannelId = parseUuid(perf.getFinalChannelId());
            UUID finalRecruiterId = parseUuid(perf.getFinalRecruiterId());
            vo.setChannelId(perf.getFinalChannelId());
            vo.setChannelName(finalChannelId != null
                    ? userNameMap.getOrDefault(finalChannelId, perf.getFinalChannelName())
                    : perf.getFinalChannelName());
            vo.setRecruiterId(perf.getFinalRecruiterId());
            vo.setRecruiterName(finalRecruiterId != null
                    ? userNameMap.getOrDefault(finalRecruiterId, perf.getFinalRecruiterName())
                    : perf.getFinalRecruiterName());
        } else {
            // 无业绩记录时回退到订单事实字段
            vo.setChannelId(order.getChannelUserId() != null ? order.getChannelUserId().toString() : null);
            vo.setChannelName(order.getChannelUserName());
            vo.setRecruiterId(null);
            vo.setRecruiterName(null);
        }

        // 金额双轨（分 → 元）
        vo.setPayAmount(centToYuan(order.getOrderAmount()));
        vo.setSettleAmount(safeCentToYuan(order.getSettleAmount()));
        vo.setEstimateServiceFee(centToYuan(order.getEstimateServiceFee()));
        vo.setEffectiveServiceFee(safeCentToYuan(order.getEffectiveServiceFee()));
        vo.setEstimateTechServiceFee(centToYuan(order.getEstimateTechServiceFee()));
        vo.setEffectiveTechServiceFee(safeCentToYuan(order.getEffectiveTechServiceFee()));

        // 提成与毛利字段从业绩记录获取
        if (perf != null) {
            vo.setEstimateRecruiterCommission(centToYuan(perf.getEstimateRecruiterCommission()));
            vo.setEffectiveRecruiterCommission(safeCentToYuan(perf.getEffectiveRecruiterCommission()));
            vo.setEstimateChannelCommission(centToYuan(perf.getEstimateChannelCommission()));
            vo.setEffectiveChannelCommission(safeCentToYuan(perf.getEffectiveChannelCommission()));
            vo.setEstimateGrossProfit(centToYuan(perf.getEstimateGrossProfit()));
            vo.setEffectiveGrossProfit(safeCentToYuan(perf.getEffectiveGrossProfit()));
        } else {
            vo.setEstimateRecruiterCommission(null);
            vo.setEffectiveRecruiterCommission(null);
            vo.setEstimateChannelCommission(null);
            vo.setEffectiveChannelCommission(null);
            vo.setEstimateGrossProfit(null);
            vo.setEffectiveGrossProfit(null);
        }

        // 服务费收益：业绩记录里维护的最终服务利润（扣除招商/渠道提成前）
        // 预估服务费支出 = 预估服务费收入 - 技术服务费 - 服务费收益
        // 结算服务费支出 = 结算服务费收入 - 服务费收益
        // 服务费支出是平台侧实际服务费（非招商+渠道提成）
        if (perf != null) {
            vo.setEstimateServiceProfit(centToYuan(perf.getEstimateServiceProfit()));
            vo.setEffectiveServiceProfit(safeCentToYuan(perf.getEffectiveServiceProfit()));
            // 服务费支出：优先从业绩记录读取独立字段，回退到反推公式
            long estExpense = perf.getEstimateServiceFeeExpense() != null ? perf.getEstimateServiceFeeExpense() : 0L;
            long effExpense = perf.getEffectiveServiceFeeExpense() != null ? perf.getEffectiveServiceFeeExpense() : 0L;
            if (estExpense <= 0) {
                long estIncome = order.getEstimateServiceFee() == null ? 0L : order.getEstimateServiceFee();
                long estTech = order.getEstimateTechServiceFee() == null ? 0L : order.getEstimateTechServiceFee();
                long estProfit = perf.getEstimateServiceProfit() == null ? 0L : perf.getEstimateServiceProfit();
                estExpense = Math.max(estIncome - estTech - estProfit, 0L);
            }
            if (effExpense <= 0) {
                long effIncome = order.getEffectiveServiceFee() == null ? 0L : order.getEffectiveServiceFee();
                long effProfit = perf.getEffectiveServiceProfit() == null ? 0L : perf.getEffectiveServiceProfit();
                effExpense = Math.max(effIncome - effProfit, 0L);
            }
            vo.setEstimateServiceFeeExpense(centToYuan(estExpense));
            vo.setEffectiveServiceFeeExpense(centToYuan(effExpense));
        } else {
            vo.setEstimateServiceProfit(null);
            vo.setEffectiveServiceProfit(null);
            vo.setEstimateServiceFeeExpense(null);
            vo.setEffectiveServiceFeeExpense(null);
        }

        // 时间
        vo.setPayTime(order.getPayTime() != null ? order.getPayTime() : order.getOrderCreateTime());
        vo.setDeliveryTime(pickDateTime(order.getExtraData(), "delivery_time", "deliveryTime", "receive_time", "receiveTime"));
        vo.setSettleTime(order.getSettleTime());
        vo.setExpireTime(pickDateTime(order.getExtraData(), "expire_time", "expireTime", "invalid_time", "invalidTime"));
        vo.setOrderCreateTime(order.getOrderCreateTime());

        // 结算状态
        if (order.getOrderStatus() != null && order.getOrderStatus() == 4) {
            vo.setSettleStatusText("失效");
        } else if (order.getSettleTime() != null) {
            vo.setSettleStatusText("已结算");
        } else {
            vo.setSettleStatusText("待结算");
        }

        return vo;
    }

    /**
     * 分转元，null 时返回 null（区别于 centToYuan 的 0 默认值）。
     */
    private BigDecimal safeCentToYuan(Long cent) {
        if (cent == null || cent == 0L) {
            return null;
        }
        return BigDecimal.valueOf(cent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal safeAdd(BigDecimal a, BigDecimal b) {
        if (a == null && b == null) return null;
        BigDecimal va = a != null ? a : BigDecimal.ZERO;
        BigDecimal vb = b != null ? b : BigDecimal.ZERO;
        return va.add(vb);
    }

    private BigDecimal safeSubtract(Long aCent, Long bCent) {
        if (aCent == null && bCent == null) return null;
        BigDecimal a = centToYuan(aCent);
        BigDecimal b = centToYuan(bCent);
        return a.subtract(b);
    }

    private BigDecimal safeSubtract(BigDecimal a, BigDecimal b) {
        if (a == null && b == null) return null;
        BigDecimal va = a != null ? a : BigDecimal.ZERO;
        BigDecimal vb = b != null ? b : BigDecimal.ZERO;
        return va.subtract(vb);
    }

    /**
     * 订单明细汇总查询。
     * <p>
     * 按筛选条件返回订单汇总统计与按日明细，用于数据页顶部汇总条与默认表格。
     * 汇总维度包括：订单数、达人数、商品数、GMV、服务费、技术费、提成等。
     * </p>
     * <ol>
     *   <li>解析时间范围，确定时间字段（createTime / settleTime）对应的数据库列</li>
     *   <li>执行汇总聚合查询（按日分组），得到按日明细行</li>
     *   <li>执行不分组的汇总查询，得到总计行</li>
     *   <li>按活动桶查询提成汇总（总计与按日），计算团长/渠道/达人三方提成</li>
     *   <li>组装 OrderSummaryVO 返回（total + records）</li>
     * </ol>
     *
     * @param orderId             订单号，模糊匹配
     * @param status              订单状态
     * @param talentId            达人 ID
     * @param merchantId          商家 ID
     * @param productId           商品 ID
     * @param productName         商品名称
     * @param shopName            店铺名称
     * @param talentName          达人昵称
     * @param colonelName         团长名称
     * @param channelName         渠道负责人名称
     * @param colonelActivityId   团长活动 ID
     * @param recruitType         招商类型
     * @param startDate           开始日期
     * @param endDate             结束日期
     * @param timeField           时间字段
     * @param userId              当前用户 ID
     * @param deptId              当前用户部门 ID
     * @param dataScope           数据范围
     * @return 订单汇总 VO，包含总计行（total）与按日明细列表（records）
     */
    @Operation(summary = "订单明细汇总", description = "按数据页筛选条件返回订单汇总与按日明细。该接口用于订单明细页顶部汇总条和默认表格。")
    @GetMapping("/data/orders/summary")
    public ApiResult<OrderSummaryVO> getOrderSummary(
            @Parameter(description = "订单号，支持模糊匹配。") @RequestParam(required = false) String orderId,
            @Parameter(description = "订单状态，支持 ORDERED、SHIPPED、FINISHED、CANCELLED。") @RequestParam(required = false) String status,
            @Parameter(description = "达人 ID（UUID），精确匹配。") @RequestParam(required = false) UUID talentId,
            @Parameter(description = "商家 merchant_id（字符串），精确匹配。") @RequestParam(required = false) String merchantId,
            @Parameter(description = "商品 ID，精确匹配。") @RequestParam(required = false) String productId,
            @Parameter(description = "商品名称/标题，模糊匹配。") @RequestParam(required = false) String productName,
            @Parameter(description = "店铺名称，模糊匹配。") @RequestParam(required = false) String shopName,
            @Parameter(description = "达人昵称，模糊匹配。") @RequestParam(required = false) String talentName,
            @Parameter(description = "团长/招商负责人名称，模糊匹配。") @RequestParam(required = false) String colonelName,
            @Parameter(description = "渠道负责人名称，模糊匹配。") @RequestParam(required = false) String channelName,
            @Parameter(description = "团长活动 ID，精确匹配。") @RequestParam(required = false) String colonelActivityId,
            @Parameter(description = "招商类型：MERCHANT（商家型招商单） 或 PROMOTION（推广单）。") @RequestParam(required = false) String recruitType,
            @Parameter(description = "开始日期，格式 yyyy-MM-dd。") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "结束日期，格式 yyyy-MM-dd。") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @Parameter(description = "时间字段：createTime（默认）或 settleTime。") @RequestParam(required = false) String timeField,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope) {
        return ok(shortTtlCacheService.get(
                orderSummaryCacheKey(timeField, startDate, endDate,
                        orderId, status, talentId, merchantId,
                        productId, productName, shopName,
                        talentName, colonelName, channelName,
                        colonelActivityId, recruitType,
                        userId, deptId, dataScope),
                ORDER_SUMMARY_CACHE_TTL,
                () -> buildOrderSummary(orderId, status, talentId, merchantId,
                        productId, productName, shopName,
                        talentName, colonelName, channelName,
                        colonelActivityId, recruitType,
                        startDate, endDate, timeField,
                        userId, deptId, dataScope)));
    }

    private OrderSummaryVO buildOrderSummary(
            String orderId, String status, UUID talentId, String merchantId,
            String productId, String productName, String shopName,
            String talentName, String colonelName, String channelName,
            String colonelActivityId, String recruitType,
            LocalDate startDate, LocalDate endDate, String timeField,
            UUID userId, UUID deptId, DataScope dataScope) {
        // 第一步：解析时间范围
        LocalDateTime start = startDate == null
                ? LocalDate.now().minusDays(30).atStartOfDay()
                : startDate.atStartOfDay();
        LocalDateTime end = endDate == null
                ? LocalDate.now().plusDays(1).atStartOfDay()
                : endDate.plusDays(1).atStartOfDay();

        // 第二步：确定时间字段对应的数据库列（含表别名）
        OrderTrackColumns columns = resolveOrderTrackColumns(timeField);

        // 第三步：执行汇总聚合查询——不分组版本（总计行）
        List<Map<String, Object>> totalRows = queryOrderSummaryAggregates(
                false,
                columns,
                timeField,
                start,
                end,
                orderId,
                status,
                talentId,
                merchantId,
                productId,
                productName,
                shopName,
                talentName,
                colonelName,
                channelName,
                colonelActivityId,
                recruitType,
                userId,
                deptId,
                dataScope);
        // 第四步：执行汇总聚合查询——按日分组版本（明细行）
        List<Map<String, Object>> dailyRows = queryOrderSummaryAggregates(
                true,
                columns,
                timeField,
                start,
                end,
                orderId,
                status,
                talentId,
                merchantId,
                productId,
                productName,
                shopName,
                talentName,
                colonelName,
                channelName,
                colonelActivityId,
                recruitType,
                userId,
                deptId,
                dataScope);
        // 第五步：查询提成汇总（总计），按活动桶计算团长/渠道/达人三方提成
        CommissionService.CommissionSummary totalCommission = queryOrderSummaryCommission(
                false,
                null,
                columns,
                timeField,
                start,
                end,
                orderId,
                status,
                talentId,
                merchantId,
                productId,
                productName,
                shopName,
                talentName,
                colonelName,
                channelName,
                colonelActivityId,
                recruitType,
                userId,
                deptId,
                dataScope);
        Map<String, CommissionService.CommissionSummary> dailyCommission = queryDailyOrderSummaryCommission(
                columns,
                timeField,
                start,
                end,
                orderId,
                status,
                talentId,
                merchantId,
                productId,
                productName,
                shopName,
                talentName,
                colonelName,
                channelName,
                colonelActivityId,
                recruitType,
                userId,
                deptId,
                dataScope);

        boolean estimateTrack = !"settle_time".equals(columns.timeColumn());

        OrderSummaryVO vo = new OrderSummaryVO();
        vo.setTotal(toOrderSummaryRow(firstRow(totalRows), totalCommission, null, estimateTrack));
        vo.setRecords(dailyRows.stream()
                .map(row -> {
                    String date = asString(row, "stat_date");
                    return toOrderSummaryRow(row, dailyCommission.get(date), date, estimateTrack);
                })
                .toList());
        return vo;
    }

    private String orderSummaryCacheKey(
            String timeField,
            LocalDate startDate, LocalDate endDate,
            String orderId, String status, UUID talentId, String merchantId,
            String productId, String productName, String shopName,
            String talentName, String colonelName, String channelName,
            String colonelActivityId, String recruitType,
            UUID userId, UUID deptId, DataScope dataScope) {
        String timeColumn = resolveTimeColumn(timeField);
        return ORDER_SUMMARY_CACHE_PREFIX + cacheKey(
                timeColumn,
                startDate, endDate,
                orderId, status, talentId, merchantId,
                productId, productName, shopName,
                talentName, colonelName, channelName,
                colonelActivityId, recruitType,
                userId, deptId, dataScope == null ? "NO_SCOPE" : dataScope);
    }

    @Operation(summary = "核心指标", description = "查询数据页首页核心指标与近 7 天趋势，支持双轨（结算/预估）并行返回。")
    @GetMapping("/dashboard/metrics")
    public ApiResult<DualTrackMetricsVO> getMetrics(
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope) {
        String cacheKeySettle = METRICS_CACHE_PREFIX + metricsCacheKey("settleTime", userId, deptId, dataScope);
        String cacheKeyEstimate = METRICS_CACHE_PREFIX + metricsCacheKey("createTime", userId, deptId, dataScope);

        MetricsVO settleMetrics = shortTtlCacheService.get(cacheKeySettle, METRICS_CACHE_TTL,
                () -> buildMetrics("settleTime", userId, deptId, dataScope));
        MetricsVO estimateMetrics = shortTtlCacheService.get(cacheKeyEstimate, METRICS_CACHE_TTL,
                () -> buildMetrics("createTime", userId, deptId, dataScope));

        DualTrackMetricsVO result = new DualTrackMetricsVO();
        result.setSettle(settleMetrics);
        result.setEstimate(estimateMetrics);
        return ok(result);
    }

    private MetricsVO buildMetrics(String timeField, UUID userId, UUID deptId, DataScope dataScope) {
        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime tomorrowStart = today.plusDays(1).atStartOfDay();
        LocalDateTime rollingStart = today.minusDays(29).atStartOfDay();
        OrderTrackColumns columns = resolveOrderTrackColumns(timeField);
        String timeColumn = columns.timeColumn();

        QueryWrapper<ColonelsettlementOrder> pendingWrapper = buildScopedQuery(userId, deptId, dataScope)
                .select("COUNT(1) AS order_count")
                .eq("order_status", toOrderStatusCode("ORDERED"))
                .ge(timeColumn, rollingStart)
                .lt(timeColumn, tomorrowStart);
        Long pendingShipCount = asLong(getSingleAggregate(pendingWrapper), "order_count");

        MetricsVO metrics = new MetricsVO();
        metrics.setPendingShipCount(pendingShipCount);
        metrics.setAmountTrack(performanceMetricsQueryService.resolveAmountTrackLabel(timeField));
        metrics.setTrack(timeField);
        boolean estimateTrack = isEstimateTrack(timeField);
        RefundMetricsAggregate refundAggregate = queryRefundMetrics(
                columns,
                todayStart,
                tomorrowStart,
                userId,
                deptId,
                dataScope);
        applyRefundMetrics(metrics, refundAggregate);

        if (performanceMetricsQueryService.hasPerformanceRecords()) {
            PerformanceMetricsQueryService.PerformanceAggregate aggregate =
                    performanceMetricsQueryService.aggregateRange(todayStart, tomorrowStart, timeField, userId, deptId, dataScope);
            List<PerformanceMetricsQueryService.TrendPoint> trendPoints = performanceMetricsQueryService.trendByDay(
                    today.minusDays(6).atStartOfDay(),
                    tomorrowStart,
                    timeField,
                    userId,
                    deptId,
                    dataScope);

            metrics.setMetricsSource("performance_records");
            metrics.setTodayOrderCount(aggregate.orderCount());
            metrics.setTodayGmv(centToYuan(aggregate.orderAmountCent()));
            metrics.setTrend7d(trendPoints.stream()
                    .map(point -> new TrendPointVO(point.date(), point.orderCount(), centToYuan(point.orderAmountCent())))
                    .toList());

            metrics.setTotalOrders(aggregate.orderCount());
            metrics.setTotalAmount(centToYuan(aggregate.orderAmountCent()));
            metrics.setServiceFeeIncome(centToYuan(aggregate.serviceFeeIncomeCent()));
            metrics.setTechServiceFee(centToYuan(aggregate.techServiceFeeCent()));
            metrics.setTalentCommission(centToYuan(aggregate.talentCommissionCent()));
            long expenseCent = aggregate.serviceFeeExpenseCent();
            long profitCent = CommissionService.serviceFeeNetCent(
                    aggregate.serviceFeeIncomeCent(),
                    aggregate.techServiceFeeCent(),
                    expenseCent,
                    estimateTrack);
            metrics.setServiceFeeExpense(centToYuan(expenseCent));
            metrics.setServiceFee(centToYuan(profitCent));
            metrics.setServiceFeeProfit(centToYuan(profitCent));
            metrics.setBizCommission(centToYuan(aggregate.recruiterCommissionCent()));
            metrics.setChannelCommission(centToYuan(aggregate.channelCommissionCent()));
            metrics.setCommission(centToYuan(aggregate.recruiterCommissionCent() + aggregate.channelCommissionCent()));
            metrics.setGrossProfit(centToYuan(Math.max(
                    profitCent - aggregate.recruiterCommissionCent() - aggregate.channelCommissionCent(),
                    0L)));
            return metrics;
        }

        metrics.setMetricsSource("orders");
        QueryWrapper<ColonelsettlementOrder> todayAggregateWrapper = buildScopedQuery(userId, deptId, dataScope)
                .select(
                        "COUNT(*) AS order_count",
                        "COALESCE(SUM(" + columns.amountColumn() + "), 0) AS order_amount_cent"
                )
                .ge(timeColumn, todayStart)
                .lt(timeColumn, tomorrowStart);
        Map<String, Object> todayAggregate = getSingleAggregate(todayAggregateWrapper);
        Long todayOrders = asLong(todayAggregate, "order_count");
        Long todayGmvCent = asLong(todayAggregate, "order_amount_cent");

        QueryWrapper<ColonelsettlementOrder> commissionWrapper = buildScopedQuery(userId, deptId, dataScope)
                .select(
                        "COALESCE(colonel_activity_id, '') AS activity_id",
                        "COALESCE(SUM(" + columns.serviceFeeColumn() + "), 0) AS service_fee_income",
                        "COALESCE(SUM(" + columns.techFeeColumn() + "), 0) AS tech_service_fee",
                        "COALESCE(SUM(" + columns.expenseColumn() + "), 0) AS service_fee_expense",
                        "COALESCE(SUM(settle_second_colonel_commission), 0) AS talent_commission"
                )
                .ge(timeColumn, todayStart)
                .lt(timeColumn, tomorrowStart)
                .groupBy("colonel_activity_id");
        List<Map<String, Object>> commissionRows = dataOrderQueryFacade.selectMaps(commissionWrapper);
        long displayTechServiceFeeCent = commissionRows.stream()
                .mapToLong(row -> asLong(row, "tech_service_fee"))
                .sum();
        CommissionService.CommissionSummary commissionSummary = commissionService.calculateByActivityBuckets(
                commissionRows.stream()
                        .map(row -> new CommissionService.ActivityCommissionBucket(
                                asString(row, "activity_id"),
                                null,
                                null,
                                asLong(row, "service_fee_income"),
                                estimateTrack ? asLong(row, "tech_service_fee") : 0L,
                                asLong(row, "service_fee_expense"),
                                asLong(row, "talent_commission")
                        ))
                        .toList()
        );

        LocalDateTime weekStart = today.minusDays(6).atStartOfDay();
        QueryWrapper<ColonelsettlementOrder> trendWrapper = buildScopedQuery(userId, deptId, dataScope)
                .select(
                        String.format("DATE(%s) AS settle_date", timeColumn),
                        "COUNT(*) AS order_count",
                        "COALESCE(SUM(" + columns.amountColumn() + "), 0) AS order_amount_cent"
                )
                .ge(timeColumn, weekStart)
                .lt(timeColumn, tomorrowStart)
                .groupBy(String.format("DATE(%s)", timeColumn));
        Map<LocalDate, Map<String, Object>> trendMap = dataOrderQueryFacade.selectMaps(trendWrapper).stream()
                .collect(java.util.stream.Collectors.toMap(
                        row -> LocalDate.parse(asString(row, "settle_date")),
                        row -> row
                ));
        List<TrendPointVO> trend7d = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            Map<String, Object> dayAggregate = trendMap.get(day);
            Long dayOrders = asLong(dayAggregate, "order_count");
            Long dayGmvCent = asLong(dayAggregate, "order_amount_cent");
            trend7d.add(new TrendPointVO(day.toString(), dayOrders, centToYuan(dayGmvCent)));
        }

        metrics.setTodayOrderCount(todayOrders);
        metrics.setTodayGmv(centToYuan(todayGmvCent));
        metrics.setTrend7d(trend7d);
        metrics.setTotalOrders(todayOrders);
        metrics.setTotalAmount(centToYuan(todayGmvCent));
        metrics.setServiceFeeIncome(centToYuan(commissionSummary.serviceFeeIncome()));
        metrics.setTechServiceFee(centToYuan(displayTechServiceFeeCent));
        metrics.setTalentCommission(centToYuan(commissionSummary.talentCommission()));
        metrics.setServiceFeeExpense(centToYuan(commissionSummary.serviceFeeExpense()));
        metrics.setServiceFee(centToYuan(commissionSummary.serviceFeeNet()));
        metrics.setServiceFeeProfit(centToYuan(commissionSummary.serviceFeeNet()));
        metrics.setBizCommission(centToYuan(commissionSummary.bizCommission()));
        metrics.setChannelCommission(centToYuan(commissionSummary.channelCommission()));
        metrics.setCommission(centToYuan(commissionSummary.bizCommission() + commissionSummary.channelCommission()));
        metrics.setGrossProfit(centToYuan(commissionSummary.grossProfit()));
        return metrics;
    }

    private RefundMetricsAggregate queryRefundMetrics(
            OrderTrackColumns columns,
            LocalDateTime start,
            LocalDateTime end,
            UUID userId,
            UUID deptId,
            DataScope dataScope) {
        QueryWrapper<ColonelsettlementOrder> wrapper = buildScopedQuery(userId, deptId, dataScope)
                .select(
                        "COUNT(CASE WHEN " + REFUND_ORDER_PREDICATE + " THEN 1 END) AS refund_order_count",
                        "COALESCE(SUM(CASE WHEN " + REFUND_ORDER_PREDICATE + " THEN " + columns.amountColumn() + " ELSE 0 END), 0) AS refund_order_amount_cent",
                        "COALESCE(SUM(CASE WHEN " + REFUND_ORDER_PREDICATE + " THEN " + columns.refundServiceFeeExpression() + " ELSE 0 END), 0) AS refund_service_fee_cent"
                )
                .ge(columns.timeColumn(), start)
                .lt(columns.timeColumn(), end);
        Map<String, Object> row = getSingleAggregate(wrapper);
        return new RefundMetricsAggregate(
                asLong(row, "refund_order_count"),
                asLong(row, "refund_order_amount_cent"),
                asLong(row, "refund_service_fee_cent"));
    }

    private void applyRefundMetrics(MetricsVO metrics, RefundMetricsAggregate aggregate) {
        metrics.setRefundOrderCount(aggregate.orderCount());
        metrics.setRefundOrderAmount(centToYuan(aggregate.orderAmountCent()));
        metrics.setRefundServiceFee(centToYuan(aggregate.serviceFeeCent()));
    }

    @Operation(summary = "导出订单CSV", description = "按筛选条件导出订单数据页 CSV。")
    @GetMapping("/orders/exports")
    @RequireRoles({RoleCodes.ADMIN, RoleCodes.BIZ_LEADER, RoleCodes.CHANNEL_LEADER})
    public void exportOrders(
            @Parameter(description = "订单号，支持模糊匹配。") @RequestParam(required = false) String orderId,
            @Parameter(description = "订单状态，支持 ORDERED、SHIPPED、FINISHED、CANCELLED。") @RequestParam(required = false) String status,
            @Parameter(description = "达人 ID（UUID），精确匹配。") @RequestParam(required = false) UUID talentId,
            @Parameter(description = "商家 merchant_id（字符串），精确匹配。") @RequestParam(required = false) String merchantId,
            @Parameter(description = "商品 ID，精确匹配。") @RequestParam(required = false) String productId,
            @Parameter(description = "商品名称/标题，模糊匹配。") @RequestParam(required = false) String productName,
            @Parameter(description = "店铺名称，模糊匹配。") @RequestParam(required = false) String shopName,
            @Parameter(description = "达人昵称，模糊匹配。") @RequestParam(required = false) String talentName,
            @Parameter(description = "团长/招商负责人名称，模糊匹配。") @RequestParam(required = false) String colonelName,
            @Parameter(description = "渠道负责人名称，模糊匹配。") @RequestParam(required = false) String channelName,
            @Parameter(description = "团长活动 ID，精确匹配。") @RequestParam(required = false) String colonelActivityId,
            @Parameter(description = "招商类型：MERCHANT（商家型招商单） 或 PROMOTION（推广单）。") @RequestParam(required = false) String recruitType,
            @Parameter(description = "开始日期，格式 yyyy-MM-dd。") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "结束日期，格式 yyyy-MM-dd。") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @Parameter(description = "时间字段：createTime（默认）或 settleTime。") @RequestParam(required = false) String timeField,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope,
            HttpServletResponse response) throws IOException {

        LocalDateTime start = startDate == null
                ? LocalDate.now().minusDays(30).atStartOfDay()
                : startDate.atStartOfDay();
        LocalDateTime end = endDate == null
                ? LocalDate.now().plusDays(1).atStartOfDay()
                : endDate.plusDays(1).atStartOfDay();

        QueryWrapper<ColonelsettlementOrder> wrapper = buildOrderFilterWrapper(
                true,
                timeField,
                start,
                end,
                orderId,
                status,
                talentId,
                merchantId,
                productId,
                productName,
                shopName,
                talentName,
                colonelName,
                channelName,
                colonelActivityId,
                recruitType,
                userId,
                deptId,
                dataScope);

        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"orders.csv\"");
        PrintWriter writer = response.getWriter();
        writer.write('\ufeff');
        writer.println("订单号,商品名称,达人名称,金额,归因来源,状态,创建时间,结算时间");

        long current = 1L;
        while (true) {
            IPage<ColonelsettlementOrder> pageResult = dataOrderQueryFacade.findPageWithScope(new Page<>(current, EXPORT_BATCH_SIZE), wrapper);
            List<ColonelsettlementOrder> orders = pageResult.getRecords();
            if (orders == null || orders.isEmpty()) {
                break;
            }
            for (ColonelsettlementOrder order : orders) {
                OrderVO vo = toOrderVO(order);
                writer.printf("%s,%s,%s,%s,%s,%s,%s,%s%n",
                        csvEscape(vo.getId()),
                        csvEscape(vo.getProductName()),
                        csvEscape(vo.getTalentName()),
                        csvEscape(vo.getAmount()),
                        csvEscape(vo.getAttributionSource() == null ? "默认归属" : vo.getAttributionSource()),
                        csvEscape(vo.getStatus()),
                        csvEscape(vo.getCreateTime()),
                        csvEscape(vo.getSettleTime()));
            }
            if (current >= pageResult.getPages()) {
                break;
            }
            current++;
        }
        writer.flush();
    }

    /**
     * 简化版导出订单 CSV（不含商品名称/店铺名称等高级筛选）。
     * 委托给完整版 exportOrders，将未提供的筛选字段传 null。
     */
    public void exportOrders(
            String orderId, String status, UUID talentId, String merchantId,
            LocalDate startDate, LocalDate endDate, String timeField,
            UUID userId, UUID deptId, DataScope dataScope,
            HttpServletResponse response) throws IOException {
        exportOrders(orderId, status, talentId, merchantId,
                null, null, null, null, null, null, null, null,
                startDate, endDate, timeField, userId, deptId, dataScope, response);
    }

    /**
     * 订单明细导出 CSV。
     */
    @Operation(summary = "导出订单明细CSV", description = "按筛选条件导出订单明细 CSV，含双轨金额。")
    @GetMapping("/orders/exports/detail")
    @RequireRoles({RoleCodes.ADMIN, RoleCodes.BIZ_LEADER, RoleCodes.CHANNEL_LEADER})
    public void exportOrderDetail(
            @Parameter(description = "订单号") @RequestParam(required = false) String orderId,
            @Parameter(description = "订单状态") @RequestParam(required = false) String status,
            @Parameter(description = "达人 ID") @RequestParam(required = false) UUID talentId,
            @Parameter(description = "商家 ID") @RequestParam(required = false) String merchantId,
            @Parameter(description = "商品 ID") @RequestParam(required = false) String productId,
            @Parameter(description = "商品名称") @RequestParam(required = false) String productName,
            @Parameter(description = "店铺名称") @RequestParam(required = false) String shopName,
            @Parameter(description = "达人昵称") @RequestParam(required = false) String talentName,
            @Parameter(description = "团长名称") @RequestParam(required = false) String colonelName,
            @Parameter(description = "渠道名称") @RequestParam(required = false) String channelName,
            @Parameter(description = "活动 ID") @RequestParam(required = false) String colonelActivityId,
            @Parameter(description = "活动名称") @RequestParam(required = false) String activityName,
            @Parameter(description = "合作方 ID") @RequestParam(required = false) String partnerId,
            @Parameter(description = "合作方名称") @RequestParam(required = false) String partnerName,
            @Parameter(description = "招商名称") @RequestParam(required = false) String recruiterName,
            @Parameter(description = "招商类型") @RequestParam(required = false) String recruitType,
            @Parameter(description = "开始日期") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "结束日期") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @Parameter(description = "时间字段") @RequestParam(required = false) String timeField,
            @Parameter(description = "招商部门 ID 列表") @RequestParam(required = false) String recruiterDeptIds,
            @Parameter(description = "渠道部门 ID 列表") @RequestParam(required = false) String channelDeptIds,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope,
            HttpServletResponse response) throws IOException {

        LocalDateTime start = startDate == null
                ? LocalDate.now().minusDays(30).atStartOfDay()
                : startDate.atStartOfDay();
        LocalDateTime end = endDate == null
                ? LocalDate.now().plusDays(1).atStartOfDay()
                : endDate.plusDays(1).atStartOfDay();

        String effectivePartnerId = firstText(partnerId, merchantId);
        String effectiveRecruiterName = firstText(recruiterName, colonelName);
        QueryWrapper<ColonelsettlementOrder> wrapper = buildOrderFilterWrapper(
                true, timeField, start, end, orderId, status, talentId, effectivePartnerId,
                productId, productName, shopName, talentName, null, null,
                colonelActivityId, recruitType, userId, deptId, dataScope);
        applyOrderDetailExtraFilters(wrapper, true, activityName, effectivePartnerId, partnerName, channelName, effectiveRecruiterName);
        applyDeptIdFilters(wrapper, true, parseUuidCsv(recruiterDeptIds), parseUuidCsv(channelDeptIds));

        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"order-detail.csv\"");
        PrintWriter writer = response.getWriter();
        writer.write('\ufeff');
        writer.println("订单ID,活动信息,商品信息,合作方信息,推广者,渠道,招商,订单状态,订单额,服务费收入,技术服务费,服务费支出,服务费收益,招商提成,渠道提成,毛利,订单时间");

        long current = 1L;
        while (true) {
            IPage<ColonelsettlementOrder> pageResult = dataOrderQueryFacade.findPageWithScope(new Page<>(current, EXPORT_BATCH_SIZE), wrapper);
            List<ColonelsettlementOrder> orders = pageResult.getRecords();
            if (orders == null || orders.isEmpty()) {
                break;
            }

            List<String> orderIds = orders.stream()
                    .map(ColonelsettlementOrder::getOrderId)
                    .filter(StringUtils::hasText)
                    .toList();
            Map<String, OrderPerformanceDTO> perfMap = loadPerformanceMap(
                    orderIds,
                    PerformanceAccessContext.of(userId, deptId, dataScope, List.of()));
            Map<String, String> activityNameMap = loadActivityNameMap(orders);
            Map<UUID, String> userNameMap = loadUserNameMap(perfMap.values());

            for (ColonelsettlementOrder order : orders) {
                OrderPerformanceDTO perf = perfMap.get(order.getOrderId());
                OrderDetailVO vo = toOrderDetailVO(order, perf, activityNameMap, userNameMap);
                writer.printf("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s%n",
                        csvEscape(vo.getOrderId()),
                        csvEscape(compactPair(valueOrDefault(vo.getActivityName(), "未归属活动"), vo.getActivityId())),
                        csvEscape(compactPair(vo.getProductName(), vo.getProductId())),
                        csvEscape(compactPair(vo.getPartnerName(), vo.getPartnerId())),
                        csvEscape(compactPair(vo.getTalentName(), vo.getTalentId())),
                        csvEscape(valueOrDefault(vo.getChannelName(), "未归因")),
                        csvEscape(valueOrDefault(vo.getRecruiterName(), "未归因")),
                        csvEscape(vo.getOrderStatusText()),
                        csvEscape(compactTrack("支付", vo.getPayAmount(), "结算", vo.getSettleAmount())),
                        csvEscape(compactTrack("预估", vo.getEstimateServiceFee(), "结算", vo.getEffectiveServiceFee())),
                        csvEscape(compactTrack("预估", vo.getEstimateTechServiceFee(), "结算", vo.getEffectiveTechServiceFee())),
                        csvEscape(compactTrack("预估", vo.getEstimateServiceFeeExpense(), "结算", vo.getEffectiveServiceFeeExpense())),
                        csvEscape(compactTrack("预估", vo.getEstimateServiceProfit(), "结算", vo.getEffectiveServiceProfit())),
                        csvEscape(compactTrack("预估", vo.getEstimateRecruiterCommission(), "结算", vo.getEffectiveRecruiterCommission())),
                        csvEscape(compactTrack("预估", vo.getEstimateChannelCommission(), "结算", vo.getEffectiveChannelCommission())),
                        csvEscape(compactTrack("预估", vo.getEstimateGrossProfit(), "结算", vo.getEffectiveGrossProfit())),
                        csvEscape(compactOrderTime(vo)));
            }
            if (current >= pageResult.getPages()) {
                break;
            }
            current++;
        }
        writer.flush();
    }

    @Operation(summary = "独家状态监控 - 达人", description = "分页查询达人独家状态监控数据。支持按月份、关键字、状态筛选。")
    @GetMapping("/operations/exclusive-talents")
    @RequireRoles({RoleCodes.ADMIN, RoleCodes.BIZ_LEADER, RoleCodes.CHANNEL_LEADER})
    public ApiResult<PageResult<ExclusiveTalentStatusVO>> getExclusiveTalentStatus(
            @Parameter(description = "页码，从 1 开始。") @RequestParam(defaultValue = "1") @Min(1) long page,
            @Parameter(description = "每页条数。") @RequestParam(defaultValue = "10") @Min(1) @Max(200) long size,
            @Parameter(description = "生效月份，格式 yyyy-MM。") @RequestParam(required = false) String effectiveMonth,
            @Parameter(description = "达人 UID 关键字，模糊匹配。") @RequestParam(required = false) String keyword,
            @Parameter(description = "状态：1=活跃，0=已过期。") @RequestParam(required = false) Integer status,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope) {
        LambdaQueryWrapper<ExclusiveTalent> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ExclusiveTalent::getDeleted, 0);
        if (StringUtils.hasText(effectiveMonth)) {
            wrapper.eq(ExclusiveTalent::getEffectiveMonth, effectiveMonth.trim());
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.like(ExclusiveTalent::getTalentUid, keyword.trim());
        }
        if (status != null) {
            wrapper.eq(ExclusiveTalent::getStatus, status);
        }
        applyTalentDataScope(wrapper, userId, deptId, dataScope);
        wrapper.orderByDesc(ExclusiveTalent::getCreateTime);
        IPage<ExclusiveTalent> result = exclusiveTalentReadFacade.selectPage(new Page<>(page, size), wrapper);
        return okPage(result.convert(ExclusiveTalentStatusVO::from));
    }

    @Operation(summary = "独家状态监控 - 商家", description = "分页查询商家独家状态监控数据。支持按月份、关键字、状态筛选。")
    @GetMapping("/operations/exclusive-merchants")
    @RequireRoles({RoleCodes.ADMIN, RoleCodes.BIZ_LEADER, RoleCodes.CHANNEL_LEADER})
    public ApiResult<PageResult<ExclusiveMerchantStatusVO>> getExclusiveMerchantStatus(
            @Parameter(description = "页码，从 1 开始。") @RequestParam(defaultValue = "1") @Min(1) long page,
            @Parameter(description = "每页条数。") @RequestParam(defaultValue = "10") @Min(1) @Max(200) long size,
            @Parameter(description = "生效月份，格式 yyyy-MM。") @RequestParam(required = false) String effectiveMonth,
            @Parameter(description = "商家名称关键字，模糊匹配。") @RequestParam(required = false) String keyword,
            @Parameter(description = "状态：1=活跃，0=已过期。") @RequestParam(required = false) Integer status,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope) {
        LambdaQueryWrapper<ExclusiveMerchant> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ExclusiveMerchant::getDeleted, 0);
        if (StringUtils.hasText(effectiveMonth)) {
            wrapper.eq(ExclusiveMerchant::getEffectiveMonth, effectiveMonth.trim());
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.like(ExclusiveMerchant::getMerchantName, keyword.trim());
        }
        if (status != null) {
            wrapper.eq(ExclusiveMerchant::getStatus, status);
        }
        applyMerchantDataScope(wrapper, userId, deptId, dataScope);
        wrapper.orderByDesc(ExclusiveMerchant::getCreateTime);
        IPage<ExclusiveMerchant> result = exclusiveMerchantReadFacade.selectPage(new Page<>(page, size), wrapper);
        return okPage(result.convert(ExclusiveMerchantStatusVO::from));
    }

    @Operation(summary = "导出活动列表CSV", description = "按活动名称筛选导出活动列表 CSV。")
    @GetMapping("/activities/exports")
    @RequireRoles({RoleCodes.ADMIN, RoleCodes.BIZ_LEADER, RoleCodes.CHANNEL_LEADER})
    public void exportActivities(
            @Parameter(description = "活动名称关键字。") @RequestParam(required = false) String activityName,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope,
            HttpServletResponse response) throws IOException {
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"activities.csv\"");
        PrintWriter writer = response.getWriter();
        writer.write('\ufeff');
        writer.println("活动ID,活动名称,开始时间,结束时间,状态");

        long current = 1L;
        LocalDateTime now = LocalDateTime.now();
        while (true) {
            long offset = (current - 1) * EXPORT_BATCH_SIZE;
            List<ColonelsettlementActivity> rows = productActivityReadFacade.selectExportPage(
                    offset,
                    EXPORT_BATCH_SIZE,
                    StringUtils.hasText(activityName) ? activityName.trim() : null,
                    now
            );
            if (rows == null || rows.isEmpty()) {
                break;
            }
            for (ColonelsettlementActivity activity : rows) {
                writer.printf("%s,%s,%s,%s,%s%n",
                        csvEscape(activity.getActivityId()),
                        csvEscape(activity.getName()),
                        csvEscape(activity.getStartTime()),
                        csvEscape(activity.getEndTime()),
                        csvEscape(activity.getStatus() != null && Integer.valueOf(1).equals(activity.getStatus()) ? "进行中" : "已结束")
                );
            }
            if (rows.size() < EXPORT_BATCH_SIZE) {
                break;
            }
            current++;
        }
        writer.flush();
    }

    private static String csvEscape(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value);
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    private static String valueOrDefault(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private static String firstText(String first, String second) {
        if (StringUtils.hasText(first)) {
            return first.trim();
        }
        return StringUtils.hasText(second) ? second.trim() : null;
    }

    private static String compactPair(String first, String second) {
        String primary = valueOrDefault(first, "-");
        String secondary = valueOrDefault(second, "-");
        return primary + " / ID：" + secondary;
    }

    private static String compactTrack(String firstLabel, BigDecimal firstValue, String secondLabel, BigDecimal secondValue) {
        return firstLabel + "：" + formatCsvMoney(firstValue) + "；" + secondLabel + "：" + formatCsvMoney(secondValue);
    }

    private static String formatCsvMoney(BigDecimal value) {
        return value == null ? "-" : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String compactOrderTime(OrderDetailVO vo) {
        return "付款：" + valueOrDefault(formatCsvTime(vo.getPayTime()), "-")
                + "；结算：" + valueOrDefault(formatCsvTime(vo.getSettleTime()), "-")
                + "；创建：" + valueOrDefault(formatCsvTime(vo.getOrderCreateTime()), "-");
    }

    private static String formatCsvTime(LocalDateTime value) {
        return value == null ? null : value.toString().replace('T', ' ');
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

    private String metricsCacheKey(String timeField, UUID userId, UUID deptId, DataScope dataScope) {
        String timeColumn = resolveTimeColumn(timeField);
        if (dataScope == DataScope.PERSONAL) {
            return cacheKey(timeColumn, DataScope.PERSONAL, userId);
        }
        if (dataScope == DataScope.DEPT) {
            return cacheKey(timeColumn, DataScope.DEPT, deptId);
        }
        if (dataScope == DataScope.ALL) {
            return cacheKey(timeColumn, DataScope.ALL);
        }
        return cacheKey(timeColumn, "NO_SCOPE");
    }

    private String resolveTimeColumn(String timeField) {
        if (!StringUtils.hasText(timeField)) {
            return "create_time";
        }
        String normalized = timeField.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "createtime", "create_time", "create" -> "create_time";
            case "settletime", "settle_time", "settle" -> "settle_time";
            default -> throw BusinessException.param("非法时间字段: " + timeField);
        };
    }

    private boolean isEstimateTrack(String timeField) {
        return "create_time".equals(resolveTimeColumn(timeField));
    }

    private long serviceFeeExpenseCent(
            long serviceFeeIncome,
            long techServiceFee,
            long serviceProfit,
            boolean estimateTrack) {
        long techDeduction = estimateTrack ? techServiceFee : 0L;
        return Math.max(serviceFeeIncome - techDeduction - serviceProfit, 0L);
    }

    private String resolveAliasedOrderTimeColumn(String timeField) {
        return "co." + resolveTimeColumn(timeField);
    }

    private OrderVO toOrderVO(ColonelsettlementOrder order) {
        OrderVO vo = new OrderVO();
        vo.setId(StringUtils.hasText(order.getOrderId()) ? order.getOrderId() : String.valueOf(order.getId()));
        vo.setProductId(order.getProductId());
        vo.setProductName(StringUtils.hasText(order.getProductTitle()) ? order.getProductTitle() : order.getProductName());
        vo.setProductImage(StringUtils.hasText(order.getProductImage()) ? order.getProductImage() : order.getProductPic());
        vo.setShopName(order.getShopName());
        vo.setProductQuantity(order.getProductQuantity() != null ? order.getProductQuantity() : order.getItemNum());
        vo.setCommissionRate(order.getCommissionRate());
        vo.setServiceFeeRate(order.getServiceFeeRate());
        vo.setChannelId(order.getChannelUserId() == null ? null : order.getChannelUserId().toString());
        vo.setChannelName(order.getChannelUserName());
        vo.setTalentName(pickText(order.getExtraData(), "talentName", "talent_nickname", "author_name"));
        vo.setAmount(centToYuan(order.getOrderAmount()));
        vo.setGoodsPrice(centToYuan(order.getActualAmount()));
        vo.setCommission(centToYuan(order.getSettleSecondColonelCommission()));
        vo.setFreight(centToYuan(0L));
        vo.setStatus(fromOrderStatusCode(order.getOrderStatus()));
        vo.setCreateTime(order.getCreateTime());
        vo.setSettleTime(order.getSettleTime());

        String source = order.getPickSource();
        if (order.getExtraData() != null && order.getExtraData().containsKey("attributionSource")) {
            source = String.valueOf(order.getExtraData().get("attributionSource"));
        }
        vo.setAttributionSource(source);
        return vo;
    }

    private String pickText(Map<String, Object> extraData, String... keys) {
        if (extraData == null || extraData.isEmpty()) {
            return "-";
        }
        for (String key : keys) {
            Object value = extraData.get(key);
            if (value != null && StringUtils.hasText(value.toString())) {
                return value.toString();
            }
        }
        return "-";
    }

    private LocalDateTime pickDateTime(Map<String, Object> extraData, String... keys) {
        if (extraData == null || extraData.isEmpty()) {
            return null;
        }
        Object value = pickRawValue(extraData, keys);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            long raw = number.longValue();
            return raw > 9_999_999_999L ? AppZone.fromEpochMilli(raw) : AppZone.fromEpochSecond(raw);
        }
        String text = value.toString().trim();
        if (!StringUtils.hasText(text)) {
            return null;
        }
        if (text.matches("^-?\\d+$")) {
            long raw = Long.parseLong(text);
            return raw > 9_999_999_999L ? AppZone.fromEpochMilli(raw) : AppZone.fromEpochSecond(raw);
        }
        try {
            return LocalDateTime.parse(text.replace('T', ' ').substring(0, Math.min(19, text.length())), UPSTREAM_DATE_TIME_FORMATTER);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private Object pickRawValue(Map<String, Object> extraData, String... keys) {
        if (extraData == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (extraData.containsKey(key)) {
                return extraData.get(key);
            }
        }
        Object nested = extraData.get("colonel_order_info");
        if (nested == null) {
            nested = extraData.get("colonelOrderInfo");
        }
        if (nested instanceof Map<?, ?> nestedMap) {
            for (String key : keys) {
                if (nestedMap.containsKey(key)) {
                    return nestedMap.get(key);
                }
            }
        }
        return null;
    }

    private Integer toOrderStatusCode(String status) {
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "ORDERED" -> 1;
            case "SHIPPED" -> 2;
            case "FINISHED" -> 3;
            case "CANCELLED" -> 4;
            default -> throw BusinessException.param("非法订单状态: " + status);
        };
    }

    /**
     * 订单类型映射（order_type 字段）：
     * 1 = 商家型招商单（MERCHANT），2 = 推广单（PROMOTION），3 = 混合，4 = 团长型
     */
    private Integer toOrderTypeCode(String recruitType) {
        if (recruitType == null) return null;
        String normalized = recruitType.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "MERCHANT" -> 1;
            case "PROMOTION" -> 2;
            case "MIXED" -> 3;
            case "COLONEL" -> 4;
            default -> throw BusinessException.param("非法招商类型: " + recruitType);
        };
    }

    private String toOrderTypeText(Integer orderType) {
        if (orderType == null) {
            return "";
        }
        return switch (orderType) {
            case 1 -> "推广者推广";
            case 2 -> "结算";
            default -> "";
        };
    }

    private String fromOrderStatusCode(Integer statusCode) {
        if (statusCode == null) {
            return "ORDERED";
        }
        return switch (statusCode) {
            case 1 -> "ORDERED";
            case 2 -> "SHIPPED";
            case 3 -> "FINISHED";
            case 4 -> "CANCELLED";
            default -> "ORDERED";
        };
    }

    private String toDetailOrderStatusText(Integer statusCode) {
        if (statusCode == null) {
            return "待结算";
        }
        return switch (statusCode) {
            case 1, 2 -> "待结算";
            case 3 -> "已结算";
            case 4 -> "已失效";
            case 5 -> "已退款";
            default -> "待结算";
        };
    }

    private BigDecimal centToYuan(Long cent) {
        long value = cent == null ? 0L : cent;
        return BigDecimal.valueOf(value).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private QueryWrapper<ColonelsettlementOrder> buildScopedQuery(
            UUID userId,
            UUID deptId,
            DataScope dataScope) {
        QueryWrapper<ColonelsettlementOrder> wrapper = new QueryWrapper<>();
        wrapper.eq("deleted", 0);
        applyScopedQueryDataScope(wrapper, userId, deptId, dataScope);
        return wrapper;
    }

    private Map<String, Object> getSingleAggregate(QueryWrapper<ColonelsettlementOrder> wrapper) {
        List<Map<String, Object>> rows = dataOrderQueryFacade.selectMaps(wrapper);
        if (rows == null || rows.isEmpty()) {
            return Map.of();
        }
        return rows.get(0);
    }

    private List<Map<String, Object>> queryOrderSummaryAggregates(
            boolean daily,
            OrderTrackColumns columns,
            String timeField,
            LocalDateTime start,
            LocalDateTime end,
            String orderId,
            String status,
            UUID talentId,
            String merchantId,
            String productId,
            String productName,
            String shopName,
            String talentName,
            String colonelName,
            String channelName,
            String colonelActivityId,
            String recruitType,
            UUID userId,
            UUID deptId,
            DataScope dataScope) {
        QueryWrapper<ColonelsettlementOrder> wrapper = buildOrderFilterWrapper(
                false,
                timeField,
                start,
                end,
                orderId,
                status,
                talentId,
                merchantId,
                productId,
                productName,
                shopName,
                talentName,
                colonelName,
                channelName,
                colonelActivityId,
                recruitType,
                userId,
                deptId,
                dataScope);
        List<String> selects = new ArrayList<>();
        String dayExpr = "DATE(" + columns.timeColumn() + ")";
        if (daily) {
            selects.add(dayExpr + " AS stat_date");
        }
        selects.add("COUNT(1) AS order_count");
        selects.add("COUNT(DISTINCT COALESCE(extra_data->>'author_short_id', talent_name)) AS talent_promoter_count");
        selects.add("COUNT(DISTINCT CASE WHEN extra_data->>'colonel_type' = '2' THEN second_colonel_buyin_id END) AS colonel_promoter_count");
        selects.add("COUNT(DISTINCT product_id) AS product_count");
        selects.add("COALESCE(SUM(" + columns.amountColumn() + "), 0) AS order_amount_cent");
        selects.add("COUNT(CASE WHEN " + REFUND_ORDER_PREDICATE + " THEN 1 END) AS refund_order_count");
        selects.add("COALESCE(SUM(CASE WHEN " + REFUND_ORDER_PREDICATE + " THEN " + columns.amountColumn() + " ELSE 0 END), 0) AS refund_order_amount_cent");
        selects.add("COALESCE(SUM(CASE WHEN " + REFUND_ORDER_PREDICATE + " THEN " + columns.refundServiceFeeExpression() + " ELSE 0 END), 0) AS refund_service_fee_cent");
        selects.add("COALESCE(SUM(actual_amount), 0) AS actual_amount_cent");
        selects.add("COALESCE(SUM(" + columns.serviceFeeColumn() + "), 0) AS service_fee_income_cent");
        selects.add("COALESCE(SUM(" + columns.techFeeColumn() + "), 0) AS tech_service_fee_cent");
        selects.add("COALESCE(SUM(" + columns.expenseColumn() + "), 0) AS service_fee_expense_cent");
        selects.add("COALESCE(SUM(settle_second_colonel_commission), 0) AS talent_commission_cent");
        wrapper.select(selects.toArray(String[]::new));
        if (daily) {
            wrapper.groupBy(dayExpr).orderByDesc(dayExpr);
        }
        List<Map<String, Object>> rows = dataOrderQueryFacade.selectMaps(wrapper);
        return rows == null ? List.of() : rows;
    }

    private CommissionService.CommissionSummary queryOrderSummaryCommission(
            boolean daily,
            String date,
            OrderTrackColumns columns,
            String timeField,
            LocalDateTime start,
            LocalDateTime end,
            String orderId,
            String status,
            UUID talentId,
            String merchantId,
            String productId,
            String productName,
            String shopName,
            String talentName,
            String colonelName,
            String channelName,
            String colonelActivityId,
            String recruitType,
            UUID userId,
            UUID deptId,
            DataScope dataScope) {
        List<Map<String, Object>> rows = queryOrderSummaryCommissionBuckets(
                daily,
                columns,
                timeField,
                start,
                end,
                orderId,
                status,
                talentId,
                merchantId,
                productId,
                productName,
                shopName,
                talentName,
                colonelName,
                channelName,
                colonelActivityId,
                recruitType,
                userId,
                deptId,
                dataScope);
        List<CommissionService.ActivityCommissionBucket> buckets = rows.stream()
                .filter(row -> !daily || date == null || date.equals(asString(row, "stat_date")))
                .map(row -> toActivityCommissionBucket(row, isEstimateTrack(timeField)))
                .toList();
        return calculateCommissionSummary(buckets);
    }

    private Map<String, CommissionService.CommissionSummary> queryDailyOrderSummaryCommission(
            OrderTrackColumns columns,
            String timeField,
            LocalDateTime start,
            LocalDateTime end,
            String orderId,
            String status,
            UUID talentId,
            String merchantId,
            String productId,
            String productName,
            String shopName,
            String talentName,
            String colonelName,
            String channelName,
            String colonelActivityId,
            String recruitType,
            UUID userId,
            UUID deptId,
            DataScope dataScope) {
        List<Map<String, Object>> rows = queryOrderSummaryCommissionBuckets(
                true,
                columns,
                timeField,
                start,
                end,
                orderId,
                status,
                talentId,
                merchantId,
                productId,
                productName,
                shopName,
                talentName,
                colonelName,
                channelName,
                colonelActivityId,
                recruitType,
                userId,
                deptId,
                dataScope);
        Map<String, List<CommissionService.ActivityCommissionBucket>> grouped = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String date = asString(row, "stat_date");
            grouped.computeIfAbsent(date, ignored -> new ArrayList<>())
                    .add(toActivityCommissionBucket(row, isEstimateTrack(timeField)));
        }
        Map<String, CommissionService.CommissionSummary> result = new LinkedHashMap<>();
        grouped.forEach((key, buckets) -> result.put(key, calculateCommissionSummary(buckets)));
        return result;
    }

    private List<Map<String, Object>> queryOrderSummaryCommissionBuckets(
            boolean daily,
            OrderTrackColumns columns,
            String timeField,
            LocalDateTime start,
            LocalDateTime end,
            String orderId,
            String status,
            UUID talentId,
            String merchantId,
            String productId,
            String productName,
            String shopName,
            String talentName,
            String colonelName,
            String channelName,
            String colonelActivityId,
            String recruitType,
            UUID userId,
            UUID deptId,
            DataScope dataScope) {
        QueryWrapper<ColonelsettlementOrder> wrapper = buildOrderFilterWrapper(
                false,
                timeField,
                start,
                end,
                orderId,
                status,
                talentId,
                merchantId,
                productId,
                productName,
                shopName,
                talentName,
                colonelName,
                channelName,
                colonelActivityId,
                recruitType,
                userId,
                deptId,
                dataScope);
        String dayExpr = "DATE(" + columns.timeColumn() + ")";
        String recruiterExpr = "COALESCE(colonel_user_id, user_id)";
        List<String> selects = new ArrayList<>();
        if (daily) {
            selects.add(dayExpr + " AS stat_date");
        }
        selects.add("COALESCE(colonel_activity_id, '') AS activity_id");
        selects.add("COALESCE(product_id, '') AS product_id");
        selects.add(recruiterExpr + " AS recruiter_user_id");
        selects.add("COALESCE(SUM(" + columns.serviceFeeColumn() + "), 0) AS service_fee_income");
        selects.add("COALESCE(SUM(" + columns.techFeeColumn() + "), 0) AS tech_service_fee");
        selects.add("COALESCE(SUM(" + columns.expenseColumn() + "), 0) AS service_fee_expense");
        selects.add("COALESCE(SUM(settle_second_colonel_commission), 0) AS talent_commission");
        wrapper.select(selects.toArray(String[]::new));
        if (daily) {
            wrapper.groupBy(dayExpr, "colonel_activity_id", "product_id", recruiterExpr)
                    .orderByDesc(dayExpr);
        } else {
            wrapper.groupBy("colonel_activity_id", "product_id", recruiterExpr);
        }
        List<Map<String, Object>> rows = dataOrderQueryFacade.selectMaps(wrapper);
        return rows == null ? List.of() : rows;
    }

    private QueryWrapper<ColonelsettlementOrder> buildOrderFilterWrapper(
            boolean aliased,
            String timeField,
            LocalDateTime start,
            LocalDateTime end,
            String orderId,
            String status,
            UUID talentId,
            String merchantId,
            String productId,
            String productName,
            String shopName,
            String talentName,
            String colonelName,
            String channelName,
            String colonelActivityId,
            String recruitType,
            UUID userId,
            UUID deptId,
            DataScope dataScope) {
        QueryWrapper<ColonelsettlementOrder> wrapper = new QueryWrapper<>();
        String timeColumn = column(aliased, resolveTimeColumn(timeField));
        wrapper.eq(column(aliased, "deleted"), 0)
                .ge(timeColumn, start)
                .lt(timeColumn, end);
        applyOrderDataScope(wrapper, aliased, userId, deptId, dataScope);
        if (StringUtils.hasText(orderId)) {
            wrapper.like(column(aliased, "order_id"), orderId.trim());
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(column(aliased, "order_status"), toOrderStatusCode(status));
        }
        if (talentId != null) {
            wrapper.eq(column(aliased, "talent_id"), talentId);
        }
        if (StringUtils.hasText(merchantId)) {
            String normalized = merchantId.trim();
            String digits = normalized.replaceAll("\\D", "");
            String shopIdText = StringUtils.hasText(digits) ? digits : normalized;
            String prefix = aliased ? "co." : "";
            wrapper.apply(
                    "(" + prefix + "extra_data->>'merchant_id' = {0} OR CAST(" + prefix + "shop_id AS TEXT) = {1})",
                    normalized,
                    shopIdText
            );
        }
        if (StringUtils.hasText(productId)) {
            wrapper.eq(column(aliased, "product_id"), productId.trim());
        }
        if (StringUtils.hasText(productName)) {
            String normalized = productName.trim();
            wrapper.and(w -> w.like(column(aliased, "product_name"), normalized)
                    .or().like(column(aliased, "product_title"), normalized));
        }
        if (StringUtils.hasText(shopName)) {
            wrapper.like(column(aliased, "shop_name"), shopName.trim());
        }
        if (StringUtils.hasText(talentName)) {
            wrapper.like(column(aliased, "talent_name"), talentName.trim());
        }
        if (StringUtils.hasText(colonelName)) {
            wrapper.like(column(aliased, "colonel_user_name"), colonelName.trim());
        }
        if (StringUtils.hasText(channelName)) {
            wrapper.like(column(aliased, "channel_user_name"), channelName.trim());
        }
        if (StringUtils.hasText(colonelActivityId)) {
            wrapper.eq(column(aliased, "colonel_activity_id"), colonelActivityId.trim());
        }
        if (StringUtils.hasText(recruitType)) {
            wrapper.eq(column(aliased, "order_type"), toOrderTypeCode(recruitType));
        }
        return wrapper;
    }

    private void applyOrderDetailExtraFilters(
            QueryWrapper<ColonelsettlementOrder> wrapper,
            boolean aliased,
            String activityName,
            String partnerId,
            String partnerName,
            String channelName,
            String recruiterName) {
        String prefix = aliased ? "co." : "";
        if (StringUtils.hasText(activityName)) {
            wrapper.apply("""
                    EXISTS (
                        SELECT 1
                        FROM colonel_activity ca
                        WHERE ca.activity_id = %scolonel_activity_id
                          AND ca.activity_name ILIKE CONCAT('%%', {0}, '%%')
                    )
                    """.formatted(prefix), activityName.trim());
        }
        if (StringUtils.hasText(partnerName)) {
            String normalized = partnerName.trim();
            wrapper.and(w -> w.like(column(aliased, "shop_name"), normalized)
                    .or().apply(prefix + "extra_data->>'partner_name' ILIKE CONCAT('%%', {0}, '%%')", normalized)
                    .or().apply(prefix + "extra_data->>'partnerName' ILIKE CONCAT('%%', {0}, '%%')", normalized)
                    .or().apply(prefix + "extra_data->>'merchant_name' ILIKE CONCAT('%%', {0}, '%%')", normalized)
                    .or().apply(prefix + "extra_data->>'merchantName' ILIKE CONCAT('%%', {0}, '%%')", normalized));
        }
        if (StringUtils.hasText(channelName)) {
            String normalized = channelName.trim();
            wrapper.and(w -> w.like(column(aliased, "channel_user_name"), normalized)
                    .or().apply("""
                            EXISTS (
                                SELECT 1
                                FROM performance_records pr
                                JOIN sys_user su ON su.id = pr.final_channel_user_id
                                WHERE pr.is_valid = TRUE
                                  AND pr.order_id = %sorder_id
                                  AND (su.real_name ILIKE CONCAT('%%', {0}, '%%')
                                       OR su.username ILIKE CONCAT('%%', {0}, '%%'))
                            )
                            """.formatted(prefix), normalized));
        }
        if (StringUtils.hasText(recruiterName)) {
            String normalized = recruiterName.trim();
            wrapper.and(w -> w.like(column(aliased, "colonel_user_name"), normalized)
                    .or().apply("""
                            EXISTS (
                                SELECT 1
                                FROM performance_records pr
                                JOIN sys_user su ON su.id = pr.final_recruiter_user_id
                                WHERE pr.is_valid = TRUE
                                  AND pr.order_id = %sorder_id
                                  AND (su.real_name ILIKE CONCAT('%%', {0}, '%%')
                                       OR su.username ILIKE CONCAT('%%', {0}, '%%'))
                            )
                            """.formatted(prefix), normalized));
        }
    }

    /**
     * 追加招商部门 / 渠道部门 IN 筛选。
     * <p>recruiterDeptIds 匹配 dept_id（招商负责人所属部门），
     * channelDeptIds 匹配 channel_dept_id（渠道负责人所属部门）。</p>
     */
    private void applyDeptIdFilters(
            QueryWrapper<ColonelsettlementOrder> wrapper,
            boolean aliased,
            List<UUID> recruiterDeptIds,
            List<UUID> channelDeptIds) {
        if (recruiterDeptIds != null && !recruiterDeptIds.isEmpty()) {
            wrapper.in(column(aliased, "dept_id"), recruiterDeptIds);
        }
        if (channelDeptIds != null && !channelDeptIds.isEmpty()) {
            wrapper.in(column(aliased, "channel_dept_id"), channelDeptIds);
        }
    }

    /**
     * 将前端 CSV 形式的部门 ID（"uuid1,uuid2"）解析为 UUID 列表。
     * 非法 UUID 直接忽略，不抛异常。
     */
    static List<UUID> parseUuidCsv(String csv) {
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
                // 非法 UUID 跳过
            }
        }
        return result;
    }

    private void applyOrderDataScope(
            QueryWrapper<ColonelsettlementOrder> wrapper,
            boolean aliased,
            UUID userId,
            UUID deptId,
            DataScope dataScope) {
        applyQueryDataScope(
                wrapper,
                userId,
                deptId,
                dataScope,
                column(aliased, "user_id"),
                column(aliased, "dept_id"));
    }

    private void applyOrderDataScope(
            QueryWrapper<ColonelsettlementOrder> wrapper,
            boolean aliased,
            UUID userId,
            UUID deptId,
            DataScope dataScope,
            Collection<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            applyOrderDataScope(wrapper, aliased, userId, deptId, dataScope);
            return;
        }
        // 招商专员的订单明细是全量只读业务视图，不按个人/部门归属裁剪订单行。
        // 该例外只作用于订单明细查询，其他数据域仍按各自的数据范围策略执行。
        if (hasAnyRole(roleCodes, RoleCodes.BIZ_STAFF)) {
            return;
        }
        String ownerColumn = column(aliased, resolveOrderOwnerColumn(roleCodes));
        String deptColumn = column(aliased, resolveOrderDeptColumn(roleCodes));
        applyQueryDataScope(wrapper, userId, deptId, dataScope, ownerColumn, deptColumn);
    }

    private String resolveOrderOwnerColumn(Collection<String> roleCodes) {
        if (hasAnyRole(roleCodes, RoleCodes.BIZ_LEADER, RoleCodes.BIZ_STAFF)) {
            return "colonel_user_id";
        }
        if (hasAnyRole(roleCodes, RoleCodes.CHANNEL_LEADER, RoleCodes.CHANNEL_STAFF)) {
            return "channel_user_id";
        }
        return "user_id";
    }

    private String resolveOrderDeptColumn(Collection<String> roleCodes) {
        if (hasAnyRole(roleCodes, RoleCodes.BIZ_LEADER, RoleCodes.BIZ_STAFF)) {
            return "dept_id";
        }
        if (hasAnyRole(roleCodes, RoleCodes.CHANNEL_LEADER, RoleCodes.CHANNEL_STAFF)) {
            return "channel_dept_id";
        }
        return "dept_id";
    }

    private boolean hasAnyRole(Collection<String> roleCodes, String... expectedRoles) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return false;
        }
        for (String roleCode : roleCodes) {
            for (String expectedRole : expectedRoles) {
                if (expectedRole.equalsIgnoreCase(roleCode)) {
                    return true;
                }
            }
        }
        return false;
    }

    private String column(boolean aliased, String column) {
        return aliased ? "co." + column : column;
    }

    private OrderTrackColumns resolveOrderTrackColumns(String timeField) {
        String timeColumn = resolveTimeColumn(timeField);
        if ("settle_time".equals(timeColumn)) {
            return new OrderTrackColumns(
                    timeColumn,
                    "settle_amount",
                    "effective_service_fee",
                    "effective_tech_service_fee",
                    "effective_service_fee_expense",
                    REFUND_EFFECTIVE_SERVICE_FEE_EXPRESSION);
        }
        return new OrderTrackColumns(
                timeColumn,
                "order_amount",
                "estimate_service_fee",
                "estimate_tech_service_fee",
                "estimate_service_fee_expense",
                REFUND_ESTIMATE_SERVICE_FEE_EXPRESSION);
    }

    private OrderSummaryRowVO toOrderSummaryRow(
            Map<String, Object> row,
            CommissionService.CommissionSummary commissionSummary,
            String date,
            boolean estimateTrack) {
        CommissionService.CommissionSummary summary = commissionSummary == null
                ? zeroCommissionSummary()
                : commissionSummary;
        long orderAmount = asLong(row, "order_amount_cent");
        long actualAmount = asLong(row, "actual_amount_cent");
        long serviceFeeIncome = asLong(row, "service_fee_income_cent");
        long techServiceFee = asLong(row, "tech_service_fee_cent");
        OrderSummaryRowVO vo = new OrderSummaryRowVO();
        vo.setDate(date);
        vo.setTalentPromoterCount(asLong(row, "talent_promoter_count"));
        vo.setColonelPromoterCount(asLong(row, "colonel_promoter_count"));
        vo.setProductCount(asLong(row, "product_count"));
        vo.setOrderCount(asLong(row, "order_count"));
        vo.setOrderAmount(centToYuan(orderAmount));
        vo.setRefundOrderCount(asLong(row, "refund_order_count"));
        vo.setRefundOrderAmount(centToYuan(asLong(row, "refund_order_amount_cent")));
        vo.setRefundServiceFee(centToYuan(asLong(row, "refund_service_fee_cent")));

        long serviceFeeExpenseCent = asLong(row, "service_fee_expense_cent");
        long serviceProfitCent = CommissionService.serviceFeeNetCent(
                serviceFeeIncome,
                techServiceFee,
                serviceFeeExpenseCent,
                estimateTrack);

        vo.setProductAverageServiceFeeRate(percent(serviceFeeIncome, actualAmount));
        vo.setOrderAverageServiceFeeRate(percent(serviceProfitCent, orderAmount));
        vo.setServiceFeeIncome(centToYuan(serviceFeeIncome));
        vo.setTechServiceFee(centToYuan(techServiceFee));
        vo.setServiceFeeExpense(centToYuan(serviceFeeExpenseCent));
        vo.setServiceFeeProfit(centToYuan(serviceProfitCent));
        vo.setGrossProfit(centToYuan(Math.max(serviceProfitCent - summary.bizCommission() - summary.channelCommission(), 0L)));
        return vo;
    }

    private CommissionService.ActivityCommissionBucket toActivityCommissionBucket(Map<String, Object> row, boolean estimateTrack) {
        String productId = asString(row, "product_id");
        return new CommissionService.ActivityCommissionBucket(
                asString(row, "activity_id"),
                StringUtils.hasText(productId) ? productId : null,
                asUuid(row, "recruiter_user_id"),
                asLong(row, "service_fee_income"),
                estimateTrack ? asLong(row, "tech_service_fee") : 0L,
                asLong(row, "service_fee_expense"),
                asLong(row, "talent_commission"));
    }

    private CommissionService.CommissionSummary calculateCommissionSummary(
            List<CommissionService.ActivityCommissionBucket> buckets) {
        CommissionService.CommissionSummary summary = commissionService.calculateByActivityBuckets(buckets);
        return summary == null ? zeroCommissionSummary() : summary;
    }

    private CommissionService.CommissionSummary zeroCommissionSummary() {
        return new CommissionService.CommissionSummary(
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                BigDecimal.ZERO,
                BigDecimal.ZERO);
    }

    private Map<String, Object> firstRow(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return Map.of();
        }
        return rows.get(0);
    }

    private Long asLong(Map<String, Object> row, String key) {
        Object value = readValue(row, key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignore) {
            return 0L;
        }
    }

    private String asString(Map<String, Object> row, String key) {
        Object value = readValue(row, key);
        return value == null ? "" : String.valueOf(value);
    }

    private UUID asUuid(Map<String, Object> row, String key) {
        Object value = readValue(row, key);
        if (value instanceof UUID uuid) {
            return uuid;
        }
        if (value == null || !StringUtils.hasText(String.valueOf(value))) {
            return null;
        }
        try {
            return UUID.fromString(String.valueOf(value));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private BigDecimal percent(long numerator, long denominator) {
        if (denominator <= 0L || numerator <= 0L) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(numerator)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
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

    private void applyPageDataScope(
            QueryWrapper<ColonelsettlementOrder> wrapper,
            UUID userId,
            UUID deptId,
            DataScope dataScope) {
        applyQueryDataScope(wrapper, userId, deptId, dataScope, "co.user_id", "co.dept_id");
    }

    private void applyScopedQueryDataScope(
            QueryWrapper<ColonelsettlementOrder> wrapper,
            UUID userId,
            UUID deptId,
            DataScope dataScope) {
        applyQueryDataScope(wrapper, userId, deptId, dataScope, "user_id", "dept_id");
    }

    private void applyTalentDataScope(
            LambdaQueryWrapper<ExclusiveTalent> wrapper,
            UUID userId,
            UUID deptId,
            DataScope dataScope) {
        applyLambdaDataScope(
                wrapper,
                userId,
                deptId,
                dataScope,
                ExclusiveTalent::getUserId,
                ExclusiveTalent::getDeptId);
    }

    private void applyMerchantDataScope(
            LambdaQueryWrapper<ExclusiveMerchant> wrapper,
            UUID userId,
            UUID deptId,
            DataScope dataScope) {
        applyLambdaDataScope(
                wrapper,
                userId,
                deptId,
                dataScope,
                ExclusiveMerchant::getUserId,
                ExclusiveMerchant::getDeptId);
    }

    private <T> void applyQueryDataScope(
            QueryWrapper<T> wrapper,
            UUID userId,
            UUID deptId,
            DataScope dataScope,
            String userIdColumn,
            String deptIdColumn) {
        if (wrapper == null || dataScope == null) {
            return;
        }
        if (!dddRefactorProperties.getDataScopePolicy().isEnabled()) {
            applyQueryDataScopeLegacy(wrapper, userId, deptId, dataScope, userIdColumn, deptIdColumn);
            return;
        }
        applyQueryDataScopeWithResolver(wrapper, userId, deptId, dataScope, userIdColumn, deptIdColumn);
    }

    private <T> void applyQueryDataScopeLegacy(
            QueryWrapper<T> wrapper,
            UUID userId,
            UUID deptId,
            DataScope dataScope,
            String userIdColumn,
            String deptIdColumn) {
        requireDataScopeContextLegacy(userId, deptId, dataScope);
        if (dataScope == DataScope.PERSONAL && userId != null && StringUtils.hasText(userIdColumn)) {
            wrapper.eq(userIdColumn, userId);
            return;
        }
        if (dataScope == DataScope.DEPT && deptId != null && StringUtils.hasText(deptIdColumn)) {
            wrapper.eq(deptIdColumn, deptId);
        }
    }

    private <T> void applyQueryDataScopeWithResolver(
            QueryWrapper<T> wrapper,
            UUID userId,
            UUID deptId,
            DataScope dataScope,
            String userIdColumn,
            String deptIdColumn) {
        requireDataScopeContextWithResolver(userId, deptId, dataScope);
        dataScopeResolver.applyTo(wrapper, userId, deptId, dataScope, userIdColumn, deptIdColumn);
    }

    private <T> void applyLambdaDataScope(
            LambdaQueryWrapper<T> wrapper,
            UUID userId,
            UUID deptId,
            DataScope dataScope,
            SFunction<T, ?> userIdColumn,
            SFunction<T, ?> deptIdColumn) {
        if (wrapper == null || dataScope == null) {
            return;
        }
        if (!dddRefactorProperties.getDataScopePolicy().isEnabled()) {
            applyLambdaDataScopeLegacy(wrapper, userId, deptId, dataScope, userIdColumn, deptIdColumn);
            return;
        }
        applyLambdaDataScopeWithResolver(wrapper, userId, deptId, dataScope, userIdColumn, deptIdColumn);
    }

    private <T> void applyLambdaDataScopeLegacy(
            LambdaQueryWrapper<T> wrapper,
            UUID userId,
            UUID deptId,
            DataScope dataScope,
            SFunction<T, ?> userIdColumn,
            SFunction<T, ?> deptIdColumn) {
        requireDataScopeContextLegacy(userId, deptId, dataScope);
        if (dataScope == DataScope.PERSONAL && userId != null && userIdColumn != null) {
            wrapper.eq(userIdColumn, userId);
            return;
        }
        if (dataScope == DataScope.DEPT && deptId != null && deptIdColumn != null) {
            wrapper.eq(deptIdColumn, deptId);
        }
    }

    private <T> void applyLambdaDataScopeWithResolver(
            LambdaQueryWrapper<T> wrapper,
            UUID userId,
            UUID deptId,
            DataScope dataScope,
            SFunction<T, ?> userIdColumn,
            SFunction<T, ?> deptIdColumn) {
        requireDataScopeContextWithResolver(userId, deptId, dataScope);
        dataScopeResolver.applyTo(wrapper, userId, deptId, dataScope, userIdColumn, deptIdColumn);
    }

    private void requireDataScopeContextLegacy(UUID userId, UUID deptId, DataScope dataScope) {
        if (dataScope == DataScope.PERSONAL && userId == null) {
            throw BusinessException.forbidden("数据权限异常：缺少用户上下文");
        }
        if (dataScope == DataScope.DEPT && deptId == null) {
            throw BusinessException.forbidden("数据权限异常：缺少部门上下文");
        }
    }

    private void requireDataScopeContextWithResolver(UUID userId, UUID deptId, DataScope dataScope) {
        DataScopeResolver.ResolvedDataScope resolved =
                dataScopeResolver.resolve(userId, deptId, dataScope);
        if (resolved.missingUser()) {
            throw BusinessException.forbidden("数据权限异常：缺少用户上下文");
        }
        if (resolved.missingDept()) {
            throw BusinessException.forbidden("数据权限异常：缺少部门上下文");
        }
    }

    private record OrderTrackColumns(
            String timeColumn,
            String amountColumn,
            String serviceFeeColumn,
            String techFeeColumn,
            String expenseColumn,
            String refundServiceFeeExpression) {
    }

    private record RefundMetricsAggregate(
            long orderCount,
            long orderAmountCent,
            long serviceFeeCent) {
    }

}
