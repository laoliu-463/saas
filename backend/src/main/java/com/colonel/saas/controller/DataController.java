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
import java.util.LinkedHashMap;
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

        IPage<ColonelsettlementOrder> orderPage = orderMapper.findPageWithScope(new Page<>(page, size), wrapper);
        Page<OrderVO> voPage = new Page<>(orderPage.getCurrent(), orderPage.getSize(), orderPage.getTotal());
        voPage.setRecords(orderPage.getRecords().stream().map(this::toOrderVO).toList());
        return okPage(voPage);
    }

    ApiResult<PageResult<OrderVO>> getOrderPage(
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
        LocalDateTime start = startDate == null
                ? LocalDate.now().minusDays(30).atStartOfDay()
                : startDate.atStartOfDay();
        LocalDateTime end = endDate == null
                ? LocalDate.now().plusDays(1).atStartOfDay()
                : endDate.plusDays(1).atStartOfDay();

        OrderTrackColumns columns = resolveOrderTrackColumns(timeField);
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

    @RequireRoles({RoleCodes.ADMIN, RoleCodes.BIZ_LEADER, RoleCodes.CHANNEL_LEADER})
    public void exportOrders(
            String orderId,
            String status,
            UUID talentId,
            String merchantId,
            LocalDate startDate,
            LocalDate endDate,
            String timeField,
            UUID userId,
            UUID deptId,
            DataScope dataScope,
            HttpServletResponse response) throws IOException {
        exportOrders(
                orderId,
                status,
                talentId,
                merchantId,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                startDate,
                endDate,
                timeField,
                userId,
                deptId,
                dataScope,
                response);
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

    public static class OrderSummaryVO {
        private OrderSummaryRowVO total;
        private List<OrderSummaryRowVO> records;

        public OrderSummaryRowVO getTotal() {
            return total;
        }

        public void setTotal(OrderSummaryRowVO total) {
            this.total = total;
        }

        public List<OrderSummaryRowVO> getRecords() {
            return records;
        }

        public void setRecords(List<OrderSummaryRowVO> records) {
            this.records = records;
        }
    }

    public static class OrderSummaryRowVO {
        private String date;
        private Long talentPromoterCount;
        private Long colonelPromoterCount;
        private Long productCount;
        private Long orderCount;
        private BigDecimal orderAmount;
        private BigDecimal productAverageServiceFeeRate;
        private BigDecimal orderAverageServiceFeeRate;
        private BigDecimal serviceFeeIncome;
        private BigDecimal techServiceFee;
        private BigDecimal serviceFeeExpense;
        private BigDecimal serviceFeeProfit;
        private BigDecimal grossProfit;

        public String getDate() {
            return date;
        }

        public void setDate(String date) {
            this.date = date;
        }

        public Long getTalentPromoterCount() {
            return talentPromoterCount;
        }

        public void setTalentPromoterCount(Long talentPromoterCount) {
            this.talentPromoterCount = talentPromoterCount;
        }

        public Long getColonelPromoterCount() {
            return colonelPromoterCount;
        }

        public void setColonelPromoterCount(Long colonelPromoterCount) {
            this.colonelPromoterCount = colonelPromoterCount;
        }

        public Long getProductCount() {
            return productCount;
        }

        public void setProductCount(Long productCount) {
            this.productCount = productCount;
        }

        public Long getOrderCount() {
            return orderCount;
        }

        public void setOrderCount(Long orderCount) {
            this.orderCount = orderCount;
        }

        public BigDecimal getOrderAmount() {
            return orderAmount;
        }

        public void setOrderAmount(BigDecimal orderAmount) {
            this.orderAmount = orderAmount;
        }

        public BigDecimal getProductAverageServiceFeeRate() {
            return productAverageServiceFeeRate;
        }

        public void setProductAverageServiceFeeRate(BigDecimal productAverageServiceFeeRate) {
            this.productAverageServiceFeeRate = productAverageServiceFeeRate;
        }

        public BigDecimal getOrderAverageServiceFeeRate() {
            return orderAverageServiceFeeRate;
        }

        public void setOrderAverageServiceFeeRate(BigDecimal orderAverageServiceFeeRate) {
            this.orderAverageServiceFeeRate = orderAverageServiceFeeRate;
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

        public BigDecimal getServiceFeeExpense() {
            return serviceFeeExpense;
        }

        public void setServiceFeeExpense(BigDecimal serviceFeeExpense) {
            this.serviceFeeExpense = serviceFeeExpense;
        }

        public BigDecimal getServiceFeeProfit() {
            return serviceFeeProfit;
        }

        public void setServiceFeeProfit(BigDecimal serviceFeeProfit) {
            this.serviceFeeProfit = serviceFeeProfit;
        }

        public BigDecimal getGrossProfit() {
            return grossProfit;
        }

        public void setGrossProfit(BigDecimal grossProfit) {
            this.grossProfit = grossProfit;
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
        private String track;

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

        public String getTrack() {
            return track;
        }

        public void setTrack(String track) {
            this.track = track;
        }
    }

    public static class DualTrackMetricsVO {
        private MetricsVO settle;
        private MetricsVO estimate;

        public MetricsVO getSettle() {
            return settle;
        }

        public void setSettle(MetricsVO settle) {
            this.settle = settle;
        }

        public MetricsVO getEstimate() {
            return estimate;
        }

        public void setEstimate(MetricsVO estimate) {
            this.estimate = estimate;
        }
    }

}
