package com.colonel.saas.config;

import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SlowQueryLoggingInterceptorTest {

    @Test
    void intercept_returnsProceedResultAndIgnoresLoggingWhenThresholdDisabled() throws Throwable {
        SlowQueryLoggingInterceptor interceptor = new SlowQueryLoggingInterceptor(0);
        Executor executor = mock(Executor.class);
        MappedStatement statement = mock(MappedStatement.class);
        when(executor.update(statement, "param")).thenReturn(7);
        Invocation invocation = new Invocation(
                executor,
                Executor.class.getMethod("update", MappedStatement.class, Object.class),
                new Object[]{statement, "param"}
        );

        Object result = interceptor.intercept(invocation);

        assertThat(result).isEqualTo(7);
        assertThat(interceptor.plugin(new Object())).isNotNull();
        interceptor.setProperties(new Properties());
    }

    @Test
    void intercept_extractsExplicitBoundSqlForSlowSixArgumentQuery() throws Throwable {
        SlowQueryLoggingInterceptor interceptor = new SlowQueryLoggingInterceptor(1);
        MappedStatement statement = mock(MappedStatement.class);
        when(statement.getId()).thenReturn("TalentMapper.selectSlow");
        BoundSql boundSql = new BoundSql(
                new Configuration(),
                "select *\nfrom talent\nwhere id = ?",
                List.of(),
                "param"
        );
        Executor executor = mock(Executor.class);
        doAnswer(invocation -> {
            Thread.sleep(5);
            return List.of("ok");
        }).when(executor).query(
                eq(statement),
                eq("param"),
                eq(RowBounds.DEFAULT),
                isNull(),
                any(CacheKey.class),
                eq(boundSql)
        );
        Method method = Executor.class.getMethod(
                "query",
                MappedStatement.class,
                Object.class,
                RowBounds.class,
                ResultHandler.class,
                CacheKey.class,
                BoundSql.class
        );
        Invocation invocation = new Invocation(
                executor,
                method,
                new Object[]{statement, "param", RowBounds.DEFAULT, null, new CacheKey(), boundSql}
        );

        Object result = interceptor.intercept(invocation);

        assertThat(result).isEqualTo(List.of("ok"));
    }

    @Test
    void intercept_asksMappedStatementForBoundSqlWhenExplicitBoundSqlIsMissing() throws Throwable {
        SlowQueryLoggingInterceptor interceptor = new SlowQueryLoggingInterceptor(1);
        MappedStatement statement = mock(MappedStatement.class);
        BoundSql boundSql = new BoundSql(new Configuration(), "select 1", List.of(), "param");
        when(statement.getId()).thenReturn("TalentMapper.select");
        when(statement.getBoundSql("param")).thenReturn(boundSql);
        Executor executor = mock(Executor.class);
        doAnswer(invocation -> {
            Thread.sleep(5);
            return List.of("ok");
        }).when(executor).query(
                eq(statement),
                eq("param"),
                eq(RowBounds.DEFAULT),
                isNull()
        );
        Method method = Executor.class.getMethod(
                "query",
                MappedStatement.class,
                Object.class,
                RowBounds.class,
                ResultHandler.class
        );
        Invocation invocation = new Invocation(
                executor,
                method,
                new Object[]{statement, "param", RowBounds.DEFAULT, null}
        );

        assertThat(interceptor.intercept(invocation)).isEqualTo(List.of("ok"));
    }
}
