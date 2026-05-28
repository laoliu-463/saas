package com.colonel.saas.controller;

import com.colonel.saas.common.base.BaseController;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.config.RuntimeExposurePolicy;
import com.colonel.saas.constant.RoleCodes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 系统环境信息控制器.
 *
 * <p>提供当前部署环境的运行时信息查询和健康检查接口，属于运维域。
 * 环境信息包括：活跃 Profile、环境标签、测试开关状态、数据库名称等。</p>
 *
 * <p>API 路径前缀：{@code /system}</p>
 *
 * <p>生产环境下仅允许管理员访问环境探针，由 {@link RuntimeExposurePolicy} 控制。
 * 健康检查接口 {@code /system/health} 无需认证。</p>
 *
 * @see RuntimeExposurePolicy
 */
@RestController
@RequestMapping("/system")
public class SystemEnvController extends BaseController {

    /** Spring 应用环境，用于获取活跃 Profile */
    private final Environment environment;
    /** 自定义环境标签，优先于 Profile 推导 */
    private final String envLabel;
    /** 测试工具开关（app.test.enabled） */
    private final boolean appTestEnabled;
    /** 抖音测试模式开关（douyin.test.enabled） */
    private final boolean douyinTestEnabled;
    /** 数据库名称环境变量（DB_NAME） */
    private final String dbNameProp;
    /** 数据源连接 URL（spring.datasource.url） */
    private final String datasourceUrl;

    /**
     * 构造注入.
     *
     * @param environment       Spring 应用环境
     * @param envLabel          自定义环境标签（可选）
     * @param appTestEnabled    测试工具开关
     * @param douyinTestEnabled 抖音测试模式开关
     * @param dbNameProp        数据库名称环境变量
     * @param datasourceUrl     数据源连接 URL
     */
    public SystemEnvController(
            Environment environment,
            @Value("${saas.env-label:}") String envLabel,
            @Value("${app.test.enabled:false}") boolean appTestEnabled,
            @Value("${douyin.test.enabled:false}") boolean douyinTestEnabled,
            @Value("${DB_NAME:}") String dbNameProp,
            @Value("${spring.datasource.url:}") String datasourceUrl) {
        this.environment = environment;
        this.envLabel = envLabel;
        this.appTestEnabled = appTestEnabled;
        this.douyinTestEnabled = douyinTestEnabled;
        this.dbNameProp = dbNameProp;
        this.datasourceUrl = datasourceUrl;
    }

    /**
     * 查询当前环境信息.
     *
     * <p>返回活跃 Profile、环境标签、测试开关状态和数据库名称等运行时信息。
     * 生产环境下仅允许管理员访问，由 {@link RuntimeExposurePolicy} 控制。</p>
     *
     * @return 环境信息映射，包含 activeProfiles、environmentLabel、appTestEnabled、douyinTestEnabled、database
     */
    @GetMapping("/env")
    public ApiResult<Map<String, Object>> env() {
        // 生产环境权限校验
        requireAdminWhenProd();
        String[] active = environment.getActiveProfiles();
        // 无活跃 Profile 时降级为默认 Profile
        if (active.length == 0) {
            active = environment.getDefaultProfiles();
        }
        List<String> profiles = Arrays.asList(active);
        // 优先使用自定义环境标签，否则从 Profile 推导
        String label = StringUtils.hasText(envLabel)
                ? envLabel.trim().toUpperCase()
                : String.join(",", profiles).toUpperCase();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("activeProfiles", profiles);
        body.put("environmentLabel", label);
        body.put("appTestEnabled", appTestEnabled);
        body.put("douyinTestEnabled", douyinTestEnabled);
        body.put("database", resolveDatabaseName());
        return ok(body);
    }

    /**
     * 健康检查接口.
     *
     * <p>无需认证，返回简单的状态标识，用于负载均衡器或容器编排探针。</p>
     *
     * @return 包含 status=UP 的健康状态映射
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "UP");
    }

    /**
     * 解析数据库名称.
     *
     * <p>优先从 DB_NAME 环境变量获取；若不存在则从数据源 URL 的路径中提取最后一段作为数据库名称。
     * 解析失败时返回 "unknown"。</p>
     *
     * @return 数据库名称，无法解析时返回 "unknown"
     */
    private String resolveDatabaseName() {
        // 优先使用 DB_NAME 环境变量
        if (StringUtils.hasText(dbNameProp)) {
            return dbNameProp.trim();
        }
        if (!StringUtils.hasText(datasourceUrl)) {
            return "unknown";
        }
        String normalized = datasourceUrl.trim();
        // 去除 jdbc: 前缀以便 URI 解析
        if (normalized.startsWith("jdbc:")) {
            normalized = normalized.substring("jdbc:".length());
        }
        try {
            // 通过 URI 路径提取数据库名称
            String path = URI.create(normalized).getPath();
            if (StringUtils.hasText(path)) {
                return Arrays.stream(path.split("/"))
                        .filter(StringUtils::hasText)
                        .reduce((first, second) -> second)
                        .orElse("unknown");
            }
        } catch (IllegalArgumentException ignored) {
            // URI 解析失败时回退到简单的字符串截取
            int slash = normalized.lastIndexOf('/');
            if (slash >= 0 && slash + 1 < normalized.length()) {
                return normalized.substring(slash + 1);
            }
        }
        return "unknown";
    }

    /**
     * 生产环境权限校验.
     *
     * <p>当 {@link RuntimeExposurePolicy} 判定当前环境需要管理员权限时，
     * 检查当前请求是否携带 ADMIN 角色，不满足则抛出 403 异常。</p>
     *
     * @throws BusinessException 非管理员访问生产环境探针时抛出
     */
    private void requireAdminWhenProd() {
        // 非生产环境直接放行
        if (!RuntimeExposurePolicy.requiresAdminForSystemEnv(environment)) {
            return;
        }
        // 当前用户具有管理员角色则放行
        if (currentRoles().stream().anyMatch(RoleCodes.ADMIN::equals)) {
            return;
        }
        throw BusinessException.forbidden("生产环境环境探针仅允许管理员访问");
    }

    /**
     * 获取当前请求中的角色编码列表.
     *
     * <p>从 RequestContextHolder 中读取 roleCodes 属性，
     * 支持 Collection 和逗号分隔字符串两种格式。返回值均为小写。</p>
     *
     * @return 当前用户角色编码列表，无角色时返回空集合
     */
    private List<String> currentRoles() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            return List.of();
        }
        // 从请求属性中获取 roleCodes
        Object raw = servletAttributes.getRequest().getAttribute("roleCodes");
        // 支持 Collection 类型的 roleCodes
        if (raw instanceof Collection<?> collection) {
            return collection.stream()
                    .map(value -> Objects.toString(value, "").trim().toLowerCase())
                    .filter(StringUtils::hasText)
                    .toList();
        }
        // 支持逗号分隔字符串类型的 roleCodes
        String text = Objects.toString(raw, "").trim();
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        return Arrays.stream(text.split(","))
                .map(value -> value.trim().toLowerCase())
                .filter(StringUtils::hasText)
                .toList();
    }
}
