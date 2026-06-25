package com.colonel.saas.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
public class AsyncConfig {

    @Bean("applicationTaskExecutor")
    public Executor applicationTaskExecutor() {
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
}
