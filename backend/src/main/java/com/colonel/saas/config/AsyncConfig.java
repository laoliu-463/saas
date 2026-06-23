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

    /**
     * 手动活动商品同步专用执行器。
     * <p>
     * 与通用 {@code applicationTaskExecutor} 分离，拒绝策略使用 {@link ThreadPoolExecutor.AbortPolicy}：
     * 当同步队列饱和时，HTTP 请求线程只返回 BUSY，不在请求线程内执行上游同步，保证前端触发接口不被抖音接口耗时拖慢。
     * </p>
     */
    @Bean("productActivitySyncExecutor")
    public Executor productActivitySyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("product-activity-sync-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}
