package com.colonel.saas.domain.product.query;

import java.util.Optional;

/**
 * 活动商品 backfill job 状态读侧持久化端口。
 */
public interface ProductBackfillJobStatusRepository {

    Optional<ProductBackfillJobStatusSnapshot> findLatestByJobId(String jobId);
}
