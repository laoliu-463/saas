package com.colonel.saas.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Properties;

/**
 * MyBatis 慢查询日志拦截器。
 * <p>
 * 拦截所有 SQL 执行（query 和 update），记录执行时间超过阈值的慢 SQL 到日志中，
 * 帮助开发和运维人员及时发现性能瓶颈。
 * </p>
 *
 * <p>工作原理：</p>
 * <ol>
 *   <li>通过 MyBatis 插件机制拦截 {@link Executor} 的 query/update 方法</li>
 *   <li>使用 {@link System#nanoTime()} 精确计时（纳秒级精度）</li>
 *   <li>执行时间超过阈值（默认 500ms，可配置）的 SQL 以 WARN 级别记录到日志</li>
 *   <li>日志包含 Mapper 全限定名、执行耗时、阈值和截断后的 SQL 文本（最长 1000 字符）</li>
 * </ol>
 *
 * <p>拦截的方法签名：</p>
 * <ul>
 *   <li>普通查询：{@code Executor.query(MappedStatement, Object, RowBounds, ResultHandler)}</li>
 *   <li>带缓存键的查询：{@code Executor.query(MappedStatement, Object, RowBounds, ResultHandler, CacheKey, BoundSql)}</li>
 *   <li>更新操作：{@code Executor.update(MappedStatement, Object)}</li>
 * </ul>
 *
 * @see MyBatisPlusConfig
 */
@Slf4j
@Component
@Intercepts({
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class, CacheKey.class, BoundSql.class}),
        @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class})
})
public class SlowQueryLoggingInterceptor implements Interceptor {

    /** 慢查询阈值（毫秒），通过 {@code app.performance.slow-query-threshold-ms} 配置，默认 500ms */
    private final long thresholdMs;

    public SlowQueryLoggingInterceptor(
            @Value("${app.performance.slow-query-threshold-ms:500}") long thresholdMs) {
        this.thresholdMs = thresholdMs;
    }

    /**
     * 拦截 SQL 执行并计时。
     * <p>
     * 使用 try-finally 模式确保即使 SQL 执行抛出异常也能完成计时和日志记录。
     * 使用 {@link System#nanoTime()} 而非 {@code currentTimeMillis()} 以获得更精确的计时。
     * </p>
     *
     * @param invocation MyBatis 调用上下文，包含目标方法、参数等信息
     * @return SQL 执行结果
     * @throws Throwable SQL 执行过程中的任何异常
     */
    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        // 使用纳秒级计时器记录开始时间
        long startNanos = System.nanoTime();
        try {
            return invocation.proceed();
        } finally {
            // 计算执行耗时（转换为毫秒），若超过阈值则记录慢查询日志
            logIfSlow(invocation, (System.nanoTime() - startNanos) / 1_000_000L);
        }
    }

    /**
     * 将拦截器包装为目标对象的代理。
     * <p>
     * 使用 MyBatis 的 {@link Plugin#wrap(Object, Interceptor)} 方法为目标 Executor
     * 创建代理，使其在执行 query/update 时经过本拦截器。
     * </p>
     *
     * @param target 待代理的目标对象（通常是 Executor 实例）
     * @return 代理后的对象，或原始对象（如果不需要代理）
     */
    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    /**
     * 设置拦截器属性。
     * <p>
     * 本拦截器通过 {@code @Value} 注入配置，不依赖 MyBatis 插件属性机制，
     * 因此此方法为空实现。
     * </p>
     *
     * @param properties 插件属性（本拦截器不使用）
     */
    @Override
    public void setProperties(Properties properties) {
        // 本拦截器无额外属性配置
    }

    /**
     * 判断是否为慢查询并记录日志。
     * <p>
     * 当执行时间超过阈值时，以 WARN 级别输出 Mapper 全限定名、耗时和 SQL 文本。
     * SQL 文本会被截断至最大 1000 字符，避免日志过大。
     * </p>
     *
     * @param invocation  MyBatis 调用上下文
     * @param durationMs  SQL 执行耗时（毫秒）
     */
    private void logIfSlow(Invocation invocation, long durationMs) {
        // 阈值为 0 或负数时禁用慢查询日志；未超过阈值则跳过
        if (thresholdMs <= 0 || durationMs < thresholdMs) {
            return;
        }
        Object[] args = invocation.getArgs();
        if (args == null || args.length == 0 || !(args[0] instanceof MappedStatement statement)) {
            return;
        }
        BoundSql boundSql = extractBoundSql(statement, args);
        // 截断 SQL 文本，避免超长 SQL 占满日志
        String sql = boundSql == null ? "" : abbreviate(boundSql.getSql(), 1000);
        log.warn("[slow sql] mapper={} durationMs={} thresholdMs={} sql={}",
                statement.getId(), durationMs, thresholdMs, sql);
    }

    /**
     * 从调用参数中提取 BoundSql 对象。
     * <p>
     * 根据拦截的方法签名不同，BoundSql 可能在参数数组的第 6 位（带缓存键的查询），
     * 也可能需要从 MappedStatement 中获取。
     * </p>
     *
     * @param statement MappedStatement 对象
     * @param args      调用参数数组
     * @return BoundSql 对象，无法提取时返回 null
     */
    private BoundSql extractBoundSql(MappedStatement statement, Object[] args) {
        // 带缓存键的查询：BoundSql 直接在参数的第 6 位（索引 5）
        if (args.length >= 6 && args[5] instanceof BoundSql boundSql) {
            return boundSql;
        }
        // 普通查询：从 MappedStatement 和参数对象中构建 BoundSql
        Object parameter = args.length >= 2 ? args[1] : null;
        return statement.getBoundSql(parameter);
    }

    /**
     * 截断并规范化 SQL 字符串。
     * <p>
     * 先将连续空白字符压缩为单个空格，再截断至指定最大长度。
     * </p>
     *
     * @param sql       原始 SQL 字符串
     * @param maxLength 最大允许长度
     * @return 规范化后的 SQL 字符串，超过长度时末尾追加 "..."
     */
    private String abbreviate(String sql, int maxLength) {
        if (sql == null) {
            return "";
        }
        // 压缩连续空白为单个空格，去除首尾空白
        String normalized = sql.replaceAll("\\s+", " ").trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength) + "...";
    }
}
