package com.colonel.saas.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.Arrays;

/**
 * 应用启动环境信息日志记录器。
 * <p>
 * 实现 {@link ApplicationRunner} 接口，在 Spring Boot 应用启动完成后自动打印
 * 关键环境信息，便于运维人员快速确认部署环境是否正确。输出内容包括：
 * <ul>
 *   <li>激活的 Spring Profile（test 或 real-pre）</li>
 *   <li>环境标签（{@code saas.env-label}）</li>
 *   <li>测试模式开关状态（{@code app.test.enabled}、{@code douyin.test.enabled}）</li>
 *   <li>连接的数据库名称（从环境变量或 JDBC URL 中解析）</li>
 * </ul>
 *
 * <p>与其他组件的关系：</p>
 * <ul>
 *   <li>与 {@link RealProdEnvironmentGuard} 互补：守卫负责校验安全，本类负责记录日志</li>
 *   <li>读取的配置项与 {@link AppProperties} 一致，但通过 {@code @Value} 直接注入以便在最早期执行</li>
 * </ul>
 */
@Component
public class StartupEnvironmentLogger implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupEnvironmentLogger.class);

    private final Environment environment;
    /** 全局测试模式开关 */
    private final boolean appTestEnabled;
    /** 抖音模块测试模式开关 */
    private final boolean douyinTestEnabled;
    /** 数据库名称（优先使用环境变量 DB_NAME） */
    private final String dbName;
    /** 数据源 URL（备用数据库名来源） */
    private final String datasourceUrl;
    /** 自定义环境标签，用于区分同一 Profile 下的不同部署实例 */
    private final String envLabel;

    public StartupEnvironmentLogger(
            Environment environment,
            @Value("${app.test.enabled:false}") boolean appTestEnabled,
            @Value("${douyin.test.enabled:false}") boolean douyinTestEnabled,
            @Value("${DB_NAME:}") String dbName,
            @Value("${spring.datasource.url:}") String datasourceUrl,
            @Value("${saas.env-label:}") String envLabel) {
        this.environment = environment;
        this.appTestEnabled = appTestEnabled;
        this.douyinTestEnabled = douyinTestEnabled;
        this.dbName = dbName;
        this.datasourceUrl = datasourceUrl;
        this.envLabel = envLabel;
    }

    /**
     * 应用启动后执行，打印当前环境摘要信息到日志。
     *
     * @param args 应用启动参数（未使用）
     */
    @Override
    public void run(ApplicationArguments args) {
        log.info("SAAS environment | activeProfiles={} | envLabel={} | app.test.enabled={} | douyin.test.enabled={} | db.name={}",
                activeProfileLabel(),
                StringUtils.hasText(envLabel) ? envLabel.trim() : "",
                appTestEnabled,
                douyinTestEnabled,
                resolveDatabaseName());
    }

    /**
     * 获取当前激活的 Profile 标签字符串。
     * <p>
     * 若无显式激活的 Profile，则回退到默认 Profile。
     * </p>
     *
     * @return 逗号分隔的 Profile 名称字符串
     */
    private String activeProfileLabel() {
        String[] activeProfiles = environment.getActiveProfiles();
        if (activeProfiles.length == 0) {
            activeProfiles = environment.getDefaultProfiles();
        }
        return String.join(",", activeProfiles);
    }

    /**
     * 解析数据库名称，用于启动日志输出。
     * <p>
     * 优先使用环境变量 {@code DB_NAME}；若未设置，则从 JDBC URL 的路径中提取。
     * 提取策略：解析 URL 路径的最后一段（如 {@code jdbc:postgresql://host:5432/mydb} 中的 {@code mydb}）。
     * </p>
     *
     * @return 数据库名称，无法解析时返回 {@code "unknown"}
     */
    private String resolveDatabaseName() {
        // 优先使用显式配置的 DB_NAME 环境变量
        if (StringUtils.hasText(dbName)) {
            return dbName.trim();
        }
        // 若数据源 URL 也未配置，无法推断
        if (!StringUtils.hasText(datasourceUrl)) {
            return "unknown";
        }
        String normalized = datasourceUrl.trim();
        // 去除 jdbc: 前缀以便使用 URI 解析
        if (normalized.startsWith("jdbc:")) {
            normalized = normalized.substring("jdbc:".length());
        }
        try {
            // 尝试通过 URI 解析提取路径中的数据库名
            String path = URI.create(normalized).getPath();
            if (StringUtils.hasText(path)) {
                // 取路径最后一段作为数据库名（如 /host/db 中的 db）
                return Arrays.stream(path.split("/"))
                        .filter(StringUtils::hasText)
                        .reduce((first, second) -> second)
                        .orElse("unknown");
            }
        } catch (IllegalArgumentException ignored) {
            // URI 格式不合法时回退到简单的字符串截取
            int slash = normalized.lastIndexOf('/');
            if (slash >= 0 && slash + 1 < normalized.length()) {
                return normalized.substring(slash + 1);
            }
        }
        return "unknown";
    }
}
