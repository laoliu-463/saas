package com.colonel.saas.service;

import com.colonel.saas.entity.PerformanceRecord;
import com.colonel.saas.event.PerformanceCalculatedEvent;
import com.colonel.saas.event.PerformanceSummaryRefreshedEvent;
import com.colonel.saas.mapper.PerformanceRecordMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardPerformanceSummaryServiceTest {

    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private PerformanceRecordMapper performanceRecordMapper;

    private DashboardPerformanceSummaryService service;

    @BeforeEach
    void setUp() {
        service = new DashboardPerformanceSummaryService(jdbcTemplate, eventPublisher, performanceRecordMapper);
    }

    @Test
    void applyPerformanceCalculated_shouldRebuildDailySummaryFromPerformanceRecord() {
        PerformanceRecord record = performanceRecord(
                "ORD-1", LocalDateTime.of(2026, 7, 16, 10, 0), 1000L, 720L, true);
        when(performanceRecordMapper.findByOrderId("ORD-1")).thenReturn(record);

        service.applyPerformanceCalculated(new PerformanceCalculatedEvent("ORD-1", 100L, 90L, false));

        verify(jdbcTemplate).update(contains("FROM performance_records"),
                eq(LocalDate.of(2026, 7, 16)), eq(LocalDate.of(2026, 7, 16)));
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isInstanceOf(PerformanceSummaryRefreshedEvent.class);
        PerformanceSummaryRefreshedEvent event = (PerformanceSummaryRefreshedEvent) eventCaptor.getValue();
        assertThat(event.orderId()).isEqualTo("ORD-1");
        assertThat(event.statDate()).isEqualTo(LocalDate.of(2026, 7, 16));
    }

    @Test
    void applyPerformanceCalculated_shouldIgnoreMissingPerformanceRecord() {
        when(performanceRecordMapper.findByOrderId("MISSING")).thenReturn(null);

        service.applyPerformanceCalculated(new PerformanceCalculatedEvent("MISSING", 0L, 0L, false));

        verifyNoInteractions(jdbcTemplate, eventPublisher);
    }

    private PerformanceRecord performanceRecord(
            String orderId, LocalDateTime orderCreateTime, long payAmount, long serviceFeeProfit, boolean valid) {
        PerformanceRecord record = new PerformanceRecord();
        record.setOrderId(orderId);
        record.setOrderCreateTime(orderCreateTime);
        record.setPayAmount(payAmount);
        record.setEffectiveServiceProfit(serviceFeeProfit);
        record.setValid(valid);
        return record;
    }
}
