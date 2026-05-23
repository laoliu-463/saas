package com.colonel.saas.controller;

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
import com.colonel.saas.mapper.ColonelsettlementActivityMapper;
import com.colonel.saas.mapper.ColonelsettlementOrderMapper;
import com.colonel.saas.mapper.ExclusiveMerchantMapper;
import com.colonel.saas.mapper.ExclusiveTalentMapper;
import com.colonel.saas.service.CommissionService;
import com.colonel.saas.service.PerformanceMetricsQueryService;
import com.colonel.saas.service.ShortTtlCacheService;
import com.colonel.saas.vo.ExclusiveMerchantStatusVO;
import com.colonel.saas.vo.ExclusiveTalentStatusVO;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Validated
@Tag(name = "数据平台", description = "数据页专用接口，包括订单数据页、核心指标、导出与运营监控。")
@RestController
@RequestMapping
@RequireRoles({RoleCodes.BIZ_LEADER, RoleCodes.BIZ_STAFF, RoleCodes.CHANNEL_LEADER, RoleCodes.CHANNEL_STAFF})
public class DataController extends BaseController {

    private static final long EXPORT_BATCH_SIZE = 2000L;
    private static final Duration METRICS_CACHE_TTL = Duration.ofSeconds(30);
    private static final String METRICS_CACHE_PREFIX = "dashboard:metrics:";

    private final ColonelsettlementOrderMapper orderMapper;
    private final CommissionService commissionService;
    private final ExclusiveTalentMapper exclusiveTalentMapper;
    private final ExclusiveMerchantMapper exclusiveMerchantMapper;
    private final ColonelsettlementActivityMapper activityMapper;
    private final ShortTtlCacheService shortTtlCacheService;
    private final PerformanceMetricsQueryService performanceMetricsQueryService;

    public DataController(
            ColonelsettlementOrderMapper orderMapper,
            CommissionService commissionService,
            ExclusiveTalentMapper exclusiveTalentMapper,
            ExclusiveMerchantMapper exclusiveMerchantMapper,
            ColonelsettlementActivityMapper activityMapper,
            ShortTtlCacheService shortTtlCacheService,
            PerformanceMetricsQueryService performanceMetricsQueryService) {
        this.orderMapper = orderMapper;
        this.commissionService = commissionService;
        this.exclusiveTalentMapper = exclusiveTalentMapper;
        this.exclusiveMerchantMapper = exclusiveMerchantMapper;
        this.activityMapper = activityMapper;
        this.shortTtlCacheService = shortTtlCacheService;
        this.performanceMetricsQueryService = performanceMetricsQueryService;
    }

    @Operation(summary = "订单分页", description = "分页查询数据页订单列表。该接口服务于数据分析页面，不等同于订单主链路接口。")
    @GetMapping("/data/orders")
    public ApiResult<PageResult<OrderVO>> getOrderPage(
            @Parameter(description = "页码，从 1 开始，最大 1000。") @RequestParam(defaultValue = "1") @Min(1) @Max(1000) long page,
            @Parameter(description = "每页条数。") @RequestParam(defaultValue = "20") @Min(1) @Max(200) long size,
            @Parameter(description = "订单号，支持模糊匹配。") @RequestParam(required = false) String orderId,
            @Parameter(description = "订单状态，支持 ORDERED、SHIPPED、FINISHED、CANCELLED。") @RequestParam(required = false) String status,
            @Parameter(description = "达人 ID（UUID），精确匹配。") @RequestParam(required = false) UUID talentId,
            @Parameter(description = "商家 merchant_id（字符串），精确匹配。") @RequestParam(required = false) String merchantId,
            @Parameter(description = "开始日期，格式 yyyy-MM-dd。") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "结束日期，格式 yyyy-MM-dd。") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @Parameter(description = "时间字段：createTime（默认）或 settleTime。") @RequestParam(required = false) String timeField,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope) {
        LocalDateTime start = startDate == null
                ? LocalDate.now().minusDays(30).atStartOfDay()
                : startDate.atStartOfDay();
        LocalDateTime end = endDate == null
                ? LocalDate.now().plusDays(1).atStartOfDay()
                : endDate.plusDays(1).atStartOfDay();

        String timeColumn = resolveAliasedOrderTimeColumn(timeField);
        QueryWrapper<ColonelsettlementOrder> wrapper = new QueryWrapper<>();
        wrapper.ge(timeColumn, start)
                .lt(timeColumn, end);
        applyPageDataScope(wrapper, userId, deptId, dataScope);
        if (StringUtils.hasText(orderId)) {
            wrapper.like("co.order_id", orderId.trim());
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq("co.order_status", toOrderStatusCode(status));
        }
        if (talentId != null) {
            wrapper.eq("co.talent_id", talentId);
        }
        if (StringUtils.hasText(merchantId)) {
            String normalized = merchantId.trim();
            String digits = normalized.replaceAll("\\D", "");
            String shopIdText = StringUtils.hasText(digits) ? digits : normalized;
            wrapper.apply(
                    "(co.extra_data->>'merchant_id' = {0} OR CAST(co.shop_id AS TEXT) = {1})",
                    normalized,
                    shopIdText
            );
        }

        IPage<ColonelsettlementOrder> orderPage = orderMapper.findPageWithScope(new Page<>(page, size), wrapper);
        Page<OrderVO> voPage = new Page<>(orderPage.getCurrent(), orderPage.getSize(), orderPage.getTotal());
        voPage.setRecords(orderPage.getRecords().stream().map(this::toOrderVO).toList());
        return okPage(voPage);
    }

    @Operation(summary = "核心指标", description = "查询数据页首页核心指标与近 7 天趋势。该接口面向数据看板展示，不承担订单归因主逻辑。")
    @GetMapping("/dashboard/metrics")
    public ApiResult<MetricsVO> getMetrics(
            @Parameter(description = "时间字段：createTime（默认）或 settleTime。") @RequestParam(required = false) String timeField,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope) {
        String cacheKey = METRICS_CACHE_PREFIX + metricsCacheKey(timeField, userId, deptId, dataScope);
        return ok(shortTtlCacheService.get(cacheKey, METRICS_CACHE_TTL, () -> buildMetrics(timeField, userId, deptId, dataScope)));
    }

    private MetricsVO buildMetrics(String timeField, UUID userId, UUID deptId, DataScope dataScope) {
        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime tomorrowStart = today.plusDays(1).atStartOfDay();
        LocalDateTime rollingStart = today.minusDays(29).atStartOfDay();
        String timeColumn = resolveTimeColumn(timeField);

        QueryWrapper<ColonelsettlementOrder> pendingWrapper = buildScopedQuery(userId, deptId, dataScope)
                .select("COUNT(1) AS order_count")
                .eq("order_status", toOrderStatusCode("ORDERED"))
                .ge(timeColumn, rollingStart)
                .lt(timeColumn, tomorrowStart);
        Long pendingShipCount = asLong(getSingleAggregate(pendingWrapper), "order_count");

        MetricsVO metrics = new MetricsVO();
        metrics.setPendingShipCount(pendingShipCount);
        metrics.setAmountTrack(performanceMetricsQueryService.resolveAmountTrackLabel(timeField));

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
            metrics.setTalentCommission(centToYuan(Math.max(
                    aggregate.serviceFeeIncomeCent() - aggregate.techServiceFeeCent() - aggregate.serviceProfitCent(),
                    0L)));
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
                        "COALESCE(SUM(order_amount), 0) AS order_amount_cent"
                )
                .ge(timeColumn, todayStart)
                .lt(timeColumn, tomorrowStart);
        Map<String, Object> todayAggregate = getSingleAggregate(todayAggregateWrapper);
        Long todayOrders = asLong(todayAggregate, "order_count");
        Long todayGmvCent = asLong(todayAggregate, "order_amount_cent");

        QueryWrapper<ColonelsettlementOrder> commissionWrapper = buildScopedQuery(userId, deptId, dataScope)
                .select(
                        "COALESCE(colonel_activity_id, '') AS activity_id",
                        "COALESCE(SUM(settle_colonel_commission), 0) AS service_fee_income",
                        "COALESCE(SUM(settle_colonel_tech_service_fee), 0) AS tech_service_fee",
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
                        "COALESCE(SUM(order_amount), 0) AS order_amount_cent"
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

        String timeColumn = resolveAliasedOrderTimeColumn(timeField);
        QueryWrapper<ColonelsettlementOrder> wrapper = new QueryWrapper<>();
        wrapper.ge(timeColumn, start)
                .lt(timeColumn, end);
        applyPageDataScope(wrapper, userId, deptId, dataScope);
        if (StringUtils.hasText(orderId)) {
            wrapper.like("co.order_id", orderId.trim());
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq("co.order_status", toOrderStatusCode(status));
        }
        if (talentId != null) {
            wrapper.eq("co.talent_id", talentId);
        }
        if (StringUtils.hasText(merchantId)) {
            String normalized = merchantId.trim();
            String digits = normalized.replaceAll("\\D", "");
            String shopIdText = StringUtils.hasText(digits) ? digits : normalized;
            wrapper.apply(
                    "(co.extra_data->>'merchant_id' = {0} OR CAST(co.shop_id AS TEXT) = {1})",
                    normalized,
                    shopIdText
            );
        }

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
            default -> "create_time";
        };
    }

    private String resolveAliasedOrderTimeColumn(String timeField) {
        return "co." + resolveTimeColumn(timeField);
    }

    private OrderVO toOrderVO(ColonelsettlementOrder order) {
        OrderVO vo = new OrderVO();
        vo.setId(StringUtils.hasText(order.getOrderId()) ? order.getOrderId() : String.valueOf(order.getId()));
        vo.setProductName(order.getProductName());
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
            case PERSONAL -> {
                if (userId != null) {
                    wrapper.eq("co.user_id", userId);
                }
            }
            case DEPT -> {
                if (deptId != null) {
                    wrapper.eq("co.dept_id", deptId);
                }
            }
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

    private void applyTalentDataScope(
            LambdaQueryWrapper<ExclusiveTalent> wrapper,
            UUID userId,
            UUID deptId,
            DataScope dataScope) {
        if (wrapper == null || dataScope == null) {
            return;
        }
        switch (dataScope) {
            case PERSONAL -> {
                if (userId != null) {
                    wrapper.eq(ExclusiveTalent::getUserId, userId);
                }
            }
            case DEPT -> {
                if (deptId != null) {
                    wrapper.eq(ExclusiveTalent::getDeptId, deptId);
                }
            }
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
            case PERSONAL -> {
                if (userId != null) {
                    wrapper.eq(ExclusiveMerchant::getUserId, userId);
                }
            }
            case DEPT -> {
                if (deptId != null) {
                    wrapper.eq(ExclusiveMerchant::getDeptId, deptId);
                }
            }
            case ALL -> {
                // no filter
            }
        }
    }

    public static class OrderVO {
        private String id;
        private String productName;
        private String talentName;
        private BigDecimal amount;
        private BigDecimal goodsPrice;
        private BigDecimal commission;
        private BigDecimal freight;
        private String status;
        private String attributionSource;
        private LocalDateTime createTime;
        private LocalDateTime settleTime;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getProductName() {
            return productName;
        }

        public void setProductName(String productName) {
            this.productName = productName;
        }

        public String getTalentName() {
            return talentName;
        }

        public void setTalentName(String talentName) {
            this.talentName = talentName;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public void setAmount(BigDecimal amount) {
            this.amount = amount;
        }

        public BigDecimal getGoodsPrice() {
            return goodsPrice;
        }

        public void setGoodsPrice(BigDecimal goodsPrice) {
            this.goodsPrice = goodsPrice;
        }

        public BigDecimal getCommission() {
            return commission;
        }

        public void setCommission(BigDecimal commission) {
            this.commission = commission;
        }

        public BigDecimal getFreight() {
            return freight;
        }

        public void setFreight(BigDecimal freight) {
            this.freight = freight;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getAttributionSource() {
            return attributionSource;
        }

        public void setAttributionSource(String attributionSource) {
            this.attributionSource = attributionSource;
        }

        public LocalDateTime getCreateTime() {
            return createTime;
        }

        public void setCreateTime(LocalDateTime createTime) {
            this.createTime = createTime;
        }

        public LocalDateTime getSettleTime() {
            return settleTime;
        }

        public void setSettleTime(LocalDateTime settleTime) {
            this.settleTime = settleTime;
        }
    }

    public static class TrendPointVO {
        private String date;
        private Long orderCount;
        private BigDecimal gmv;

        public TrendPointVO(String date, Long orderCount, BigDecimal gmv) {
            this.date = date;
            this.orderCount = orderCount;
            this.gmv = gmv;
        }

        public String getDate() {
            return date;
        }

        public void setDate(String date) {
            this.date = date;
        }

        public Long getOrderCount() {
            return orderCount;
        }

        public void setOrderCount(Long orderCount) {
            this.orderCount = orderCount;
        }

        public BigDecimal getGmv() {
            return gmv;
        }

        public void setGmv(BigDecimal gmv) {
            this.gmv = gmv;
        }
    }

    public static class MetricsVO {
        private Long todayOrderCount;
        private BigDecimal todayGmv;
        private Long pendingShipCount;
        private List<TrendPointVO> trend7d;

        private Long totalOrders;
        private BigDecimal totalAmount;
        private BigDecimal serviceFee;
        private BigDecimal commission;
        private BigDecimal serviceFeeIncome;
        private BigDecimal techServiceFee;
        private BigDecimal talentCommission;
        private BigDecimal bizCommission;
        private BigDecimal channelCommission;
        private BigDecimal grossProfit;
        private String amountTrack;
        private String metricsSource;

        public Long getTodayOrderCount() {
            return todayOrderCount;
        }

        public void setTodayOrderCount(Long todayOrderCount) {
            this.todayOrderCount = todayOrderCount;
        }

        public BigDecimal getTodayGmv() {
            return todayGmv;
        }

        public void setTodayGmv(BigDecimal todayGmv) {
            this.todayGmv = todayGmv;
        }

        public Long getPendingShipCount() {
            return pendingShipCount;
        }

        public void setPendingShipCount(Long pendingShipCount) {
            this.pendingShipCount = pendingShipCount;
        }

        public List<TrendPointVO> getTrend7d() {
            return trend7d;
        }

        public void setTrend7d(List<TrendPointVO> trend7d) {
            this.trend7d = trend7d;
        }

        public Long getTotalOrders() {
            return totalOrders;
        }

        public void setTotalOrders(Long totalOrders) {
            this.totalOrders = totalOrders;
        }

        public BigDecimal getTotalAmount() {
            return totalAmount;
        }

        public void setTotalAmount(BigDecimal totalAmount) {
            this.totalAmount = totalAmount;
        }

        public BigDecimal getServiceFee() {
            return serviceFee;
        }

        public void setServiceFee(BigDecimal serviceFee) {
            this.serviceFee = serviceFee;
        }

        public BigDecimal getCommission() {
            return commission;
        }

        public void setCommission(BigDecimal commission) {
            this.commission = commission;
        }

        public BigDecimal getServiceFeeIncome() {
            return serviceFeeIncome;
        }

        public void setServiceFeeIncome(BigDecimal serviceFeeIncome) {
            this.serviceFeeIncome = serviceFeeIncome;
        }

        public BigDecimal getTechServiceFee() {
            return techServiceFee;
        }

        public void setTechServiceFee(BigDecimal techServiceFee) {
            this.techServiceFee = techServiceFee;
        }

        public BigDecimal getTalentCommission() {
            return talentCommission;
        }

        public void setTalentCommission(BigDecimal talentCommission) {
            this.talentCommission = talentCommission;
        }

        public BigDecimal getBizCommission() {
            return bizCommission;
        }

        public void setBizCommission(BigDecimal bizCommission) {
            this.bizCommission = bizCommission;
        }

        public BigDecimal getChannelCommission() {
            return channelCommission;
        }

        public void setChannelCommission(BigDecimal channelCommission) {
            this.channelCommission = channelCommission;
        }

        public BigDecimal getGrossProfit() {
            return grossProfit;
        }

        public void setGrossProfit(BigDecimal grossProfit) {
            this.grossProfit = grossProfit;
        }

        public String getAmountTrack() {
            return amountTrack;
        }

        public void setAmountTrack(String amountTrack) {
            this.amountTrack = amountTrack;
        }

        public String getMetricsSource() {
            return metricsSource;
        }

        public void setMetricsSource(String metricsSource) {
            this.metricsSource = metricsSource;
        }
    }

}
