package com.colonel.saas.domain.event;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.UUID;

@Mapper
public interface DomainEventOutboxMapper extends BaseMapper<DomainEventOutbox> {

    @Select("""
            SELECT *
            FROM domain_event_outbox
            WHERE status IN ('PENDING', 'FAILED')
              AND retry_count < COALESCE(max_retry, #{maxRetry})
              AND (next_retry_at IS NULL OR next_retry_at <= CURRENT_TIMESTAMP)
            ORDER BY occurred_at ASC
            LIMIT #{limit}
            FOR UPDATE SKIP LOCKED
            """)
    List<DomainEventOutbox> lockPendingEvents(@Param("maxRetry") int maxRetry, @Param("limit") int limit);

    @Update("""
            UPDATE domain_event_outbox
            SET status = #{status},
                retry_count = #{retryCount},
                error_message = #{errorMessage},
                published_at = #{publishedAt},
                next_retry_at = #{nextRetryAt}
            WHERE event_id = #{eventId}
            """)
    int updateDispatchResult(
            @Param("eventId") UUID eventId,
            @Param("status") String status,
            @Param("retryCount") int retryCount,
            @Param("errorMessage") String errorMessage,
            @Param("publishedAt") java.time.LocalDateTime publishedAt,
            @Param("nextRetryAt") java.time.LocalDateTime nextRetryAt);

    @Update("""
            UPDATE domain_event_outbox
            SET status = 'PENDING',
                retry_count = 0,
                error_message = NULL,
                next_retry_at = CURRENT_TIMESTAMP
            WHERE event_id = #{eventId}
            """)
    int resetToPending(@Param("eventId") UUID eventId);
}
