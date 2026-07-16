package com.colonel.saas.listener;

import com.colonel.saas.config.OrderDerivedCacheKeys;
import com.colonel.saas.domain.talent.application.TalentClaimApplicationService;
import com.colonel.saas.event.OrderSyncedEvent;
import com.colonel.saas.service.ShortTtlCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderSyncedEventListenerTest {

    @Mock private ShortTtlCacheService shortTtlCacheService;
    @Mock private TalentClaimApplicationService talentClaimApplicationService;

    private OrderSyncedEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new OrderSyncedEventListener(
                shortTtlCacheService,
                talentClaimApplicationService);
    }

    @Test
    void onOrderSynced_shouldOnlyEvictDerivedCachesAndExtendTalentProtection() {
        OrderSyncedEvent event = event(1, "event_uid", Map.of("author_id", " dy_author "));

        listener.onOrderSynced(event);

        verify(shortTtlCacheService).evictByPrefix(OrderDerivedCacheKeys.DASHBOARD_SUMMARY_PREFIX);
        verify(shortTtlCacheService).evictByPrefix(OrderDerivedCacheKeys.DASHBOARD_METRICS_PREFIX);
        verify(shortTtlCacheService).evictByPrefix(OrderDerivedCacheKeys.ORDER_STATS_PREFIX);
        verify(talentClaimApplicationService).extendActiveClaimProtectionByTalentUid(eq("dy_author"), any(LocalDateTime.class));
    }

    @Test
    void onOrderSynced_shouldSkipTalentProtectionResetForCancelledOrder() {
        OrderSyncedEvent event = event(4, "event_uid", Map.of("author_id", "dy_author"));

        listener.onOrderSynced(event);

        verify(talentClaimApplicationService, never()).extendActiveClaimProtectionByTalentUid(any(), any());
    }

    private static OrderSyncedEvent event(Integer orderStatus, String talentUid, Map<String, Object> extraData) {
        return new OrderSyncedEvent(
                "ORD-1",
                UUID.randomUUID(),
                true,
                "ATTRIBUTED",
                100L,
                100L,
                100L,
                10L,
                10L,
                1L,
                1L,
                10L,
                1L,
                0L,
                orderStatus,
                LocalDateTime.now(),
                talentUid,
                extraData);
    }
}
