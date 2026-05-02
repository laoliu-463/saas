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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

@Validated
@Tag(name = "数据平台", description = "数据页专用接口，包括订单数据页、核心指标、手机号解密、导出与运营监控。")
@RestController
@RequestMapping
@RequireRoles({RoleCodes.BIZ_LEADER, RoleCodes.BIZ_STAFF, RoleCodes.CHANNEL_LEADER, RoleCodes.CHANNEL_STAFF})
public class DataController extends BaseController {

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
        wrapper.ge("co.create_time", start)
                .lt("co.create_time", end);
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

        LambdaQueryWrapper<ColonelsettlementOrder> todayWrapper = new LambdaQueryWrapper<ColonelsettlementOrder>()
                .between(ColonelsettlementOrder::getCreateTime, todayStart, tomorrowStart);
        applyDataScope(todayWrapper, userId, deptId, dataScope);
        List<ColonelsettlementOrder> todayRows = orderMapper.selectList(todayWrapper);
        Long todayOrders = (long) todayRows.size();
        Long todayGmvCent = sumField(todayRows, ColonelsettlementOrder::getOrderAmount);

        LambdaQueryWrapper<ColonelsettlementOrder> pendingWrapper = new LambdaQueryWrapper<ColonelsettlementOrder>()
                .eq(ColonelsettlementOrder::getOrderStatus, toOrderStatusCode("ORDERED"))
                .between(ColonelsettlementOrder::getCreateTime, rollingStart, tomorrowStart);
        applyDataScope(pendingWrapper, userId, deptId, dataScope);
        Long pendingShipCount = safeCount(pendingWrapper);
        CommissionService.CommissionSummary commissionSummary = commissionService.calculate(todayRows);

        LocalDateTime weekStart = today.minusDays(6).atStartOfDay();
        LambdaQueryWrapper<ColonelsettlementOrder> weekWrapper = new LambdaQueryWrapper<ColonelsettlementOrder>()
                .between(ColonelsettlementOrder::getCreateTime, weekStart, tomorrowStart);
        applyDataScope(weekWrapper, userId, deptId, dataScope);
        List<ColonelsettlementOrder> weekRows = orderMapper.selectList(weekWrapper);
        Map<LocalDate, List<ColonelsettlementOrder>> weekBuckets = new HashMap<>();
        for (ColonelsettlementOrder row : weekRows) {
            if (row.getCreateTime() == null) {
                continue;
            }
            LocalDate day = row.getCreateTime().toLocalDate();
            weekBuckets.computeIfAbsent(day, key -> new ArrayList<>()).add(row);
        }
        List<TrendPointVO> trend7d = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            List<ColonelsettlementOrder> dayRows = weekBuckets.getOrDefault(day, List.of());
            Long dayOrders = (long) dayRows.size();
            Long dayGmvCent = sumField(dayRows, ColonelsettlementOrder::getOrderAmount);
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
            @RequestBody DecryptOrderRequest request) {
        if (request == null || request.getOrderIds() == null) {
            throw new BusinessException("orderIds cannot be empty");
        }
        return ok(orderDecryptService.decryptPhones(request.getOrderIds()));
    }

    @Operation(summary = "导出订单CSV", description = "按筛选条件导出订单数据页 CSV。")
    @GetMapping("/orders/exports")
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
        wrapper.ge("co.create_time", start)
                .lt("co.create_time", end);
        if (StringUtils.hasText(status)) {
            wrapper.eq("co.order_status", toOrderStatusCode(status));
        }

        List<ColonelsettlementOrder> orders = orderMapper.findPageWithScope(new Page<>(1, 10000), wrapper).getRecords();

        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"orders.csv\"");
        PrintWriter writer = response.getWriter();
        writer.write('\ufeff');
        writer.println("订单号,商品名称,达人名称,金额,归因来源,状态,创建时间");

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
        writer.flush();
    }

    @Operation(summary = "独家状态监控 - 达人", description = "查询达人独家状态监控数据，当前为测试演示数据接口。")
    @GetMapping("/operations/exclusive-talents")
    public ApiResult<List<Map<String, Object>>> getExclusiveTalentStatus() {
        List<Map<String, Object>> mockTalents = new ArrayList<>();
        Map<String, Object> t1 = new HashMap<>();
        t1.put("talentName", "达人A-独家合作演示");
        t1.put("channelName", "渠道负责人-华东组");
        t1.put("feeRatio", "10%");
        t1.put("sampleCount", 5);
        t1.put("status", "ACTIVE");
        mockTalents.add(t1);
        return ok(mockTalents);
    }

    @Operation(summary = "独家状态监控 - 商家", description = "查询商家独家状态监控数据，当前为测试演示数据接口。")
    @GetMapping("/operations/exclusive-merchants")
    public ApiResult<List<Map<String, Object>>> getExclusiveMerchantStatus() {
        List<Map<String, Object>> mockMerchants = new ArrayList<>();
        Map<String, Object> m1 = new HashMap<>();
        m1.put("merchantName", "商家A-独家合作演示");
        m1.put("zsName", "招商负责人-美妆组");
        m1.put("feeRatio", "20%");
        m1.put("status", "ACTIVE");
        mockMerchants.add(m1);
        return ok(mockMerchants);
    }

    @Operation(summary = "导出活动列表CSV", description = "导出活动列表 CSV，当前为测试演示数据导出接口。")
    @GetMapping("/activities/exports")
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
        writer.println("活动ID,活动名称,活动类型,状态,创建时间");
        writer.println("1,主链路演示活动,团长活动,ACTIVE,2026-04-01 12:00:00");
        writer.flush();
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

    private Long sumField(
            List<ColonelsettlementOrder> rows,
            Function<ColonelsettlementOrder, Long> getter) {
        long sum = 0L;
        for (ColonelsettlementOrder row : rows) {
            Long value = getter.apply(row);
            sum += value == null ? 0L : value;
        }
        return sum;
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
