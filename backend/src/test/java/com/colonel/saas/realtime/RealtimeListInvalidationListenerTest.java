package com.colonel.saas.realtime;

import com.colonel.saas.domain.product.event.ActivitySyncCompletedEvent;
import com.colonel.saas.domain.product.event.ProductListedEvent;
import com.colonel.saas.event.OrderSyncedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RealtimeListInvalidationListenerTest {

    @Mock
    private RealtimeUpdateService realtimeUpdateService;

    private RealtimeListInvalidationListener listener;

    @BeforeEach
    void setUp() {
        listener = new RealtimeListInvalidationListener(realtimeUpdateService);
    }

    @Test
    void onOrderSynced_shouldInvalidateOrdersTopicImmediately() {
        OrderSyncedEvent event = new OrderSyncedEvent(
                "ORDER-001",
                UUID.randomUUID(),
                true,
                "UNATTRIBUTED",
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                1,
                LocalDateTime.now(),
                null,
                Map.of());

        listener.onOrderSynced(event);

        verify(realtimeUpdateService).publish("orders", "ORDER_SYNCED", "ORDER-001");
    }

    @Test
    void onActivitySyncCompleted_shouldInvalidateProductsTopicImmediately() {
        ActivitySyncCompletedEvent event = new ActivitySyncCompletedEvent(
                UUID.randomUUID().toString(),
                "ACT-001",
                "六月活动",
                "FULL",
                3,
                9,
                0,
                "SUCCESS",
                null,
                LocalDateTime.now(),
                null);

        listener.onActivitySyncCompleted(event);

        verify(realtimeUpdateService).publish("products", "ACTIVITY_SYNC_COMPLETED", "ACT-001");
    }

    @Test
    void onProductListed_shouldInvalidateProductsTopicImmediately() {
        ProductListedEvent event = new ProductListedEvent(
                UUID.randomUUID(),
                "ACT-001",
                "PRODUCT-001",
                UUID.randomUUID(),
                null,
                LocalDateTime.now(),
                null);

        listener.onProductListed(event);

        verify(realtimeUpdateService).publish("products", "PRODUCT_LISTED", "PRODUCT-001");
    }
}
