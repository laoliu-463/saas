package com.colonel.saas.service;

import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.config.DddRefactorProperties;
import com.colonel.saas.domain.user.policy.CurrentUserPermissionChecker;
import com.colonel.saas.domain.user.policy.CurrentUserPermissionPolicy;
import com.colonel.saas.domain.user.policy.DataScopeResolver;
import com.colonel.saas.domain.user.policy.DataScopePolicy;
import com.colonel.saas.dto.performance.PerformanceBatchRequest;
import com.colonel.saas.dto.performance.PerformanceListQuery;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.entity.PerformanceRecord;
import com.colonel.saas.domain.order.facade.OrderReadFacade;
import com.colonel.saas.mapper.PerformanceRecordMapper;
import com.colonel.saas.domain.performance.policy.PerformanceAccessContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PerformanceQueryServiceTest {

    @Mock
    private PerformanceRecordMapper performanceRecordMapper;
    @Mock
    private OrderReadFacade orderReadFacade;
    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private OperationLogService operationLogService;

    private PerformanceQueryService service;
    private CurrentUserPermissionChecker currentUserPermissionChecker;

    @BeforeEach
    void setUp() {
        currentUserPermissionChecker = new CurrentUserPermissionChecker(new CurrentUserPermissionPolicy());
        service = new PerformanceQueryService(
                performanceRecordMapper,
                orderReadFacade,
                jdbcTemplate,
                new DataScopeResolver(new DataScopePolicy()),
                currentUserPermissionChecker,
                new DddRefactorProperties());
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
        assertThat(response.getProductId()).isEqualTo("P-1");
        assertThat(response.getProductName()).isEqualTo("商品A");
        assertThat(response.getPartnerName()).isEqualTo("合作方A");
        assertThat(response.getDefaultChannelName()).isEqualTo("默认渠道");
        assertThat(response.getFinalChannelName()).isEqualTo("最终渠道");
        assertThat(response.getDefaultRecruiterName()).isEqualTo("默认招商");
        assertThat(response.getFinalRecruiterName()).isEqualTo("最终招商");
        assertThat(response.getChannelAttributionType()).isEqualTo("pick_source");
        assertThat(response.getRecruiterAttributionType()).isEqualTo("activity_owner");
        assertThat(response.getPayAmount()).isEqualTo(12345L);
        assertThat(response.getEstimateGrossProfit()).isEqualTo(600L);
        assertThat(response.getRecruiterCommissionRate()).isEqualByComparingTo("0.10");
        assertThat(response.getOrderStatus()).isEqualTo("FINISHED");
        assertThat(response.getPayTime()).isEqualTo(LocalDateTime.of(2026, 5, 24, 10, 30, 45));
        assertThat(response.getSettleTime()).isEqualTo(LocalDateTime.of(2026, 5, 25, 11, 5, 6));
    }

    @Test
    void getPerformance_shouldReturnNotCalculatedWhenOrderExistsButPerformanceMissing() {
        when(performanceRecordMapper.findByOrderId("ORDER-MISSING")).thenReturn(null);
        when(orderReadFacade.existsActiveByOrderId("ORDER-MISSING")).thenReturn(true);

        assertThatThrownBy(() -> service.getPerformance("ORDER-MISSING", admin()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("PERFORMANCE_NOT_CALCULATED");

        verify(jdbcTemplate, never()).queryForMap(any(String.class), any(Object[].class));
    }

    @Test
    void getPerformance_shouldReturnNotCalculatedWhenPerformanceRecordInvalid() {
        PerformanceRecord invalid = record("ORDER-REVERSED", UUID.randomUUID(), UUID.randomUUID());
        invalid.setValid(false);
        when(performanceRecordMapper.findByOrderId("ORDER-REVERSED")).thenReturn(invalid);
        when(orderReadFacade.existsActiveByOrderId("ORDER-REVERSED")).thenReturn(true);

        assertThatThrownBy(() -> service.getPerformance("ORDER-REVERSED", admin()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("PERFORMANCE_NOT_CALCULATED");

        verify(jdbcTemplate, never()).query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<?>>any(), any(Object[].class));
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
    void batchGetPerformance_shouldTreatInvalidPerformanceRecordAsNotCalculated() {
        PerformanceBatchRequest request = new PerformanceBatchRequest();
        request.setOrderIds(List.of("ORDER-REVERSED"));
        PerformanceRecord invalid = record("ORDER-REVERSED", UUID.randomUUID(), UUID.randomUUID());
        invalid.setValid(false);
        when(performanceRecordMapper.findByOrderId("ORDER-REVERSED")).thenReturn(invalid);

        var result = service.batchGetPerformance(request, admin());

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).isFound()).isFalse();
        assertThat(result.getItems().get(0).isAuthorized()).isTrue();
        assertThat(result.getItems().get(0).getMessage()).isEqualTo("PERFORMANCE_NOT_CALCULATED");
        assertThat(result.getItems().get(0).getPerformance()).isNull();
        verify(jdbcTemplate, never()).query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<?>>any(), any(Object[].class));
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
        assertThat(sqlCaptor.getValue()).contains("LEFT JOIN colonel_activity ca ON ca.activity_id = pr.activity_id");
        assertThat(sqlCaptor.getValue()).doesNotContain("colonelsettlement_activity");
        assertThat(sqlCaptor.getValue()).contains("LIMIT ?");
    }

    @Test
    void listPerformanceDataScopePolicyEnabledPath_shouldDelegateFallbackScopeDecisionToUserPolicy() {
        DataScopePolicy dataScopePolicy = spy(new DataScopePolicy());
        DddRefactorProperties properties = new DddRefactorProperties();
        properties.getDataScopePolicy().setEnabled(true);
        service = new PerformanceQueryService(
                performanceRecordMapper,
                orderReadFacade,
                jdbcTemplate,
                new DataScopeResolver(dataScopePolicy),
                currentUserPermissionChecker,
                properties);
        when(jdbcTemplate.queryForObject(any(String.class), eq(Long.class), any(Object[].class))).thenReturn(0L);
        when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<?>>any(), any(Object[].class)))
                .thenReturn(List.of());
        PerformanceListQuery query = new PerformanceListQuery();
        query.setPage(1);
        query.setPageSize(20);
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();

        service.listPerformance(query, PerformanceAccessContext.of(
                userId,
                deptId,
                DataScope.DEPT,
                List.of()));

        verify(dataScopePolicy).contextRequirement(userId, deptId, DataScope.DEPT);
        verify(dataScopePolicy).decide(userId, deptId, DataScope.DEPT);
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), org.mockito.ArgumentMatchers.<RowMapper<?>>any(), any(Object[].class));
        assertThat(sqlCaptor.getValue())
                .contains("pr.final_channel_user_id IN")
                .contains("OR pr.final_recruiter_user_id IN")
                .contains("LIMIT ?");
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
                    org.mockito.Mockito.lenient().when(rs.getString("activity_id")).thenReturn("ACT-1");
                    org.mockito.Mockito.lenient().when(rs.getString("activity_name")).thenReturn("春季活动");
                    org.mockito.Mockito.lenient().when(rs.getString("partner_name")).thenReturn("合作方A");
                    org.mockito.Mockito.lenient().when(rs.getString("talent_name")).thenReturn("达人A");
                    org.mockito.Mockito.lenient().when(rs.getString("default_channel_name")).thenReturn("默认渠道");
                    org.mockito.Mockito.lenient().when(rs.getString("default_recruiter_name")).thenReturn("默认招商");
                    org.mockito.Mockito.lenient().when(rs.getString("final_channel_name_resolved")).thenReturn("最终渠道");
                    org.mockito.Mockito.lenient().when(rs.getString("final_recruiter_name_resolved")).thenReturn("最终招商");
                    org.mockito.Mockito.lenient().when(rs.getString("channel_attribution")).thenReturn("pick_source");
                    org.mockito.Mockito.lenient().when(rs.getString("recruiter_attribution")).thenReturn("activity_owner");
                    org.mockito.Mockito.lenient().when(rs.getObject("order_status")).thenReturn(3);
                    org.mockito.Mockito.lenient().when(rs.getLong("pay_amount")).thenReturn(12345L);
                    org.mockito.Mockito.lenient().when(rs.getLong("settle_amount")).thenReturn(12000L);
                    org.mockito.Mockito.lenient().when(rs.getLong("estimate_service_fee")).thenReturn(2345L);
                    org.mockito.Mockito.lenient().when(rs.getLong("effective_service_fee")).thenReturn(2200L);
                    org.mockito.Mockito.lenient().when(rs.getLong("estimate_tech_service_fee")).thenReturn(345L);
                    org.mockito.Mockito.lenient().when(rs.getLong("effective_tech_service_fee")).thenReturn(300L);
                    org.mockito.Mockito.lenient().when(rs.getLong("estimate_service_profit")).thenReturn(2000L);
                    org.mockito.Mockito.lenient().when(rs.getLong("effective_service_profit")).thenReturn(1900L);
                    org.mockito.Mockito.lenient().when(rs.getLong("estimate_recruiter_commission")).thenReturn(800L);
                    org.mockito.Mockito.lenient().when(rs.getLong("effective_recruiter_commission")).thenReturn(700L);
                    org.mockito.Mockito.lenient().when(rs.getLong("estimate_channel_commission")).thenReturn(600L);
                    org.mockito.Mockito.lenient().when(rs.getLong("effective_channel_commission")).thenReturn(500L);
                    org.mockito.Mockito.lenient().when(rs.getLong("estimate_gross_profit")).thenReturn(600L);
                    org.mockito.Mockito.lenient().when(rs.getLong("effective_gross_profit")).thenReturn(700L);
                    org.mockito.Mockito.lenient().when(rs.getBigDecimal("recruiter_commission_rate")).thenReturn(new BigDecimal("0.10"));
                    org.mockito.Mockito.lenient().when(rs.getBigDecimal("channel_commission_rate")).thenReturn(new BigDecimal("0.20"));
                    org.mockito.Mockito.lenient().when(rs.getTimestamp("pay_time")).thenReturn(Timestamp.valueOf("2026-05-24 10:30:45"));
                    org.mockito.Mockito.lenient().when(rs.getTimestamp("settle_time")).thenReturn(Timestamp.valueOf("2026-05-25 11:05:06"));
                    org.mockito.Mockito.lenient().when(rs.getTimestamp("calculated_at")).thenReturn(Timestamp.valueOf("2026-05-25 12:00:00"));
                    org.mockito.Mockito.lenient().when(rs.getBoolean("is_valid")).thenReturn(true);
                    return List.of(mapper.mapRow(rs, 0));
                });
    }
}
