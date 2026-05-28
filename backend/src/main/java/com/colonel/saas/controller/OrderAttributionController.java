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
 * @see ColonelsettlementOrderMapper
 */
@Tag(name = "订单回流与归因", description = "订单回流摘要与未归因订单排查接口。")
@Validated
@RestController
@RequestMapping
@RequireRoles({RoleCodes.BIZ_LEADER, RoleCodes.BIZ_STAFF, RoleCodes.CHANNEL_LEADER, RoleCodes.CHANNEL_STAFF, RoleCodes.ADMIN})
public class OrderAttributionController extends BaseController {

    /** 订单 Mapper，用于查询结算订单数据与聚合统计 */
    private final ColonelsettlementOrderMapper orderMapper;

    /**
     * 构造注入.
     *
     * @param orderMapper 订单 Mapper
     */
    public OrderAttributionController(ColonelsettlementOrderMapper orderMapper) {
        this.orderMapper = orderMapper;
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
        // 未传日期时默认查询近 30 天
        LocalDateTime start = startDate == null
                ? LocalDate.now().minusDays(30).atStartOfDay()
                : startDate.atStartOfDay();
        LocalDateTime end = endDate == null
                ? LocalDate.now().plusDays(1).atStartOfDay()
                : endDate.plusDays(1).atStartOfDay();

        // 构建未归因订单查询条件：归因状态为 UNATTRIBUTED 或为空
        QueryWrapper<ColonelsettlementOrder> wrapper = new QueryWrapper<>();
        wrapper.ge("co.settle_time", start)
                .lt("co.settle_time", end)
                .and(q -> q.eq("co.attribution_status", "UNATTRIBUTED").or().isNull("co.attribution_status"));
        // 应用数据范围过滤
        applyPageDataScope(wrapper, userId, deptId, dataScope);

        // 执行已显式追加数据范围条件的分页查询，并将实体映射为视图对象
        IPage<ColonelsettlementOrder> rows = orderMapper.findPageWithScope(new Page<>(Math.max(page, 1), Math.max(size, 1)), wrapper);
        Page<OrderRowVO> result = new Page<>(rows.getCurrent(), rows.getSize(), rows.getTotal());
        result.setRecords(rows.getRecords().stream().map(this::toRow).toList());
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
        // 计算近 30 天时间范围
        LocalDate today = LocalDate.now();
        LocalDateTime start = today.minusDays(29).atStartOfDay();
        LocalDateTime end = today.plusDays(1).atStartOfDay();

        // 构建总计聚合查询：订单量、金额、服务费、已归因/未归因数
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

        // 将聚合结果映射到摘要视图对象，金额从分转换为元
        SummaryVO summary = new SummaryVO();
        Map<String, Object> totalRow = getSingleAggregate(totalWrapper);
        summary.setOrderCount(asLong(totalRow, "order_count"));
        summary.setOrderAmount(centToYuan(asLong(totalRow, "order_amount_cent")));
        summary.setServiceFee(centToYuan(asLong(totalRow, "service_fee_cent")));
        summary.setAttributedOrderCount(asLong(totalRow, "attributed_order_count"));
        summary.setUnattributedOrderCount(asLong(totalRow, "unattributed_order_count"));

        // 查询渠道维度业绩分布：按渠道用户 ID 分组统计
        QueryWrapper<ColonelsettlementOrder> channelWrapper = buildScopedQuery(userId, deptId, dataScope)
                .select(
                        "COALESCE(user_id, channel_user_id) AS owner_id",
                        "COUNT(*) AS order_count",
                        "COALESCE(SUM(order_amount), 0) AS order_amount_cent",
                        "COALESCE(SUM(settle_colonel_commission), 0) AS service_fee_cent"
                )
                .eq("attribution_status", "ATTRIBUTED")
                .and(q -> q.isNotNull("user_id").or().isNotNull("channel_user_id"))
                .ge("settle_time", start)
                .lt("settle_time", end)
                .groupBy("COALESCE(user_id, channel_user_id)");
        summary.setChannelPerformance(toPerformanceList(orderMapper.selectMaps(channelWrapper)));

        // 查询团长维度业绩分布：按团长用户 ID 分组统计
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

    /**
     * 将结算订单实体转换为未归因订单视图对象.
     *
     * @param order 结算订单实体
     * @return 未归因订单视图对象，金额已从分转换为元
     */
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

    /**
     * 将分转换为元，保留两位小数.
     *
     * @param cent 金额（分），null 时视为 0
     * @return 金额（元），使用 HALF_UP 舍入模式
     */
    private BigDecimal centToYuan(Long cent) {
        long value = cent == null ? 0L : cent;
        return BigDecimal.valueOf(value).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    /**
     * 构建带数据范围过滤的基础查询条件.
     *
     * <p>创建已排除软删除记录的基础 QueryWrapper，并根据数据范围追加用户/部门过滤条件。</p>
     *
     * @param userId    当前用户 ID
     * @param deptId    当前用户部门 ID
     * @param dataScope 数据范围枚举
     * @return 已应用数据范围过滤的 QueryWrapper
     */
    private QueryWrapper<ColonelsettlementOrder> buildScopedQuery(
            UUID userId,
            UUID deptId,
            DataScope dataScope) {
        QueryWrapper<ColonelsettlementOrder> wrapper = new QueryWrapper<>();
        wrapper.eq("deleted", 0);
        applyScopedQueryDataScope(wrapper, userId, deptId, dataScope);
        return wrapper;
    }

    /**
     * 执行聚合查询并返回第一行结果.
     *
     * <p>用于查询 COUNT / SUM 等聚合函数的单行结果。
     * 查询结果为空时返回空 Map。</p>
     *
     * @param wrapper 已配置聚合字段和条件的 QueryWrapper
     * @return 聚合结果的第一行映射，无数据时返回空 Map
     */
    private Map<String, Object> getSingleAggregate(QueryWrapper<ColonelsettlementOrder> wrapper) {
        List<Map<String, Object>> rows = orderMapper.selectMaps(wrapper);
        if (rows == null || rows.isEmpty()) {
            return Map.of();
        }
        return rows.get(0);
    }

    /**
     * 将聚合查询结果列表转换为业绩视图对象列表.
     *
     * <p>提取 owner_id、order_count、order_amount_cent、service_fee_cent 字段，
     * 金额从分转换为元，并按订单量降序排列。</p>
     *
     * @param rows 聚合查询结果映射列表
     * @return 业绩视图对象列表，按订单量降序排列
     */
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

    /**
     * 根据数据范围向 LambdaQueryWrapper 追加过滤条件.
     *
     * <p>PERSONAL 范围按 user_id 过滤，DEPT 范围按 dept_id 过滤，ALL 范围不过滤。</p>
     *
     * @param wrapper   Lambda 查询条件包装器
     * @param userId    当前用户 ID
     * @param deptId    当前用户部门 ID
     * @param dataScope 数据范围枚举
     */
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

    /**
     * 根据数据范围向分页查询 QueryWrapper 追加带表别名的过滤条件.
     *
     * <p>与 {@link #applyDataScope} 功能相同，但使用带 {@code co.} 前缀的列名，
     * 适用于 JOIN 查询场景中的分页查询。</p>
     *
     * @param wrapper   分页查询条件包装器（使用 co 表别名）
     * @param userId    当前用户 ID
     * @param deptId    当前用户部门 ID
     * @param dataScope 数据范围枚举
     */
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

    /**
     * 根据数据范围向聚合查询 QueryWrapper 追加无表别名的过滤条件.
     *
     * <p>与 {@link #applyDataScope} 功能相同，但使用不带表别名的裸列名，
     * 适用于非 JOIN 的简单聚合查询场景。</p>
     *
     * @param wrapper   聚合查询条件包装器（无表别名）
     * @param userId    当前用户 ID
     * @param deptId    当前用户部门 ID
     * @param dataScope 数据范围枚举
     */
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

    /**
     * 从 Map 中提取 Long 类型值，支持 Number 和字符串两种格式.
     *
     * @param row 数据行映射
     * @param key 字段名
     * @return Long 值，null 或无法解析时返回 0L
     */
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

    /**
     * 从 Map 中提取 String 类型值.
     *
     * @param row 数据行映射
     * @param key 字段名
     * @return String 值，null 时返回空字符串
     */
    private String asString(Map<String, Object> row, String key) {
        Object value = readValue(row, key);
        return value == null ? "" : String.valueOf(value);
    }

    /**
     * 从 Map 中读取值，支持忽略大小写的键匹配.
     *
     * <p>先尝试精确匹配，再尝试忽略大小写匹配。
     * 用于兼容不同数据库驱动返回的列名大小写差异。</p>
     *
     * @param row 数据行映射
     * @param key 字段名
     * @return 匹配的值，无匹配时返回 null
     */
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
