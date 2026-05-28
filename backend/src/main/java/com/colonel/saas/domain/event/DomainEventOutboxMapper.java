package com.colonel.saas.domain.event;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.UUID;

/**
 * 领域事件 Outbox 表的 MyBatis Mapper 接口。
 *
 * <p>除继承 {@link BaseMapper} 提供的通用 CRUD 外，本接口定义了
 * Outbox 模式特有的三个核心操作：</p>
 * <ul>
 *   <li>{@link #lockPendingEvents} — 乐观并发拉取待处理事件（SELECT ... FOR UPDATE SKIP LOCKED）</li>
 *   <li>{@link #updateDispatchResult} — 回写投递结果（状态、重试次数、错误信息）</li>
 *   <li>{@link #resetToPending} — 将 DEAD 事件重置为 PENDING，支持人工重放</li>
 * </ul>
 */
@Mapper
public interface DomainEventOutboxMapper extends BaseMapper<DomainEventOutbox> {

    /**
     * 批量锁定待处理事件，支持并发安全的事件拉取。
     *
     * <p>使用 {@code FOR UPDATE SKIP LOCKED} 保证多实例并发拉取时
     * 同一事件不会被重复处理——若行已被其他事务锁定则跳过，避免死锁。</p>
     *
     * <p>筛选条件：
     * <ul>
     *   <li>状态为 PENDING 或 FAILED（可重试）</li>
     *   <li>重试次数未超过 maxRetry（表字段或入参默认值）</li>
     *   <li>next_retry_at 为空或已到达（退避窗口已过）</li>
     * </ul>
     * 按发生时间升序排列，确保先发生的事件优先处理。</p>
     *
     * @param maxRetry 最大重试次数（当表字段 max_retry 为 NULL 时的回退默认值）
     * @param limit    本次最多拉取的事件条数，防止一次加载过多
     * @return 待处理事件列表（已被当前事务锁定）
     */
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

    /**
     * 更新事件的投递结果，由派发器在投递完成后调用。
     *
     * <p>投递成功时设置 status=PUBLISHED、publishedAt=now，errorMessage 清空。
     * 投递失败时设置 status=FAILED/DEAD、errorMessage、nextRetryAt（指数退避）。</p>
     *
     * @param eventId       事件唯一标识
     * @param status        目标状态（PUBLISHED / FAILED / DEAD）
     * @param retryCount    更新后的重试次数
     * @param errorMessage  错误信息（成功时传 null）
     * @param publishedAt   发布时间（成功时传 LocalDateTime.now()，失败时传 null）
     * @param nextRetryAt   下次重试时间（失败时计算，成功时传 null）
     * @return 受影响行数（通常为 1）
     */
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

    /**
     * 将已死亡（DEAD）的事件重置为待处理（PENDING），用于运维手动重放。
     *
     * <p>重置后 retry_count 归零、error_message 清空、next_retry_at 设为当前时间，
     * 使事件可以被下一轮拉取重新处理。</p>
     *
     * @param eventId 事件唯一标识
     * @return 受影响行数（通常为 1）
     */
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
