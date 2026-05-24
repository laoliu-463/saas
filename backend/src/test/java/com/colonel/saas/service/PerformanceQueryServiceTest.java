package com.colonel.saas.service;

import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.dto.performance.PerformanceBatchRequest;
import com.colonel.saas.dto.performance.PerformanceListQuery;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.entity.PerformanceRecord;
import com.colonel.saas.mapper.ColonelsettlementOrderMapper;
import com.colonel.saas.mapper.PerformanceRecordMapper;
import com.colonel.saas.service.performance.PerformanceAccessContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PerformanceQueryServiceTest {

    @Mock
    private PerformanceRecordMapper performanceRecordMapper;
    @Mock
    private ColonelsettlementOrderMapper orderMapper;
    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private OperationLogService operationLogService;

    private PerformanceQueryService service;

    @BeforeEach
    void setUp() {
        service = new PerformanceQueryService(performanceRecordMapper, orderMapper, jdbcTemplate);
    }

    @Test
    void getPerformance_shouldReturnCalculatedRecordWhenAuthorized() {
        UUID channelUserId = UUID.randomUUID();
        PerformanceRecord record = record("ORDER-1", channelUserId, UUID.randomUUID());
        when(performanceRecordMapper.findByOrderId("ORDER-1")).thenReturn(record);
        mockDetailQuery("ORDER-1", "商品A");

        var response = service.getPerformance("ORDER-1", PerformanceAccessContext.of(
                channelUserId,
                null,
                DataScope.PERSONAL,
                List.of("channel_staff")));

        assertThat(response.getOrderId()).isEqualTo("ORDER-1");
        assertThat(response.getProductName()).isEqualTo("商品A");
    }

    @Test
    void getPerformance_shouldReturnNotCalculatedWhenOrderExistsButPerformanceMissing() {
        when(performanceRecordMapper.findByOrderId("ORDER-MISSING")).thenReturn(null);
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setOrderId("ORDER-MISSING");
        when(orderMapper.selectOne(any())).thenReturn(order);

        assertThatThrownBy(() -> service.getPerformance("ORDER-MISSING", admin()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("PERFORMANCE_NOT_CALCULATED");

        verify(jdbcTemplate, never()).queryForMap(any(String.class), any(Object[].class));
    }

    @Test
    void batchGetPerformance_shouldLimitRequestSizeAndHideUnauthorizedRows() {
        PerformanceBatchRequest request = new PerformanceBatchRequest();
        request.setOrderIds(List.of("ORDER-A", "ORDER-B"));
        UUID allowed = UUID.randomUUID();
        when(performanceRecordMapper.findByOrderId("ORDER-A")).thenReturn(record("ORDER-A", allowed, UUID.randomUUID()));
        when(performanceRecordMapper.findByOrderId("ORDER-B")).thenReturn(record("ORDER-B", UUID.randomUUID(), UUID.randomUUID()));
        mockDetailQuery("ORDER-A", "商品A");

        var result = service.batchGetPerformance(request, PerformanceAccessContext.of(
                allowed,
                null,
                DataScope.PERSONAL,
                List.of("channel_staff")));

        assertThat(result.getItems()).hasSize(2);
        assertThat(result.getItems().get(0).isFound()).isTrue();
        assertThat(result.getItems().get(0).isAuthorized()).isTrue();
        assertThat(result.getItems().get(1).isFound()).isTrue();
        assertThat(result.getItems().get(1).isAuthorized()).isFalse();
        assertThat(result.getItems().get(1).getPerformance()).isNull();
    }

    @Test
    void listPerformance_shouldCapPageSizeAndAppendScope() {
        when(jdbcTemplate.queryForObject(any(String.class), eq(Long.class), any(Object[].class))).thenReturn(0L);
        when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<?>>any(), any(Object[].class)))
                .thenReturn(List.of());
        PerformanceListQuery query = new PerformanceListQuery();
        query.setPage(1);
        query.setPageSize(500);
        query.setTimeFilterType("calculate");

        service.listPerformance(query, PerformanceAccessContext.of(
                UUID.randomUUID(),
                null,
                DataScope.PERSONAL,
                List.of("biz_staff")));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), org.mockito.ArgumentMatchers.<RowMapper<?>>any(), any(Object[].class));
        assertThat(sqlCaptor.getValue()).contains("pr.calculated_at");
        assertThat(sqlCaptor.getValue()).contains("pr.final_recruiter_user_id = ?");
        assertThat(sqlCaptor.getValue()).contains("LIMIT ?");
    }

    private PerformanceAccessContext admin() {
        return PerformanceAccessContext.of(UUID.randomUUID(), null, DataScope.ALL, List.of("admin"));
    }

    private PerformanceRecord record(String orderId, UUID channelUserId, UUID recruiterUserId) {
        PerformanceRecord record = new PerformanceRecord();
        record.setOrderId(orderId);
        record.setFinalChannelUserId(channelUserId);
        record.setFinalRecruiterUserId(recruiterUserId);
        record.setValid(true);
        return record;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void mockDetailQuery(String orderId, String productName) {
        when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper>any(), any(Object[].class)))
                .thenAnswer(invocation -> {
                    RowMapper mapper = invocation.getArgument(1);
                    java.sql.ResultSet rs = org.mockito.Mockito.mock(java.sql.ResultSet.class, answer -> {
                        if ("getString".equals(answer.getMethod().getName())) {
                            return "";
                        }
                        if ("getObject".equals(answer.getMethod().getName())) {
                            return null;
                        }
                        if ("getLong".equals(answer.getMethod().getName())) {
                            return 0L;
                        }
                        if ("getBoolean".equals(answer.getMethod().getName())) {
                            return false;
                        }
                        return org.mockito.Mockito.RETURNS_DEFAULTS.answer(answer);
                    });
                    org.mockito.Mockito.lenient().when(rs.getString("order_id")).thenReturn(orderId);
                    org.mockito.Mockito.lenient().when(rs.getString("product_id")).thenReturn("P-1");
                    org.mockito.Mockito.lenient().when(rs.getString("product_name")).thenReturn(productName);
                    org.mockito.Mockito.lenient().when(rs.getString("partner_name")).thenReturn("合作方A");
                    org.mockito.Mockito.lenient().when(rs.getString("talent_name")).thenReturn("达人A");
                    org.mockito.Mockito.lenient().when(rs.getString("channel_attribution")).thenReturn("pick_source");
                    org.mockito.Mockito.lenient().when(rs.getString("recruiter_attribution")).thenReturn("activity_owner");
                    org.mockito.Mockito.lenient().when(rs.getBoolean("is_valid")).thenReturn(true);
                    return List.of(mapper.mapRow(rs, 0));
                });
    }
}
