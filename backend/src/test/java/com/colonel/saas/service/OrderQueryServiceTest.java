package com.colonel.saas.service;

import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.dto.order.OrderDetailResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

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
        assertThat(detail.getPromotion().isMatched()).isTrue();
        assertThat(detail.getSample().isMatched()).isTrue();
        assertThat(detail.getSample().getSampleStatus()).isEqualTo("FINISHED");
        assertThat(detail.getSample().isCompletedByOrderRule()).isTrue();
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
    void getOrderDetail_shouldThrowWhenMissing() {
        when(jdbcTemplate.queryForList(anyString(), eq("missing-order"))).thenReturn(List.of());

        assertThatThrownBy(() -> service.getOrderDetail("missing-order", null, null, DataScope.ALL))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("订单不存在");
    }
}
