package com.colonel.saas.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * 达人数据充实模式（enrich mode）环境守卫。
 * <p>
 * 在应用启动阶段校验 {@code talent.enrich.mode} 配置是否与当前 Spring Profile 兼容。
 * 核心安全规则：<strong>real-pre 环境禁止使用 test 模式的达人演示数据</strong>，
 * 从而防止真实环境中混入 mock 达人资料导致业务数据污染。
 * </p>
 *
 * <p>与其他组件的关系：</p>
 * <ul>
 *   <li>与 {@link RealProdEnvironmentGuard} 配合，共同保护生产/准生产环境的配置安全性</li>
 *   <li>与 {@link TalentCollectProperties}（达人采集模式）独立，enrich mode 控制的是 Provider 演示数据</li>
 * </ul>
 *
 * @see RealProdEnvironmentGuard
 * @see TalentCollectProperties
 */
@Configuration
public class TalentEnrichModeGuard {

    private final Environment environment;

    /** 达人充实模式：real = 使用真实数据，test = 使用演示/测试数据 */
    @Value("${talent.enrich.mode:real}")
    private String enrichMode;

    public TalentEnrichModeGuard(Environment environment) {
        this.environment = environment;
    }

    /**
     * 应用启动时校验配置合法性。
     * <p>
     * 若当前激活的 Profile 包含 {@code real-pre} 且充实模式为 {@code test}，
     * 则抛出 {@link IllegalStateException} 阻止应用启动。
     * </p>
     *
     * @throws IllegalStateException 当 real-pre 环境配置了 test 模式时抛出
     */
    @PostConstruct
    public void validate() {
        // 遍历所有激活的 Profile，检查是否在生产环境误用了测试模式
        for (String profile : environment.getActiveProfiles()) {
            if ("real-pre".equalsIgnoreCase(profile) && "test".equalsIgnoreCase(enrichMode)) {
                throw new IllegalStateException("real-pre profile does not allow talent.enrich.mode=test");
            }
        }
    }
}

