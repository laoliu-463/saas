package com.colonel.saas.mapper;

import com.colonel.saas.entity.ColonelActivitySyncJobLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 活动列表异步同步任务日志 Mapper。
 */
@Mapper
public interface ColonelActivitySyncJobLogMapper {

    void insert(ColonelActivitySyncJobLog jobLog);

    ColonelActivitySyncJobLog selectByJobId(@Param("jobId") String jobId);

    int updateStatus(
            @Param("jobId") String jobId,
            @Param("status") String status,
            @Param("finishedAt") LocalDateTime finishedAt,
            @Param("activitiesTotal") Integer activitiesTotal,
            @Param("activitiesSynced") Integer activitiesSynced,
            @Param("activitiesFailed") Integer activitiesFailed,
            @Param("errorMessage") String errorMessage,
            @Param("metadataJson") String metadataJson);

    int updateRunning(
            @Param("jobId") String jobId,
            @Param("startedAt") LocalDateTime startedAt);

    /**
     * 将超时的 RUNNING/QUEUED 任务标记为 ABANDONED。
     */
    int reconcileStaleJobs(
            @Param("staleBefore") LocalDateTime staleBefore);

    /**
     * P8.4 修复: 原子 claim 一个活跃任务 (PostgreSQL ON CONFLICT DO UPDATE).
     *
     * <p>配合 partial unique index {@code idx_colonel_activity_sync_job_log_active_scope}
     * (sync_type, scope) WHERE status IN ('QUEUED', 'RUNNING'):
     * <ul>
     *   <li>同 scope 无活跃任务: 插入新行, status=QUEUED, 返回 true</li>
     *   <li>同 scope 已有活跃任务: 复用旧 job_id, status 不变, 返回 false</li>
     * </ul>
     */
    boolean tryClaimActiveJob(
            @Param("jobId") String jobId,
            @Param("syncType") String syncType,
            @Param("scope") String scope,
            @Param("triggeredBy") UUID triggeredBy,
            @Param("createdAt") LocalDateTime createdAt);
}
