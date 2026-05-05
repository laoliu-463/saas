package com.colonel.saas.controller;

import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.common.base.BaseController;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.constant.RoleCodes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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

    public RedisProbeController(RedisConnectionFactory redisConnectionFactory) {
        this.redisConnectionFactory = redisConnectionFactory;
    }

    @Operation(summary = "[联调] Redis 探针", description = "执行一次 PING 检测 Redis 连通性，不暴露基础设施拓扑信息。")
    @GetMapping("/redis-probe")
    public ApiResult<Map<String, Object>> redisProbe() {
        Map<String, Object> result = new HashMap<>();
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
