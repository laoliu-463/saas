package com.colonel.saas.domain.performance.application;

import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.entity.PerformanceRecord;
import com.colonel.saas.service.PerformanceCalculationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PerformanceCalculationApplicationServiceTest {

    @Mock
    private PerformanceCalculationService performanceCalculationService;

    private PerformanceCalculationApplicationService service;

    @BeforeEach
    void setUp() {
        service = new PerformanceCalculationApplicationService(performanceCalculationService);
    }

    @Test
    void upsertFromOrder_shouldDelegateToLegacyCalculationService() {
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        PerformanceRecord expected = new PerformanceRecord();
        when(performanceCalculationService.upsertFromOrder(order)).thenReturn(expected);

        PerformanceRecord actual = service.upsertFromOrder(order);

        assertThat(actual).isSameAs(expected);
        verify(performanceCalculationService).upsertFromOrder(order);
    }
}
