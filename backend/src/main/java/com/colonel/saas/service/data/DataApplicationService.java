package com.colonel.saas.service.data;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.common.base.BaseController;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.common.result.PageResult;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.entity.ColonelsettlementActivity;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.entity.ExclusiveMerchant;
import com.colonel.saas.entity.ExclusiveTalent;
import com.colonel.saas.entity.PerformanceRecord;
import com.colonel.saas.entity.SysUser;
import com.colonel.saas.vo.data.OrderDetailVO;
import com.colonel.saas.mapper.ColonelsettlementActivityMapper;
import com.colonel.saas.mapper.ColonelsettlementOrderMapper;
import com.colonel.saas.mapper.ExclusiveMerchantMapper;
import com.colonel.saas.mapper.ExclusiveTalentMapper;
import com.colonel.saas.mapper.PerformanceRecordMapper;
import com.colonel.saas.mapper.SysUserMapper;
import com.colonel.saas.service.CommissionService;
import com.colonel.saas.service.PerformanceMetricsQueryService;
import com.colonel.saas.service.ShortTtlCacheService;
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
 * @see com.colonel.saas.mapper.ColonelsettlementOrderMapper 订单 Mapper
 * @see com.colonel.saas.common.enums.DataScope 数据范围枚举
 */
public class DataApplicationService extends BaseController {

    /** 导出批次大小：每批次从数据库查询 2000 条记录写入 CSV */
    private static final long EXPORT_BATCH_SIZE = 2000L;

    /** 核心指标缓存 TTL：30 秒，避免高并发下频繁查询数据库 */
    private static final Duration METRICS_CACHE_TTL = Duration.ofSeconds(30);

    /** 指标缓存键前缀，格式：dashboard:metrics:{track}:{scope}:{id} */
    private static final String METRICS_CACHE_PREFIX = "dashboard:metrics:";

    /** 订单 Mapper，负责订单表的基础查询与分页（含数据范围过滤） */
    private final ColonelsettlementOrderMapper orderMapper;

    /** 提成计算服务，负责按活动桶计算团长/渠道/达人三方提成 */
    private final CommissionService commissionService;

    /** 独家达人 Mapper，负责独家达人监控数据查询 */
    private final ExclusiveTalentMapper exclusiveTalentMapper;

    /** 独家商家 Mapper，负责独家商家监控数据查询 */
    private final ExclusiveMerchantMapper exclusiveMerchantMapper;

    /** 活动 Mapper，负责活动列表导出查询 */
    private final ColonelsettlementActivityMapper activityMapper;

    /** 短 TTL 缓存服务，用于指标缓存与筛选项缓存 */
    private final ShortTtlCacheService shortTtlCacheService;

    /** 业绩指标聚合查询服务，负责从 performance_records 表聚合核心指标与趋势数据 */
    private final PerformanceMetricsQueryService performanceMetricsQueryService;

    /** 业绩记录 Mapper，负责按订单号批量查询业绩提成数据 */
    private final PerformanceRecordMapper performanceRecordMapper;

    /** 用户 Mapper，负责查询渠道/招商负责人姓名 */
    private final SysUserMapper sysUserMapper;

    /**
     * 构造注入所有依赖服务与 Mapper。
     *
     * @param orderMapper                    订单 Mapper
     * @param commissionService              提成计算服务
     * @param exclusiveTalentMapper          独家达人 Mapper
     * @param exclusiveMerchantMapper        独家商家 Mapper
     * @param activityMapper                 活动 Mapper
     * @param shortTtlCacheService           短 TTL 缓存服务
     * @param performanceMetricsQueryService 业绩指标聚合查询服务
     */
    public DataApplicationService(
            ColonelsettlementOrderMapper orderMapper,
            CommissionService commissionService,
            ExclusiveTalentMapper exclusiveTalentMapper,
            ExclusiveMerchantMapper exclusiveMerchantMapper,
            ColonelsettlementActivityMapper activityMapper,
            ShortTtlCacheService shortTtlCacheService,
            PerformanceMetricsQueryService performanceMetricsQueryService,
            PerformanceRecordMapper performanceRecordMapper,
            SysUserMapper sysUserMapper) {
        this.orderMapper = orderMapper;
        this.commissionService = commissionService;
        this.exclusiveTalentMapper = exclusiveTalentMapper;
        this.exclusiveMerchantMapper = exclusiveMerchantMapper;
        this.activityMapper = activityMapper;
        this.shortTtlCacheService = shortTtlCacheService;
        this.performanceMetricsQueryService = performanceMetricsQueryService;
        this.performanceRecordMapper = performanceRecordMapper;
        this.sysUserMapper = sysUserMapper;
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
        IPage<ColonelsettlementOrder> orderPage = orderMapper.findPageWithScope(new Page<>(page, size), wrapper);

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
            @Parameter(description = "招商类型") @RequestParam(required = false) String recruitType,
            @Parameter(description = "开始日期") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "结束日期") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @Parameter(description = "时间字段") @RequestParam(required = false) String timeField,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope) {

        LocalDateTime start = startDate == null
                ? LocalDate.now().minusDays(30).atStartOfDay()
                : startDate.atStartOfDay();
        LocalDateTime end = endDate == null
                ? LocalDate.now().plusDays(1).atStartOfDay()
                : endDate.plusDays(1).atStartOfDay();

        QueryWrapper<ColonelsettlementOrder> wrapper = buildOrderFilterWrapper(
                true, timeField, start, end, orderId, status, talentId, merchantId,
                productId, productName, shopName, talentName, colonelName, channelName,
                colonelActivityId, recruitType, userId, deptId, dataScope);

        IPage<ColonelsettlementOrder> orderPage = orderMapper.findPageWithScope(new Page<>(page, size), wrapper);
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
        Map<String, PerformanceRecord> perfMap = loadPerformanceMap(orderIds);

        // 批量查询活动名称
        Map<String, String> activityNameMap = loadActivityNameMap(orders);

        // 批量查询用户姓名（渠道 + 招商）
        Map<UUID, String> userNameMap = loadUserNameMap(perfMap.values());

        // 组装 VO
        Page<OrderDetailVO> voPage = new Page<>(orderPage.getCurrent(), orderPage.getSize(), orderPage.getTotal());
        voPage.setRecords(orders.stream().map(order -> {
            PerformanceRecord perf = perfMap.get(order.getOrderId());
            return toOrderDetailVO(order, perf, activityNameMap, userNameMap);
        }).toList());
        return okPage(voPage);
    }

    /**
     * 批量加载业绩记录，构建 orderId → PerformanceRecord 映射。
     */
    private Map<String, PerformanceRecord> loadPerformanceMap(List<String> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return Map.of();
        }
        List<PerformanceRecord> records = performanceRecordMapper.findByOrderIds(orderIds);
        Map<String, PerformanceRecord> map = new LinkedHashMap<>();
        for (PerformanceRecord r : records) {
            if (r.getOrderId() != null) {
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
        List<ColonelsettlementActivity> activities = activityMapper.selectNamesByActivityIds(activityIds);
        Map<String, String> map = new LinkedHashMap<>();
        for (ColonelsettlementActivity a : activities) {
            if (a.getActivityId() != null && a.getName() != null) {
                map.put(a.getActivityId(), a.getName());
            }
        }
        return map;
    }

    /**
     * 批量加载用户姓名映射 userId → realName。
     */
    private Map<UUID, String> loadUserNameMap(Collection<PerformanceRecord> records) {
        Set<UUID> userIds = new HashSet<>();
        for (PerformanceRecord r : records) {
            if (r.getFinalChannelUserId() != null) userIds.add(r.getFinalChannelUserId());
            if (r.getFinalRecruiterUserId() != null) userIds.add(r.getFinalRecruiterUserId());
        }
        if (userIds.isEmpty()) {
            return Map.of();
        }
        LambdaQueryWrapper<SysUser> uw = new LambdaQueryWrapper<>();
        uw.in(SysUser::getId, userIds);
        List<SysUser> users = sysUserMapper.selectList(uw);
        Map<UUID, String> map = new LinkedHashMap<>();
        for (SysUser u : users) {
            if (u.getId() != null) {
                map.put(u.getId(), StringUtils.hasText(u.getRealName()) ? u.getRealName() : u.getUsername());
            }
        }
        return map;
    }

    /**
     * 将订单实体 + 业绩记录合并为 OrderDetailVO。
     */
    private OrderDetailVO toOrderDetailVO(
            ColonelsettlementOrder order,
            PerformanceRecord perf,
            Map<String, String> activityNameMap,
            Map<UUID, String> userNameMap) {
        OrderDetailVO vo = new OrderDetailVO();

        // 订单基本信息
        vo.setOrderId(StringUtils.hasText(order.getOrderId()) ? order.getOrderId() : String.valueOf(order.getId()));
        vo.setOrderStatus(order.getOrderStatus());
        vo.setOrderStatusText(fromOrderStatusCode(order.getOrderStatus()));

        // 活动
        String activityId = order.getActivityId();
        vo.setActivityId(activityId);
        vo.setActivityName(StringUtils.hasText(activityId) ? activityNameMap.getOrDefault(activityId, null) : null);

        // 商品
        vo.setProductId(order.getProductId());
        vo.setProductName(StringUtils.hasText(order.getProductTitle()) ? order.getProductTitle() : order.getProductName());
        vo.setProductImage(StringUtils.hasText(order.getProductImage()) ? order.getProductImage() : order.getProductPic());

        // 合作方
        vo.setPartnerId(order.getShopId() != null ? String.valueOf(order.getShopId()) : null);
        vo.setPartnerName(order.getShopName());

        // 推广者
        vo.setTalentId(order.getTalentId() != null ? order.getTalentId().toString() : null);
        vo.setTalentName(StringUtils.hasText(order.getTalentName()) ? order.getTalentName()
                : pickText(order.getExtraData(), "talentName", "talent_nickname", "author_name"));

        // 渠道/招商：优先从业绩记录获取最终归属
        if (perf != null) {
            vo.setChannelId(perf.getFinalChannelUserId() != null ? perf.getFinalChannelUserId().toString() : null);
            vo.setChannelName(perf.getFinalChannelUserId() != null ? userNameMap.getOrDefault(perf.getFinalChannelUserId(), null) : null);
            vo.setRecruiterId(perf.getFinalRecruiterUserId() != null ? perf.getFinalRecruiterUserId().toString() : null);
            vo.setRecruiterName(perf.getFinalRecruiterUserId() != null ? userNameMap.getOrDefault(perf.getFinalRecruiterUserId(), null) : null);
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

        // 提成字段从业绩记录获取
        if (perf != null) {
            vo.setEstimateRecruiterCommission(centToYuan(perf.getEstimateRecruiterCommission()));
            vo.setEffectiveRecruiterCommission(safeCentToYuan(perf.getEffectiveRecruiterCommission()));
            vo.setEstimateChannelCommission(centToYuan(perf.getEstimateChannelCommission()));
            vo.setEffectiveChannelCommission(safeCentToYuan(perf.getEffectiveChannelCommission()));
            vo.setEstimateServiceProfit(centToYuan(perf.getEstimateServiceProfit()));
            vo.setEffectiveServiceProfit(safeCentToYuan(perf.getEffectiveServiceProfit()));
        } else {
            // 无业绩记录：服务费收益 = 服务费收入 - 技术服务费
            vo.setEstimateServiceProfit(safeSubtract(order.getEstimateServiceFee(), order.getEstimateTechServiceFee()));
            vo.setEffectiveServiceProfit(safeSubtract(order.getEffectiveServiceFee(), order.getEffectiveTechServiceFee()));
            vo.setEstimateRecruiterCommission(null);
            vo.setEffectiveRecruiterCommission(null);
            vo.setEstimateChannelCommission(null);
            vo.setEffectiveChannelCommission(null);
        }

        // 服务费支出 = 招商提成 + 渠道提成
        vo.setEstimateServiceFeeExpense(safeAdd(vo.getEstimateRecruiterCommission(), vo.getEstimateChannelCommission()));
        vo.setEffectiveServiceFeeExpense(safeAdd(vo.getEffectiveRecruiterCommission(), vo.getEffectiveChannelCommission()));

        // 时间
        vo.setPayTime(order.getPayTime() != null ? order.getPayTime() : order.getOrderCreateTime());
        vo.setSettleTime(order.getSettleTime());
        vo.setOrderCreateTime(order.getOrderCreateTime());

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

        OrderSummaryVO vo = new OrderSummaryVO();
        vo.setTotal(toOrderSummaryRow(firstRow(totalRows), totalCommission, null));
        vo.setRecords(dailyRows.stream()
                .map(row -> {
                    String date = asString(row, "stat_date");
                    return toOrderSummaryRow(row, dailyCommission.get(date), date);
                })
                .toList());
        return ok(vo);
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
            metrics.setServiceFee(centToYuan(aggregate.serviceProfitCent()));
            metrics.setBizCommission(centToYuan(aggregate.recruiterCommissionCent()));
            metrics.setChannelCommission(centToYuan(aggregate.channelCommissionCent()));
            metrics.setCommission(centToYuan(aggregate.recruiterCommissionCent() + aggregate.channelCommissionCent()));
            metrics.setGrossProfit(centToYuan(aggregate.grossProfitCent()));
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
                        "COALESCE(SUM(settle_second_colonel_commission), 0) AS talent_commission"
                )
                .ge(timeColumn, todayStart)
                .lt(timeColumn, tomorrowStart)
                .groupBy("colonel_activity_id");
        CommissionService.CommissionSummary commissionSummary = commissionService.calculateByActivityBuckets(
                orderMapper.selectMaps(commissionWrapper).stream()
                        .map(row -> new CommissionService.ActivityCommissionBucket(
                                asString(row, "activity_id"),
                                null,
                                null,
                                asLong(row, "service_fee_income"),
                                asLong(row, "tech_service_fee"),
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
        Map<LocalDate, Map<String, Object>> trendMap = orderMapper.selectMaps(trendWrapper).stream()
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
        metrics.setTechServiceFee(centToYuan(commissionSummary.techServiceFee()));
        metrics.setTalentCommission(centToYuan(commissionSummary.talentCommission()));
        metrics.setServiceFee(centToYuan(commissionSummary.serviceFeeNet()));
        metrics.setBizCommission(centToYuan(commissionSummary.bizCommission()));
        metrics.setChannelCommission(centToYuan(commissionSummary.channelCommission()));
        metrics.setCommission(centToYuan(commissionSummary.bizCommission() + commissionSummary.channelCommission()));
        metrics.setGrossProfit(centToYuan(commissionSummary.grossProfit()));
        return metrics;
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
            IPage<ColonelsettlementOrder> pageResult = orderMapper.findPageWithScope(new Page<>(current, EXPORT_BATCH_SIZE), wrapper);
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
            @Parameter(description = "招商类型") @RequestParam(required = false) String recruitType,
            @Parameter(description = "开始日期") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "结束日期") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @Parameter(description = "时间字段") @RequestParam(required = false) String timeField,
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
                true, timeField, start, end, orderId, status, talentId, merchantId,
                productId, productName, shopName, talentName, colonelName, channelName,
                colonelActivityId, recruitType, userId, deptId, dataScope);

        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"order-detail.csv\"");
        PrintWriter writer = response.getWriter();
        writer.write('\ufeff');
        writer.println("订单号,活动名称,商品名称,商品ID,合作方,达人,渠道,招商,订单状态,订单额,预估服务费,结算服务费,预估技术服务费,结算技术服务费,服务费支出,服务费收益,招商提成,渠道提成,付款时间,结算时间");

        long current = 1L;
        while (true) {
            IPage<ColonelsettlementOrder> pageResult = orderMapper.findPageWithScope(new Page<>(current, EXPORT_BATCH_SIZE), wrapper);
            List<ColonelsettlementOrder> orders = pageResult.getRecords();
            if (orders == null || orders.isEmpty()) {
                break;
            }

            List<String> orderIds = orders.stream()
                    .map(ColonelsettlementOrder::getOrderId)
                    .filter(StringUtils::hasText)
                    .toList();
            Map<String, PerformanceRecord> perfMap = loadPerformanceMap(orderIds);
            Map<String, String> activityNameMap = loadActivityNameMap(orders);
            Map<UUID, String> userNameMap = loadUserNameMap(perfMap.values());

            for (ColonelsettlementOrder order : orders) {
                PerformanceRecord perf = perfMap.get(order.getOrderId());
                OrderDetailVO vo = toOrderDetailVO(order, perf, activityNameMap, userNameMap);
                writer.printf("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s%n",
                        csvEscape(vo.getOrderId()),
                        csvEscape(vo.getActivityName()),
                        csvEscape(vo.getProductName()),
                        csvEscape(vo.getProductId()),
                        csvEscape(vo.getPartnerName()),
                        csvEscape(vo.getTalentName()),
                        csvEscape(vo.getChannelName()),
                        csvEscape(vo.getRecruiterName()),
                        csvEscape(vo.getOrderStatusText()),
                        csvEscape(vo.getPayAmount()),
                        csvEscape(vo.getEstimateServiceFee()),
                        csvEscape(vo.getEffectiveServiceFee()),
                        csvEscape(vo.getEstimateTechServiceFee()),
                        csvEscape(vo.getEffectiveTechServiceFee()),
                        csvEscape(vo.getEstimateServiceFeeExpense()),
                        csvEscape(vo.getEstimateServiceProfit()),
                        csvEscape(vo.getEstimateRecruiterCommission()),
                        csvEscape(vo.getEstimateChannelCommission()),
                        csvEscape(vo.getPayTime()),
                        csvEscape(vo.getSettleTime()));
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
        IPage<ExclusiveTalent> result = exclusiveTalentMapper.selectPage(new Page<>(page, size), wrapper);
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
        IPage<ExclusiveMerchant> result = exclusiveMerchantMapper.selectPage(new Page<>(page, size), wrapper);
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
            List<ColonelsettlementActivity> rows = activityMapper.selectExportPage(
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
        List<Map<String, Object>> rows = orderMapper.selectMaps(wrapper);
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
        selects.add("COUNT(DISTINCT talent_id) AS talent_promoter_count");
        selects.add("COUNT(DISTINCT COALESCE(colonel_buyin_id, second_colonel_buyin_id)) AS colonel_promoter_count");
        selects.add("COUNT(DISTINCT product_id) AS product_count");
        selects.add("COALESCE(SUM(" + columns.amountColumn() + "), 0) AS order_amount_cent");
        selects.add("COALESCE(SUM(actual_amount), 0) AS actual_amount_cent");
        selects.add("COALESCE(SUM(" + columns.serviceFeeColumn() + "), 0) AS service_fee_income_cent");
        selects.add("COALESCE(SUM(" + columns.techFeeColumn() + "), 0) AS tech_service_fee_cent");
        selects.add("COALESCE(SUM(settle_second_colonel_commission), 0) AS talent_commission_cent");
        wrapper.select(selects.toArray(String[]::new));
        if (daily) {
            wrapper.groupBy(dayExpr).orderByDesc(dayExpr);
        }
        List<Map<String, Object>> rows = orderMapper.selectMaps(wrapper);
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
                .map(this::toActivityCommissionBucket)
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
                    .add(toActivityCommissionBucket(row));
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
        selects.add("COALESCE(SUM(settle_second_colonel_commission), 0) AS talent_commission");
        wrapper.select(selects.toArray(String[]::new));
        if (daily) {
            wrapper.groupBy(dayExpr, "colonel_activity_id", "product_id", recruiterExpr)
                    .orderByDesc(dayExpr);
        } else {
            wrapper.groupBy("colonel_activity_id", "product_id", recruiterExpr);
        }
        List<Map<String, Object>> rows = orderMapper.selectMaps(wrapper);
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

    private void applyOrderDataScope(
            QueryWrapper<ColonelsettlementOrder> wrapper,
            boolean aliased,
            UUID userId,
            UUID deptId,
            DataScope dataScope) {
        if (wrapper == null || dataScope == null) {
            return;
        }
        switch (dataScope) {
            case PERSONAL -> wrapper.eq(column(aliased, "user_id"), requireScopeUser(userId));
            case DEPT -> wrapper.eq(column(aliased, "dept_id"), requireScopeDept(deptId));
            case ALL -> {
                // no filter
            }
        }
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
                    "effective_tech_service_fee");
        }
        return new OrderTrackColumns(
                timeColumn,
                "order_amount",
                "estimate_service_fee",
                "estimate_tech_service_fee");
    }

    private OrderSummaryRowVO toOrderSummaryRow(
            Map<String, Object> row,
            CommissionService.CommissionSummary commissionSummary,
            String date) {
        CommissionService.CommissionSummary summary = commissionSummary == null
                ? zeroCommissionSummary()
                : commissionSummary;
        long orderAmount = asLong(row, "order_amount_cent");
        long actualAmount = asLong(row, "actual_amount_cent");
        long serviceFeeIncome = asLong(row, "service_fee_income_cent");
        OrderSummaryRowVO vo = new OrderSummaryRowVO();
        vo.setDate(date);
        vo.setTalentPromoterCount(asLong(row, "talent_promoter_count"));
        vo.setColonelPromoterCount(asLong(row, "colonel_promoter_count"));
        vo.setProductCount(asLong(row, "product_count"));
        vo.setOrderCount(asLong(row, "order_count"));
        vo.setOrderAmount(centToYuan(orderAmount));
        vo.setProductAverageServiceFeeRate(percent(serviceFeeIncome, actualAmount));
        vo.setOrderAverageServiceFeeRate(percent(serviceFeeIncome, orderAmount));
        vo.setServiceFeeIncome(centToYuan(serviceFeeIncome));
        vo.setTechServiceFee(centToYuan(asLong(row, "tech_service_fee_cent")));
        vo.setServiceFeeExpense(centToYuan(summary.bizCommission() + summary.channelCommission()));
        vo.setServiceFeeProfit(centToYuan(summary.serviceFeeNet()));
        vo.setGrossProfit(centToYuan(summary.grossProfit()));
        return vo;
    }

    private CommissionService.ActivityCommissionBucket toActivityCommissionBucket(Map<String, Object> row) {
        String productId = asString(row, "product_id");
        return new CommissionService.ActivityCommissionBucket(
                asString(row, "activity_id"),
                StringUtils.hasText(productId) ? productId : null,
                asUuid(row, "recruiter_user_id"),
                asLong(row, "service_fee_income"),
                asLong(row, "tech_service_fee"),
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
        if (wrapper == null || dataScope == null) {
            return;
        }
        switch (dataScope) {
            case PERSONAL -> wrapper.eq("co.user_id", requireScopeUser(userId));
            case DEPT -> wrapper.eq("co.dept_id", requireScopeDept(deptId));
            case ALL -> {
                // no filter
            }
        }
    }

    private void applyScopedQueryDataScope(
            QueryWrapper<ColonelsettlementOrder> wrapper,
            UUID userId,
            UUID deptId,
            DataScope dataScope) {
        if (wrapper == null || dataScope == null) {
            return;
        }
        switch (dataScope) {
            case PERSONAL -> wrapper.eq("user_id", requireScopeUser(userId));
            case DEPT -> wrapper.eq("dept_id", requireScopeDept(deptId));
            case ALL -> {
                // no filter
            }
        }
    }

    private void applyTalentDataScope(
            LambdaQueryWrapper<ExclusiveTalent> wrapper,
            UUID userId,
            UUID deptId,
            DataScope dataScope) {
        if (wrapper == null || dataScope == null) {
            return;
        }
        switch (dataScope) {
            case PERSONAL -> wrapper.eq(ExclusiveTalent::getUserId, requireScopeUser(userId));
            case DEPT -> wrapper.eq(ExclusiveTalent::getDeptId, requireScopeDept(deptId));
            case ALL -> {
                // no filter
            }
        }
    }

    private void applyMerchantDataScope(
            LambdaQueryWrapper<ExclusiveMerchant> wrapper,
            UUID userId,
            UUID deptId,
            DataScope dataScope) {
        if (wrapper == null || dataScope == null) {
            return;
        }
        switch (dataScope) {
            case PERSONAL -> wrapper.eq(ExclusiveMerchant::getUserId, requireScopeUser(userId));
            case DEPT -> wrapper.eq(ExclusiveMerchant::getDeptId, requireScopeDept(deptId));
            case ALL -> {
                // no filter
            }
        }
    }

    private UUID requireScopeUser(UUID userId) {
        if (userId == null) {
            throw BusinessException.forbidden("数据权限异常：缺少用户上下文");
        }
        return userId;
    }

    private UUID requireScopeDept(UUID deptId) {
        if (deptId == null) {
            throw BusinessException.forbidden("数据权限异常：缺少部门上下文");
        }
        return deptId;
    }

    private record OrderTrackColumns(
            String timeColumn,
            String amountColumn,
            String serviceFeeColumn,
            String techFeeColumn) {
    }

}
