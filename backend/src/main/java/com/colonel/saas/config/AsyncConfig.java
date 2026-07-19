package com.colonel.saas.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 应用级共享异步任务执行器。
 * <p>
 * 提供 {@code applicationTaskExecutor} bean 名（4 个 service 通过 {@code @Qualifier}
 * 注入）：商品回补、达人/商品手动同步、抖音 Token 刷新、达人周刷新。
 * </p>
 * <p>
 * 参数选择：
 * <ul>
 *   <li>core=4 / max=8：商品回补 / 同步任务并发度可控，避免冲垮抖音 API 限流</li>
 *   <li>queue=200：积压任务</li>
 *   <li>CallerRunsPolicy：队列满后由调用线程执行，HTTP 同步请求会因此被阻塞 — 但应用场景都是 fire-and-forget 后台任务，不会触发</li>
 *   <li>setWaitForTasksToCompleteOnShutdown=true + setAwaitTerminationSeconds=60：容器关闭时排空队列</li>
 * </ul>
 */
@Configuration
public class AsyncConfig implements AsyncConfigurer {

    /**
     * 创建应用级异步执行器，并同时使用 Spring 约定的 {@code taskExecutor} 别名。
     * <p>
     * 这样既兼容现有 {@code @Qualifier("applicationTaskExecutor")} 注入，
     * 也避免未指定执行器的 {@code @Async} 回退到无界的
     * {@code SimpleAsyncTaskExecutor}。
     * </p>
     */
    @Bean(name = {"applicationTaskExecutor", "taskExecutor"})
    public ThreadPoolTaskExecutor applicationTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(200);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("app-task-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }

    /**
     * 明确告诉 Spring {@code @Async} 使用受控线程池，避免多个执行器 bean
     * 同时存在时发生默认执行器解析歧义。
     */
    @Override
    public Executor getAsyncExecutor() {
        return applicationTaskExecutor();
    }
}
