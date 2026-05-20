package com.colonel.saas.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class SampleStatusLogService {

    public record LogEntry(UUID requestId, Integer fromStatus, Integer toStatus, UUID operatorId, String remark) {
    }

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

    public void logBatch(List<LogEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.batchUpdate("""
                INSERT INTO sample_status_log (id, request_id, from_status, to_status, operator_id, operate_time, remark)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                entries,
                entries.size(),
                (ps, entry) -> {
                    ps.setObject(1, UUID.randomUUID());
                    ps.setObject(2, entry.requestId());
                    ps.setObject(3, entry.fromStatus());
                    ps.setObject(4, entry.toStatus());
                    ps.setObject(5, entry.operatorId());
                    ps.setObject(6, now);
                    ps.setObject(7, entry.remark());
                }
        );
    }
}
