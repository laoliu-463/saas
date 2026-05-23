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

@RestController
@RequestMapping("/system")
public class SystemEnvController extends BaseController {

    private final Environment environment;
    private final String envLabel;
    private final boolean appTestEnabled;
    private final boolean douyinTestEnabled;
    private final String dbNameProp;
    private final String datasourceUrl;

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

    @GetMapping("/env")
    public ApiResult<Map<String, Object>> env() {
        requireAdminWhenProd();
        String[] active = environment.getActiveProfiles();
        if (active.length == 0) {
            active = environment.getDefaultProfiles();
        }
        List<String> profiles = Arrays.asList(active);
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

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "UP");
    }

    private String resolveDatabaseName() {
        if (StringUtils.hasText(dbNameProp)) {
            return dbNameProp.trim();
        }
        if (!StringUtils.hasText(datasourceUrl)) {
            return "unknown";
        }
        String normalized = datasourceUrl.trim();
        if (normalized.startsWith("jdbc:")) {
            normalized = normalized.substring("jdbc:".length());
        }
        try {
            String path = URI.create(normalized).getPath();
            if (StringUtils.hasText(path)) {
                return Arrays.stream(path.split("/"))
                        .filter(StringUtils::hasText)
                        .reduce((first, second) -> second)
                        .orElse("unknown");
            }
        } catch (IllegalArgumentException ignored) {
            int slash = normalized.lastIndexOf('/');
            if (slash >= 0 && slash + 1 < normalized.length()) {
                return normalized.substring(slash + 1);
            }
        }
        return "unknown";
    }

    private void requireAdminWhenProd() {
        if (!RuntimeExposurePolicy.requiresAdminForSystemEnv(environment)) {
            return;
        }
        if (currentRoles().stream().anyMatch(RoleCodes.ADMIN::equals)) {
            return;
        }
        throw BusinessException.forbidden("生产环境环境探针仅允许管理员访问");
    }

    private List<String> currentRoles() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            return List.of();
        }
        Object raw = servletAttributes.getRequest().getAttribute("roleCodes");
        if (raw instanceof Collection<?> collection) {
            return collection.stream()
                    .map(value -> Objects.toString(value, "").trim().toLowerCase())
                    .filter(StringUtils::hasText)
                    .toList();
        }
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
