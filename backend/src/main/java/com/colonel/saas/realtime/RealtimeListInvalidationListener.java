package com.colonel.saas.realtime;

import com.colonel.saas.domain.product.event.ActivitySyncCompletedEvent;
import com.colonel.saas.domain.product.event.ProductHiddenEvent;
import com.colonel.saas.domain.product.event.ProductListedEvent;
import com.colonel.saas.domain.product.event.ProductOwnerChangedEvent;
import com.colonel.saas.event.OrderSyncedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 将后端领域事件转换为前端列表失效通知。
 */
@Component
public class RealtimeListInvalidationListener {

    private static final String TOPIC_ORDERS = "orders";
    private static final String TOPIC_PRODUCTS = "products";

    private final RealtimeUpdateService realtimeUpdateService;

    public RealtimeListInvalidationListener(RealtimeUpdateService realtimeUpdateService) {
        this.realtimeUpdateService = realtimeUpdateService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onOrderSynced(OrderSyncedEvent event) {
        realtimeUpdateService.publish(TOPIC_ORDERS, "ORDER_SYNCED", event.orderId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onActivitySyncCompleted(ActivitySyncCompletedEvent event) {
        realtimeUpdateService.publish(TOPIC_PRODUCTS, "ACTIVITY_SYNC_COMPLETED", event.activityId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onProductListed(ProductListedEvent event) {
        realtimeUpdateService.publish(TOPIC_PRODUCTS, "PRODUCT_LISTED", event.productId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onProductHidden(ProductHiddenEvent event) {
        realtimeUpdateService.publish(TOPIC_PRODUCTS, "PRODUCT_HIDDEN", event.productId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onProductOwnerChanged(ProductOwnerChangedEvent event) {
        realtimeUpdateService.publish(TOPIC_PRODUCTS, "PRODUCT_OWNER_CHANGED", event.productId());
    }
}
