package com.colonel.saas.domain.event;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Optional;
import java.util.UUID;

/**
 * 领域事件消费日志 Mapper，提供消费幂等性检查的查询方法。
 *
 * <p>配合 {@link ConfigChangedEventRouter} 在分发前检查
 * 某消费者是否已成功消费过指定事件，避免重复处理。</p>
 */
@Mapper
public interface DomainEventConsumeLogMapper extends BaseMapper<DomainEventConsumeLog> {

    /**
     * 查询指定事件是否已被指定消费者成功消费过。
     *
     * <p>用于消费幂等性判断：若返回非空 Optional，则跳过该消费者的处理逻辑。</p>
     *
     * @param eventId      事件唯一标识
     * @param consumerName 消费者名称
     * @return 成功消费记录（存在则表示已消费过），不存在则返回 {@code Optional.empty()}
     */
    @Select("""
            SELECT *
            FROM domain_event_consume_log
            WHERE event_id = #{eventId}
              AND consumer_name = #{consumerName}
              AND status = 'SUCCESS'
            LIMIT 1
            """)
    Optional<DomainEventConsumeLog> findSuccessful(
            @Param("eventId") UUID eventId,
            @Param("consumerName") String consumerName);
}
