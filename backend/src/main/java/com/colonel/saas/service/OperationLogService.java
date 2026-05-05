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
            jdbcTemplate.execute(String.format(
                    "CREATE TABLE IF NOT EXISTS %s PARTITION OF operation_log FOR VALUES FROM ('%s') TO ('%s')",
                    partitionName,
                    start,
                    end
            ));
            jdbcTemplate.execute(String.format("CREATE INDEX IF NOT EXISTS idx_%s_create_time ON %s (create_time)", partitionName, partitionName));
            jdbcTemplate.execute(String.format("CREATE INDEX IF NOT EXISTS idx_%s_user_id ON %s (user_id)", partitionName, partitionName));
            jdbcTemplate.execute(String.format("CREATE INDEX IF NOT EXISTS idx_%s_module ON %s (module)", partitionName, partitionName));
        } catch (RuntimeException ex) {
            ENSURED_PARTITIONS.remove(partitionName);
            throw ex;
        }
    }
}
