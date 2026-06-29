package com.colonel.saas.domain.product.infrastructure;

import com.colonel.saas.domain.product.query.ProductBackfillJobStatusRepository;
import com.colonel.saas.domain.product.query.ProductBackfillJobStatusSnapshot;
import com.colonel.saas.entity.ProductSyncJobLog;
import com.colonel.saas.mapper.ProductSyncJobLogMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * MyBatis-backed 活动商品 backfill job 状态读侧适配器。
 */
@Repository
public class ProductBackfillJobStatusMapperRepository implements ProductBackfillJobStatusRepository {

    private final ProductSyncJobLogMapper jobLogMapper;

    public ProductBackfillJobStatusMapperRepository(ProductSyncJobLogMapper jobLogMapper) {
        this.jobLogMapper = jobLogMapper;
    }

    @Override
    public Optional<ProductBackfillJobStatusSnapshot> findLatestByJobId(String jobId) {
        return Optional.ofNullable(jobLogMapper.selectLatestByJobId(jobId))
                .map(this::toSnapshot);
    }

    private ProductBackfillJobStatusSnapshot toSnapshot(ProductSyncJobLog job) {
        return new ProductBackfillJobStatusSnapshot(
                job.getJobId(),
                job.getStatus(),
                job.getDryRun(),
                job.getScope(),
                job.getActivitiesScanned(),
                job.getActivitiesSuccess(),
                job.getActivitiesIncomplete(),
                job.getActivitiesFailed(),
                job.getApiFetchedRows(),
                job.getApiDistinctProductIds(),
                job.getInserted(),
                job.getUpdated(),
                job.getSkipped(),
                job.getFailed(),
                job.getRequestParamsJson(),
                job.getStopReasonStatsJson(),
                job.getStartedAt(),
                job.getFinishedAt());
    }
}
