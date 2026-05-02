package com.colonel.saas.controller;

import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.common.base.BaseController;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.constant.RoleCodes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/ops")
@RequireRoles({RoleCodes.ADMIN})
@Tag(name = "环境探针")
@SecurityRequirement(name = "bearerAuth")
public class RedisProbeController extends BaseController {

    private final RedisConnectionFactory redisConnectionFactory;
    private final String redisHost;
    private final int redisPort;
    private final int redisDatabase;
    private final String redisPassword;

    public RedisProbeController(
            RedisConnectionFactory redisConnectionFactory,
            @Value("${spring.data.redis.host}") String redisHost,
            @Value("${spring.data.redis.port}") int redisPort,
            @Value("${spring.data.redis.database}") int redisDatabase,
            @Value("${spring.data.redis.password:}") String redisPassword) {
        this.redisConnectionFactory = redisConnectionFactory;
        this.redisHost = redisHost;
        this.redisPort = redisPort;
        this.redisDatabase = redisDatabase;
        this.redisPassword = redisPassword;
    }

    @Operation(summary = "[联调] Redis 探针", description = "返回当前 Spring Redis 配置，并执行一次 PING，帮助定位 real/pre 环境中的 Redis 连通问题。")
    @GetMapping("/redis-probe")
    public ApiResult<Map<String, Object>> redisProbe() {
        Map<String, Object> result = new HashMap<>();
        result.put("host", redisHost);
        result.put("port", redisPort);
        result.put("database", redisDatabase);
        result.put("passwordPresent", redisPassword != null && !redisPassword.isBlank());
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            result.put("status", "success");
            result.put("ping", connection.ping());
        } catch (Exception ex) {
            result.put("status", "failed");
            result.put("errorType", ex.getClass().getSimpleName());
            result.put("message", ex.getMessage());
        }
        return ok(result);
    }
}
