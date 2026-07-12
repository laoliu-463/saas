package com.colonel.saas.service;

import com.colonel.saas.event.OrderSyncedEvent;
import com.colonel.saas.event.PerformanceSummaryRefreshedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class DashboardPerformanceSummaryServiceTest {

    private JdbcTemplate jdbcTemplate;
    private ApplicationEventPublisher eventPublisher;
    private DashboardPerformanceSummaryService service;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        service = new DashboardPerformanceSummaryService(jdbcTemplate, eventPublisher);
    }

    @Test
    void applyOrderSynced_shouldSkipExistingOrderUpdatesToAvoidDuplicateDailyTotals() {
        service.applyOrderSynced(new OrderSyncedEvent(
                "ORDER-EXISTING-001",
                UUID.randomUUID(),
                false,
                "ATTRIBUTED",
                12800L,
                12800L,
                12000L,
                1200L,
                1100L,
                100L,
                90L,
                1200L,
                100L,
                200L,
                2,
                LocalDateTime.of(2026, 4, 17, 10, 30),
                null,
                Map.of()
        ));

        verifyNoInteractions(jdbcTemplate, eventPublisher);
    }

    @Test
    void applyOrderSynced_shouldBucketByOrderCreateDate() {
        service.applyOrderSynced(new OrderSyncedEvent(
                "ORDER-NEW-001",
                UUID.randomUUID(),
                true,
                "ATTRIBUTED",
                12800L,
                12800L,
                12000L,
                1200L,
                1100L,
                100L,
                90L,
                1200L,
                100L,
                200L,
                2,
                LocalDateTime.of(2026, 4, 17, 10, 30),
                null,
                Map.of()
        ));

        verify(jdbcTemplate).update(
                anyString(),
                eq(LocalDate.of(2026, 4, 17)),
                eq(12800L),
                eq(1100L));
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isInstanceOf(PerformanceSummaryRefreshedEvent.class);
        PerformanceSummaryRefreshedEvent published = (PerformanceSummaryRefreshedEvent) eventCaptor.getValue();
        assertThat(published.period()).isEqualTo("DAY");
        assertThat(published.statDate()).isEqualTo(LocalDate.of(2026, 4, 17));
        assertThat(published.orderAmountDelta()).isEqualTo(12800L);
        assertThat(published.serviceFeeNetDelta()).isEqualTo(1100L);
    }

    @Test
    void applyOrderSynced_shouldSkipRefundedOrders() {
        service.applyOrderSynced(new OrderSyncedEvent(
                "ORDER-REFUNDED-001",
                UUID.randomUUID(),
                true,
                "ATTRIBUTED",
                12800L,
                12800L,
                12000L,
                1200L,
                1100L,
                100L,
                90L,
                1200L,
                100L,
                200L,
                5,
                LocalDateTime.of(2026, 4, 17, 10, 30),
                null,
                Map.of()
        ));

        verifyNoInteractions(jdbcTemplate, eventPublisher);
    }

    @Test
    void applyOrderSynced_shouldUseSettlementServiceFeeExpenseForNetProfit() {
        service.applyOrderSynced(new OrderSyncedEvent(
                "ORDER-EXPENSE-001",
                UUID.randomUUID(),
                true,
                "ATTRIBUTED",
                12800L,
                12800L,
                12000L,
                1200L,
                1100L,
                300L,
                200L,
                100L,
                90L,
                1200L,
                100L,
                200L,
                2,
                LocalDateTime.of(2026, 4, 17, 10, 30),
                null,
                Map.of(),
                "P-1",
                "A-1",
                "SHOP-1",
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "DEFAULT",
                "usr_ABC_1712000000",
                LocalDateTime.of(2026, 4, 17, 10, 30),
                LocalDateTime.of(2026, 4, 20, 10, 30),
                false,
                LocalDateTime.of(2026, 4, 17, 10, 31)));

        verify(jdbcTemplate).update(
                anyString(),
                eq(LocalDate.of(2026, 4, 17)),
                eq(12800L),
                eq(900L));
    }
}
