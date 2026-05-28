package com.colonel.saas.config;

import cn.hutool.http.HttpGlobalConfig;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

/**
 * Hutool HTTP 客户端全局配置。
 * <p>
 * 在应用启动时统一设置 Hutool HTTP 工具的全局超时时间，
 * 确保所有使用 {@code HttpUtil} / {@code HttpRequest} 发出的请求
 * 都遵循统一的超时策略，避免因外部接口（如抖音开放平台、快递鸟/快递100）
 * 响应缓慢导致线程长时间阻塞。
 * </p>
 *
 * <p>与其他组件的关系：</p>
 * <ul>
 *   <li>作用于 Hutool 全局配置 {@link HttpGlobalConfig}，影响范围为整个 JVM 内所有 Hutool HTTP 调用</li>
 *   <li>配合 {@link TalentCollectProperties} 中达人采集的超时配置一起使用</li>
 * </ul>
 */
@Configuration
public class HutoolHttpConfig {

    /**
     * 初始化 Hutool HTTP 全局超时设置。
     * <p>
     * 将连接超时和读超时统一设置为 10 秒（10_000 毫秒），
     * 在应用启动阶段（{@link PostConstruct}）自动执行。
     * </p>
     */
    @PostConstruct
    public void init() {
        // 设置全局超时为 10 秒，防止外部接口响应过慢导致请求长时间挂起
        HttpGlobalConfig.setTimeout(10_000);
    }
}
