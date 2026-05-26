package com.colonel.saas.service;

import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.dto.performance.PerformanceBatchItemDTO;
import com.colonel.saas.dto.performance.PerformanceBatchResponse;
import com.colonel.saas.dto.performance.PerformanceDetailDTO;
import com.colonel.saas.dto.performance.PerformanceListItemDTO;
import com.colonel.saas.dto.performance.PerformanceListQuery;
import com.colonel.saas.dto.performance.PerformancePageResponse;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.entity.PerformanceRecord;
import com.colonel.saas.mapper.ColonelsettlementOrderMapper;
import com.colonel.saas.mapper.PerformanceRecordMapper;
import com.colonel.saas.service.performance.PerformanceAccessContext;
import com.colonel.saas.service.performance.PerformanceAccessScope;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class PerformanceQueryService {

    static final int BATCH_MAX = 200;
    static final long LIST_MAX_PAGE_SIZE = 100;
    public static final long EXPORT_MAX_ROWS = 5000;

    private static final String BASE_FROM = """
            FROM performance_records pr
            LEFT JOIN colonelsettlement_order co ON co.order_id = pr.order_id AND co.deleted = 0
            LEFT JOIN colonelsettlement_activity ca ON ca.activity_id = pr.activity_id
            """;

    private final PerformanceRecordMapper performanceRecordMapper;
    private final ColonelsettlementOrderMapper orderMapper;
    private final JdbcTemplate jdbcTemplate;

    public PerformanceQueryService(
            PerformanceRecordMapper performanceRecordMapper,
            ColonelsettlementOrderMapper orderMapper,
            JdbcTemplate jdbcTemplate) {
        this.performanceRecordMapper = performanceRecordMapper;
        this.orderMapper = orderMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    public PerformanceDetailDTO getPerformance(String orderId, PerformanceAccessContext context) {
        return getByOrderId(orderId, context);
    }

    public PerformanceBatchResponse batchGetPerformance(
            com.colonel.saas.dto.performance.PerformanceBatchRequest request,
            PerformanceAccessContext context) {
        return batchGet(request == null ? List.of() : request.getOrderIds(), context);
    }

    public PerformanceDetailDTO getByOrderId(String orderId, PerformanceAccessContext context) {
        if (!StringUtils.hasText(orderId)) {
            throw BusinessException.param("orderId 不能为空");
        }
        String normalized = orderId.trim();
        PerformanceRecord record = performanceRecordMapper.findByOrderId(normalized);
        if (!hasCurrentPerformance(record)) {
            throwPerformanceUnavailable(normalized);
        }
        if (!PerformanceAccessScope.canAccessRecord(record, context) && !canAccessRecordByScopedSql(normalized, context)) {
            throw BusinessException.forbidden("无权查看该订单业绩");
        }
        return loadDetailByOrderId(normalized);
    }

    public PerformanceBatchResponse batchGet(List<String> orderIds, PerformanceAccessContext context) {
        if (orderIds == null || orderIds.isEmpty()) {
            return emptyBatchResponse(List.of());
        }
        List<String> normalized = orderIds.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
        if (normalized.size() > BATCH_MAX) {
            throw BusinessException.param("单次最多查询 " + BATCH_MAX + " 个订单");
        }
        PerformanceBatchResponse response = new PerformanceBatchResponse();
        List<PerformanceBatchItemDTO> items = new ArrayList<>();
        Map<String, PerformanceDetailDTO> detailMap = loadDetails(normalized);
        for (String orderId : orderIds.stream().filter(StringUtils::hasText).map(String::trim).toList()) {
            PerformanceBatchItemDTO item = new PerformanceBatchItemDTO();
            item.setOrderId(orderId);
            PerformanceDetailDTO detail = detailMap.get(orderId);
            if (detail == null) {
                PerformanceRecord record = performanceRecordMapper.findByOrderId(orderId);
                if (!hasCurrentPerformance(record)) {
                    item.setFound(false);
                    item.setAuthorized(true);
                    item.setMessage("PERFORMANCE_NOT_CALCULATED");
                } else {
                    item.setFound(false);
                    item.setAuthorized(false);
                    item.setMessage("FORBIDDEN");
                }
            } else {
                PerformanceRecord record = performanceRecordMapper.findByOrderId(orderId);
                if (record != null && canAccessBatchRecord(orderId, record, context)) {
                    item.setFound(true);
                    item.setAuthorized(true);
                    item.setPerformance(detail);
                } else {
                    item.setFound(record != null);
                    item.setAuthorized(false);
                    item.setMessage("FORBIDDEN");
                }
            }
            items.add(item);
        }
        response.setItems(items);
        return response;
    }

    public PerformancePageResponse list(PerformanceListQuery query, PerformanceAccessContext context) {
        PerformanceListQuery safeQuery = query == null ? new PerformanceListQuery() : query;
        PerformanceAccessScope.assertFilterAllowed(
                safeQuery.getChannelId(),
                safeQuery.getRecruiterId(),
                context);

        long pageSize = normalizePageSize(safeQuery.getPageSize());
        long page = safeQuery.getPage() <= 0 ? 1 : safeQuery.getPage();
        long offset = (page - 1) * pageSize;

        List<Object> args = new ArrayList<>();
        StringBuilder where = buildFilterWhere(safeQuery, context, args);
        long total = count(where, args);

        String sortColumn = resolveSortColumn(safeQuery.getSortBy());
        String sortOrder = "asc".equalsIgnoreCase(safeQuery.getSortOrder()) ? "ASC" : "DESC";
        String sql = selectListColumns() + BASE_FROM + where
                + " ORDER BY " + sortColumn + " " + sortOrder
                + " LIMIT ? OFFSET ?";
        args.add(pageSize);
        args.add(offset);

        List<PerformanceListItemDTO> items = jdbcTemplate.query(sql, (rs, rowNum) -> mapListItem(rs), args.toArray());
        PerformancePageResponse response = new PerformancePageResponse();
        response.setPage(page);
        response.setPageSize(pageSize);
        response.setTotal(total);
        response.setItems(items);
        return response;
    }

    public PerformancePageResponse listPerformance(PerformanceListQuery query, PerformanceAccessContext context) {
        return list(query, context);
    }

    public List<PerformanceListItemDTO> listForExport(PerformanceListQuery query, PerformanceAccessContext context) {
        PerformanceListQuery safeQuery = query == null ? new PerformanceListQuery() : query;
        PerformanceAccessScope.assertFilterAllowed(
                safeQuery.getChannelId(),
                safeQuery.getRecruiterId(),
                context);
        List<Object> args = new ArrayList<>();
        StringBuilder where = buildFilterWhere(safeQuery, context, args);
        long total = count(where, args);
        if (total > EXPORT_MAX_ROWS) {
            throw BusinessException.param("导出数据量 " + total + " 超过上限 " + EXPORT_MAX_ROWS + "，请缩小筛选范围");
        }
        String sortColumn = resolveSortColumn(safeQuery.getSortBy());
        String sortOrder = "asc".equalsIgnoreCase(safeQuery.getSortOrder()) ? "ASC" : "DESC";
        String sql = selectListColumns() + BASE_FROM + where
                + " ORDER BY " + sortColumn + " " + sortOrder
                + " LIMIT " + EXPORT_MAX_ROWS;
        return jdbcTemplate.query(sql, (rs, rowNum) -> mapListItem(rs), args.toArray());
    }

    public List<PerformanceDetailDTO> listDetailsForExport(PerformanceListQuery query, PerformanceAccessContext context) {
        PerformanceListQuery exportQuery = query == null ? new PerformanceListQuery() : query;
        List<PerformanceListItemDTO> items = listForExport(exportQuery, context);
        if (items.isEmpty()) {
            return List.of();
        }
        List<String> orderIds = items.stream().map(PerformanceListItemDTO::getOrderId).toList();
        return orderIds.stream().map(this::loadDetailByOrderId).toList();
    }

    private PerformanceBatchResponse emptyBatchResponse(List<PerformanceBatchItemDTO> items) {
        PerformanceBatchResponse response = new PerformanceBatchResponse();
        response.setItems(items);
        return response;
    }

    private long normalizePageSize(long pageSize) {
        if (pageSize <= 0) {
            return 20;
        }
        return Math.min(pageSize, LIST_MAX_PAGE_SIZE);
    }

    private StringBuilder buildFilterWhere(
            PerformanceListQuery query,
            PerformanceAccessContext context,
            List<Object> args) {
        StringBuilder where = new StringBuilder(" WHERE pr.is_valid = TRUE ");
        PerformanceAccessScope.appendScopeCondition(where, args, context, "pr");

        if (StringUtils.hasText(query.getOrderId())) {
            where.append(" AND pr.order_id = ?");
            args.add(query.getOrderId().trim());
        }
        if (StringUtils.hasText(query.getProductId())) {
            where.append(" AND pr.product_id = ?");
            args.add(query.getProductId().trim());
        }
        if (StringUtils.hasText(query.getProductName())) {
            where.append(" AND COALESCE(co.product_name, co.product_title, '') ILIKE ?");
            args.add("%" + query.getProductName().trim() + "%");
        }
        if (query.getPartnerId() != null) {
            where.append(" AND pr.partner_id = ?");
            args.add(query.getPartnerId());
        }
        if (StringUtils.hasText(query.getPartnerName())) {
            where.append(" AND COALESCE(co.shop_name, '') ILIKE ?");
            args.add("%" + query.getPartnerName().trim() + "%");
        }
        if (StringUtils.hasText(query.getActivityId())) {
            where.append(" AND pr.activity_id = ?");
            args.add(query.getActivityId().trim());
        }
        if (query.getTalentId() != null) {
            where.append(" AND pr.talent_id = ?");
            args.add(query.getTalentId());
        }
        if (query.getChannelId() != null) {
            where.append(" AND pr.final_channel_user_id = ?");
            args.add(query.getChannelId());
        }
        if (query.getRecruiterId() != null) {
            where.append(" AND pr.final_recruiter_user_id = ?");
            args.add(query.getRecruiterId());
        }
        if (StringUtils.hasText(query.getOrderStatus())) {
            where.append(" AND pr.order_status = ?");
            args.add(toOrderStatusCode(query.getOrderStatus()));
        }
        appendTimeFilter(where, args, query.getTimeFilterType(), query.getTimeStart(), query.getTimeEnd());
        return where;
    }

    private void appendTimeFilter(
            StringBuilder where,
            List<Object> args,
            String timeFilterType,
            LocalDateTime timeStart,
            LocalDateTime timeEnd) {
        String column = resolveTimeFilterColumn(timeFilterType);
        if (timeStart != null) {
            where.append(" AND ").append(column).append(" >= ?");
            args.add(timeStart);
        }
        if (timeEnd != null) {
            where.append(" AND ").append(column).append(" < ?");
            args.add(timeEnd);
        }
        if ("settle".equalsIgnoreCase(timeFilterType) && (timeStart != null || timeEnd != null)) {
            where.append(" AND pr.settle_time IS NOT NULL");
        }
    }

    private String resolveTimeFilterColumn(String timeFilterType) {
        if (!StringUtils.hasText(timeFilterType)) {
            return "COALESCE(pr.order_create_time, co.create_time)";
        }
        return switch (timeFilterType.trim().toLowerCase(Locale.ROOT)) {
            case "settle" -> "pr.settle_time";
            case "calculate" -> "pr.calculated_at";
            default -> "COALESCE(pr.order_create_time, co.create_time)";
        };
    }

    private long count(StringBuilder where, List<Object> args) {
        String sql = "SELECT COUNT(1) " + BASE_FROM + where;
        Long count = jdbcTemplate.queryForObject(sql, Long.class, args.toArray());
        return count == null ? 0L : count;
    }

    private String selectListColumns() {
        return """
                SELECT pr.order_id,
                       pr.product_id,
                       COALESCE(co.product_name, co.product_title) AS product_name,
                       pr.partner_id,
                       co.shop_name AS partner_name,
                       pr.talent_id::text AS talent_id,
                       COALESCE(co.talent_name, '') AS talent_name,
                       pr.final_channel_user_id::text AS final_channel_id,
                       co.channel_user_name AS final_channel_name,
                       pr.final_recruiter_user_id::text AS final_recruiter_id,
                       co.colonel_user_name AS final_recruiter_name,
                       pr.pay_amount,
                       pr.settle_amount,
                       pr.estimate_service_profit,
                       pr.effective_service_profit,
                       pr.estimate_recruiter_commission,
                       pr.effective_recruiter_commission,
                       pr.estimate_channel_commission,
                       pr.effective_channel_commission,
                       pr.estimate_gross_profit,
                       pr.effective_gross_profit,
                       pr.order_status,
                       COALESCE(pr.order_create_time, co.create_time) AS pay_time,
                       pr.settle_time,
                       pr.calculated_at
                """;
    }

    private PerformanceListItemDTO mapListItem(java.sql.ResultSet rs) throws java.sql.SQLException {
        PerformanceListItemDTO item = new PerformanceListItemDTO();
        item.setOrderId(rs.getString("order_id"));
        item.setProductId(rs.getString("product_id"));
        item.setProductName(rs.getString("product_name"));
        item.setPartnerId(rs.getObject("partner_id") == null ? null : String.valueOf(rs.getObject("partner_id")));
        item.setPartnerName(rs.getString("partner_name"));
        item.setTalentId(rs.getString("talent_id"));
        item.setTalentName(rs.getString("talent_name"));
        item.setFinalChannelId(rs.getString("final_channel_id"));
        item.setFinalChannelName(rs.getString("final_channel_name"));
        item.setFinalRecruiterId(rs.getString("final_recruiter_id"));
        item.setFinalRecruiterName(rs.getString("final_recruiter_name"));
        item.setPayAmount(rs.getLong("pay_amount"));
        item.setSettleAmount(rs.getLong("settle_amount"));
        item.setEstimateServiceProfit(rs.getLong("estimate_service_profit"));
        item.setEffectiveServiceProfit(rs.getLong("effective_service_profit"));
        item.setEstimateRecruiterCommission(rs.getLong("estimate_recruiter_commission"));
        item.setEffectiveRecruiterCommission(rs.getLong("effective_recruiter_commission"));
        item.setEstimateChannelCommission(rs.getLong("estimate_channel_commission"));
        item.setEffectiveChannelCommission(rs.getLong("effective_channel_commission"));
        item.setEstimateGrossProfit(rs.getLong("estimate_gross_profit"));
        item.setEffectiveGrossProfit(rs.getLong("effective_gross_profit"));
        item.setOrderStatus(fromOrderStatusCode(rs.getObject("order_status")));
        item.setPayTime(readDateTime(rs, "pay_time"));
        item.setSettleTime(readDateTime(rs, "settle_time"));
        item.setCalculatedAt(readDateTime(rs, "calculated_at"));
        return item;
    }

    private Map<String, PerformanceDetailDTO> loadDetails(List<String> orderIds) {
        if (orderIds.isEmpty()) {
            return Map.of();
        }
        Map<String, PerformanceDetailDTO> mapped = new LinkedHashMap<>();
        for (String orderId : orderIds) {
            PerformanceRecord record = performanceRecordMapper.findByOrderId(orderId);
            if (hasCurrentPerformance(record)) {
                mapped.put(orderId, loadDetailByOrderId(orderId));
            }
        }
        return mapped;
    }

    private boolean canAccessRecordByScopedSql(String orderId, PerformanceAccessContext context) {
        if (context == null || !StringUtils.hasText(orderId)) {
            return false;
        }
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE pr.order_id = ? AND pr.is_valid = TRUE ");
        args.add(orderId);
        PerformanceAccessScope.appendScopeCondition(where, args, context, "pr");
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM performance_records pr" + where,
                Long.class,
                args.toArray());
        return count != null && count > 0;
    }

    private boolean canAccessBatchRecord(String orderId, PerformanceRecord record, PerformanceAccessContext context) {
        return (hasCurrentPerformance(record) && PerformanceAccessScope.canAccessRecord(record, context))
                || canAccessRecordByScopedSql(orderId, context);
    }

    private boolean hasCurrentPerformance(PerformanceRecord record) {
        return record != null && Boolean.TRUE.equals(record.getValid());
    }

    private void throwPerformanceUnavailable(String orderId) {
        ColonelsettlementOrder order = orderMapper.selectOne(new LambdaQueryWrapper<ColonelsettlementOrder>()
                .eq(ColonelsettlementOrder::getOrderId, orderId)
                .eq(ColonelsettlementOrder::getDeleted, 0)
                .last("LIMIT 1"));
        if (order == null) {
            throw BusinessException.notFound("订单不存在: " + orderId);
        }
        throw BusinessException.stateInvalid("PERFORMANCE_NOT_CALCULATED");
    }

    private PerformanceDetailDTO loadDetailByOrderId(String orderId) {
        String sql = """
                SELECT pr.*,
                       COALESCE(co.product_name, co.product_title) AS product_name,
                       co.shop_name AS partner_name,
                       COALESCE(co.talent_name, '') AS talent_name,
                       ca.name AS activity_name,
                       dc.username AS default_channel_name,
                       dr.username AS default_recruiter_name,
                       fc.username AS final_channel_name_resolved,
                       fr.username AS final_recruiter_name_resolved,
                       COALESCE(pr.order_create_time, co.create_time) AS pay_time
                """ + BASE_FROM + """
                 LEFT JOIN sys_user dc ON dc.id = pr.default_channel_user_id
                 LEFT JOIN sys_user dr ON dr.id = pr.default_recruiter_user_id
                 LEFT JOIN sys_user fc ON fc.id = pr.final_channel_user_id
                 LEFT JOIN sys_user fr ON fr.id = pr.final_recruiter_user_id
                 WHERE pr.order_id = ?
                   AND pr.is_valid = TRUE
                 LIMIT 1
                """;
        List<PerformanceDetailDTO> rows = jdbcTemplate.query(sql, (rs, rowNum) -> {
            PerformanceDetailDTO dto = new PerformanceDetailDTO();
            dto.setOrderId(rs.getString("order_id"));
            dto.setProductId(rs.getString("product_id"));
            dto.setProductName(rs.getString("product_name"));
            dto.setActivityId(rs.getString("activity_id"));
            dto.setActivityName(rs.getString("activity_name"));
            dto.setPartnerId(rs.getObject("partner_id") == null ? null : String.valueOf(rs.getObject("partner_id")));
            dto.setPartnerName(rs.getString("partner_name"));
            dto.setTalentId(rs.getString("talent_id"));
            dto.setTalentName(rs.getString("talent_name"));
            dto.setDefaultChannelId(readUuid(rs, "default_channel_user_id"));
            dto.setDefaultRecruiterId(readUuid(rs, "default_recruiter_user_id"));
            dto.setFinalChannelId(readUuid(rs, "final_channel_user_id"));
            dto.setFinalRecruiterId(readUuid(rs, "final_recruiter_user_id"));
            dto.setDefaultChannelName(firstNonBlank(rs.getString("default_channel_name")));
            dto.setDefaultRecruiterName(firstNonBlank(rs.getString("default_recruiter_name")));
            dto.setFinalChannelName(firstNonBlank(rs.getString("final_channel_name_resolved")));
            dto.setFinalRecruiterName(firstNonBlank(rs.getString("final_recruiter_name_resolved")));
            dto.setChannelAttributionType(rs.getString("channel_attribution"));
            dto.setRecruiterAttributionType(rs.getString("recruiter_attribution"));
            dto.setPayAmount(rs.getLong("pay_amount"));
            dto.setSettleAmount(rs.getLong("settle_amount"));
            dto.setEstimateServiceFee(rs.getLong("estimate_service_fee"));
            dto.setEffectiveServiceFee(rs.getLong("effective_service_fee"));
            dto.setEstimateTechServiceFee(rs.getLong("estimate_tech_service_fee"));
            dto.setEffectiveTechServiceFee(rs.getLong("effective_tech_service_fee"));
            dto.setEstimateServiceProfit(rs.getLong("estimate_service_profit"));
            dto.setEffectiveServiceProfit(rs.getLong("effective_service_profit"));
            dto.setEstimateRecruiterCommission(rs.getLong("estimate_recruiter_commission"));
            dto.setEffectiveRecruiterCommission(rs.getLong("effective_recruiter_commission"));
            dto.setEstimateChannelCommission(rs.getLong("estimate_channel_commission"));
            dto.setEffectiveChannelCommission(rs.getLong("effective_channel_commission"));
            dto.setEstimateGrossProfit(rs.getLong("estimate_gross_profit"));
            dto.setEffectiveGrossProfit(rs.getLong("effective_gross_profit"));
            dto.setRecruiterCommissionRate(rs.getBigDecimal("recruiter_commission_rate"));
            dto.setChannelCommissionRate(rs.getBigDecimal("channel_commission_rate"));
            dto.setOrderStatus(fromOrderStatusCode(rs.getObject("order_status")));
            dto.setPayTime(readDateTime(rs, "pay_time"));
            dto.setSettleTime(readDateTime(rs, "settle_time"));
            dto.setCalculatedAt(readDateTime(rs, "calculated_at"));
            dto.setValid(rs.getBoolean("is_valid"));
            dto.setReversed(rs.getBoolean("is_reversed"));
            return dto;
        }, orderId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private String resolveSortColumn(String sortBy) {
        if (!StringUtils.hasText(sortBy)) {
            return "COALESCE(pr.order_create_time, co.create_time)";
        }
        return switch (sortBy.trim().toLowerCase(Locale.ROOT)) {
            case "settletime", "settle_time" -> "pr.settle_time";
            case "calculatedat", "calculated_at" -> "pr.calculated_at";
            case "payamount", "pay_amount" -> "pr.pay_amount";
            case "settleamount", "settle_amount" -> "pr.settle_amount";
            default -> "COALESCE(pr.order_create_time, co.create_time)";
        };
    }

    private Integer toOrderStatusCode(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        return switch (status.trim().toUpperCase(Locale.ROOT)) {
            case "ORDERED" -> 1;
            case "SHIPPED" -> 2;
            case "FINISHED" -> 3;
            case "CANCELLED" -> 4;
            default -> throw BusinessException.param("非法订单状态: " + status);
        };
    }

    private String fromOrderStatusCode(Object statusCode) {
        if (statusCode == null) {
            return "ORDERED";
        }
        int code = statusCode instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(statusCode));
        return switch (code) {
            case 1 -> "ORDERED";
            case 2 -> "SHIPPED";
            case 3 -> "FINISHED";
            case 4 -> "CANCELLED";
            default -> "ORDERED";
        };
    }

    private LocalDateTime readDateTime(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        java.sql.Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private String readUuid(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        Object value = rs.getObject(column);
        return value == null ? null : String.valueOf(value);
    }

    private String firstNonBlank(String value) {
        return StringUtils.hasText(value) ? value : null;
    }
}
