package com.colonel.saas.domain.performance.facade;

import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.dto.performance.OrderPerformanceBatchResponse;
import com.colonel.saas.dto.performance.OrderPerformanceDTO;
import com.colonel.saas.dto.performance.PerformanceDetailDTO;
import com.colonel.saas.dto.performance.PerformanceSummaryQuery;
import com.colonel.saas.dto.performance.PerformanceSummaryResponse;
import com.colonel.saas.service.PerformanceQueryService;
import com.colonel.saas.service.PerformanceSummaryService;
import com.colonel.saas.service.performance.PerformanceAccessContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LegacyOrderPerformanceQueryFacadeTest {

    @Mock private PerformanceQueryService performanceQueryService;
    @Mock private PerformanceSummaryService performanceSummaryService;

    private LegacyOrderPerformanceQueryFacade facade;

    @BeforeEach
    void setUp() {
        facade = new LegacyOrderPerformanceQueryFacade(performanceQueryService, performanceSummaryService);
    }

    private static PerformanceAccessContext ctx() {
        return PerformanceAccessContext.of(null, null, DataScope.ALL, null);
    }

    @Test
    void getOrderPerformance_mapsFinalChannelAndProfit() {
        PerformanceDetailDTO detail = new PerformanceDetailDTO();
        detail.setOrderId("order-1");
        detail.setFinalChannelId("channel-1");
        detail.setFinalChannelName("渠道A");
        detail.setFinalRecruiterId("recruiter-1");
        detail.setFinalRecruiterName("团长A");
        detail.setChannelAttributionType("OVERRIDE");
        detail.setRecruiterAttributionType("DEFAULT");
        detail.setEstimateServiceProfit(1000L);
        detail.setEffectiveServiceProfit(900L);
        detail.setEstimateRecruiterCommission(200L);
        detail.setEffectiveRecruiterCommission(180L);
        detail.setEstimateChannelCommission(50L);
        detail.setEffectiveChannelCommission(45L);
        detail.setEstimateGrossProfit(750L);
        detail.setEffectiveGrossProfit(675L);
        detail.setValid(true);
        detail.setReversed(false);

        when(performanceQueryService.getPerformance(eq("order-1"), any(PerformanceAccessContext.class)))
                .thenReturn(detail);

        OrderPerformanceDTO result = facade.getOrderPerformance("order-1", ctx());

        assertThat(result.getOrderId()).isEqualTo("order-1");
        assertThat(result.getFinalChannelId()).isEqualTo("channel-1");
        assertThat(result.getFinalChannelName()).isEqualTo("渠道A");
        assertThat(result.getFinalRecruiterId()).isEqualTo("recruiter-1");
        assertThat(result.getFinalRecruiterName()).isEqualTo("团长A");
        assertThat(result.getChannelAttributionType()).isEqualTo("OVERRIDE");
        assertThat(result.getRecruiterAttributionType()).isEqualTo("DEFAULT");
        assertThat(result.getEstimateServiceProfit()).isEqualTo(1000L);
        assertThat(result.getEffectiveServiceProfit()).isEqualTo(900L);
        assertThat(result.getEstimateRecruiterCommission()).isEqualTo(200L);
        assertThat(result.getEffectiveRecruiterCommission()).isEqualTo(180L);
        assertThat(result.getEstimateChannelCommission()).isEqualTo(50L);
        assertThat(result.getEffectiveChannelCommission()).isEqualTo(45L);
        assertThat(result.getEstimateGrossProfit()).isEqualTo(750L);
        assertThat(result.getEffectiveGrossProfit()).isEqualTo(675L);
        assertThat(result.getIsValid()).isTrue();
        assertThat(result.getIsReversed()).isFalse();
    }

    @Test
    void getOrderPerformance_returnsEmptyWhenServiceThrows() {
        when(performanceQueryService.getPerformance(any(), any()))
                .thenThrow(new RuntimeException("boom"));

        OrderPerformanceDTO result = facade.getOrderPerformance("order-bad", ctx());

        assertThat(result.getOrderId()).isEqualTo("order-bad");
        assertThat(result.getIsValid()).isFalse();
        assertThat(result.getIsReversed()).isFalse();
        assertThat(result.getFinalChannelId()).isNull();
    }

    @Test
    void getOrderPerformance_handlesNullOrderId() {
        OrderPerformanceDTO result = facade.getOrderPerformance(null, ctx());
        assertThat(result).isNotNull();
        assertThat(result.getOrderId()).isNull();
        assertThat(result.getIsValid()).isFalse();
    }

    @Test
    void batchGetOrderPerformance_collectsItemsByOrderId() {
        OrderPerformanceDTO d1 = new OrderPerformanceDTO();
        d1.setOrderId("order-1");
        d1.setFinalChannelId("c1");
        d1.setIsValid(true);
        OrderPerformanceDTO d2 = new OrderPerformanceDTO();
        d2.setOrderId("order-2");
        d2.setFinalChannelId("c2");
        d2.setIsValid(true);

        when(performanceQueryService.batchGetOrderPerformance(
                eq(Arrays.asList("order-1", "order-2")), any(PerformanceAccessContext.class)))
                .thenReturn(Arrays.asList(d1, d2));

        OrderPerformanceBatchResponse response = facade.batchGetOrderPerformance(
                Arrays.asList("order-1", "order-2"), ctx());

        assertThat(response.getItems()).hasSize(2);
        assertThat(response.getItems().get(0).getOrderId()).isEqualTo("order-1");
        assertThat(response.getItems().get(0).getFinalChannelId()).isEqualTo("c1");
        assertThat(response.getItems().get(1).getOrderId()).isEqualTo("order-2");
        assertThat(response.getItems().get(1).getFinalChannelId()).isEqualTo("c2");
    }

    @Test
    void batchGetOrderPerformance_returnsEmptyForEmptyInput() {
        OrderPerformanceBatchResponse response = facade.batchGetOrderPerformance(
                Collections.emptyList(), ctx());
        assertThat(response.getItems()).isEmpty();

        response = facade.batchGetOrderPerformance(null, ctx());
        assertThat(response.getItems()).isEmpty();
    }

    @Test
    void batchGetOrderPerformance_returnsEmptyWhenServiceThrows() {
        when(performanceQueryService.batchGetOrderPerformance(any(), any()))
                .thenThrow(new RuntimeException("boom"));
        OrderPerformanceBatchResponse response = facade.batchGetOrderPerformance(
                Arrays.asList("order-1"), ctx());
        assertThat(response.getItems()).isEmpty();
    }

    @Test
    void getPerformanceSummary_delegatesToSummaryService() {
        PerformanceSummaryResponse summary = new PerformanceSummaryResponse();
        when(performanceSummaryService.getSummary(any(PerformanceSummaryQuery.class), any()))
                .thenReturn(summary);
        PerformanceSummaryResponse result = facade.getPerformanceSummary(
                new PerformanceSummaryQuery(), ctx());
        assertThat(result).isSameAs(summary);
    }

    @Test
    void getPerformanceSummary_returnsEmptyWhenServiceThrows() {
        when(performanceSummaryService.getSummary(any(), any()))
                .thenThrow(new RuntimeException("boom"));
        PerformanceSummaryResponse result = facade.getPerformanceSummary(
                new PerformanceSummaryQuery(), ctx());
        assertThat(result).isNotNull();
    }
}