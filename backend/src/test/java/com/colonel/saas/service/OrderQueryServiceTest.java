package com.colonel.saas.service;

import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.common.exception.ForbiddenException;
import com.colonel.saas.dto.order.OrderDetailResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderQueryServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private OrderQueryService service;

    @BeforeEach
    void setUp() {
        service = new OrderQueryService(jdbcTemplate);
    }

    @Test
    void getOrderDetail_shouldBuildAttributedDetail() {
        UUID channelUserId = UUID.randomUUID();
        UUID colonelUserId = UUID.randomUUID();
        when(jdbcTemplate.queryForList(anyString(), eq("mock-order-1")))
                .thenReturn(List.of(Map.ofEntries(
                        Map.entry("order_id", "mock-order-1"),
                        Map.entry("order_status", 1),
                        Map.entry("attribution_status", "ATTRIBUTED"),
                        Map.entry("attribution_remark", "ATTRIBUTED"),
                        Map.entry("pick_source", "MOCKPS01"),
                        Map.entry("product_id", "10901825"),
                        Map.entry("product_name", "主链路演示商品-已归因"),
                        Map.entry("activity_id", "MOCK_ACTIVITY_A"),
                        Map.entry("channel_user_id", channelUserId),
                        Map.entry("channel_user_name", "渠道A-华东区域"),
                        Map.entry("colonel_user_id", colonelUserId),
                        Map.entry("colonel_user_name", "招商A-美妆组"),
                        Map.entry("order_amount", 19900L),
                        Map.entry("settle_amount", 18800L),
                        Map.entry("estimate_service_fee", 2600L),
                        Map.entry("effective_service_fee", 2400L),
                        Map.entry("estimate_tech_service_fee", 260L),
                        Map.entry("effective_tech_service_fee", 240L),
                        Map.entry("settle_colonel_commission", 2600L),
                        Map.entry("create_time", Timestamp.valueOf(LocalDateTime.of(2026, 4, 27, 12, 0, 0))),
                        Map.entry("update_time", Timestamp.valueOf(LocalDateTime.of(2026, 4, 27, 12, 1, 0))),
                        Map.entry("mapping_id", UUID.randomUUID()),
                        Map.entry("mapping_promotion_url", "https://mock.douyin.local/xxx?pick_source=MOCKPS01"),
                        Map.entry("mapping_created_at", Timestamp.valueOf(LocalDateTime.of(2026, 4, 27, 11, 50, 0))),
                        Map.entry("mapping_talent_uid", "MOCK_TALENT_001"),
                        Map.entry("mapping_talent_name", "达人A-已产出合作")
                )));
        when(jdbcTemplate.queryForList(anyString(), eq("10901825"), eq("MOCK_TALENT_001"), eq(channelUserId)))
                .thenReturn(List.of(Map.of(
                        "id", UUID.randomUUID(),
                        "request_no", "MOCK-SAMPLE-001",
                        "status", 6
                )));

        OrderDetailResponse detail = service.getOrderDetail("mock-order-1", null, null, DataScope.ALL);

        assertThat(detail.getOrderId()).isEqualTo("mock-order-1");
        assertThat(detail.getAttributionStatus()).isEqualTo("ATTRIBUTED");
        assertThat(detail.getProduct().getActivityName()).isEqualTo("主链路演示活动-A");
        assertThat(detail.getChannel().getChannelName()).isEqualTo("渠道A-华东区域");
        assertThat(detail.getAmount().getPayAmount()).isEqualTo(19900L);
        assertThat(detail.getAmount().getSettleAmount()).isEqualTo(18800L);
        assertThat(detail.getAmount().getEstimateServiceFee()).isEqualTo(2600L);
        assertThat(detail.getAmount().getEffectiveServiceFee()).isEqualTo(2400L);
        assertThat(detail.getAmount().getEstimateTechServiceFee()).isEqualTo(260L);
        assertThat(detail.getAmount().getEffectiveTechServiceFee()).isEqualTo(240L);
        assertThat(detail.getPromotion().isMatched()).isTrue();
        assertThat(detail.getSample().isMatched()).isTrue();
        assertThat(detail.getSample().getSampleStatus()).isEqualTo("FINISHED");
        assertThat(detail.getSample().isCompletedByOrderRule()).isTrue();
    }

    @Test
    void getOrderDetail_amountFieldsShouldPreserveCentUnitAndCurrentMapping() {
        // 用差异化数值区分每个金额字段的来源列，防止"沉默换算/换列"漂移。
        when(jdbcTemplate.queryForList(anyString(), eq("amount-mapping-order")))
                .thenReturn(List.of(Map.ofEntries(
                        Map.entry("order_id", "amount-mapping-order"),
                        Map.entry("order_status", 1),
                        Map.entry("attribution_status", "ATTRIBUTED"),
                        Map.entry("attribution_remark", "ATTRIBUTED"),
                        Map.entry("product_id", "P-AMT"),
                        Map.entry("activity_id", "REAL_ACT_AMT"),
                        Map.entry("order_amount", 6900L),
                        Map.entry("settle_amount", 6500L),
                        Map.entry("estimate_service_fee", 800L),
                        Map.entry("effective_service_fee", 700L),
                        Map.entry("estimate_tech_service_fee", 80L),
                        Map.entry("effective_tech_service_fee", 70L),
                        Map.entry("settle_colonel_commission", 600L),
                        Map.entry("create_time", Timestamp.valueOf(LocalDateTime.of(2026, 5, 26, 12, 0, 0))),
                        Map.entry("update_time", Timestamp.valueOf(LocalDateTime.of(2026, 5, 26, 12, 1, 0)))
                )));
        when(jdbcTemplate.queryForList(anyString(), eq("P-AMT"))).thenReturn(List.of());

        OrderDetailResponse.AmountInfo amount =
                service.getOrderDetail("amount-mapping-order", null, null, DataScope.ALL).getAmount();

        // 单位：DB 列均为"分"，DTO 原样下发，前端按 /100 渲染。任何换算应被此测试发现。
        assertThat(amount.getOrderAmount()).isEqualTo(6900L);
        // payAmount 当前实现复用 order_amount 列（schema 注释：order_amount 即 pay_amount）。
        assertThat(amount.getPayAmount()).isEqualTo(6900L);
        assertThat(amount.getSettleAmount()).isEqualTo(6500L);
        // 双轨服务费：预估 / 结算。
        assertThat(amount.getEstimateServiceFee()).isEqualTo(800L);
        assertThat(amount.getEffectiveServiceFee()).isEqualTo(700L);
        assertThat(amount.getEstimateTechServiceFee()).isEqualTo(80L);
        assertThat(amount.getEffectiveTechServiceFee()).isEqualTo(70L);
        // serviceFee 对齐业绩域双轨口径：取 effective_service_fee（结算服务费 = 700L），
        // 而非历史口径 settle_colonel_commission（团长结算佣金 = 600L）。
        // 若回退到团长佣金口径，应改回 row.get("settle_colonel_commission")，并同步前端 label。
        assertThat(amount.getServiceFee()).isEqualTo(700L);
    }

    @Test
    void getOrderDetail_shouldBuildUnattributedDiagnosis() {
        when(jdbcTemplate.queryForList(anyString(), eq("mock-order-2")))
                .thenReturn(List.of(Map.ofEntries(
                        Map.entry("order_id", "mock-order-2"),
                        Map.entry("order_status", 1),
                        Map.entry("attribution_status", "UNATTRIBUTED"),
                        Map.entry("attribution_remark", "MAPPING_NOT_FOUND"),
                        Map.entry("pick_source", "MOCK_MISSING_PS"),
                        Map.entry("product_id", "10901826"),
                        Map.entry("product_name", "排查演示商品-推广映射缺失"),
                        Map.entry("activity_id", "MOCK_ACTIVITY_A"),
                        Map.entry("order_amount", 9900L),
                        Map.entry("settle_colonel_commission", 1200L),
                        Map.entry("create_time", Timestamp.valueOf(LocalDateTime.of(2026, 4, 27, 12, 0, 0))),
                        Map.entry("update_time", Timestamp.valueOf(LocalDateTime.of(2026, 4, 27, 12, 1, 0)))
                )));
        when(jdbcTemplate.queryForList(anyString(), eq("10901826")))
                .thenReturn(List.of());

        OrderDetailResponse detail = service.getOrderDetail("mock-order-2", null, null, DataScope.ALL);

        assertThat(detail.getAttributionStatus()).isEqualTo("UNATTRIBUTED");
        assertThat(detail.getDiagnosis().getReasonCode()).isEqualTo("MAPPING_NOT_FOUND");
        assertThat(detail.getDiagnosis().getReasonText()).isEqualTo("未找到对应推广链接");
        assertThat(detail.getPromotion().isMatched()).isFalse();
        assertThat(detail.getSample().isMatched()).isFalse();
    }

    @Test
    void getOrderDetail_shouldBuildNativeColonelMissingDiagnosis() {
        when(jdbcTemplate.queryForList(anyString(), eq("mock-order-3")))
                .thenReturn(List.of(Map.ofEntries(
                        Map.entry("order_id", "mock-order-3"),
                        Map.entry("order_status", 1),
                        Map.entry("attribution_status", "UNATTRIBUTED"),
                        Map.entry("attribution_remark", "COLONEL_MAPPING_NOT_FOUND"),
                        Map.entry("product_id", "10901827"),
                        Map.entry("product_name", "原生团长缺映射订单"),
                        Map.entry("activity_id", "REAL_ACTIVITY_X"),
                        Map.entry("order_amount", 10900L),
                        Map.entry("settle_colonel_commission", 900L),
                        Map.entry("create_time", Timestamp.valueOf(LocalDateTime.of(2026, 5, 8, 12, 27, 18))),
                        Map.entry("update_time", Timestamp.valueOf(LocalDateTime.of(2026, 5, 8, 12, 27, 18)))
                )));
        when(jdbcTemplate.queryForList(anyString(), eq("10901827")))
                .thenReturn(List.of());

        OrderDetailResponse detail = service.getOrderDetail("mock-order-3", null, null, DataScope.ALL);

        assertThat(detail.getAttributionStatus()).isEqualTo("UNATTRIBUTED");
        assertThat(detail.getDiagnosis().getReasonCode()).isEqualTo("COLONEL_MAPPING_NOT_FOUND");
        assertThat(detail.getDiagnosis().getReasonText()).isEqualTo("原生团长订单未找到归因映射");
        assertThat(detail.getDiagnosis().getSuggestion()).contains("活动、商品和推广映射");
    }

    @Test
    void getOrderDetail_shouldBuildTalentClaimOwnerConflictDiagnosis() {
        when(jdbcTemplate.queryForList(anyString(), eq("mock-order-4")))
                .thenReturn(List.of(Map.ofEntries(
                        Map.entry("order_id", "mock-order-4"),
                        Map.entry("order_status", 1),
                        Map.entry("attribution_status", "UNATTRIBUTED"),
                        Map.entry("attribution_remark", AttributionService.REASON_TALENT_CLAIM_OWNER_CONFLICT),
                        Map.entry("product_id", "10901828"),
                        Map.entry("product_name", "达人认领冲突订单"),
                        Map.entry("activity_id", "REAL_ACTIVITY_Y"),
                        Map.entry("order_amount", 12900L),
                        Map.entry("settle_colonel_commission", 1100L),
                        Map.entry("create_time", Timestamp.valueOf(LocalDateTime.of(2026, 5, 21, 12, 0, 0))),
                        Map.entry("update_time", Timestamp.valueOf(LocalDateTime.of(2026, 5, 21, 12, 0, 0)))
                )));
        when(jdbcTemplate.queryForList(anyString(), eq("10901828")))
                .thenReturn(List.of());

        OrderDetailResponse detail = service.getOrderDetail("mock-order-4", null, null, DataScope.ALL);

        assertThat(detail.getAttributionStatus()).isEqualTo("UNATTRIBUTED");
        assertThat(detail.getDiagnosis().getReasonCode()).isEqualTo(AttributionService.REASON_TALENT_CLAIM_OWNER_CONFLICT);
        assertThat(detail.getDiagnosis().getReasonText()).isEqualTo("归因负责人和达人认领人不一致");
        assertThat(detail.getDiagnosis().getSuggestion()).contains("有效认领记录");
    }

    @Test
    void getOrderDetail_shouldThrowWhenMissing() {
        when(jdbcTemplate.queryForList(anyString(), eq("missing-order"))).thenReturn(List.of());

        assertThatThrownBy(() -> service.getOrderDetail("missing-order", null, null, DataScope.ALL))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("订单不存在");
    }

    @Test
    void getOrderDetail_shouldRejectOutOfScopePersonalAndDeptAccess() {
        UUID orderUserId = UUID.randomUUID();
        UUID orderDeptId = UUID.randomUUID();
        when(jdbcTemplate.queryForList(anyString(), eq("scoped-order")))
                .thenReturn(List.of(Map.ofEntries(
                        Map.entry("order_id", "scoped-order"),
                        Map.entry("order_status", 1),
                        Map.entry("product_id", "10901829"),
                        Map.entry("order_user_id", orderUserId),
                        Map.entry("order_dept_id", orderDeptId)
                )));

        assertThatThrownBy(() -> service.getOrderDetail("scoped-order", UUID.randomUUID(), orderDeptId, DataScope.PERSONAL))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("无权查看该订单详情");
        assertThatThrownBy(() -> service.getOrderDetail("scoped-order", orderUserId, UUID.randomUUID(), DataScope.DEPT))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("无权查看该订单详情");
    }

    @Test
    void privateLabelHelpers_shouldCoverStatusAndDiagnosisMappings() {
        assertThat(ReflectionTestUtils.<String>invokeMethod(service, "orderStatusLabel", (Integer) null)).isEqualTo("-");
        assertThat(ReflectionTestUtils.<String>invokeMethod(service, "orderStatusLabel", 2)).isEqualTo("已发货");
        assertThat(ReflectionTestUtils.<String>invokeMethod(service, "orderStatusLabel", 3)).isEqualTo("已完成");
        assertThat(ReflectionTestUtils.<String>invokeMethod(service, "orderStatusLabel", 4)).isEqualTo("已取消");
        assertThat(ReflectionTestUtils.<String>invokeMethod(service, "orderStatusLabel", 99)).isEqualTo("状态 99");

        assertThat(ReflectionTestUtils.<String>invokeMethod(service, "attributionStatusLabel", " ")).isEqualTo("-");
        assertThat(ReflectionTestUtils.<String>invokeMethod(service, "attributionStatusLabel", "ATTRIBUTED")).isEqualTo("已确认业绩");
        assertThat(ReflectionTestUtils.<String>invokeMethod(service, "attributionStatusLabel", "UNATTRIBUTED")).isEqualTo("待排查订单");
        assertThat(ReflectionTestUtils.<String>invokeMethod(service, "attributionStatusLabel", "PARTIAL")).isEqualTo("部分归因");
        assertThat(ReflectionTestUtils.<String>invokeMethod(service, "attributionStatusLabel", "FAILED")).isEqualTo("同步/归因失败");
        assertThat(ReflectionTestUtils.<String>invokeMethod(service, "attributionStatusLabel", "CUSTOM")).isEqualTo("CUSTOM");

        assertThat(ReflectionTestUtils.<String>invokeMethod(service, "unattributedReasonText", " ")).isNull();
        assertThat(ReflectionTestUtils.<String>invokeMethod(service, "unattributedReasonText", AttributionService.REASON_NO_PICK_SOURCE))
                .isEqualTo("订单未携带推广参数");
        assertThat(ReflectionTestUtils.<String>invokeMethod(service, "unattributedReasonText", "订单未携带推广参数"))
                .isEqualTo("订单未携带推广参数");
        assertThat(ReflectionTestUtils.<String>invokeMethod(service, "unattributedReasonText", AttributionService.REASON_MAPPING_NOT_FOUND))
                .isEqualTo("未找到对应推广链接");
        assertThat(ReflectionTestUtils.<String>invokeMethod(service, "unattributedReasonText", AttributionService.REASON_COLONEL_MAPPING_AMBIGUOUS))
                .isEqualTo("原生团长订单命中多条归因映射");
        assertThat(ReflectionTestUtils.<String>invokeMethod(service, "unattributedReasonText", AttributionService.REASON_PRODUCT_NOT_FOUND))
                .isEqualTo("未匹配到本地商品库");
        assertThat(ReflectionTestUtils.<String>invokeMethod(service, "unattributedReasonText", AttributionService.REASON_ACTIVITY_NOT_FOUND))
                .isEqualTo("商品未关联活动");
        assertThat(ReflectionTestUtils.<String>invokeMethod(service, "unattributedReasonText", AttributionService.REASON_CHANNEL_NOT_FOUND))
                .isEqualTo("未匹配到渠道负责人");
        assertThat(ReflectionTestUtils.<String>invokeMethod(service, "unattributedReasonText", "订单同步失败"))
                .isEqualTo("订单同步失败");
        assertThat(ReflectionTestUtils.<String>invokeMethod(service, "unattributedReasonText", "UNKNOWN_REASON"))
                .isEqualTo("UNKNOWN_REASON");

        assertThat(ReflectionTestUtils.<String>invokeMethod(service, "unattributedReasonSuggestion", " ")).isNull();
        assertThat(ReflectionTestUtils.<String>invokeMethod(service, "unattributedReasonSuggestion", AttributionService.REASON_NO_PICK_SOURCE))
                .contains("系统生成的推广链接");
        assertThat(ReflectionTestUtils.<String>invokeMethod(service, "unattributedReasonSuggestion", AttributionService.REASON_MAPPING_NOT_FOUND))
                .contains("pick_source");
        assertThat(ReflectionTestUtils.<String>invokeMethod(service, "unattributedReasonSuggestion", AttributionService.REASON_COLONEL_MAPPING_NOT_FOUND))
                .contains("活动、商品和推广映射");
        assertThat(ReflectionTestUtils.<String>invokeMethod(service, "unattributedReasonSuggestion", AttributionService.REASON_COLONEL_MAPPING_AMBIGUOUS))
                .contains("多条渠道映射");
        assertThat(ReflectionTestUtils.<String>invokeMethod(service, "unattributedReasonSuggestion", AttributionService.REASON_TALENT_CLAIM_OWNER_CONFLICT))
                .contains("有效认领记录");
        assertThat(ReflectionTestUtils.<String>invokeMethod(service, "unattributedReasonSuggestion", AttributionService.REASON_PRODUCT_NOT_FOUND))
                .contains("商品主链路");
        assertThat(ReflectionTestUtils.<String>invokeMethod(service, "unattributedReasonSuggestion", AttributionService.REASON_ACTIVITY_NOT_FOUND))
                .contains("绑定活动");
        assertThat(ReflectionTestUtils.<String>invokeMethod(service, "unattributedReasonSuggestion", AttributionService.REASON_CHANNEL_NOT_FOUND))
                .contains("渠道负责人");
        assertThat(ReflectionTestUtils.<String>invokeMethod(service, "unattributedReasonSuggestion", "订单同步失败"))
                .contains("订单同步日志");
        assertThat(ReflectionTestUtils.<String>invokeMethod(service, "unattributedReasonSuggestion", "UNKNOWN_REASON"))
                .contains("推广链路");
    }

    @Test
    void privateConversionHelpers_shouldNormalizeStatusIdsAndPrimitiveValues() {
        assertThat(ReflectionTestUtils.<String>invokeMethod(service, "sampleStatusApi", (Integer) null)).isNull();
        assertThat(ReflectionTestUtils.<String>invokeMethod(service, "sampleStatusApi", 1)).isEqualTo("PENDING_AUDIT");
        assertThat(ReflectionTestUtils.<String>invokeMethod(service, "sampleStatusApi", 2)).isEqualTo("PENDING_SHIP");
        assertThat(ReflectionTestUtils.<String>invokeMethod(service, "sampleStatusApi", 3)).isEqualTo("SHIPPED");
        assertThat(ReflectionTestUtils.<String>invokeMethod(service, "sampleStatusApi", 4)).isEqualTo("SHIPPED");
        assertThat(ReflectionTestUtils.<String>invokeMethod(service, "sampleStatusApi", 5)).isEqualTo("PENDING_TASK");
        assertThat(ReflectionTestUtils.<String>invokeMethod(service, "sampleStatusApi", 6)).isEqualTo("FINISHED");
        assertThat(ReflectionTestUtils.<String>invokeMethod(service, "sampleStatusApi", 7)).isEqualTo("REJECTED");
        assertThat(ReflectionTestUtils.<String>invokeMethod(service, "sampleStatusApi", 8)).isEqualTo("CLOSED");
        assertThat(ReflectionTestUtils.<String>invokeMethod(service, "sampleStatusApi", 99)).isEqualTo("99");

        assertThat(ReflectionTestUtils.<String>invokeMethod(service, "sampleStatusText", " ")).isEqualTo("-");
        assertThat(ReflectionTestUtils.<String>invokeMethod(service, "sampleStatusText", "PENDING_AUDIT")).isEqualTo("待审核");
        assertThat(ReflectionTestUtils.<String>invokeMethod(service, "sampleStatusText", "PENDING_SHIP")).isEqualTo("待发货");
        assertThat(ReflectionTestUtils.<String>invokeMethod(service, "sampleStatusText", "SHIPPED")).isEqualTo("快递中");
        assertThat(ReflectionTestUtils.<String>invokeMethod(service, "sampleStatusText", "PENDING_TASK")).isEqualTo("待交作业");
        assertThat(ReflectionTestUtils.<String>invokeMethod(service, "sampleStatusText", "FINISHED")).isEqualTo("已完成");
        assertThat(ReflectionTestUtils.<String>invokeMethod(service, "sampleStatusText", "REJECTED")).isEqualTo("已拒绝");
        assertThat(ReflectionTestUtils.<String>invokeMethod(service, "sampleStatusText", "CLOSED")).isEqualTo("已关闭");
        assertThat(ReflectionTestUtils.<String>invokeMethod(service, "sampleStatusText", "CUSTOM")).isEqualTo("CUSTOM");

        assertThat(ReflectionTestUtils.<String>invokeMethod(service, "resolveActivityName", " ")).isNull();
        assertThat(ReflectionTestUtils.<String>invokeMethod(service, "resolveActivityName", "MOCK_ACTIVITY_A_B")).isEqualTo("主链路演示活动-A B");
        assertThat(ReflectionTestUtils.<String>invokeMethod(service, "resolveActivityName", "REAL_ACTIVITY")).isEqualTo("REAL_ACTIVITY");
        assertThat(ReflectionTestUtils.<String>invokeMethod(service, "firstNonBlank", (Object) null)).isNull();
        assertThat(ReflectionTestUtils.<String>invokeMethod(service, "firstNonBlank", (Object) new String[]{"", " value "})).isEqualTo(" value ");
        assertThat(ReflectionTestUtils.<Object>invokeMethod(service, "firstNonNull", (Object) null)).isNull();
        assertThat(ReflectionTestUtils.<Object>invokeMethod(service, "firstNonNull", (Object) new Object[]{null, 12})).isEqualTo(12);

        LocalDateTime now = LocalDateTime.of(2026, 5, 22, 10, 0);
        assertThat(ReflectionTestUtils.<LocalDateTime>invokeMethod(service, "firstNonNullTime", (Object) null)).isNull();
        assertThat(ReflectionTestUtils.<LocalDateTime>invokeMethod(service, "firstNonNullTime", (Object) new LocalDateTime[]{null, now})).isEqualTo(now);
        assertThat(ReflectionTestUtils.<Integer>invokeMethod(service, "asInteger", 12L)).isEqualTo(12);
        assertThat(ReflectionTestUtils.<Integer>invokeMethod(service, "asInteger", "34")).isEqualTo(34);
        assertThat(ReflectionTestUtils.<Integer>invokeMethod(service, "asInteger", "bad")).isNull();
        assertThat(ReflectionTestUtils.<Long>invokeMethod(service, "asLong", 12)).isEqualTo(12L);
        assertThat(ReflectionTestUtils.<Long>invokeMethod(service, "asLong", "34")).isEqualTo(34L);
        assertThat(ReflectionTestUtils.<Long>invokeMethod(service, "asLong", "bad")).isNull();
        assertThat(ReflectionTestUtils.<LocalDateTime>invokeMethod(service, "toDateTime", now)).isEqualTo(now);
        assertThat(ReflectionTestUtils.<LocalDateTime>invokeMethod(service, "toDateTime", Timestamp.valueOf(now))).isEqualTo(now);
        assertThat(ReflectionTestUtils.<LocalDateTime>invokeMethod(service, "toDateTime", "bad")).isNull();
        assertThat(ReflectionTestUtils.<String>invokeMethod(service, "uuidText", UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")))
                .isEqualTo("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        assertThat(ReflectionTestUtils.<String>invokeMethod(service, "uuidText", " ")).isNull();
        assertThat(ReflectionTestUtils.<UUID>invokeMethod(service, "uuidValue", "bad")).isNull();
    }
}
