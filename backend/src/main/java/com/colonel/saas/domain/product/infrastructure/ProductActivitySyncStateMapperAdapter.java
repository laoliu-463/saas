package com.colonel.saas.domain.product.infrastructure;

import com.colonel.saas.domain.product.application.port.ProductActivitySyncStatePort;
import com.colonel.saas.mapper.ColonelsettlementActivityMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis-backed 活动商品同步状态写入适配器。
 */
@Component
public class ProductActivitySyncStateMapperAdapter implements ProductActivitySyncStatePort {

    private final ColonelsettlementActivityMapper activityMapper;

    public ProductActivitySyncStateMapperAdapter(ColonelsettlementActivityMapper activityMapper) {
        this.activityMapper = activityMapper;
    }

    @Override
    public void markActivitySyncCompleted(String activityId, LocalDateTime completedAt) {
        activityMapper.touchLastSyncAt(activityId, completedAt);
    }
}
