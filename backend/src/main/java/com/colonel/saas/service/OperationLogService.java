package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.entity.OperationLog;
import com.colonel.saas.entity.SysUser;
import com.colonel.saas.mapper.OperationLogMapper;
import com.colonel.saas.mapper.SysUserMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OperationLogService {

    private static final DateTimeFormatter PARTITION_MONTH = DateTimeFormatter.ofPattern("yyyy_MM");
    private static final Set<String> ENSURED_PARTITIONS = ConcurrentHashMap.newKeySet();

    private final OperationLogMapper operationLogMapper;
    private final SysUserMapper sysUserMapper;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public OperationLogService(
            OperationLogMapper operationLogMapper,
            SysUserMapper sysUserMapper,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper) {
        this.operationLogMapper = operationLogMapper;
        this.sysUserMapper = sysUserMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public void record(OperationLog log) {
        if (log == null) {
            return;
        }
        if (log.getId() == null) {
            log.setId(java.util.UUID.randomUUID());
        }
        if (log.getCreateTime() == null) {
            log.setCreateTime(LocalDateTime.now());
        }
        if (log.getDeleted() == null) {
            log.setDeleted(0);
        }
        ensureLogPartition(log.getCreateTime());
        String sql = """
                INSERT INTO operation_log (
                    id, user_id, username, module, action, target_type, target_id, target_name, content,
                    request_method, request_url, request_params, request_body, response_code, response_body,
                    ip_address, user_agent, duration_ms, error_message, create_time, deleted
                ) VALUES (
                    ?, ?, ?, ?, ?, ?, ?, ?, ?,
                    ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), ?, CAST(? AS jsonb),
                    ?, ?, ?, ?, ?, ?
                )
                """;
        jdbcTemplate.update(
                sql,
                log.getId(),
                log.getUserId(),
                log.getUsername(),
                log.getModule(),
                log.getAction(),
                log.getTargetType(),
                log.getTargetId(),
                log.getTargetName(),
                log.getContent(),
                log.getRequestMethod(),
                log.getRequestUrl(),
                toJson(log.getRequestParams()),
                toJson(log.getRequestBody()),
                log.getResponseCode(),
                toJson(log.getResponseBody()),
                log.getIpAddress(),
                log.getUserAgent(),
                log.getDurationMs(),
                log.getErrorMessage(),
                log.getCreateTime(),
                log.getDeleted()
        );
    }

    @Transactional(rollbackFor = Exception.class)
    public void recordSystemAction(
            java.util.UUID operatorId,
            String module,
            String action,
            String requestMethod,
            String targetType,
            String targetId,
            String targetName,
            String content) {
        OperationLog log = new OperationLog();
        log.setUserId(operatorId);
        log.setUsername(resolveUsername(operatorId));
        log.setModule(module);
        log.setAction(action);
        log.setRequestMethod(requestMethod);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setTargetName(targetName);
        log.setContent(content);
        record(log);
    }

    public IPage<OperationLog> findPage(
            String module,
            String action,
            String username,
            String requestMethod,
            LocalDate startDate,
            LocalDate endDate,
            long page,
            long size) {
        LocalDateTime start = (startDate == null ? LocalDate.now().minusDays(89) : startDate).atStartOfDay();
        LocalDateTime end = (endDate == null ? LocalDate.now().plusDays(1) : endDate.plusDays(1)).atStartOfDay();

        QueryWrapper<OperationLog> wrapper = new QueryWrapper<>();
        wrapper.eq("deleted", 0)
                .ge("create_time", start)
                .lt("create_time", end);
        if (StringUtils.hasText(module)) {
            wrapper.like("module", module.trim());
        }
        if (StringUtils.hasText(action)) {
            wrapper.like("action", action.trim());
        }
        if (StringUtils.hasText(username)) {
            wrapper.like("username", username.trim());
        }
        if (StringUtils.hasText(requestMethod)) {
            wrapper.eq("request_method", requestMethod.trim().toUpperCase());
        }
        wrapper.orderByDesc("create_time");
        return operationLogMapper.selectPage(new Page<>(page, size), wrapper);
    }

    private String resolveUsername(java.util.UUID operatorId) {
        if (operatorId == null) {
            return null;
        }
        SysUser user = sysUserMapper.selectById(operatorId);
        return user == null ? null : user.getUsername();
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("serialize operation log json failed", e);
        }
    }

    private void ensureLogPartition(LocalDateTime createTime) {
        LocalDateTime target = createTime == null ? LocalDateTime.now() : createTime;
        YearMonth month = YearMonth.from(target);
        String partitionName = "op_log_" + month.format(PARTITION_MONTH);
        if (!ENSURED_PARTITIONS.add(partitionName)) {
            return;
        }
        LocalDate start = month.atDay(1);
        LocalDate end = month.plusMonths(1).atDay(1);
        try {
            String createPartitionSql = String.format(
                    "CREATE TABLE IF NOT EXISTS %s PARTITION OF operation_log FOR VALUES FROM ('%s') TO ('%s')",
                    partitionName,
                    start,
                    end
            );
            jdbcTemplate.execute(Objects.requireNonNull(createPartitionSql));
            String idxCreateTimeSql = String.format("CREATE INDEX IF NOT EXISTS idx_%s_create_time ON %s (create_time)", partitionName, partitionName);
            String idxUserIdSql = String.format("CREATE INDEX IF NOT EXISTS idx_%s_user_id ON %s (user_id)", partitionName, partitionName);
            String idxModuleSql = String.format("CREATE INDEX IF NOT EXISTS idx_%s_module ON %s (module)", partitionName, partitionName);
            jdbcTemplate.execute(Objects.requireNonNull(idxCreateTimeSql));
            jdbcTemplate.execute(Objects.requireNonNull(idxUserIdSql));
            jdbcTemplate.execute(Objects.requireNonNull(idxModuleSql));
        } catch (RuntimeException ex) {
            ENSURED_PARTITIONS.remove(partitionName);
            throw ex;
        }
    }

    /**
     * 删除过期的操作日志分区（默认按分区月粒度清理）。
     * <p>
     * 注意：分区表数据删除优先用 DROP PARTITION（DROP TABLE 子分区）而非按行 DELETE，
     * 以避免全表 vacuum/锁放大。
     */
    @Transactional(rollbackFor = Exception.class)
    public int cleanupOldPartitions(int retentionDays) {
        int days = Math.max(retentionDays, 1);
        YearMonth cutoff = YearMonth.from(LocalDate.now().minusDays(days));

        List<String> partitions = listOperationLogPartitions();
        int dropped = 0;
        for (String name : partitions) {
            YearMonth month = parsePartitionMonth(name);
            if (month == null) {
                continue;
            }
            if (month.isBefore(cutoff)) {
                jdbcTemplate.execute("DROP TABLE IF EXISTS " + name);
                ENSURED_PARTITIONS.remove(name);
                dropped++;
            }
        }
        return dropped;
    }

    private List<String> listOperationLogPartitions() {
        String sql = """
                SELECT c.relname AS partition_name
                FROM pg_inherits i
                JOIN pg_class p ON i.inhparent = p.oid
                JOIN pg_class c ON i.inhrelid = c.oid
                WHERE p.relname = 'operation_log'
                """;
        List<String> result = new ArrayList<>();
        jdbcTemplate.query(sql, rs -> {
            String name = rs.getString("partition_name");
            if (StringUtils.hasText(name)) {
                result.add(name);
            }
        });
        return result;
    }

    private YearMonth parsePartitionMonth(String partitionName) {
        if (!StringUtils.hasText(partitionName)) {
            return null;
        }
        if (!partitionName.startsWith("op_log_")) {
            return null;
        }
        String month = partitionName.substring("op_log_".length());
        try {
            return YearMonth.parse(month, PARTITION_MONTH);
        } catch (Exception ignore) {
            return null;
        }
    }
}
