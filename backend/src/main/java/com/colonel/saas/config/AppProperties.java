package com.colonel.saas.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 应用级集中化配置属性。
 * <p>
 * 将所有与环境相关的开关集中在此处，避免业务层代码散布大量 {@code @Value} 注解。
 * 通过 {@code app.*} 前缀从 {@code application.yml} 绑定配置。
 * </p>
 *
 * <p>主要用途：</p>
 * <ul>
 *   <li>控制 test/mock 模式与真实 SDK 模式的全局切换（{@link TestConfig}）</li>
 *   <li>数据库名称标识（用于日志和环境确认）</li>
 *   <li>配合 {@link RealProdEnvironmentGuard} 在启动时做环境安全校验</li>
 * </ul>
 *
 * <p>配置示例（application.yml）：</p>
 * <pre>
 * app:
 *   db-name: saas_prod
 *   test:
 *     enabled: false
 *     douyin: false
 *     order: false
 * </pre>
 *
 * @see RealProdEnvironmentGuard
 * @see StartupEnvironmentLogger
 */
@Data
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    /** 测试模式子配置，控制各模块是否使用 mock 数据 */
    private TestConfig test = new TestConfig();
    /** 数据库名称标识，用于启动日志输出和环境确认 */
    private String dbName;

    /**
     * 测试/Mock 模式子配置。
     * <p>
     * 当 {@code enabled} 为 {@code true} 时，系统进入测试模式，各子开关分别控制
     * 对应模块是否使用 mock 数据替代真实抖音 SDK 调用。
     * 在 real-pre 环境中，{@link RealProdEnvironmentGuard} 会强制校验
     * 此处的开关必须全部为 {@code false}。
     * </p>
     */
    @Data
    public static class TestConfig {
        /** 全局测试模式总开关：true = test/mock 模式，false = 使用真实 SDK */
        private boolean enabled = false;
        /** 应用启动时是否自动填充种子数据（管理员账号、示例数据等） */
        private boolean seedOnStartup = false;
        /** 是否启用抖音 API 的 mock 响应 */
        private boolean douyin = false;
        /** 是否启用推广链接的 mock 生成 */
        private boolean promotion = false;
        /** 是否启用订单同步的 mock 数据 */
        private boolean order = false;
        /** 是否启用达人数据的 mock 响应 */
        private boolean talent = false;
        /** 是否启用物流数据的 mock 响应 */
        private boolean logistics = false;
    }
}
