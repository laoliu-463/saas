package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.mapper.ColonelsettlementOrderMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 订单域查询与筛选服务。
 *
 * <p>负责订单主链路（/orders、/orders/unattributed、/orders/stats、/orders/filter-options）
 * 的筛选条件构造与分页查询。订单域不变量（CLAUDE.md）：仅事实，不算提成，不应用独家覆盖。
 * 本服务只读 / 写 {@code colonelsettlement_order} 事实表，不访问 {@code performance_records}、
 * {@code exclusive_talent}、{@code exclusive_merchant} 等业绩 / 独家覆盖表。</p>
 *
 * <h3>职责</h3>
 * <ul>
 *   <li>构造订单列表的 {@link LambdaQueryWrapper}（与 {@code OrderController.getOrders} 共用）</li>
 *   <li>构造订单统计的 {@link QueryWrapper}（与 {@code OrderController.getStats} 共用）</li>
 *   <li>应用数据范围（PERSONAL/DEPT/ALL）过滤，保证非 admin 只能看自己/本组</li>
 *   <li>应用时间区间筛选（createTime / settleTime）</li>
 *   <li>应用归因状态 / 未归因原因筛选</li>
 *   <li>应用 Dashboard 诊断分类筛选（白名单 + 防 SQL 注入）</li>
 *   <li>解析 CSV 形式的部门 ID 列表（招募/渠道部门）</li>
 * </ul>
 *
 * <h3>协作</h3>
 * <ul>
 *   <li>{@link ColonelsettlementOrderMapper#findPageWithScope} 实际执行分页查询</li>
 *   <li>{@link DashboardService} 提供诊断分类白名单与 CASE WHEN SQL 片段</li>
 *   <li>{@link AttributionService} 提供归因状态 / 原因常量</li>
 * </ul>
 *
 * @see com.colonel.saas.controller.OrderController
 */
@Service
public class OrderService {

    private final ColonelsettlementOrderMapper orderMapper;
    private final DashboardService dashboardService;

    public OrderService(ColonelsettlementOrderMapper orderMapper, DashboardService dashboardService) {
        this.orderMapper = orderMapper;
        this.dashboardService = dashboardService;
    }

    // ============================================================
    // 对外方法：分页 / 统计
    // ============================================================

    /**
     * 分页查询订单。
     *
     * <p>所有筛选参数与 {@code OrderController.getOrders} 保持一致；本方法供
     * 单元测试以 mock {@link ColonelsettlementOrderMapper} 验证 wrapper 拼装是否
     * 正确。</p>
     */
    public IPage<ColonelsettlementOrder> findPage(
            long page,
            long size,
            String orderId,
            String attributionStatus,
            String unattributedReason,
            String activityId,
            String productId,
            String channelKeyword,
            String colonelKeyword,
            Integer orderStatus,
            String startTime,
            String endTime,
            String timeField,
            String dashboardDiagnosis,
            List<UUID> recruiterDeptIds,
            List<UUID> channelDeptIds,
            UUID userId,
            UUID deptId,
            DataScope dataScope) {
        Page<ColonelsettlementOrder> query = new Page<>(page, size);
        LambdaQueryWrapper<ColonelsettlementOrder> wrapper = buildWrapper(
                orderId,
                attributionStatus,
                unattributedReason,
                activityId,
                productId,
                channelKeyword,
                colonelKeyword,
                orderStatus,
                startTime,
                endTime,
                timeField,
                dashboardDiagnosis,
                normalizeUuidList(recruiterDeptIds),
                normalizeUuidList(channelDeptIds)
        );
        selectOrderListColumns(wrapper);
        applyDataScope(wrapper, userId, deptId, dataScope);
        wrapper.orderByDesc(ColonelsettlementOrder::getUpdateTime)
                .orderByDesc(ColonelsettlementOrder::getCreateTime);
        IPage<ColonelsettlementOrder> result = orderMapper.selectPage(query, wrapper);
        if (result != null && result.getRecords() != null) {
            result.getRecords().forEach(this::normalizeOrderRow);
        }
        return result;
    }

    /**
     * 计算订单统计（按归因状态分组 + 按未归因原因分组）。
     *
     * <p>返回结构与 {@code OrderController.OrderStats} 同构，但本服务不耦合 controller
     * 内部类，使用独立 record 暴露，便于单测与跨域复用。</p>
     */
    public OrderStatsResult findStats(
            String orderId,
            String attributionStatus,
            String unattributedReason,
            String activityId,
            String productId,
            String channelKeyword,
            String colonelKeyword,
            Integer orderStatus,
            String startTime,
            String endTime,
            String timeField,
            String dashboardDiagnosis,
            List<UUID> recruiterDeptIds,
            List<UUID> channelDeptIds,
            UUID userId,
            UUID deptId,
            DataScope dataScope,
            LocalDateTime lastSyncTime) {
        List<UUID> normalizedRecruiterDeptIds = normalizeUuidList(recruiterDeptIds);
        List<UUID> normalizedChannelDeptIds = normalizeUuidList(channelDeptIds);

        QueryWrapper<ColonelsettlementOrder> statusWrapper = buildStatsWrapper(
                orderId,
                attributionStatus,
                unattributedReason,
                activityId,
                productId,
                channelKeyword,
                colonelKeyword,
                orderStatus,
                startTime,
                endTime,
                timeField,
                dashboardDiagnosis,
                normalizedRecruiterDeptIds,
                normalizedChannelDeptIds
        );
        applyQueryDataScope(statusWrapper, userId, deptId, dataScope);
        statusWrapper.select("attribution_status AS attributionStatus", "COUNT(*) AS total")
                .groupBy("attribution_status");

        long totalOrders = 0L;
        long attributedOrders = 0L;
        long unattributedOrders = 0L;
        long partialOrders = 0L;
        for (Map<String, Object> row : orderMapper.selectMaps(statusWrapper)) {
            String status = asText(readValue(row, "attributionStatus"));
            long count = asLong(readValue(row, "total"));
            totalOrders += count;
            if (AttributionService.STATUS_ATTRIBUTED.equals(status)) {
                attributedOrders += count;
            }
            if (AttributionService.STATUS_UNATTRIBUTED.equals(status)) {
                unattributedOrders += count;
            }
            if ("PARTIAL".equals(status)) {
                partialOrders += count;
            }
        }

        QueryWrapper<ColonelsettlementOrder> reasonWrapper = buildStatsWrapper(
                orderId,
                attributionStatus,
                unattributedReason,
                activityId,
                productId,
                channelKeyword,
                colonelKeyword,
                orderStatus,
                startTime,
                endTime,
                timeField,
                dashboardDiagnosis,
                normalizedRecruiterDeptIds,
                normalizedChannelDeptIds
        );
        applyQueryDataScope(reasonWrapper, userId, deptId, dataScope);
        reasonWrapper.eq("attribution_status", AttributionService.STATUS_UNATTRIBUTED)
                .isNotNull("attribution_remark")
                .select("attribution_remark AS reason", "COUNT(*) AS total")
                .groupBy("attribution_remark");

        List<ReasonCount> reasonCounts = new ArrayList<>();
        long syncFailedOrders = 0L;
        for (Map<String, Object> row : orderMapper.selectMaps(reasonWrapper)) {
            String reason = asText(readValue(row, "reason"));
            long count = asLong(readValue(row, "total"));
            if (!StringUtils.hasText(reason)) {
                continue;
            }
            reasonCounts.add(new ReasonCount(reason, count));
            if (AttributionService.REASON_SYNC_FAILED.equals(reason)) {
                syncFailedOrders += count;
            }
        }
        reasonCounts.sort(Comparator.comparingLong(ReasonCount::count).reversed());
        return new OrderStatsResult(
                totalOrders,
                attributedOrders,
                unattributedOrders,
                partialOrders,
                syncFailedOrders,
                lastSyncTime,
                reasonCounts
        );
    }

    // ============================================================
    // 包级私有 / 公开：wrapper 构造工具（供单测验证 + OrderController 委托）
    // ============================================================

    /**
     * 构建订单列表的 {@link LambdaQueryWrapper}。所有筛选条件与 controller
     * 内 {@code buildWrapper} 同构，单元测试可验证 SQL 片段是否被正确拼装。
     */
    public LambdaQueryWrapper<ColonelsettlementOrder> buildWrapper(
            String orderId,
            String attributionStatus,
            String unattributedReason,
            String activityId,
            String productId,
            String channelKeyword,
            String colonelKeyword,
            Integer orderStatus,
            String startTime,
            String endTime,
            String timeField,
            String dashboardDiagnosis,
            List<UUID> recruiterDeptIds,
            List<UUID> channelDeptIds) {
        LocalDateTime start = parseLocalDateTime(startTime);
        LocalDateTime end = parseLocalDateTime(endTime);
        List<UUID> normalizedRecruiterDeptIds = normalizeUuidList(recruiterDeptIds);
        List<UUID> normalizedChannelDeptIds = normalizeUuidList(channelDeptIds);
        LambdaQueryWrapper<ColonelsettlementOrder> wrapper = new LambdaQueryWrapper<ColonelsettlementOrder>()
                .eq(ColonelsettlementOrder::getDeleted, 0)
                .eq(StringUtils.hasText(orderId), ColonelsettlementOrder::getOrderId, orderId)
                .eq(StringUtils.hasText(unattributedReason), ColonelsettlementOrder::getAttributionRemark, unattributedReason)
                .eq(StringUtils.hasText(activityId), ColonelsettlementOrder::getActivityId, activityId)
                .eq(StringUtils.hasText(productId), ColonelsettlementOrder::getProductId, productId)
                .eq(orderStatus != null, ColonelsettlementOrder::getOrderStatus, orderStatus)
                .in(!normalizedRecruiterDeptIds.isEmpty(), ColonelsettlementOrder::getDeptId, normalizedRecruiterDeptIds)
                .in(!normalizedChannelDeptIds.isEmpty(), ColonelsettlementOrder::getChannelDeptId, normalizedChannelDeptIds)
                .and(StringUtils.hasText(channelKeyword), nested -> nested
                        .like(ColonelsettlementOrder::getChannelUserName, channelKeyword)
                        .or()
                        .like(ColonelsettlementOrder::getChannelUserId, channelKeyword))
                .and(StringUtils.hasText(colonelKeyword), nested -> nested
                        .like(ColonelsettlementOrder::getColonelUserName, colonelKeyword)
                        .or()
                        .like(ColonelsettlementOrder::getColonelUserId, colonelKeyword));
        applyAttributionStatusFilter(wrapper, attributionStatus);
        applyTimeRange(wrapper, resolveTimeField(timeField), start, end);
        applyDashboardDiagnosisFilter(wrapper, null, dashboardDiagnosis);
        return wrapper;
    }

    /**
     * 构建订单统计的 {@link QueryWrapper}（用于 selectMaps + group by 聚合）。
     */
    public QueryWrapper<ColonelsettlementOrder> buildStatsWrapper(
            String orderId,
            String attributionStatus,
            String unattributedReason,
            String activityId,
            String productId,
            String channelKeyword,
            String colonelKeyword,
            Integer orderStatus,
            String startTime,
            String endTime,
            String timeField,
            String dashboardDiagnosis,
            List<UUID> recruiterDeptIds,
            List<UUID> channelDeptIds) {
        LocalDateTime start = parseLocalDateTime(startTime);
        LocalDateTime end = parseLocalDateTime(endTime);
        List<UUID> normalizedRecruiterDeptIds = normalizeUuidList(recruiterDeptIds);
        List<UUID> normalizedChannelDeptIds = normalizeUuidList(channelDeptIds);
        QueryWrapper<ColonelsettlementOrder> wrapper = new QueryWrapper<>();
        wrapper.eq("deleted", 0)
                .eq(StringUtils.hasText(orderId), "order_id", orderId)
                .eq(StringUtils.hasText(unattributedReason), "attribution_remark", unattributedReason)
                .eq(StringUtils.hasText(activityId), "colonel_activity_id", activityId)
                .eq(StringUtils.hasText(productId), "product_id", productId)
                .eq(orderStatus != null, "order_status", orderStatus)
                .in(!normalizedRecruiterDeptIds.isEmpty(), "dept_id", normalizedRecruiterDeptIds)
                .in(!normalizedChannelDeptIds.isEmpty(), "channel_dept_id", normalizedChannelDeptIds)
                .and(StringUtils.hasText(channelKeyword), nested -> nested
                        .like("channel_user_name", channelKeyword)
                        .or()
                        .like("channel_user_id", channelKeyword))
                .and(StringUtils.hasText(colonelKeyword), nested -> nested
                        .like("colonel_user_name", colonelKeyword)
                        .or()
                        .like("colonel_user_id", colonelKeyword));
        applyAttributionStatusFilter(wrapper, attributionStatus);
        applyTimeRange(wrapper, resolveTimeField(timeField), start, end);
        applyDashboardDiagnosisFilter(wrapper, null, dashboardDiagnosis);
        return wrapper;
    }

    /**
     * 解析前端 CSV（"a,b,c"）或重复同名参数为 UUID 列表。非法 UUID 静默跳过。
     */
    public static List<UUID> parseUuidCsv(String csv) {
        if (!StringUtils.hasText(csv)) {
            return List.of();
        }
        List<UUID> result = new ArrayList<>();
        for (String token : csv.split(",")) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                result.add(UUID.fromString(trimmed));
            } catch (IllegalArgumentException ignored) {
                // 非法 UUID 跳过，不影响其他合法值
            }
        }
        return result;
    }

    /**
     * 把 LocalDateTime 字符串解析为 {@link LocalDateTime}。非空但解析失败返回 null
     * （业务上等价于"该值没生效"，避免 500）。
     */
    public static LocalDateTime parseLocalDateTime(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return LocalDateTime.parse(text, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 把时间字段字符串归一为 SQL 列名。
     */
    public static String resolveTimeField(String timeField) {
        return "settleTime".equalsIgnoreCase(timeField) ? "settle_time" : "create_time";
    }

    // ============================================================
    // 包内辅助：归因 / 时间 / 范围 / 诊断分类 / 列选择 / 行规范化
    // ============================================================

    public void applyAttributionStatusFilter(
            LambdaQueryWrapper<ColonelsettlementOrder> wrapper,
            String attributionStatus) {
        if (wrapper == null || !StringUtils.hasText(attributionStatus)) {
            return;
        }
        if (AttributionService.STATUS_UNATTRIBUTED.equals(attributionStatus)) {
            wrapper.and(nested -> nested
                    .eq(ColonelsettlementOrder::getAttributionStatus, AttributionService.STATUS_UNATTRIBUTED)
                    .or()
                    .isNull(ColonelsettlementOrder::getAttributionStatus));
            return;
        }
        wrapper.eq(ColonelsettlementOrder::getAttributionStatus, attributionStatus);
    }

    public void applyAttributionStatusFilter(
            QueryWrapper<ColonelsettlementOrder> wrapper,
            String attributionStatus) {
        if (wrapper == null || !StringUtils.hasText(attributionStatus)) {
            return;
        }
        if (AttributionService.STATUS_UNATTRIBUTED.equals(attributionStatus)) {
            wrapper.and(nested -> nested
                    .eq("attribution_status", AttributionService.STATUS_UNATTRIBUTED)
                    .or()
                    .isNull("attribution_status"));
            return;
        }
        wrapper.eq("attribution_status", attributionStatus);
    }

    public void applyTimeRange(LambdaQueryWrapper<ColonelsettlementOrder> wrapper, String timeField, LocalDateTime start, LocalDateTime end) {
        if ("settle_time".equals(timeField)) {
            wrapper.ge(start != null, ColonelsettlementOrder::getSettleTime, start)
                    .le(end != null, ColonelsettlementOrder::getSettleTime, end);
            return;
        }
        wrapper.ge(start != null, ColonelsettlementOrder::getCreateTime, start)
                .le(end != null, ColonelsettlementOrder::getCreateTime, end);
    }

    public void applyTimeRange(QueryWrapper<ColonelsettlementOrder> wrapper, String timeField, LocalDateTime start, LocalDateTime end) {
        wrapper.ge(start != null, timeField, start)
                .le(end != null, timeField, end);
    }

    public void selectOrderListColumns(LambdaQueryWrapper<ColonelsettlementOrder> wrapper) {
        if (wrapper == null) {
            return;
        }
        wrapper.select(ColonelsettlementOrder.class, field -> !"extra_data".equals(field.getColumn()));
    }

    public void applyDashboardDiagnosisFilter(
            LambdaQueryWrapper<ColonelsettlementOrder> wrapper,
            String alias,
            String dashboardDiagnosis) {
        if (wrapper == null || !StringUtils.hasText(dashboardDiagnosis)) {
            return;
        }
        String prefix = StringUtils.hasText(alias) ? alias + "." : "colonelsettlement_order.";
        applyDiagnosisSql(wrapper, prefix, dashboardDiagnosis.trim());
    }

    public void applyDashboardDiagnosisFilter(
            QueryWrapper<ColonelsettlementOrder> wrapper,
            String alias,
            String dashboardDiagnosis) {
        if (wrapper == null || !StringUtils.hasText(dashboardDiagnosis)) {
            return;
        }
        String prefix = StringUtils.hasText(alias) ? alias + "." : "colonelsettlement_order.";
        applyDiagnosisSql(wrapper, prefix, dashboardDiagnosis.trim());
    }

    private void applyDiagnosisSql(LambdaQueryWrapper<ColonelsettlementOrder> wrapper, String prefix, String diagnosis) {
        String normalizedDiagnosis = dashboardService.normalizeDiagnosisCategory(diagnosis);
        if (!StringUtils.hasText(normalizedDiagnosis)) {
            return;
        }
        String safePrefix = sanitizeDiagnosisSqlPrefix(prefix);
        String categorySql = dashboardService.diagnosisCategoryCaseSql(
                safePrefix + "colonel_activity_id",
                safePrefix + "second_colonel_activity_id",
                safePrefix + "product_id",
                safePrefix + "create_time",
                safePrefix + "colonel_buyin_id",
                safePrefix + "attribution_status",
                safePrefix + "attribution_remark"
        );
        wrapper.apply("(" + categorySql + ") = {0}", normalizedDiagnosis);
    }

    private void applyDiagnosisSql(QueryWrapper<ColonelsettlementOrder> wrapper, String prefix, String diagnosis) {
        String normalizedDiagnosis = dashboardService.normalizeDiagnosisCategory(diagnosis);
        if (!StringUtils.hasText(normalizedDiagnosis)) {
            return;
        }
        String safePrefix = sanitizeDiagnosisSqlPrefix(prefix);
        String categorySql = dashboardService.diagnosisCategoryCaseSql(
                safePrefix + "colonel_activity_id",
                safePrefix + "second_colonel_activity_id",
                safePrefix + "product_id",
                safePrefix + "create_time",
                safePrefix + "colonel_buyin_id",
                safePrefix + "attribution_status",
                safePrefix + "attribution_remark"
        );
        wrapper.apply("(" + categorySql + ") = {0}", normalizedDiagnosis);
    }

    /**
     * 限制诊断分类 SQL 前缀仅允许两种白名单值（"colonelsettlement_order." 或 "fo."），
     * 其他输入会被回退到默认前缀，避免 SQL 注入。
     */
    public String sanitizeDiagnosisSqlPrefix(String prefix) {
        if (!StringUtils.hasText(prefix)) {
            return "colonelsettlement_order.";
        }
        String normalized = prefix.trim();
        if ("colonelsettlement_order.".equals(normalized) || "fo.".equals(normalized)) {
            return normalized;
        }
        return "colonelsettlement_order.";
    }

    public void applyDataScope(
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

    public void applyQueryDataScope(
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

    public void normalizeOrderRow(ColonelsettlementOrder order) {
        if (order == null) {
            return;
        }
        order.setUnattributedReason(order.getAttributionRemark());
    }

    // ============================================================
    // 私有：UUID 列表归一 / 字段读取 / 数字解析
    // ============================================================

    private static List<UUID> normalizeUuidList(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream().filter(Objects::nonNull).distinct().toList();
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

    private String asText(Object raw) {
        return raw == null ? null : String.valueOf(raw);
    }

    private long asLong(Object raw) {
        if (raw instanceof Number number) {
            return number.longValue();
        }
        if (raw == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(raw));
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    // ============================================================
    // DTO：跨方法结果传递（不依赖 controller 内部类）
    // ============================================================

    /**
     * 订单统计聚合结果。
     */
    public record OrderStatsResult(
            long totalOrders,
            long attributedOrders,
            long unattributedOrders,
            long partialOrders,
            long syncFailedOrders,
            LocalDateTime lastSyncTime,
            List<ReasonCount> unattributedReasons
    ) {
    }

    /**
     * 未归因原因计数（reason 编码 + 出现次数）。
     */
    public record ReasonCount(String reason, Long count) {
    }
}
