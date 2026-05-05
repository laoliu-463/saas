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
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.mapper.ColonelsettlementOrderMapper;
import com.colonel.saas.service.CommissionService;
import com.colonel.saas.service.OrderDecryptService;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Validated
@Tag(name = "数据平台", description = "数据页专用接口，包括订单数据页、核心指标、手机号解密、导出与运营监控。")
@RestController
@RequestMapping
@RequireRoles({RoleCodes.BIZ_LEADER, RoleCodes.BIZ_STAFF, RoleCodes.CHANNEL_LEADER, RoleCodes.CHANNEL_STAFF})
public class DataController extends BaseController {

    private static final long EXPORT_BATCH_SIZE = 2000L;

    private final ColonelsettlementOrderMapper orderMapper;
    private final OrderDecryptService orderDecryptService;
    private final CommissionService commissionService;

    public DataController(
            ColonelsettlementOrderMapper orderMapper,
            OrderDecryptService orderDecryptService,
            CommissionService commissionService) {
        this.orderMapper = orderMapper;
        this.orderDecryptService = orderDecryptService;
        this.commissionService = commissionService;
    }

    @Operation(summary = "订单分页", description = "分页查询数据页订单列表。该接口服务于数据分析页面，不等同于订单主链路接口。")
    @GetMapping("/data/orders")
    public ApiResult<PageResult<OrderVO>> getOrderPage(
            @Parameter(description = "页码，从 1 开始。") @RequestParam(defaultValue = "1") @Min(1) long page,
            @Parameter(description = "每页条数。") @RequestParam(defaultValue = "10") @Min(1) @Max(200) long size,
            @Parameter(description = "订单号，支持模糊匹配。") @RequestParam(required = false) String orderId,
            @Parameter(description = "订单状态，支持 ORDERED、SHIPPED、FINISHED、CANCELLED。") @RequestParam(required = false) String status,
            @Parameter(description = "开始日期，格式 yyyy-MM-dd。") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "结束日期，格式 yyyy-MM-dd。") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope) {
        LocalDateTime start = startDate == null
                ? LocalDate.now().minusDays(30).atStartOfDay()
                : startDate.atStartOfDay();
        LocalDateTime end = endDate == null
                ? LocalDate.now().plusDays(1).atStartOfDay()
                : endDate.plusDays(1).atStartOfDay();

        QueryWrapper<ColonelsettlementOrder> wrapper = new QueryWrapper<>();
        wrapper.ge("co.settle_time", start)
                .lt("co.settle_time", end);
        applyPageDataScope(wrapper, userId, deptId, dataScope);
        if (StringUtils.hasText(orderId)) {
            wrapper.like("co.order_id", orderId.trim());
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq("co.order_status", toOrderStatusCode(status));
        }

        IPage<ColonelsettlementOrder> orderPage = orderMapper.findPageWithScope(new Page<>(page, size), wrapper);
        Page<OrderVO> voPage = new Page<>(orderPage.getCurrent(), orderPage.getSize(), orderPage.getTotal());
        voPage.setRecords(orderPage.getRecords().stream().map(this::toOrderVO).toList());
        return okPage(voPage);
    }

    @Operation(summary = "核心指标", description = "查询数据页首页核心指标与近 7 天趋势。该接口面向数据看板展示，不承担订单归因主逻辑。")
    @GetMapping("/dashboard/metrics")
    public ApiResult<MetricsVO> getMetrics(
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope) {
        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime tomorrowStart = today.plusDays(1).atStartOfDay();
        LocalDateTime rollingStart = today.minusDays(29).atStartOfDay();

        QueryWrapper<ColonelsettlementOrder> todayAggregateWrapper = buildScopedQuery(userId, deptId, dataScope)
                .select(
                        "COUNT(*) AS order_count",
                        "COALESCE(SUM(order_amount), 0) AS order_amount_cent"
                )
                .ge("settle_time", todayStart)
                .lt("settle_time", tomorrowStart);
        Map<String, Object> todayAggregate = getSingleAggregate(todayAggregateWrapper);
        Long todayOrders = asLong(todayAggregate, "order_count");
        Long todayGmvCent = asLong(todayAggregate, "order_amount_cent");

        LambdaQueryWrapper<ColonelsettlementOrder> pendingWrapper = new LambdaQueryWrapper<ColonelsettlementOrder>()
                .eq(ColonelsettlementOrder::getOrderStatus, toOrderStatusCode("ORDERED"))
                .between(ColonelsettlementOrder::getSettleTime, rollingStart, tomorrowStart);
        applyDataScope(pendingWrapper, userId, deptId, dataScope);
        Long pendingShipCount = safeCount(pendingWrapper);
        QueryWrapper<ColonelsettlementOrder> commissionWrapper = buildScopedQuery(userId, deptId, dataScope)
                .select(
                        "COALESCE(colonel_activity_id, '') AS activity_id",
                        "COALESCE(SUM(settle_colonel_commission), 0) AS service_fee_income",
                        "COALESCE(SUM(settle_colonel_tech_service_fee), 0) AS tech_service_fee",
                        "COALESCE(SUM(settle_second_colonel_commission), 0) AS talent_commission"
                )
                .ge("settle_time", todayStart)
                .lt("settle_time", tomorrowStart)
                .groupBy("colonel_activity_id");
        CommissionService.CommissionSummary commissionSummary = commissionService.calculateByActivityBuckets(
                orderMapper.selectMaps(commissionWrapper).stream()
                        .map(row -> new CommissionService.ActivityCommissionBucket(
                                asString(row, "activity_id"),
                                asLong(row, "service_fee_income"),
                                asLong(row, "tech_service_fee"),
                                asLong(row, "talent_commission")
                        ))
                        .toList()
        );

        LocalDateTime weekStart = today.minusDays(6).atStartOfDay();
        QueryWrapper<ColonelsettlementOrder> trendWrapper = buildScopedQuery(userId, deptId, dataScope)
                .select(
                        "DATE(settle_time) AS settle_date",
                        "COUNT(*) AS order_count",
                        "COALESCE(SUM(order_amount), 0) AS order_amount_cent"
                )
                .ge("settle_time", weekStart)
                .lt("settle_time", tomorrowStart)
                .groupBy("DATE(settle_time)");
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

        MetricsVO metrics = new MetricsVO();
        metrics.setTodayOrderCount(todayOrders);
        metrics.setTodayGmv(centToYuan(todayGmvCent));
        metrics.setPendingShipCount(pendingShipCount);
        metrics.setTrend7d(trend7d);

        // 兼容当前前端 data/index.vue 的历史字段名。
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
        return ok(metrics);
    }

    @Operation(summary = "订单手机号解密（仅展示，不落库）", description = "按订单 ID 列表解密手机号，仅返回展示结果，不写回数据库。")
    @PostMapping("/orders/phone-decryptions")
    public ApiResult<List<OrderDecryptService.DecryptPhoneVO>> decryptOrderPhones(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "手机号解密请求体。",
                    required = true,
                    content = @Content(examples = @ExampleObject(value = "{\"orderIds\":[\"ORDER_001\",\"ORDER_002\"]}"))
            )
            @RequestBody DecryptOrderRequest request,
            @RequestAttribute(value = "userId", required = false) UUID userId,
            @RequestAttribute(value = "username", required = false) String username) {
        if (request == null || request.getOrderIds() == null) {
            throw new BusinessException("orderIds cannot be empty");
        }
        return ok(orderDecryptService.decryptPhones(request.getOrderIds(), userId, username));
    }

    @Operation(summary = "导出订单CSV", description = "按筛选条件导出订单数据页 CSV。")
    @GetMapping("/orders/exports")
    @RequireRoles({RoleCodes.ADMIN, RoleCodes.BIZ_LEADER, RoleCodes.CHANNEL_LEADER})
    public void exportOrders(
            @Parameter(description = "订单状态，支持 ORDERED、SHIPPED、FINISHED、CANCELLED。") @RequestParam(required = false) String status,
            @Parameter(description = "开始日期，格式 yyyy-MM-dd。") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "结束日期，格式 yyyy-MM-dd。") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
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

        QueryWrapper<ColonelsettlementOrder> wrapper = new QueryWrapper<>();
        wrapper.ge("co.settle_time", start)
                .lt("co.settle_time", end);
        applyPageDataScope(wrapper, userId, deptId, dataScope);
        if (StringUtils.hasText(status)) {
            wrapper.eq("co.order_status", toOrderStatusCode(status));
        }

        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"orders.csv\"");
        PrintWriter writer = response.getWriter();
        writer.write('\ufeff');
        writer.println("订单号,商品名称,达人名称,金额,归因来源,状态,创建时间");

        long current = 1L;
        while (true) {
            IPage<ColonelsettlementOrder> pageResult = orderMapper.findPageWithScope(new Page<>(current, EXPORT_BATCH_SIZE), wrapper);
            List<ColonelsettlementOrder> orders = pageResult.getRecords();
            if (orders == null || orders.isEmpty()) {
                break;
            }
            for (ColonelsettlementOrder order : orders) {
                OrderVO vo = toOrderVO(order);
                writer.printf("%s,%s,%s,%s,%s,%s,%s%n",
                        vo.getId(),
                        vo.getProductName(),
                        vo.getTalentName(),
                        vo.getAmount(),
                        vo.getAttributionSource() == null ? "默认归属" : vo.getAttributionSource(),
                        vo.getStatus(),
                        vo.getCreateTime());
            }
            if (current >= pageResult.getPages()) {
                break;
            }
            current++;
        }
        writer.flush();
    }

    @Operation(summary = "独家状态监控 - 达人", description = "查询达人独家状态监控数据。当前本地 Mock 阶段未接入真实独家计算，默认返回空列表。")
    @GetMapping("/operations/exclusive-talents")
    @RequireRoles({RoleCodes.ADMIN, RoleCodes.OPS_STAFF, RoleCodes.BIZ_LEADER, RoleCodes.CHANNEL_LEADER})
    public ApiResult<List<Map<String, Object>>> getExclusiveTalentStatus() {
        return ok(List.of());
    }

    @Operation(summary = "独家状态监控 - 商家", description = "查询商家独家状态监控数据。当前本地 Mock 阶段未接入真实独家计算，默认返回空列表。")
    @GetMapping("/operations/exclusive-merchants")
    @RequireRoles({RoleCodes.ADMIN, RoleCodes.OPS_STAFF, RoleCodes.BIZ_LEADER, RoleCodes.CHANNEL_LEADER})
    public ApiResult<List<Map<String, Object>>> getExclusiveMerchantStatus() {
        return ok(List.of());
    }

    @Operation(summary = "导出活动列表CSV", description = "导出活动列表 CSV。当前阶段未开放活动导出。")
    @GetMapping("/activities/exports")
    @RequireRoles({RoleCodes.ADMIN, RoleCodes.BIZ_LEADER, RoleCodes.CHANNEL_LEADER})
    public void exportActivities(
            @Parameter(description = "活动名称关键字。") @RequestParam(required = false) String activityName,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope,
            HttpServletResponse response) throws IOException {
        throw new BusinessException("活动导出暂未开放");
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

    private Long safeCount(LambdaQueryWrapper<ColonelsettlementOrder> wrapper) {
        Long count = orderMapper.selectCount(wrapper);
        return count == null ? 0L : count;
    }

    private Integer toOrderStatusCode(String status) {
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "ORDERED" -> 1;
            case "SHIPPED" -> 2;
            case "FINISHED" -> 3;
            case "CANCELLED" -> 4;
            default -> throw new BusinessException("非法订单状态: " + status);
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
    }

    public static class DecryptOrderRequest {
        @Schema(description = "订单 ID 列表。", example = "[\"ORDER_001\",\"ORDER_002\"]")
        private List<String> orderIds;

        public List<String> getOrderIds() {
            return orderIds;
        }

        public void setOrderIds(List<String> orderIds) {
            this.orderIds = orderIds;
        }
    }
}
