package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.entity.OperationLog;
import com.colonel.saas.mapper.OperationLogMapper;
import com.colonel.saas.domain.user.facade.UserDomainFacade;
import com.colonel.saas.common.web.RequestIdContext;
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

/**
 * 操作日志服务。
 * <p>
 * 负责系统操作日志的记录、查询和清理。操作日志采用 PostgreSQL 原生分区表设计（按月分区），
 * 写入时通过 {@link #ensureLogPartition(LocalDateTime)} 自动创建目标月份的分区表及索引，
 * 清理时通过 {@link #cleanupOldPartitions(int)} 按分区粒度 DROP 旧表（而非逐行 DELETE）。
 * </p>
 * <p>
 * 日志写入使用 {@link JdbcTemplate} 原生 SQL（绕过 MyBatis-Plus），以支持 jsonb 字段的
 * CAST 转换和分区键感知。查询使用 MyBatis-Plus 的分页能力。
 * </p>
 * <p>
 * 分区管理使用进程内 {@link ConcurrentHashMap} 做幂等保护，避免同一分区内并发 DDL。
 * </p>
 *
 * @see OperationLog 操作日志实体
 * @see com.colonel.saas.security.OperationLogInterceptor 操作日志 AOP 拦截器
 */
@Service
public class OperationLogService {

    /** 分区表名称日期格式（如 {@code op_log_2026_05}） */
    private static final DateTimeFormatter PARTITION_MONTH = DateTimeFormatter.ofPattern("yyyy_MM");
    /** 已确保存在的分区名称集合（进程级幂等，避免重复 DDL） */
    private static final Set<String> ENSURED_PARTITIONS = ConcurrentHashMap.newKeySet();

    /** 操作日志 Mapper（MyBatis-Plus，用于分页查询） */
    private final OperationLogMapper operationLogMapper;
    /** 系统用户门面（用于根据 userId 反查用户名） */
    private final UserDomainFacade userDomainFacade;
    /** JDBC 模板（用于原生 SQL 写入和分区 DDL） */
    private final JdbcTemplate jdbcTemplate;
    /** JSON 序列化工具（用于将 Map/Object 字段序列化为 jsonb） */
    private final ObjectMapper objectMapper;

    public OperationLogService(
            OperationLogMapper operationLogMapper,
            @org.springframework.context.annotation.Lazy UserDomainFacade userDomainFacade,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper) {
        this.operationLogMapper = operationLogMapper;
        this.userDomainFacade = userDomainFacade;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 记录一条操作日志。
     * <p>
     * 处理流程：
     * <ol>
     *   <li>校验日志对象非空，自动生成 UUID 主键和创建时间（若未设置）</li>
     *   <li>调用 {@link #ensureLogPartition(LocalDateTime)} 确保目标月份分区表存在</li>
     *   <li>通过 {@link JdbcTemplate} 原生 SQL 写入，使用 CAST 转换 jsonb 字段</li>
     * </ol>
     * </p>
     *
     * @param log 待记录的操作日志实体，包含请求/响应/操作者等完整上下文
     * @see OperationLogInterceptor 由 AOP 拦截器在请求完成后自动调用
     */
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
        if (!StringUtils.hasText(log.getTraceId())) {
            log.setTraceId(RequestIdContext.current());
        }
        ensureLogPartition(log.getCreateTime());
        String sql = """
                INSERT INTO operation_log (
                    id, user_id, username, module, action, target_type, target_id, target_name, content,
                    request_method, request_url, request_params, request_body, response_code, response_body,
                    ip_address, user_agent, duration_ms, error_code, error_message, trace_id, create_time, deleted
                ) VALUES (
                    ?, ?, ?, ?, ?, ?, ?, ?, ?,
                    ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), ?, CAST(? AS jsonb),
                    ?, ?, ?, ?, ?, ?, ?, ?
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
                log.getErrorCode(),
                log.getErrorMessage(),
                log.getTraceId(),
                log.getCreateTime(),
                log.getDeleted()
        );
    }

    /**
     * 记录一条系统操作日志（便捷方法）。
     * <p>
     * 由后台任务、定时作业或系统自动触发的场景调用。自动通过 {@link #resolveUsername(UUID)}
     * 反查操作者用户名，其余字段由调用方显式传入。
     * </p>
     *
     * @param operatorId   操作者用户 ID（可为 null，表示系统自动操作）
     * @param module       操作模块标识（如 "ORDER"、"TALENT"、"SAMPLE"）
     * @param action       操作动作（如 "SYNC"、"CREATE"、"UPDATE"）
     * @param requestMethod HTTP 请求方法（如 "POST"、"PUT"），系统操作可传 null
     * @param targetType   操作目标类型（如 "Order"、"Talent"）
     * @param targetId     操作目标 ID
     * @param targetName   操作目标名称（用于日志列表展示）
     * @param content      操作描述内容
     * @see #record(OperationLog) 底层写入方法
     */
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

    /**
     * 分页查询操作日志。
     * <p>
     * 支持按模块、动作、用户名、请求方法组合过滤。默认查询最近 89 天的日志，
     * 结果按创建时间倒序排列。
     * </p>
     *
     * @param module        模糊匹配模块名（如 "ORDER"、"TALENT"），为 null 则不过滤
     * @param action        模糊匹配动作名（如 "CREATE"、"SYNC"），为 null 则不过滤
     * @param username      模糊匹配用户名，为 null 则不过滤
     * @param requestMethod 精确匹配 HTTP 请求方法（如 "POST"、"PUT"），为 null 则不过滤
     * @param startDate     查询起始日期（含），为 null 默认最近 89 天
     * @param endDate       查询截止日期（不含次日），为 null 默认当前日期+1
     * @param page          页码（从 1 开始）
     * @param size          每页条数
     * @return 分页结果，包含日志列表和总数
     */
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

    /**
     * 根据用户 ID 反查用户名。
     * <p>
     * 用于系统操作日志场景，当操作者 ID 已知但用户名未显式传入时，
     * 通过 {@link UserDomainFacade} 查询用户主数据获取用户名。
     * 若用户不存在或 operatorId 为 null，则返回 null。
     * </p>
     *
     * @param operatorId 操作者用户 ID，可为 null
     * @return 用户名字符串，查无结果时返回 null
     */
    private String resolveUsername(java.util.UUID operatorId) {
        if (operatorId == null) {
            return null;
        }
        return userDomainFacade.getUsername(operatorId);
    }

    /**
     * 将对象序列化为 JSON 字符串。
     * <p>
     * 用于日志写入时将 requestParams、requestBody、responseBody 等字段
     * 转换为 PostgreSQL jsonb 类型所需的 JSON 字符串格式。
     * 若序列化失败，抛出 {@link IllegalStateException}。
     * </p>
     *
     * @param value 待序列化的对象，可为 null
     * @return JSON 字符串，若输入为 null 则返回 null
     * @throws IllegalStateException 序列化失败时抛出
     */
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

    /**
     * 确保指定月份的操作日志分区表存在。
     * <p>
     * 处理流程：
     * <ol>
     *   <li>根据 createTime 计算目标月份的分区名称（如 {@code op_log_2026_05}）</li>
     *   <li>通过 {@link #ENSURED_PARTITIONS} 进程级 Set 做幂等检查，避免重复 DDL</li>
     *   <li>若分区不存在，通过 {@link JdbcTemplate} 执行 {@code CREATE TABLE IF NOT EXISTS} 创建分区</li>
     *   <li>为分区表创建 create_time、user_id、module 三个索引</li>
     *   <li>若 DDL 执行失败，从 ENSURED_PARTITIONS 中移除，允许后续重试</li>
     * </ol>
     * </p>
     *
     * @param createTime 日志的创建时间，用于确定目标月份；若为 null 则使用当前时间
     */
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

    /**
     * 查询 operation_log 主表的所有分区表名称。
     * <p>
     * 通过查询 PostgreSQL 系统目录 {@code pg_inherits}、{@code pg_class} 获取
     * 继承自 operation_log 的所有子分区表名称，用于后续按月清理过期分区。
     * </p>
     *
     * @return 分区表名称列表（如 ["op_log_2026_03", "op_log_2026_04"]），无分区时返回空列表
     */
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

    /**
     * 从分区表名称中解析出对应的年月信息。
     * <p>
     * 分区表命名格式为 {@code op_log_yyyy_MM}（如 {@code op_log_2026_05}），
     * 本方法提取 {@code op_log_} 前缀后的日期部分并解析为 {@link YearMonth}。
     * 若名称为空、不以 op_log_ 开头或日期格式不合法，返回 null。
     * </p>
     *
     * @param partitionName 分区表名称（如 "op_log_2026_05"）
     * @return 对应的 YearMonth，解析失败时返回 null
     */
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
