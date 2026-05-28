package com.colonel.saas.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 达人画像同步表结构初始化引导。
 * <p>
 * 应用启动时自动执行 {@code db/alter-talent-profile-sync.sql} 脚本，
 * 确保达人画像同步相关的数据库表结构已创建或迁移。
 * 通过 {@code talent.profile.schema-bootstrap.enabled} 配置项控制是否启用（默认启用）。
 * </p>
 *
 * <ul>
 *     <li>启动时读取 classpath SQL 脚本并逐条执行（{@link #run}）</li>
 *     <li>按分号拆分 SQL 语句，跳过注释和空行（{@link #splitStatements}）</li>
 * </ul>
 *
 * <p><b>业务域：</b>达人域 — 画像同步表结构迁移</p>
 * <p><b>协作关系：</b></p>
 * <ul>
 *     <li>{@link JdbcTemplate} — SQL 执行引擎</li>
 * </ul>
 */
@Slf4j
@Component
@ConditionalOnProperty(
        name = "talent.profile.schema-bootstrap.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class TalentProfileSchemaBootstrap implements ApplicationRunner {

    /** JDBC 模板，用于执行 DDL/DML 语句 */
    private final JdbcTemplate jdbcTemplate;

    public TalentProfileSchemaBootstrap(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 应用启动时执行达人画像同步表结构迁移脚本。
     * <p>处理流程：</p>
     * <ol>
     *     <li>从 classpath 加载 {@code db/alter-talent-profile-sync.sql}</li>
     *     <li>将 SQL 按分号拆分为独立语句</li>
     *     <li>逐条执行，确保表结构就绪</li>
     * </ol>
     *
     * @param args 应用启动参数
     * @throws Exception SQL 脚本读取或执行异常
     */
    @Override
    public void run(ApplicationArguments args) throws Exception {
        // 第一步：从 classpath 加载 SQL 脚本
        ClassPathResource resource = new ClassPathResource("db/alter-talent-profile-sync.sql");
        String sql = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        // 第二步：逐条执行 SQL 语句
        for (String statement : splitStatements(sql)) {
            jdbcTemplate.execute(statement);
        }
        log.info("Talent profile sync schema ensured");
    }

    /**
     * 将 SQL 脚本按分号拆分为独立语句。
     * <p>跳过以 {@code --} 开头的注释行和空行，按分号结尾分割。
     * 处理不以分号结尾的尾部语句。</p>
     *
     * @param sql 原始 SQL 脚本文本
     * @return 拆分后的 SQL 语句列表（不含末尾分号）
     */
    static List<String> splitStatements(String sql) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        // 逐行解析，跳过注释和空行，按分号分割语句
        for (String line : sql.split("\\R")) {
            String trimmed = line.trim();
            // 跳过 SQL 注释行和空行
            if (trimmed.startsWith("--") || trimmed.isEmpty()) {
                continue;
            }
            current.append(line).append('\n');
            if (trimmed.endsWith(";")) {
                // 移除末尾分号，提取有效语句
                String statement = current.toString().trim();
                if (statement.endsWith(";")) {
                    statement = statement.substring(0, statement.length() - 1).trim();
                }
                if (!statement.isEmpty()) {
                    statements.add(statement);
                }
                current.setLength(0);
            }
        }
        // 处理不以分号结尾的尾部语句
        String tail = current.toString().trim();
        if (!tail.isEmpty()) {
            if (tail.endsWith(";")) {
                tail = tail.substring(0, tail.length() - 1).trim();
            }
            statements.add(tail);
        }
        return statements;
    }
}
