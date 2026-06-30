package com.colonel.saas.domain.product.infrastructure;

import com.colonel.saas.domain.product.application.port.ProductActivitySyncSchedulePort;
import com.colonel.saas.mapper.ColonelsettlementActivityMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * MyBatis-backed 活动商品定时同步候选活动读取适配器。
 */
@Component
public class ProductActivitySyncScheduleMapperAdapter implements ProductActivitySyncSchedulePort {

    private final ColonelsettlementActivityMapper activityMapper;

    public ProductActivitySyncScheduleMapperAdapter(ColonelsettlementActivityMapper activityMapper) {
        this.activityMapper = activityMapper;
    }

    @Override
    public List<String> findActivitiesDueForSync(int limit, LocalDateTime lastSyncedBefore) {
        return activityMapper.selectActiveActivityIds(limit, lastSyncedBefore);
    }
}
