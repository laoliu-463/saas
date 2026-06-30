package com.colonel.saas.domain.product.application.port;

import java.time.LocalDateTime;

/**
 * 活动商品同步状态写入端口。
 */
public interface ProductActivitySyncStatePort {

    void markActivitySyncCompleted(String activityId, LocalDateTime completedAt);
}
