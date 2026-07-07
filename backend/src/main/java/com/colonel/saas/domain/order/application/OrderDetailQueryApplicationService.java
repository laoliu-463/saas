package com.colonel.saas.domain.order.application;

import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.common.exception.ForbiddenException;
import com.colonel.saas.config.DddRefactorProperties;
import com.colonel.saas.domain.user.policy.DataScopeResolver;
import com.colonel.saas.dto.order.OrderDetailResponse;
import com.colonel.saas.service.AttributionService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 订单查询 Application Service（DDD-ORDER-006 Slice 1）。
 *
 * <p>从 {@code service.OrderQueryService} 整体迁移过来的查询业务：
 * <ul>
 *   <li>{@link #getOrderDetail} - 订单详情聚合（多表 JOIN 装配 + 权限校验）</li>
 * </ul>
 *
 * <p>读模型独立入口，与 {@link OrderSyncApplicationService}（写模型入口）并列。
 * 本类承接 OrderQueryService 的所有 private helper（SQL 装配 / 类型转换 / 状态标签）。</p>
 *
 * <p>依赖：
 * <ul>
 *   <li>{@link JdbcTemplate} —— 多表 JOIN 原始 SQL</li>
 *   <li>{@link DataScopeResolver} —— 数据范围策略（灰度切换）</li>
 *   <li>{@link DddRefactorProperties} —— DataScopePolicy 开关</li>
 * </ul>
 */
@Service
public class OrderDetailQueryApplicationService {

    private final JdbcTemplate jdbcTemplate;
    private final DataScopeResolver dataScopeResolver;
    private final DddRefactorProperties dddRefactorProperties;

    public OrderDetailQueryApplicationService(
            JdbcTemplate jdbcTemplate,
            DataScopeResolver dataScopeResolver,
            DddRefactorProperties dddRefactorProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataScopeResolver = dataScopeResolver;
        this.dddRefactorProperties = dddRefactorProperties;
    }

    /**
     * 订单详情查询。
     */
    public OrderDetailResponse getOrderDetail(
            String orderId,
            UUID currentUserId,
            UUID currentDeptId,
            DataScope dataScope) {
        Map<String, Object> row = findOrderDetailRow(orderId);
        if (row == null || row.isEmpty()) {
            throw BusinessException.notFound("订单不存在");
        }
        assertCanAccess(row, currentUserId, currentDeptId, dataScope);

        String attributionStatus = asText(row.get("attribution_status"));
        String attributionRemark = asText(row.get("attribution_remark"));
        String pickSource = firstNonBlank(
                asText(row.get("pick_source")),
                asText(row.get("mapping_pick_source")),
                asText(row.get("promotion_pick_source"))
        );
        String activityId = firstNonBlank(
                asText(row.get("activity_id")),
                asText(row.get("mapping_activity_id"))
        );
        String colonelUserId = uuidText(firstNonNull(row.get("colonel_user_id"), row.get("state_colonel_user_id")));
        String colonelName = firstNonBlank(
                asText(row.get("colonel_user_name")),
                asText(row.get("colonel_real_name"))
        );
        String channelUserId = uuidText(firstNonNull(
                row.get("channel_user_id"),
                row.get("mapping_channel_user_id"),
                row.get("promotion_channel_user_id")
        ));
        String channelName = firstNonBlank(
                asText(row.get("channel_user_name")),
                asText(row.get("mapping_channel_name")),
                asText(row.get("promotion_channel_name"))
        );
        String authorId = asText(row.get("author_id"));
        String talentUid = firstNonBlank(
                asText(row.get("mapping_talent_uid")),
                asText(row.get("promotion_talent_uid")),
                asText(row.get("extra_talent_uid")),
                authorId,
                asText(row.get("talent_name"))
        );
        String talentName = firstNonBlank(
                asText(row.get("mapping_talent_name")),
                asText(row.get("promotion_talent_name")),
                asText(row.get("talent_nickname")),
                asText(row.get("crawler_talent_name")),
                asText(row.get("talent_name"))
        );

        OrderDetailResponse response = new OrderDetailResponse();
        response.setOrderId(asText(row.get("order_id")));
        response.setOrderStatus(asInteger(row.get("order_status")));
        response.setOrderStatusText(orderStatusLabel(response.getOrderStatus()));
        response.setAttributionStatus(attributionStatus);
        response.setAttributionStatusText(attributionStatusLabel(attributionStatus));
        response.setAttributionRemark(attributionRemark);
        response.setPickSource(pickSource);

        OrderDetailResponse.ProductInfo product = new OrderDetailResponse.ProductInfo();
        product.setProductId(asText(row.get("product_id")));
        product.setProductName(firstNonBlank(
                asText(row.get("product_title")),
                asText(row.get("product_name")),
                asText(row.get("snapshot_title"))
        ));
        product.setActivityId(activityId);
        product.setActivityName(resolveActivityName(activityId));
        product.setColonelUserId(colonelUserId);
        product.setColonelName(colonelName);
        response.setProduct(product);

        OrderDetailResponse.ChannelInfo channel = new OrderDetailResponse.ChannelInfo();
        channel.setChannelUserId(channelUserId);
        channel.setChannelName(channelName);
        response.setChannel(channel);

        OrderDetailResponse.TalentInfo talent = new OrderDetailResponse.TalentInfo();
        talent.setTalentId(firstNonBlank(
                asText(row.get("mapping_talent_uid")),
                asText(row.get("promotion_talent_uid")),
                asText(row.get("extra_talent_uid")),
                authorId
        ));
        talent.setTalentUid(talentUid);
        talent.setAuthorId(authorId);
        talent.setTalentName(talentName);
        response.setTalent(talent);

        OrderDetailResponse.AmountInfo amount = new OrderDetailResponse.AmountInfo();
        amount.setOrderAmount(asLong(row.get("order_amount")));
        amount.setServiceFee(asLong(row.get("effective_service_fee")));
        amount.setPayAmount(asLong(row.get("order_amount")));
        amount.setSettleAmount(asLong(row.get("settle_amount")));
        amount.setEstimateServiceFee(asLong(row.get("estimate_service_fee")));
        amount.setEffectiveServiceFee(asLong(row.get("effective_service_fee")));
        amount.setEstimateTechServiceFee(asLong(row.get("estimate_tech_service_fee")));
        amount.setEffectiveTechServiceFee(asLong(row.get("effective_tech_service_fee")));
        response.setAmount(amount);

        OrderDetailResponse.PromotionInfo promotion = new OrderDetailResponse.PromotionInfo();
        promotion.setPickSource(pickSource);
        promotion.setMappingId(uuidText(row.get("mapping_id")));
        promotion.setPromotionUrl(firstNonBlank(
                asText(row.get("promotion_url")),
                asText(row.get("mapping_promotion_url"))
        ));
        promotion.setCreatedAt(firstNonNullTime(
                toDateTime(row.get("promotion_created_at")),
                toDateTime(row.get("mapping_created_at"))
        ));
        promotion.setMatched(StringUtils.hasText(promotion.getPromotionUrl()) || StringUtils.hasText(promotion.getMappingId()));
        response.setPromotion(promotion);

        OrderDetailResponse.SampleInfo sample = findSampleInfo(
                asText(row.get("product_id")),
                talentUid,
                authorId,
                uuidValue(channelUserId)
        );
        response.setSample(sample);

        OrderDetailResponse.DiagnosisInfo diagnosis = new OrderDetailResponse.DiagnosisInfo();
        if (AttributionService.STATUS_UNATTRIBUTED.equals(attributionStatus)) {
            diagnosis.setReasonCode(attributionRemark);
            diagnosis.setReasonText(unattributedReasonText(attributionRemark));
            diagnosis.setSuggestion(unattributedReasonSuggestion(attributionRemark));
        }
        response.setDiagnosis(diagnosis);

        OrderDetailResponse.TimeInfo time = new OrderDetailResponse.TimeInfo();
        time.setCreateTime(toDateTime(row.get("create_time")));
        time.setSettleTime(toDateTime(row.get("settle_time")));
        time.setSyncTime(toDateTime(row.get("update_time")));
        response.setTime(time);

        return response;
    }

    void assertCanAccess(Map<String, Object> row, UUID currentUserId, UUID currentDeptId, DataScope dataScope) {
        if (row == null) {
            return;
        }
        if (!dddRefactorProperties.getDataScopePolicy().isEnabled()) {
            assertCanAccessLegacy(row, currentUserId, currentDeptId, dataScope);
            return;
        }
        assertCanAccessWithPolicy(row, currentUserId, currentDeptId, dataScope);
    }

    void assertCanAccessLegacy(Map<String, Object> row, UUID currentUserId, UUID currentDeptId, DataScope dataScope) {
        if (dataScope == null || dataScope == DataScope.ALL) {
            return;
        }
        UUID orderUserId = uuidValue(asText(row.get("order_user_id")));
        UUID orderDeptId = uuidValue(asText(row.get("order_dept_id")));
        if (dataScope == DataScope.PERSONAL) {
            if (currentUserId == null || !currentUserId.equals(orderUserId)) {
                throw new ForbiddenException("无权查看该订单详情");
            }
            return;
        }
        if (currentDeptId == null || !currentDeptId.equals(orderDeptId)) {
            throw new ForbiddenException("无权查看该订单详情");
        }
    }

    void assertCanAccessWithPolicy(Map<String, Object> row, UUID currentUserId, UUID currentDeptId, DataScope dataScope) {
        DataScopeResolver.ResolvedDataScope resolved =
                dataScopeResolver.resolve(currentUserId, currentDeptId, dataScope);
        if (!resolved.contextSatisfied()) {
            throw new ForbiddenException("无权查看该订单详情");
        }

        if (resolved.noFilter()) {
            return;
        }

        UUID orderUserId = uuidValue(asText(row.get("order_user_id")));
        UUID orderDeptId = uuidValue(asText(row.get("order_dept_id")));
        if (resolved.filtersUser() && !currentUserId.equals(orderUserId)) {
            throw new ForbiddenException("无权查看该订单详情");
        }
        if (resolved.filtersDept() && !currentDeptId.equals(orderDeptId)) {
            throw new ForbiddenException("无权查看该订单详情");
        }
    }

    Map<String, Object> findOrderDetailRow(String orderId) {
        String sql = """
                SELECT
                    co.order_id,
                    co.order_status,
                    co.attribution_status,
                    co.attribution_remark,
                    co.pick_source,
                    co.product_id,
                    co.product_name,
                    co.product_title,
                    co.colonel_activity_id AS activity_id,
                    co.user_id AS order_user_id,
                    co.dept_id AS order_dept_id,
                    co.channel_user_id,
                    co.channel_user_name,
                    co.colonel_user_id,
                    co.colonel_user_name,
                    co.order_amount,
                    co.settle_amount,
                    co.estimate_service_fee,
                    co.effective_service_fee,
                    co.estimate_tech_service_fee,
                    co.effective_tech_service_fee,
                    co.settle_colonel_commission,
                    co.settle_time,
                    co.create_time,
                    co.update_time,
                    co.talent_name,
                    co.extra_data ->> 'talent_uid' AS extra_talent_uid,
                    co.extra_data ->> 'author_id' AS author_id,
                    psm.id AS mapping_id,
                    psm.pick_source AS mapping_pick_source,
                    psm.converted_url AS mapping_promotion_url,
                    psm.create_time AS mapping_created_at,
                    psm.activity_id AS mapping_activity_id,
                    psm.user_id AS mapping_channel_user_id,
                    psm.channel_user_name AS mapping_channel_name,
                    psm.talent_id AS mapping_talent_uid,
                    psm.talent_name AS mapping_talent_name,
                    pl.pick_source AS promotion_pick_source,
                    pl.promotion_url,
                    pl.created_at AS promotion_created_at,
                    pl.channel_user_id AS promotion_channel_user_id,
                    pl.channel_user_name AS promotion_channel_name,
                    pl.talent_id AS promotion_talent_uid,
                    pl.talent_name AS promotion_talent_name,
                    pos.assignee_id AS state_colonel_user_id,
                    su.real_name AS colonel_real_name,
                    ps.title AS snapshot_title,
                    t.nickname AS talent_nickname,
                    cti.nickname AS crawler_talent_name
                FROM colonelsettlement_order co
                LEFT JOIN pick_source_mapping psm
                    ON psm.pick_source = co.pick_source
                   AND psm.deleted = 0
                LEFT JOIN promotion_link pl
                    ON (
                        (co.promotion_link_id IS NOT NULL AND pl.id = co.promotion_link_id)
                        OR (psm.promotion_link_id IS NOT NULL AND pl.id = psm.promotion_link_id)
                        OR (co.pick_source IS NOT NULL AND pl.pick_source = co.pick_source)
                    )
                   AND pl.deleted = 0
                LEFT JOIN product_operation_state pos
                    ON pos.activity_id = co.colonel_activity_id
                   AND pos.product_id = co.product_id
                   AND pos.deleted = 0
                LEFT JOIN sys_user su
                    ON su.id = COALESCE(co.colonel_user_id, pos.assignee_id)
                   AND su.deleted = 0
                LEFT JOIN product_snapshot ps
                    ON ps.activity_id = co.colonel_activity_id
                   AND ps.product_id = co.product_id
                   AND ps.deleted = 0
                LEFT JOIN talent t
                    ON t.douyin_uid = COALESCE(psm.talent_id, pl.talent_id, co.extra_data ->> 'talent_uid', co.extra_data ->> 'author_id', co.talent_name)
                   AND t.deleted = 0
                LEFT JOIN crawler_talent_info cti
                    ON cti.talent_id = COALESCE(psm.talent_id, pl.talent_id, co.extra_data ->> 'talent_uid', co.extra_data ->> 'author_id', co.talent_name)
                WHERE co.order_id = ?
                  AND co.deleted = 0
                ORDER BY co.create_time DESC
                LIMIT 1
                """;
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, orderId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    OrderDetailResponse.SampleInfo findSampleInfo(
            String sourceProductId,
            String talentUid,
            String authorId,
            UUID channelUserId) {
        OrderDetailResponse.SampleInfo sample = new OrderDetailResponse.SampleInfo();
        sample.setMatched(false);
        if (!StringUtils.hasText(sourceProductId)) {
            return sample;
        }

        String resolvedTalentUid = firstNonBlank(talentUid, authorId);
        Map<String, Object> row = resolvedTalentUid != null
                ? findSampleByProductAndTalent(sourceProductId, resolvedTalentUid, channelUserId)
                : findSampleByProduct(sourceProductId, channelUserId);
        if (row == null || row.isEmpty()) {
            return sample;
        }

        String status = sampleStatusApi(asInteger(row.get("status")));
        sample.setMatched(true);
        sample.setSampleRequestId(firstNonBlank(
                asText(row.get("request_no")),
                uuidText(row.get("id"))
        ));
        sample.setSampleStatus(status);
        sample.setSampleStatusText(sampleStatusText(status));
        sample.setCompletedByOrderRule("FINISHED".equals(status));
        return sample;
    }

    Map<String, Object> findSampleByProductAndTalent(String sourceProductId, String talentUid, UUID channelUserId) {
        StringBuilder sql = new StringBuilder("""
                SELECT sr.id, sr.request_no, sr.status
                FROM sample_request sr
                JOIN product p ON p.id = sr.product_id
                WHERE sr.deleted = 0
                  AND p.deleted = 0
                  AND p.product_id = ?
                  AND sr.talent_uid = ?
                ORDER BY sr.create_time DESC
                LIMIT 1
                """);
        if (channelUserId != null) {
            sql.insert(sql.indexOf("ORDER BY"), "  AND sr.channel_user_id = ?\n");
        }
        List<Map<String, Object>> rows = channelUserId == null
                ? jdbcTemplate.queryForList(sql.toString(), sourceProductId, talentUid)
                : jdbcTemplate.queryForList(sql.toString(), sourceProductId, talentUid, channelUserId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    Map<String, Object> findSampleByProduct(String sourceProductId, UUID channelUserId) {
        StringBuilder sql = new StringBuilder("""
                SELECT sr.id, sr.request_no, sr.status
                FROM sample_request sr
                JOIN product p ON p.id = sr.product_id
                WHERE sr.deleted = 0
                  AND p.deleted = 0
                  AND p.product_id = ?
                ORDER BY sr.create_time DESC
                LIMIT 1
                """);
        if (channelUserId != null) {
            sql.insert(sql.indexOf("ORDER BY"), "  AND sr.channel_user_id = ?\n");
        }
        List<Map<String, Object>> rows = channelUserId == null
                ? jdbcTemplate.queryForList(sql.toString(), sourceProductId)
                : jdbcTemplate.queryForList(sql.toString(), sourceProductId, channelUserId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    String orderStatusLabel(Integer value) {
        if (value == null) {
            return "-";
        }
        return switch (value) {
            case 1 -> "已下单";
            case 2 -> "已发货";
            case 3 -> "已完成";
            case 4 -> "已取消";
            default -> "状态 " + value;
        };
    }

    String attributionStatusLabel(String value) {
        if (!StringUtils.hasText(value)) {
            return "-";
        }
        return switch (value) {
            case AttributionService.STATUS_ATTRIBUTED -> "已确认业绩";
            case AttributionService.STATUS_UNATTRIBUTED -> "待排查订单";
            case "PARTIAL" -> "部分归因";
            case "FAILED" -> "同步/归因失败";
            default -> value;
        };
    }

    String unattributedReasonText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return switch (value) {
            case AttributionService.REASON_NO_PICK_SOURCE, "订单未携带推广参数" -> "订单未携带推广参数";
            case AttributionService.REASON_MAPPING_NOT_FOUND, "pick_source 未匹配到有效归因映射" -> "未找到对应推广链接";
            case AttributionService.REASON_COLONEL_MAPPING_NOT_FOUND -> "原生团长订单未找到归因映射";
            case AttributionService.REASON_COLONEL_MAPPING_AMBIGUOUS -> "原生团长订单命中多条归因映射";
            case AttributionService.REASON_TALENT_CLAIM_OWNER_CONFLICT -> "归因负责人和达人认领人不一致";
            case AttributionService.REASON_PRODUCT_NOT_FOUND -> "未匹配到本地商品库";
            case AttributionService.REASON_ACTIVITY_NOT_FOUND -> "商品未关联活动";
            case AttributionService.REASON_CHANNEL_NOT_FOUND -> "未匹配到渠道负责人";
            case AttributionService.REASON_SYNC_FAILED, "订单同步失败" -> "订单同步失败";
            default -> value;
        };
    }

    String unattributedReasonSuggestion(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return switch (value) {
            case AttributionService.REASON_NO_PICK_SOURCE, "订单未携带推广参数" -> "请确认达人是否使用系统生成的推广链接。";
            case AttributionService.REASON_MAPPING_NOT_FOUND, "pick_source 未匹配到有效归因映射" ->
                    "请检查该 pick_source 是否由系统转链生成，或是否已过期/未落库。";
            case AttributionService.REASON_COLONEL_MAPPING_NOT_FOUND ->
                    "请确认该原生团长订单对应的活动、商品和推广映射是否已落库。";
            case AttributionService.REASON_COLONEL_MAPPING_AMBIGUOUS ->
                    "请排查同一活动商品是否存在多条渠道映射，避免原生团长订单串单。";
            case AttributionService.REASON_TALENT_CLAIM_OWNER_CONFLICT ->
                    "请核对该达人当前有效认领记录和推广映射负责人，必要时重新认领或重建转链映射。";
            case AttributionService.REASON_PRODUCT_NOT_FOUND -> "请检查商品主链路是否已同步入库。";
            case AttributionService.REASON_ACTIVITY_NOT_FOUND -> "请检查该商品是否已绑定活动并完成状态落库。";
            case AttributionService.REASON_CHANNEL_NOT_FOUND -> "请检查渠道负责人是否已完成分配。";
            case AttributionService.REASON_SYNC_FAILED, "订单同步失败" -> "请查看订单同步日志和第三方回流结果。";
            default -> "请检查该订单对应的推广链路、商品归属和渠道分配。";
        };
    }

    String sampleStatusApi(Integer status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case 1 -> "PENDING_AUDIT";
            case 2 -> "PENDING_SHIP";
            case 3, 4 -> "SHIPPED";
            case 5 -> "PENDING_TASK";
            case 6 -> "FINISHED";
            case 7 -> "REJECTED";
            case 8 -> "CLOSED";
            default -> String.valueOf(status);
        };
    }

    String sampleStatusText(String status) {
        if (!StringUtils.hasText(status)) {
            return "-";
        }
        return switch (status) {
            case "PENDING_AUDIT" -> "待审核";
            case "PENDING_SHIP" -> "待发货";
            case "SHIPPED" -> "快递中";
            case "PENDING_TASK" -> "待交作业";
            case "FINISHED" -> "已完成";
            case "REJECTED" -> "已拒绝";
            case "CLOSED" -> "已关闭";
            default -> status;
        };
    }

    String resolveActivityName(String activityId) {
        if (!StringUtils.hasText(activityId)) {
            return null;
        }
        if (activityId.startsWith("MOCK_ACTIVITY_")) {
            return "主链路演示活动-" + activityId.substring("MOCK_ACTIVITY_".length()).replace('_', ' ');
        }
        return activityId;
    }

    String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    Object firstNonNull(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    LocalDateTime firstNonNullTime(LocalDateTime... values) {
        if (values == null) {
            return null;
        }
        for (LocalDateTime value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    String asText(Object raw) {
        return raw == null ? null : String.valueOf(raw);
    }

    Integer asInteger(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(raw));
        } catch (Exception ex) {
            return null;
        }
    }

    Long asLong(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(raw));
        } catch (Exception ex) {
            return null;
        }
    }

    LocalDateTime toDateTime(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof LocalDateTime time) {
            return time;
        }
        if (raw instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        return null;
    }

    String uuidText(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof UUID uuid) {
            return uuid.toString();
        }
        String text = String.valueOf(raw).trim();
        return text.isEmpty() ? null : text;
    }

    UUID uuidValue(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (Exception ex) {
            return null;
        }
    }
}
