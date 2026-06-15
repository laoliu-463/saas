package com.colonel.saas.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.colonel.saas.entity.ProductActivitySyncState;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 活动级商品同步状态 Mapper。
 */
@Mapper
public interface ProductActivitySyncStateMapper extends BaseMapper<ProductActivitySyncState> {

    @Insert("""
            INSERT INTO product_activity_sync_state (
                id, activity_id, scope, last_success_at, last_attempt_at, last_status,
                last_stop_reason, last_cursor, last_page, last_fetched_rows,
                last_distinct_product_ids, last_inserted, last_updated, last_skipped,
                last_failed, consecutive_failures, last_error_message,
                create_time, update_time, deleted
            ) VALUES (
                #{state.id}, #{state.activityId}, #{state.scope}, #{state.lastSuccessAt}, #{state.lastAttemptAt},
                #{state.lastStatus}, #{state.lastStopReason}, #{state.lastCursor}, #{state.lastPage},
                #{state.lastFetchedRows}, #{state.lastDistinctProductIds}, #{state.lastInserted},
                #{state.lastUpdated}, #{state.lastSkipped}, #{state.lastFailed}, #{state.consecutiveFailures},
                #{state.lastErrorMessage}, #{state.createTime}, #{state.updateTime}, 0
            )
            ON CONFLICT (activity_id, scope) DO UPDATE SET
                last_success_at = COALESCE(EXCLUDED.last_success_at, product_activity_sync_state.last_success_at),
                last_attempt_at = EXCLUDED.last_attempt_at,
                last_status = EXCLUDED.last_status,
                last_stop_reason = EXCLUDED.last_stop_reason,
                last_cursor = EXCLUDED.last_cursor,
                last_page = EXCLUDED.last_page,
                last_fetched_rows = EXCLUDED.last_fetched_rows,
                last_distinct_product_ids = EXCLUDED.last_distinct_product_ids,
                last_inserted = EXCLUDED.last_inserted,
                last_updated = EXCLUDED.last_updated,
                last_skipped = EXCLUDED.last_skipped,
                last_failed = EXCLUDED.last_failed,
                consecutive_failures = EXCLUDED.consecutive_failures,
                last_error_message = EXCLUDED.last_error_message,
                update_time = EXCLUDED.update_time,
                deleted = 0
            """)
    int upsert(@Param("state") ProductActivitySyncState state);
}
