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
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

@Tag(name = "订单回流与归因")
@RestController
@RequestMapping
@RequireRoles({RoleCodes.BIZ_LEADER, RoleCodes.BIZ_STAFF, RoleCodes.CHANNEL_LEADER, RoleCodes.CHANNEL_STAFF, RoleCodes.ADMIN})
public class OrderAttributionController extends BaseController {

    private final ColonelsettlementOrderMapper orderMapper;

    public OrderAttributionController(ColonelsettlementOrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    @Operation(summary = "未归因订单分页")
    @GetMapping("/orders/order-attribution-unattributed")
    public ApiResult<PageResult<OrderRowVO>> getUnattributedOrders(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        LocalDateTime start = startDate == null
                ? LocalDate.now().minusDays(30).atStartOfDay()
                : startDate.atStartOfDay();
        LocalDateTime end = endDate == null
                ? LocalDate.now().plusDays(1).atStartOfDay()
                : endDate.plusDays(1).atStartOfDay();

        QueryWrapper<ColonelsettlementOrder> wrapper = new QueryWrapper<>();
        wrapper.ge("co.create_time", start)
                .lt("co.create_time", end)
                .and(q -> q.eq("co.attribution_status", "UNATTRIBUTED").or().isNull("co.attribution_status"));

        IPage<ColonelsettlementOrder> rows = orderMapper.findPageWithScope(new Page<>(Math.max(page, 1), Math.max(size, 1)), wrapper);
        Page<OrderRowVO> result = new Page<>(rows.getCurrent(), rows.getSize(), rows.getTotal());
        result.setRecords(rows.getRecords().stream().map(this::toRow).toList());
        return okPage(result);
    }

    @Operation(summary = "订单回流摘要")
    @GetMapping("/dashboard/order-attribution-summary")
    public ApiResult<SummaryVO> getSummary(
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope) {
        LocalDate today = LocalDate.now();
        LocalDateTime start = today.minusDays(29).atStartOfDay();
        LocalDateTime end = today.plusDays(1).atStartOfDay();

        LambdaQueryWrapper<ColonelsettlementOrder> wrapper = new LambdaQueryWrapper<ColonelsettlementOrder>()
                .between(ColonelsettlementOrder::getCreateTime, start, end);
        applyDataScope(wrapper, userId, deptId, dataScope);

        List<ColonelsettlementOrder> rows = orderMapper.selectList(wrapper);
        SummaryVO summary = new SummaryVO();
        summary.setOrderCount((long) rows.size());
        summary.setOrderAmount(centToYuan(sumField(rows, ColonelsettlementOrder::getOrderAmount)));
        summary.setServiceFee(centToYuan(sumField(rows, ColonelsettlementOrder::getSettleColonelCommission)));
        summary.setAttributedOrderCount(rows.stream()
                .filter(row -> "ATTRIBUTED".equalsIgnoreCase(row.getAttributionStatus()))
                .count());
        summary.setUnattributedOrderCount(rows.stream()
                .filter(row -> !"ATTRIBUTED".equalsIgnoreCase(row.getAttributionStatus()))
                .count());
        summary.setChannelPerformance(buildPerformance(rows, true));
        summary.setColonelPerformance(buildPerformance(rows, false));
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

    private List<PerformanceVO> buildPerformance(List<ColonelsettlementOrder> rows, boolean channel) {
        return rows.stream()
                .filter(row -> "ATTRIBUTED".equalsIgnoreCase(row.getAttributionStatus()))
                .collect(java.util.stream.Collectors.groupingBy(row -> channel ? row.getChannelUserId() : row.getUserId()))
                .entrySet()
                .stream()
                .filter(entry -> entry.getKey() != null)
                .map(entry -> new PerformanceVO(
                        String.valueOf(entry.getKey()),
                        (long) entry.getValue().size(),
                        centToYuan(sumField(entry.getValue(), ColonelsettlementOrder::getOrderAmount)),
                        centToYuan(sumField(entry.getValue(), ColonelsettlementOrder::getSettleColonelCommission))
                ))
                .toList();
    }

    private Long sumField(List<ColonelsettlementOrder> rows, Function<ColonelsettlementOrder, Long> getter) {
        long sum = 0L;
        for (ColonelsettlementOrder row : rows) {
            Long value = getter.apply(row);
            sum += value == null ? 0L : value;
        }
        return sum;
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
            }
        }
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
