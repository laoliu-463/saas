package com.colonel.saas.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.colonel.saas.entity.ProductSyncJobLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 商品同步任务日志 Mapper。
 */
@Mapper
public interface ProductSyncJobLogMapper extends BaseMapper<ProductSyncJobLog> {

    /**
     * 查找超过阈值的 RUNNING 僵尸 job，Phase 4-1.5 用于定时清理。
     */
    @Select("""
            SELECT * FROM product_sync_job_log
            WHERE status = 'RUNNING'
              AND deleted = 0
              AND COALESCE(update_time, started_at) < #{threshold}
            ORDER BY started_at ASC
            """)
    List<ProductSyncJobLog> selectStaleRunningJobs(@Param("threshold") LocalDateTime threshold);

    /**
     * 把一个 RUNNING 僵尸 job 标为 ABANDONED（并写 finished_at），不删业务事实。
     */
    @Update("""
            UPDATE product_sync_job_log
            SET status = 'ABANDONED',
                finished_at = #{finishedAt},
                error_message = COALESCE(error_message, 'stale RUNNING job reconciled by StaleProductSyncJobReconcileJob'),
                update_time = #{finishedAt}
            WHERE id = #{id}
              AND status = 'RUNNING'
              AND deleted = 0
            """)
    int abandonStaleRunningJob(@Param("id") java.util.UUID id,
                                @Param("finishedAt") LocalDateTime finishedAt);

    /**
     * 按 jobId 查询最新一条任务记录（同类 jobId 应只会有一条有效记录）。
     */
    @Select("""
            SELECT * FROM product_sync_job_log
            WHERE deleted = 0
              AND job_id = #{jobId}
            ORDER BY create_time DESC
            LIMIT 1
            """)
    ProductSyncJobLog selectLatestByJobId(@Param("jobId") String jobId);

    /**
     * 查询同一任务类型和作用域下仍处于活动状态的最新任务。
     */
    @Select("""
            SELECT * FROM product_sync_job_log
            WHERE deleted = 0
              AND job_type = #{jobType}
              AND scope = #{scope}
              AND status IN ('QUEUED', 'RUNNING')
            ORDER BY create_time DESC
            LIMIT 1
            """)
    ProductSyncJobLog selectLatestActiveByJobTypeAndScope(@Param("jobType") String jobType,
                                                           @Param("scope") String scope);

    /**
     * 拉取待执行的手动同步队列任务。
     */
    @Select("""
            SELECT * FROM product_sync_job_log
            WHERE deleted = 0
              AND job_type = #{jobType}
              AND status = 'QUEUED'
            ORDER BY create_time ASC
            LIMIT #{limit}
            """)
    List<ProductSyncJobLog> selectQueuedJobs(@Param("jobType") String jobType,
                                             @Param("limit") int limit);

    /**
     * 把 QUEUED 任务原子切换为 RUNNING，防止重复 worker 同时执行同一 job。
     */
    @Update("""
            UPDATE product_sync_job_log
            SET status = 'RUNNING',
                started_at = #{startedAt},
                request_params_json = #{requestParamsJson},
                error_message = NULL,
                update_time = #{startedAt}
            WHERE id = #{id}
              AND status = 'QUEUED'
              AND deleted = 0
            """)
    int markQueuedJobRunning(@Param("id") java.util.UUID id,
                             @Param("startedAt") LocalDateTime startedAt,
                             @Param("requestParamsJson") String requestParamsJson);
}
