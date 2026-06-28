package com.colonel.saas.domain.order.application;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.config.DddRefactorProperties;
import com.colonel.saas.domain.user.policy.DataScopePolicy;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.mapper.ColonelsettlementOrderMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 订单域归因排查服务。
 *
 * <p>负责未归因订单分页查询与近 30 天订单回流归因摘要(渠道/团长维度业绩分布),
 * 供 {@code OrderAttributionController} 与未来单测共同调用。订单域不变量(CLAUDE.md):
 * 仅事实,不算提成,不应用独家覆盖。本服务只读 {@code colonelsettlement_order} 事实表,
 * 不写 {@code performance_records},不调用 {@code CommissionService} 重算提成,
 * 也不引用 {@code ExclusiveTalentService} / {@code ExclusiveMerchantService} 套独家覆盖。</p>
 *
 * <h3>职责</h3>
 * <ul>
 *   <li>{@link #findUnattributedPage} — 未归因订单分页(归因状态 UNATTRIBUTED 或为空 + settle_time 区间)</li>
 *   <li>{@link #summarize} — 近 30 天订单回流归因摘要(总量 + 渠道/团长业绩分布)</li>
 *   <li>{@link #buildScopedQuery} — 复用数据范围过滤 QueryWrapper 工厂</li>
 *   <li>内部辅助:分转元、聚合行提取、列名大小写不敏感读取</li>
 * </ul>
 *
 * @see com.colonel.saas.controller.OrderAttributionController
 */
@Service
public class OrderAttributionService {

    /**
     * 默认查询窗口:近 30 天(从今天往前 29 天,到明天 0 点,半开区间)。
     */
    public static final int DEFAULT_LOOKBACK_DAYS = 30;

    private final ColonelsettlementOrderMapper orderMapper;
    private final DataScopePolicy dataScopePolicy;
    private final DddRefactorProperties dddRefactorProperties;

    public OrderAttributionService(
            ColonelsettlementOrderMapper orderMapper,
            DataScopePolicy dataScopePolicy,
            DddRefactorProperties dddRefactorProperties) {
        this.orderMapper = orderMapper;
        this.dataScopePolicy = dataScopePolicy;
        this.dddRefactorProperties = dddRefactorProperties;
    }

    // ============================================================
    // 对外方法
    // ============================================================

    /**
     * 分页查询未归因订单(归因状态 UNATTRIBUTED 或为空 + settle_time 区间)。
     *
     * <p>原 {@code OrderAttributionController.getUnattributedOrders} 行为;
     * 抽 service 后,controller 可直接委托,行为完全一致。</p>
     *
     * @param page     页码
     * @param size     每页条数
     * @param startDate 起始日期(可选;null → 今天 - 30 天)
     * @param endDate   结束日期(可选;null → 今天 + 1 天)
     * @param userId    当前用户 ID
     * @param deptId    当前用户部门 ID
     * @param dataScope 数据范围
     * @return 未归因订单分页结果
     */
    public IPage<ColonelsettlementOrder> findUnattributedPage(
            long page,
            long size,
            LocalDate startDate,
            LocalDate endDate,
            UUID userId,
            UUID deptId,
            DataScope dataScope) {
        LocalDate today = LocalDate.now();
        LocalDateTime start = startDate == null
                ? today.minusDays(DEFAULT_LOOKBACK_DAYS - 1).atStartOfDay()
                : startDate.atStartOfDay();
        LocalDateTime end = endDate == null
                ? today.plusDays(1).atStartOfDay()
                : endDate.plusDays(1).atStartOfDay();

        QueryWrapper<ColonelsettlementOrder> wrapper = new QueryWrapper<>();
        wrapper.ge("co.settle_time", start)
                .lt("co.settle_time", end)
                .and(q -> q.eq("co.attribution_status", "UNATTRIBUTED").or().isNull("co.attribution_status"));
        applyPageDataScope(wrapper, userId, deptId, dataScope);

        return orderMapper.findPageWithScope(
                new Page<>(Math.max(page, 1), Math.max(size, 1)),
                wrapper);
    }

    /**
     * 查询近 30 天订单回流归因摘要。
     *
     * <p>汇总指标:订单总数、订单金额(分→元)、服务费(分→元)、已归因 / 未归因订单数;
     * 同时按渠道负责人、团长用户 ID 维度聚合前 30 天业绩分布(按订单数降序)。</p>
     *
     * @param userId    当前用户 ID
     * @param deptId    当前用户部门 ID
     * @param dataScope 数据范围
     * @return 订单回流归因摘要
     */
    public SummaryResult summarize(UUID userId, UUID deptId, DataScope dataScope) {
        LocalDate today = LocalDate.now();
        LocalDateTime start = today.minusDays(DEFAULT_LOOKBACK_DAYS - 1).atStartOfDay();
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

        Map<String, Object> totalRow = getSingleAggregate(totalWrapper);
        SummaryResult summary = new SummaryResult();
        summary.orderCount = asLong(totalRow, "order_count");
        summary.orderAmount = centToYuan(asLong(totalRow, "order_amount_cent"));
        summary.serviceFee = centToYuan(asLong(totalRow, "service_fee_cent"));
        summary.attributedOrderCount = asLong(totalRow, "attributed_order_count");
        summary.unattributedOrderCount = asLong(totalRow, "unattributed_order_count");

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
        summary.channelPerformance = toPerformanceList(orderMapper.selectMaps(channelWrapper));

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
        summary.colonelPerformance = toPerformanceList(orderMapper.selectMaps(colonelWrapper));
        return summary;
    }

    /**
     * 未归因订单行 → 视图对象映射(供 controller 复用,避免重复实现)。
     *
     * @param order 结算订单实体
     * @return 未归因订单行,金额已分→元
     */
    public UnattributedOrderRow toRow(ColonelsettlementOrder order) {
        UnattributedOrderRow row = new UnattributedOrderRow();
        row.orderId = order.getOrderId();
        row.productId = order.getProductId();
        row.productName = order.getProductName();
        row.activityId = order.getActivityId();
        row.pickSource = order.getPickSource();
        row.orderAmount = centToYuan(order.getOrderAmount());
        row.attributionStatus = order.getAttributionStatus();
        row.attributionRemark = order.getAttributionRemark();
        row.createTime = order.getCreateTime();
        return row;
    }

    // ============================================================
    // 包级辅助:数据范围 / 单行聚合 / 业绩列表 / 列读取
    // ============================================================

    /**
     * 构建带数据范围过滤的基础查询条件。
     * 内部已排除软删除记录,并按数据范围追加 user/dept 过滤条件。
     */
    public QueryWrapper<ColonelsettlementOrder> buildScopedQuery(
            UUID userId,
            UUID deptId,
            DataScope dataScope) {
        QueryWrapper<ColonelsettlementOrder> wrapper = new QueryWrapper<>();
        wrapper.eq("deleted", 0);
        applyScopedQueryDataScope(wrapper, userId, deptId, dataScope);
        return wrapper;
    }

    public void applyPageDataScope(
            QueryWrapper<ColonelsettlementOrder> wrapper,
            UUID userId,
            UUID deptId,
            DataScope dataScope) {
        applyDataScope(wrapper, userId, deptId, dataScope, "co.user_id", "co.dept_id");
    }

    public void applyScopedQueryDataScope(
            QueryWrapper<ColonelsettlementOrder> wrapper,
            UUID userId,
            UUID deptId,
            DataScope dataScope) {
        if (wrapper == null || dataScope == null) {
            return;
        }
        applyDataScope(wrapper, userId, deptId, dataScope, "user_id", "dept_id");
    }

    private void applyDataScope(
            QueryWrapper<ColonelsettlementOrder> wrapper,
            UUID userId,
            UUID deptId,
            DataScope dataScope,
            String userIdColumn,
            String deptIdColumn) {
        if (wrapper == null || dataScope == null) {
            return;
        }
        if (!dddRefactorProperties.getDataScopePolicy().isEnabled()) {
            applyDataScopeLegacy(wrapper, userId, deptId, dataScope, userIdColumn, deptIdColumn);
            return;
        }
        applyDataScopeWithPolicy(wrapper, userId, deptId, dataScope, userIdColumn, deptIdColumn);
    }

    private void applyDataScopeLegacy(
            QueryWrapper<ColonelsettlementOrder> wrapper,
            UUID userId,
            UUID deptId,
            DataScope dataScope,
            String userIdColumn,
            String deptIdColumn) {
        if (dataScope == DataScope.PERSONAL && userId != null && StringUtils.hasText(userIdColumn)) {
            wrapper.eq(userIdColumn, userId);
            return;
        }
        if (dataScope == DataScope.DEPT && deptId != null && StringUtils.hasText(deptIdColumn)) {
            wrapper.eq(deptIdColumn, deptId);
        }
    }

    private void applyDataScopeWithPolicy(
            QueryWrapper<ColonelsettlementOrder> wrapper,
            UUID userId,
            UUID deptId,
            DataScope dataScope,
            String userIdColumn,
            String deptIdColumn) {
        dataScopePolicy.applyTo(wrapper, userId, deptId, dataScope, userIdColumn, deptIdColumn);
    }

    public Map<String, Object> getSingleAggregate(QueryWrapper<ColonelsettlementOrder> wrapper) {
        List<Map<String, Object>> rows = orderMapper.selectMaps(wrapper);
        if (rows == null || rows.isEmpty()) {
            return Map.of();
        }
        return rows.get(0);
    }

    public List<PerformanceItem> toPerformanceList(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream()
                .map(row -> new PerformanceItem(
                        asString(row, "owner_id"),
                        asLong(row, "order_count"),
                        centToYuan(asLong(row, "order_amount_cent")),
                        centToYuan(asLong(row, "service_fee_cent"))
                ))
                .sorted(Comparator.comparingLong(PerformanceItem::orderCount).reversed())
                .toList();
    }

    public BigDecimal centToYuan(Long cent) {
        long value = cent == null ? 0L : cent;
        return BigDecimal.valueOf(value).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    public Long asLong(Map<String, Object> row, String key) {
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

    public String asString(Map<String, Object> row, String key) {
        Object value = readValue(row, key);
        return value == null ? "" : String.valueOf(value);
    }

    public Object readValue(Map<String, Object> row, String key) {
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

    // ============================================================
    // DTO:跨方法结果传递(独立于 controller 内部类)
    // ============================================================

    /**
     * 订单回流归因摘要(独立 record,便于单测 / 跨域复用)。
     */
    public static final class SummaryResult {
        public Long orderCount;
        public BigDecimal orderAmount;
        public BigDecimal serviceFee;
        public Long attributedOrderCount;
        public Long unattributedOrderCount;
        public List<PerformanceItem> channelPerformance = List.of();
        public List<PerformanceItem> colonelPerformance = List.of();
    }

    /**
     * 单用户业绩行(渠道负责人 / 团长,owner_id + 订单数 + 金额 + 服务费)。
     */
    public record PerformanceItem(
            String ownerId,
            Long orderCount,
            BigDecimal orderAmount,
            BigDecimal serviceFee
    ) {
    }

    /**
     * 未归因订单行(供 controller 装配 OrderRowVO)。
     * 字段命名与 {@code OrderAttributionController.OrderRowVO} 兼容。
     */
    public static final class UnattributedOrderRow {
        public String orderId;
        public String productId;
        public String productName;
        public String activityId;
        public String pickSource;
        public BigDecimal orderAmount;
        public String attributionStatus;
        public String attributionRemark;
        public LocalDateTime createTime;
    }
}
