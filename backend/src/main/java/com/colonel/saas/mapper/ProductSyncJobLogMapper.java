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
}
