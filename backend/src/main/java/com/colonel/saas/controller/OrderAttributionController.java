package com.colonel.saas.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.common.base.BaseController;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.common.result.PageResult;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.domain.order.application.OrderAttributionService;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 订单回流与归因控制器.
 *
 * <p>提供未归因订单分页查询和订单回流归因摘要，属于订单域。
 * 用于订单回流与归因排查页面，支持按数据范围（个人/部门/全部）过滤。</p>
 *
 * <p>API 路径：无统一前缀，两个端点分别挂载在不同路径下：
 * <ul>
 *   <li>{@code GET /orders/order-attribution-unattributed} — 未归因订单分页</li>
 *   <li>{@code GET /dashboard/order-attribution-summary} — 订单回流摘要</li>
 * </ul>
 *
 * <p>访问权限：业务负责人、业务人员、渠道负责人、渠道人员、管理员。</p>
 *
 * @see OrderAttributionService
 */
@Tag(name = "订单回流与归因", description = "订单回流摘要与未归因订单排查接口。")
@Validated
@RestController
@RequestMapping
@RequireRoles({RoleCodes.BIZ_LEADER, RoleCodes.BIZ_STAFF, RoleCodes.CHANNEL_LEADER, RoleCodes.CHANNEL_STAFF, RoleCodes.ADMIN})
public class OrderAttributionController extends BaseController {

    /** 订单归因排查服务，封装订单域查询和数据范围过滤 */
    private final OrderAttributionService attributionService;

    /**
     * 构造注入.
     *
     * @param attributionService 订单归因排查服务
     */
    public OrderAttributionController(OrderAttributionService attributionService) {
        this.attributionService = attributionService;
    }

    /**
     * 分页查询未归因订单.
     *
     * <p>查询结算时间在指定范围内、归因状态为 UNATTRIBUTED 或归因状态为空的订单。
     * 默认查询近 30 天数据。查询结果按数据范围（个人/部门/全部）过滤后分页返回。</p>
     *
     * @param page      页码，从 1 开始
     * @param size      每页条数
     * @param startDate 开始日期（可选）
     * @param endDate   结束日期（可选）
     * @param userId    当前用户 ID，从拦截器注入
     * @param deptId    当前用户部门 ID（可选），从拦截器注入
     * @param dataScope 数据范围枚举（可选），从拦截器注入
     * @return 未归因订单分页结果
     */
    @Operation(summary = "未归因订单分页", description = "分页查询未归因订单，用于订单回流与归因排查页。")
    @GetMapping("/orders/order-attribution-unattributed")
    public ApiResult<PageResult<OrderRowVO>> getUnattributedOrders(
            @Parameter(description = "页码，从 1 开始，最大 1000。") @RequestParam(name = "page", defaultValue = "1") @Min(1) @Max(1000) long page,
            @Parameter(description = "每页条数，最大 200。") @RequestParam(name = "size", defaultValue = "10") @Min(1) @Max(200) long size,
            @Parameter(description = "开始日期，格式 yyyy-MM-dd。") @RequestParam(name = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "结束日期，格式 yyyy-MM-dd。") @RequestParam(name = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope) {
        IPage<ColonelsettlementOrder> rows = attributionService.findUnattributedPage(
                page,
                size,
                startDate,
                endDate,
                userId,
                deptId,
                dataScope);
        Page<OrderRowVO> result = new Page<>(rows.getCurrent(), rows.getSize(), rows.getTotal());
        result.setRecords(rows.getRecords().stream()
                .map(attributionService::toRow)
                .map(this::toRow)
                .toList());
        return okPage(result);
    }

    /**
     * 查询订单回流归因摘要.
     *
     * <p>汇总近 30 天订单回流与归因结果，包含以下维度：
     * <ul>
     *   <li>总订单量、订单金额（元）、服务费（元）</li>
     *   <li>已归因 / 未归因订单数</li>
     *   <li>渠道维度业绩分布（渠道人员订单量、金额、服务费排名）</li>
     *   <li>团长维度业绩分布（团长订单量、金额、服务费排名）</li>
     * </ul>
     * 所有金额从分转换为元，保留两位小数。</p>
     *
     * @param userId    当前用户 ID，从拦截器注入
     * @param deptId    当前用户部门 ID（可选），从拦截器注入
     * @param dataScope 数据范围枚举（可选），从拦截器注入
     * @return 订单回流归因摘要
     */
    @Operation(summary = "订单回流摘要", description = "汇总近 30 天订单回流与归因结果，输出订单量、金额、服务费及渠道/团长业绩分布。")
    @GetMapping("/dashboard/order-attribution-summary")
    public ApiResult<SummaryVO> getSummary(
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope) {
        return ok(toSummary(attributionService.summarize(userId, deptId, dataScope)));
    }

    /**
     * 将结算订单实体转换为未归因订单视图对象.
     *
     * @param order 结算订单实体
     * @return 未归因订单视图对象，金额已从分转换为元
     */
    private OrderRowVO toRow(OrderAttributionService.UnattributedOrderRow order) {
        OrderRowVO row = new OrderRowVO();
        row.setOrderId(order.orderId);
        row.setProductId(order.productId);
        row.setProductName(order.productName);
        row.setActivityId(order.activityId);
        row.setPickSource(order.pickSource);
        row.setOrderAmount(order.orderAmount);
        row.setAttributionStatus(order.attributionStatus);
        row.setAttributionRemark(order.attributionRemark);
        row.setCreateTime(order.createTime);
        return row;
    }

    private SummaryVO toSummary(OrderAttributionService.SummaryResult result) {
        SummaryVO summary = new SummaryVO();
        summary.setOrderCount(result.orderCount);
        summary.setOrderAmount(result.orderAmount);
        summary.setServiceFee(result.serviceFee);
        summary.setAttributedOrderCount(result.attributedOrderCount);
        summary.setUnattributedOrderCount(result.unattributedOrderCount);
        summary.setChannelPerformance(toPerformanceList(result.channelPerformance));
        summary.setColonelPerformance(toPerformanceList(result.colonelPerformance));
        return summary;
    }

    private List<PerformanceVO> toPerformanceList(List<OrderAttributionService.PerformanceItem> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        return items.stream()
                .map(item -> new PerformanceVO(item.ownerId(), item.orderCount(), item.orderAmount(), item.serviceFee()))
                .toList();
    }

    /**
     * 未归因订单行视图对象.
     *
     * <p>用于未归因订单分页列表的数据传输对象，包含订单基本信息、
     * 推广来源、归因状态与金额等字段。</p>
     */
    public static class OrderRowVO {
        /** 订单 ID */
        private String orderId;
        /** 商品 ID */
        private String productId;
        /** 商品名称 */
        private String productName;
        /** 活动 ID */
        private String activityId;
        /** 推广来源标识（pick_source） */
        private String pickSource;
        /** 订单金额（元） */
        private BigDecimal orderAmount;
        /** 归因状态：ATTRIBUTED / UNATTRIBUTED */
        private String attributionStatus;
        /** 归因备注说明 */
        private String attributionRemark;
        /** 订单创建时间 */
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

    /**
     * 订单回流归因摘要视图对象.
     *
     * <p>包含近 30 天订单回流的整体统计和按渠道/团长维度的业绩分布。
     * 所有金额字段单位为元。</p>
     */
    public static class SummaryVO {
        /** 订单总量 */
        private Long orderCount;
        /** 订单总金额（元） */
        private BigDecimal orderAmount;
        /** 服务费总额（元） */
        private BigDecimal serviceFee;
        /** 已归因订单数 */
        private Long attributedOrderCount;
        /** 未归因订单数 */
        private Long unattributedOrderCount;
        /** 渠道维度业绩分布列表 */
        private List<PerformanceVO> channelPerformance;
        /** 团长维度业绩分布列表 */
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

    /**
     * 业绩分布视图对象（Record）.
     *
     * <p>按用户维度聚合的订单业绩统计，用于渠道和团长两个维度的排名展示。</p>
     *
     * @param ownerId     用户 ID（渠道人员或团长）
     * @param orderCount  订单数
     * @param orderAmount 订单总金额（元）
     * @param serviceFee  服务费总额（元）
     */
    public record PerformanceVO(String ownerId, Long orderCount, BigDecimal orderAmount, BigDecimal serviceFee) {
    }
}
