package com.colonel.saas.domain.event;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Optional;
import java.util.UUID;

@Mapper
public interface DomainEventConsumeLogMapper extends BaseMapper<DomainEventConsumeLog> {

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
