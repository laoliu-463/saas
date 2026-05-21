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

@Slf4j
@Component
@Intercepts({
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class, CacheKey.class, BoundSql.class}),
        @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class})
})
public class SlowQueryLoggingInterceptor implements Interceptor {

    private final long thresholdMs;

    public SlowQueryLoggingInterceptor(
            @Value("${app.performance.slow-query-threshold-ms:500}") long thresholdMs) {
        this.thresholdMs = thresholdMs;
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        long startNanos = System.nanoTime();
        try {
            return invocation.proceed();
        } finally {
            logIfSlow(invocation, (System.nanoTime() - startNanos) / 1_000_000L);
        }
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
    }

    private void logIfSlow(Invocation invocation, long durationMs) {
        if (thresholdMs <= 0 || durationMs < thresholdMs) {
            return;
        }
        Object[] args = invocation.getArgs();
        if (args == null || args.length == 0 || !(args[0] instanceof MappedStatement statement)) {
            return;
        }
        BoundSql boundSql = extractBoundSql(statement, args);
        String sql = boundSql == null ? "" : abbreviate(boundSql.getSql(), 1000);
        log.warn("[slow sql] mapper={} durationMs={} thresholdMs={} sql={}",
                statement.getId(), durationMs, thresholdMs, sql);
    }

    private BoundSql extractBoundSql(MappedStatement statement, Object[] args) {
        if (args.length >= 6 && args[5] instanceof BoundSql boundSql) {
            return boundSql;
        }
        Object parameter = args.length >= 2 ? args[1] : null;
        return statement.getBoundSql(parameter);
    }

    private String abbreviate(String sql, int maxLength) {
        if (sql == null) {
            return "";
        }
        String normalized = sql.replaceAll("\\s+", " ").trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength) + "...";
    }
}
