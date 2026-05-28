package com.colonel.saas.auth.controller;

import com.colonel.saas.auth.dto.LoginRequest;
import com.colonel.saas.auth.dto.LoginResponse;
import com.colonel.saas.auth.dto.LogoutRequest;
import com.colonel.saas.auth.dto.RefreshRequest;
import com.colonel.saas.auth.dto.RefreshResponse;
import com.colonel.saas.auth.service.AuthService;
import com.colonel.saas.common.base.BaseController;
import com.colonel.saas.common.result.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证中心控制器，提供用户登录、令牌刷新和登出等鉴权相关接口。
 *
 * <ul>
 *   <li>用户登录：校验用户名密码，返回 JWT Access Token 和 Refresh Token</li>
 *   <li>令牌刷新：使用 Refresh Token 无感获取新的 Access Token</li>
 *   <li>用户登出：吊销当前 Access Token 和 Refresh Token，终止会话</li>
 * </ul>
 *
 * <p>所属业务领域：用户域 / 认证中心
 * <p>访问权限：登录和刷新接口无需认证；登出接口需要携带有效令牌
 *
 * @see com.colonel.saas.auth.service.AuthService
 * @see com.colonel.saas.auth.dto.LoginRequest
 * @see com.colonel.saas.auth.dto.LoginResponse
 */
@Tag(name = "认证中心", description = "登录鉴权相关接口")
@RestController
@RequestMapping("/auth")
public class AuthController extends BaseController {

    /** 认证服务，处理登录、登出、令牌刷新等核心逻辑 */
    private final AuthService authService;

    /**
     * 构造注入认证服务。
     *
     * @param authService 认证服务实例
     */
    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 用户登录接口。
     *
     * <p>处理流程：
     * <ol>
     *   <li>校验用户名和密码是否正确</li>
     *   <li>检查账号状态（是否已禁用、待激活等）</li>
     *   <li>校验登录失败次数，超过阈值则临时锁定账号</li>
     *   <li>查询用户角色和数据范围，生成 JWT Access Token 和 Refresh Token</li>
     *   <li>记录登录审计日志，返回令牌与用户信息</li>
     * </ol>
     *
     * @param request 登录请求参数，包含用户名和密码
     * @return 包含 JWT 令牌、用户信息、角色编码和数据范围的登录响应
     * @throws com.colonel.saas.common.exception.BusinessException 用户名或密码错误、账号已停用、登录失败次数过多
     */
    @Operation(summary = "用户登录", description = "使用用户名和密码登录，返回 JWT 令牌与数据范围信息")
    @PostMapping("/login")
    public ApiResult<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ok(authService.login(request));
    }

    /**
     * 刷新令牌接口，使用 Refresh Token 获取新的 Access Token。
     *
     * <p>处理流程：
     * <ol>
     *   <li>解析并校验 Refresh Token 的有效性（签名、过期时间、类型）</li>
     *   <li>检查 Refresh Token 是否已被吊销（Redis 黑名单）</li>
     *   <li>查询用户当前角色和数据范围，重新生成 Access Token</li>
     *   <li>返回新的 Access Token，Refresh Token 保持不变</li>
     * </ol>
     *
     * @param request 刷新令牌请求，包含 Refresh Token
     * @return 包含新 Access Token 和有效期的刷新响应
     * @throws com.colonel.saas.common.exception.BusinessException Refresh Token 无效、已过期或已吊销
     */
    @Operation(summary = "刷新令牌", description = "使用 Refresh Token 获取新的 Access Token")
    @PostMapping("/refresh")
    public ApiResult<RefreshResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ok(authService.refreshToken(request));
    }

    /**
     * 用户登出接口，吊销当前会话的所有令牌。
     *
     * <p>处理流程：
     * <ol>
     *   <li>解析并校验 Refresh Token 的有效性</li>
     *   <li>将 Refresh Token 加入 Redis 黑名单（吊销）</li>
     *   <li>如果请求中携带了 Access Token，也将其加入黑名单（尽力吊销）</li>
     *   <li>记录登出审计日志</li>
     * </ol>
     *
     * @param request 登出请求，包含 Access Token（可选）和 Refresh Token（必填）
     * @return 空响应，表示登出成功
     * @throws com.colonel.saas.common.exception.BusinessException Refresh Token 无效或已吊销
     */
    @Operation(summary = "用户登出", description = "登出并吊销当前 Access Token 和 Refresh Token")
    @PostMapping("/logout")
    public ApiResult<Void> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request);
        return ok(null);
    }
}
