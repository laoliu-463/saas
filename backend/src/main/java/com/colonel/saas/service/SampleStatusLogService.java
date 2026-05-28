package com.colonel.saas.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 寄样状态变更日志服务。
 * <p>
 * 负责记录寄样申请的每一次状态变更，使用原生 JDBC 写入 sample_status_log 表，
 * 不经过 MyBatis-Plus 以保证写入性能和事务一致性。
 * </p>
 *
 * <ul>
 *     <li>单条状态变更日志记录（{@link #log}）</li>
 *     <li>批量状态变更日志记录（{@link #logBatch}）</li>
 * </ul>
 *
 * <p><b>业务域：</b>寄样域 — 状态变更审计</p>
 * <p><b>协作关系：</b></p>
 * <ul>
 *     <li>{@link SampleLogisticsImportService} — 物流导入时记录状态变更</li>
 *     <li>{@link SampleLogisticsSyncService} — 物流同步时记录状态变更</li>
 * </ul>
 */
@Service
public class SampleStatusLogService {

    /**
     * 批量日志条目。
     *
     * @param requestId  寄样申请 ID
     * @param fromStatus 原状态
     * @param toStatus   目标状态
     * @param operatorId 操作人 ID
     * @param remark     变更备注
     */
    public record LogEntry(UUID requestId, Integer fromStatus, Integer toStatus, UUID operatorId, String remark) {
    }

    /** Spring JDBC 模板 */
    private final JdbcTemplate jdbcTemplate;

    public SampleStatusLogService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 记录单条寄样状态变更日志。
     * <p>自动生成 UUID 主键和操作时间，直接通过 JDBC INSERT 写入。</p>
     *
     * @param requestId  寄样申请 ID
     * @param fromStatus 原状态码
     * @param toStatus   目标状态码
     * @param operatorId 操作人 ID
     * @param remark     变更备注
     */
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

    /**
     * 批量记录寄样状态变更日志。
     * <p>使用 JDBC 批量更新提升写入性能，空列表直接跳过。</p>
     *
     * @param entries 批量日志条目列表
     */
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
