package com.colonel.saas.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 插件配置。
 * <p>
 * 注册 MyBatis-Plus 的核心拦截器链，包括：
 * <ul>
 *   <li><strong>乐观锁拦截器</strong>（{@link OptimisticLockerInnerInterceptor}）：
 *       自动在 UPDATE 语句中追加 {@code WHERE version = ?} 条件，防止并发更新冲突。
 *       实体类需包含 {@code @Version} 注解的字段。</li>
 *   <li><strong>分页拦截器</strong>（{@link PaginationInnerInterceptor}）：
 *       自动将 {@code SELECT COUNT} 和分页查询适配 PostgreSQL 方言，
 *       并设置单次查询最大返回 2000 条，防止内存溢出。</li>
 * </ul>
 *
 * <p>与其他组件的关系：</p>
 * <ul>
 *   <li>配合 {@link CustomMetaObjectHandler} 使用，后者自动填充审计字段（createTime/updateTime 等）</li>
 *   <li>配合 {@link SlowQueryLoggingInterceptor} 使用，后者拦截 SQL 执行记录慢查询</li>
 * </ul>
 *
 * @see CustomMetaObjectHandler
 * @see SlowQueryLoggingInterceptor
 */
@Configuration
public class MyBatisPlusConfig {

    /**
     * 创建 MyBatis-Plus 拦截器链。
     * <p>
     * 拦截器的注册顺序很重要：乐观锁拦截器在前，分页拦截器在后。
     * </p>
     *
     * @return 配置完成的 MybatisPlusInterceptor 实例
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 注册乐观锁拦截器：自动在 UPDATE 语句中添加 version 条件
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        // 注册分页拦截器：适配 PostgreSQL 方言
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.POSTGRE_SQL);
        // 设置单页最大查询限制为 2000 条，防止无限制分页导致内存溢出
        pagination.setMaxLimit(2000L);
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }
}
