package com.colonel.saas.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * 定时任务调度器线程池配置。
 * <p>
 * 为 Spring 的 {@code @Scheduled} 注解和 {@link TaskScheduler} 提供专用线程池，
 * 避免使用默认的单线程调度器导致多个定时任务互相阻塞。
 * </p>
 *
 * <p>应用场景：</p>
 * <ul>
 *   <li>物流状态定时同步（{@code logistics.sync.cron}）</li>
 *   <li>短 TTL 缓存过期清理</li>
 *   <li>其他周期性后台任务</li>
 * </ul>
 *
 * <p>配置要点：</p>
 * <ul>
 *   <li>线程池大小为 4，满足当前 V1 阶段的定时任务并发需求</li>
 *   <li>线程名前缀为 {@code saas-scheduler-}，便于日志排查</li>
 *   <li>应用关闭时等待任务完成，最多等待 30 秒，防止任务被强制中断</li>
 * </ul>
 */
@Configuration
public class SchedulingConfig {

    /**
     * 创建定时任务调度器线程池。
     *
     * @return 配置完成的 TaskScheduler 实例
     */
    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        // 线程池大小设为 4，支持最多 4 个定时任务并发执行
        scheduler.setPoolSize(4);
        // 线程名前缀，方便在日志中识别调度器线程
        scheduler.setThreadNamePrefix("saas-scheduler-");
        // 应用关闭时等待正在执行的任务完成，避免数据不一致
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        // 最多等待 30 秒，超时后强制终止
        scheduler.setAwaitTerminationSeconds(30);
        return scheduler;
    }
}
