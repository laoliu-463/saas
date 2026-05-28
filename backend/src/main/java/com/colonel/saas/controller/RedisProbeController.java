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

/**
 * 环境探针控制器
 * <p>
 * 提供基础设施连通性检测接口，主要用于联调和运维场景。
 * 当前支持 Redis 连通性检测，通过执行 PING 命令验证 Redis 服务是否可用。
 * 不暴露基础设施拓扑信息，仅返回连通性状态。
 * </p>
 *
 * <p>API 路径前缀：{@code /ops}</p>
 * <p>所属业务领域：系统运维（环境探针）</p>
 * <p>访问权限：仅管理员（ADMIN）</p>
 */
@RestController
@RequestMapping("/ops")
@RequireRoles({RoleCodes.ADMIN})
@Tag(name = "环境探针")
@SecurityRequirement(name = "bearerAuth")
public class RedisProbeController extends BaseController {

    /** Redis 连接工厂，用于获取 Redis 连接执行探针检测 */
    private final RedisConnectionFactory redisConnectionFactory;

    /**
     * 构造函数，注入 Redis 连接工厂依赖
     *
     * @param redisConnectionFactory Redis 连接工厂实例
     */
    public RedisProbeController(RedisConnectionFactory redisConnectionFactory) {
        this.redisConnectionFactory = redisConnectionFactory;
    }

    /**
     * Redis 探针检测
     * <p>
     * 执行一次 Redis PING 命令检测 Redis 服务的连通性。
     * 成功时返回 status=success 和 PING 响应；失败时返回 status=failed
     * 及异常类型和错误信息。不暴露任何基础设施拓扑信息。
     * </p>
     *
     * <p>HTTP 方法：GET</p>
     * <p>请求路径：{@code /ops/redis-probe}</p>
     *
     * @return 包含检测结果的 Map：成功时含 status="success" 和 ping 响应；
     *         失败时含 status="failed"、errorType 和 message
     */
    @Operation(summary = "[联调] Redis 探针", description = "执行一次 PING 检测 Redis 连通性，不暴露基础设施拓扑信息。")
    @GetMapping("/redis-probe")
    public ApiResult<Map<String, Object>> redisProbe() {
        Map<String, Object> result = new HashMap<>();
        // 尝试获取 Redis 连接并执行 PING 命令
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            result.put("status", "success");
            result.put("ping", connection.ping());
        } catch (Exception ex) {
            // 捕获连接异常，记录错误类型和信息，不暴露内部拓扑
            result.put("status", "failed");
            result.put("errorType", ex.getClass().getSimpleName());
            result.put("message", ex.getMessage());
        }
        return ok(result);
    }
}
