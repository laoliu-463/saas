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
}
