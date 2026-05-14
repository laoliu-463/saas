package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.mapper.ColonelsettlementOrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private ColonelsettlementOrderMapper orderMapper;
    @Mock
    private JdbcTemplate jdbcTemplate;

    private DashboardService service;

    @BeforeEach
    void setUp() {
        service = new DashboardService(orderMapper, jdbcTemplate);
    }

    @Test
    void getSummary_shouldKeepAttributedCountFromOrderFacts() {
        mockBaseOrderAggregates(5L, 2L);
        mockJdbcSequences(
                List.of(
                Map.of("category", DashboardService.DIAGNOSIS_MECHANISM_HIT_HISTORY_UNSAFE, "total_count", 2L),
                Map.of("category", DashboardService.DIAGNOSIS_CANNOT_AUTO_ATTRIBUTION, "total_count", 1L)
                ),
                1L,
                List.of(productRow("3223881", "3814081914181124118", "命中商品"))
        );

        DashboardService.Summary summary = service.getSummary(LocalDateTime.now().minusDays(7), LocalDateTime.now(), null, null, DataScope.ALL);

        assertThat(summary.getAttributedOrderCount()).isEqualTo(5L);
        assertThat(summary.getUnattributedOrderCount()).isEqualTo(2L);
        assertThat(summary.getUnsafeBecauseCreatedAfterOrderCount()).isEqualTo(2L);
        assertThat(summary.getOrderCount()).isEqualTo(7L);
        assertThat(summary.getAttributionRate()).isEqualTo(5D / 7D);
    }

    @Test
    void getSummary_shouldExposeUnsafeNativeMatchAsDiagnosticInsteadOfAttributedPerformance() {
        mockBaseOrderAggregates(10L, 4L);
        mockJdbcSequences(
                List.of(Map.of("category", DashboardService.DIAGNOSIS_MECHANISM_HIT_HISTORY_UNSAFE, "total_count", 4L)),
                0L,
                List.of()
        );

        DashboardService.Summary summary = service.getSummary(null, null, null, null, DataScope.ALL);

        assertThat(summary.getUnsafeBecauseCreatedAfterOrderCount()).isEqualTo(4L);
        assertThat(summary.getAttributedOrderCount()).isEqualTo(10L);
        assertThat(summary.getChannelPerformance()).isEmpty();
        assertThat(summary.getColonelPerformance()).isEmpty();
    }

    @Test
    void getSummary_shouldExposeUpstreamMissingWhenProductFactsAreMissing() {
        mockBaseOrderAggregates(3L, 5L);
        mockJdbcSequences(
                List.of(Map.of("category", DashboardService.DIAGNOSIS_UPSTREAM_PRODUCT_UNCOVERED, "total_count", 5L)),
                0L,
                List.of()
        );

        DashboardService.Summary summary = service.getSummary(null, null, null, null, DataScope.ALL);

        assertThat(summary.getUpstreamProductUncoveredCount()).isEqualTo(5L);
        assertThat(summary.getDiagnosticBreakdown())
                .extracting(DashboardService.DiagnosticItem::getCategory)
                .containsExactly(DashboardService.DIAGNOSIS_UPSTREAM_PRODUCT_UNCOVERED);
        assertThat(summary.getDiagnosticBreakdown().get(0).getDrillDownQuery().attributionStatus()).isNull();
        verify(jdbcTemplate, times(3)).queryForList(anyString(), any(Object[].class));
    }

    @Test
    void getSummary_shouldUseOrderTableFactsAndReturnBreakdownDrilldown() {
        mockBaseOrderAggregates(8L, 2L);
        mockJdbcSequences(
                List.of(Map.of("category", DashboardService.DIAGNOSIS_CANNOT_AUTO_ATTRIBUTION, "total_count", 2L)),
                1L,
                List.of(productRow("3559407", "3810204346914832817", "空样本商品"))
        );

        DashboardService.Summary summary = service.getSummary(null, null, null, null, DataScope.ALL);

        assertThat(summary.getOrderCount()).isEqualTo(10L);
        assertThat(summary.getOrderAmount()).isEqualTo(120000L);
        assertThat(summary.getServiceFee()).isEqualTo(2300L);
        assertThat(summary.getActivityProductBreakdown()).hasSize(1);
        DashboardService.ActivityProductItem item = summary.getActivityProductBreakdown().get(0);
        assertThat(item.getActivityId()).isEqualTo("3559407");
        assertThat(item.getProductId()).isEqualTo("3810204346914832817");
        assertThat(item.getDrillDownQuery().activityId()).isEqualTo("3559407");
        assertThat(item.getDrillDownQuery().productId()).isEqualTo("3810204346914832817");
        assertThat(item.getDrillDownQuery().timeField()).isEqualTo("settleTime");

        ArgumentCaptor<QueryWrapper<ColonelsettlementOrder>> wrapperCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(orderMapper, times(2)).selectCount(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getAllValues()).isNotEmpty();
    }

    private void mockBaseOrderAggregates(long attributedCount, long unattributedCount) {
        when(orderMapper.selectMaps(any(QueryWrapper.class)))
                .thenReturn(List.of(Map.of("ordercount", attributedCount + unattributedCount, "orderamount", 120000L, "servicefee", 2300L)))
                .thenReturn(List.of())
                .thenReturn(List.of())
                .thenReturn(List.of());
        when(orderMapper.selectCount(any(QueryWrapper.class)))
                .thenReturn(attributedCount)
                .thenReturn(unattributedCount);
    }

    private void mockJdbcSequences(List<Map<String, Object>> diagnostics, long total, List<Map<String, Object>> rows) {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(diagnostics)
                .thenReturn(List.of(Map.of("total_count", total)))
                .thenReturn(rows);
    }

    private Map<String, Object> productRow(String activityId, String productId, String productName) {
        return Map.ofEntries(
                Map.entry("activity_id", activityId),
                Map.entry("product_id", productId),
                Map.entry("product_name", productName),
                Map.entry("product_cover", "https://img.example/test.png"),
                Map.entry("biz_status", "LINKED"),
                Map.entry("assignee_name", "招商A"),
                Map.entry("order_count", 3L),
                Map.entry("order_amount", 4500L),
                Map.entry("unattributed_order_count", 1L),
                Map.entry("mapping_count", 1L),
                Map.entry("promotion_link_count", 1L)
        );
    }
}
