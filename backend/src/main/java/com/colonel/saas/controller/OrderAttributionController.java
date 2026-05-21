package com.colonel.saas.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.common.base.BaseController;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.common.result.PageResult;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.mapper.ColonelsettlementOrderMapper;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Tag(name = "订单回流与归因", description = "订单回流摘要与未归因订单排查接口。")
@Validated
@RestController
@RequestMapping
@RequireRoles({RoleCodes.BIZ_LEADER, RoleCodes.BIZ_STAFF, RoleCodes.CHANNEL_LEADER, RoleCodes.CHANNEL_STAFF, RoleCodes.ADMIN})
public class OrderAttributionController extends BaseController {

    private final ColonelsettlementOrderMapper orderMapper;

    public OrderAttributionController(ColonelsettlementOrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    @Operation(summary = "未归因订单分页", description = "分页查询未归因订单，用于订单回流与归因排查页。")
    @GetMapping("/orders/order-attribution-unattributed")
    public ApiResult<PageResult<OrderRowVO>> getUnattributedOrders(
            @Parameter(description = "页码，从 1 开始，最大 1000。") @RequestParam(defaultValue = "1") @Min(1) @Max(1000) long page,
            @Parameter(description = "每页条数，最大 200。") @RequestParam(defaultValue = "10") @Min(1) @Max(200) long size,
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
                .lt("co.settle_time", end)
                .and(q -> q.eq("co.attribution_status", "UNATTRIBUTED").or().isNull("co.attribution_status"));
        applyPageDataScope(wrapper, userId, deptId, dataScope);

        IPage<ColonelsettlementOrder> rows = orderMapper.findPageWithScope(new Page<>(Math.max(page, 1), Math.max(size, 1)), wrapper);
        Page<OrderRowVO> result = new Page<>(rows.getCurrent(), rows.getSize(), rows.getTotal());
        result.setRecords(rows.getRecords().stream().map(this::toRow).toList());
        return okPage(result);
    }

    @Operation(summary = "订单回流摘要", description = "汇总近 30 天订单回流与归因结果，输出订单量、金额、服务费及渠道/团长业绩分布。")
    @GetMapping("/dashboard/order-attribution-summary")
    public ApiResult<SummaryVO> getSummary(
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope) {
        LocalDate today = LocalDate.now();
        LocalDateTime start = today.minusDays(29).atStartOfDay();
        LocalDateTime end = today.plusDays(1).atStartOfDay();

        QueryWrapper<ColonelsettlementOrder> totalWrapper = buildScopedQuery(userId, deptId, dataScope)
                .select(
                        "COUNT(*) AS order_count",
                        "COALESCE(SUM(order_amount), 0) AS order_amount_cent",
                        "COALESCE(SUM(settle_colonel_commission), 0) AS service_fee_cent",
                        "COALESCE(SUM(CASE WHEN attribution_status = 'ATTRIBUTED' THEN 1 ELSE 0 END), 0) AS attributed_order_count",
                        "COALESCE(SUM(CASE WHEN attribution_status = 'ATTRIBUTED' THEN 0 ELSE 1 END), 0) AS unattributed_order_count"
                )
                .ge("settle_time", start)
                .lt("settle_time", end);

        SummaryVO summary = new SummaryVO();
        Map<String, Object> totalRow = getSingleAggregate(totalWrapper);
        summary.setOrderCount(asLong(totalRow, "order_count"));
        summary.setOrderAmount(centToYuan(asLong(totalRow, "order_amount_cent")));
        summary.setServiceFee(centToYuan(asLong(totalRow, "service_fee_cent")));
        summary.setAttributedOrderCount(asLong(totalRow, "attributed_order_count"));
        summary.setUnattributedOrderCount(asLong(totalRow, "unattributed_order_count"));

        QueryWrapper<ColonelsettlementOrder> channelWrapper = buildScopedQuery(userId, deptId, dataScope)
                .select(
                        "channel_user_id AS owner_id",
                        "COUNT(*) AS order_count",
                        "COALESCE(SUM(order_amount), 0) AS order_amount_cent",
                        "COALESCE(SUM(settle_colonel_commission), 0) AS service_fee_cent"
                )
                .eq("attribution_status", "ATTRIBUTED")
                .isNotNull("channel_user_id")
                .ge("settle_time", start)
                .lt("settle_time", end)
                .groupBy("channel_user_id");
        summary.setChannelPerformance(toPerformanceList(orderMapper.selectMaps(channelWrapper)));

        QueryWrapper<ColonelsettlementOrder> colonelWrapper = buildScopedQuery(userId, deptId, dataScope)
                .select(
                        "colonel_user_id AS owner_id",
                        "COUNT(*) AS order_count",
                        "COALESCE(SUM(order_amount), 0) AS order_amount_cent",
                        "COALESCE(SUM(settle_colonel_commission), 0) AS service_fee_cent"
                )
                .eq("attribution_status", "ATTRIBUTED")
                .isNotNull("colonel_user_id")
                .ge("settle_time", start)
                .lt("settle_time", end)
                .groupBy("colonel_user_id");
        summary.setColonelPerformance(toPerformanceList(orderMapper.selectMaps(colonelWrapper)));
        return ok(summary);
    }

    private OrderRowVO toRow(ColonelsettlementOrder order) {
        OrderRowVO row = new OrderRowVO();
        row.setOrderId(order.getOrderId());
        row.setProductId(order.getProductId());
        row.setProductName(order.getProductName());
        row.setActivityId(order.getActivityId());
        row.setPickSource(order.getPickSource());
        row.setOrderAmount(centToYuan(order.getOrderAmount()));
        row.setAttributionStatus(order.getAttributionStatus());
        row.setAttributionRemark(order.getAttributionRemark());
        row.setCreateTime(order.getCreateTime());
        return row;
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

    private List<PerformanceVO> toPerformanceList(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream()
                .map(row -> new PerformanceVO(
                        asString(row, "owner_id"),
                        asLong(row, "order_count"),
                        centToYuan(asLong(row, "order_amount_cent")),
                        centToYuan(asLong(row, "service_fee_cent"))
                ))
                .sorted(Comparator.comparing(PerformanceVO::orderCount).reversed())
                .toList();
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
            }
        }
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

    public static class OrderRowVO {
        private String orderId;
        private String productId;
        private String productName;
        private String activityId;
        private String pickSource;
        private BigDecimal orderAmount;
        private String attributionStatus;
        private String attributionRemark;
        private LocalDateTime createTime;

        public String getOrderId() { return orderId; }
        public void setOrderId(String orderId) { this.orderId = orderId; }
        public String getProductId() { return productId; }
        public void setProductId(String productId) { this.productId = productId; }
        public String getProductName() { return productName; }
        public void setProductName(String productName) { this.productName = productName; }
        public String getActivityId() { return activityId; }
        public void setActivityId(String activityId) { this.activityId = activityId; }
        public String getPickSource() { return pickSource; }
        public void setPickSource(String pickSource) { this.pickSource = pickSource; }
        public BigDecimal getOrderAmount() { return orderAmount; }
        public void setOrderAmount(BigDecimal orderAmount) { this.orderAmount = orderAmount; }
        public String getAttributionStatus() { return attributionStatus; }
        public void setAttributionStatus(String attributionStatus) { this.attributionStatus = attributionStatus; }
        public String getAttributionRemark() { return attributionRemark; }
        public void setAttributionRemark(String attributionRemark) { this.attributionRemark = attributionRemark; }
        public LocalDateTime getCreateTime() { return createTime; }
        public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    }

    public static class SummaryVO {
        private Long orderCount;
        private BigDecimal orderAmount;
        private BigDecimal serviceFee;
        private Long attributedOrderCount;
        private Long unattributedOrderCount;
        private List<PerformanceVO> channelPerformance;
        private List<PerformanceVO> colonelPerformance;

        public Long getOrderCount() { return orderCount; }
        public void setOrderCount(Long orderCount) { this.orderCount = orderCount; }
        public BigDecimal getOrderAmount() { return orderAmount; }
        public void setOrderAmount(BigDecimal orderAmount) { this.orderAmount = orderAmount; }
        public BigDecimal getServiceFee() { return serviceFee; }
        public void setServiceFee(BigDecimal serviceFee) { this.serviceFee = serviceFee; }
        public Long getAttributedOrderCount() { return attributedOrderCount; }
        public void setAttributedOrderCount(Long attributedOrderCount) { this.attributedOrderCount = attributedOrderCount; }
        public Long getUnattributedOrderCount() { return unattributedOrderCount; }
        public void setUnattributedOrderCount(Long unattributedOrderCount) { this.unattributedOrderCount = unattributedOrderCount; }
        public List<PerformanceVO> getChannelPerformance() { return channelPerformance; }
        public void setChannelPerformance(List<PerformanceVO> channelPerformance) { this.channelPerformance = channelPerformance; }
        public List<PerformanceVO> getColonelPerformance() { return colonelPerformance; }
        public void setColonelPerformance(List<PerformanceVO> colonelPerformance) { this.colonelPerformance = colonelPerformance; }
    }

    public record PerformanceVO(String ownerId, Long orderCount, BigDecimal orderAmount, BigDecimal serviceFee) {
    }
}
