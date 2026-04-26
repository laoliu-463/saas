package com.colonel.saas.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class SampleStatusLogService {

    private final JdbcTemplate jdbcTemplate;

    public SampleStatusLogService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void log(UUID requestId, Integer fromStatus, Integer toStatus, UUID operatorId, String remark) {
        UUID id = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                INSERT INTO sample_status_log (id, request_id, from_status, to_status, operator_id, operate_time, remark)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                id,
                requestId,
                fromStatus,
                toStatus,
                operatorId,
                now,
                remark
        );
    }
}
