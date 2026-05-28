package com.colonel.saas;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 抖音团长 SaaS 平台的 Spring Boot 应用启动类。
 * <p>
 * 该类是整个后端服务的入口，负责引导 Spring 容器初始化和自动配置。
 * 启用以下核心能力：
 * <ul>
 *   <li>{@link MapperScan} — 自动扫描 MyBatis Mapper 接口，
 *       包括业务数据层（{@code com.colonel.saas.mapper}）和领域事件层（{@code com.colonel.saas.domain.event}）</li>
 *   <li>{@link EnableScheduling} — 启用定时任务调度，支持物流查询、数据同步等周期任务</li>
 *   <li>{@link EnableAsync} — 启用异步方法执行，支持事件发布、通知等异步处理</li>
 * </ul>
 */
@SpringBootApplication
@MapperScan({"com.colonel.saas.mapper", "com.colonel.saas.domain.event"})
@EnableScheduling
@EnableAsync
public class ColonelSaasApplication {

    /**
     * 应用程序主入口。
     *
     * @param args 命令行参数，可通过 {@code --app.test.enabled=true} 等参数控制启动行为
     */
    public static void main(String[] args) {
        SpringApplication.run(ColonelSaasApplication.class, args);
    }
}
