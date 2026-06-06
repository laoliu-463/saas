package com.colonel.saas.service;

import com.colonel.saas.event.OrderSyncedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class DashboardPerformanceSummaryServiceTest {

    @Test
    void applyOrderSynced_shouldSkipExistingOrderUpdatesToAvoidDuplicateDailyTotals() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        DashboardPerformanceSummaryService service = new DashboardPerformanceSummaryService(jdbcTemplate);

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

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void applyOrderSynced_shouldBucketByOrderCreateDate() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        DashboardPerformanceSummaryService service = new DashboardPerformanceSummaryService(jdbcTemplate);

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
    }
}
