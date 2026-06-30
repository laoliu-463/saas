package com.colonel.saas.domain.product.application.port;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 活动商品定时同步候选活动读取端口。
 */
public interface ProductActivitySyncSchedulePort {

    List<String> findActivitiesDueForSync(int limit, LocalDateTime lastSyncedBefore);
}
