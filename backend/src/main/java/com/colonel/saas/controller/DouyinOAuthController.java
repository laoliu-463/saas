package com.colonel.saas.controller;

import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.common.base.BaseController;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.douyin.DouyinOAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * 抖音 OAuth 授权控制器。
 * <p>
 * 负责处理抖店 OAuth2.0 授权流程，包括生成授权地址和处理授权回调。
 * 本控制器是系统与抖店开放平台对接的入口，通过 OAuth 流程获取访问令牌（Token），
 * 为后续的抖店 API 调用提供认证基础。
 * </p>
 *
 * <ul>
 *   <li>生成抖店 OAuth 授权地址（仅管理员可操作）</li>
 *   <li>处理抖店 OAuth 回调，完成 code 到 Token 的换取</li>
 *   <li>授权成功或失败后重定向到前端页面</li>
 * </ul>
 *
 * <p><strong>API 路径前缀：</strong>{@code /douyin/oauth}</p>
 * <p><strong>架构角色：</strong>表现层（Controller），负责 OAuth 流程的 HTTP 入口处理，
 * 委托 {@link DouyinOAuthService} 完成实际的授权逻辑。</p>
 * <p><strong>访问控制：</strong></p>
 * <ul>
 *   <li>{@code /authorize-url} — 仅 {@code ADMIN} 角色可访问</li>
 *   <li>{@code /callback} — 公开接口，由抖店授权服务器回调</li>
 * </ul>
 *
 * @see DouyinOAuthService
 * @see BaseController
 */
@Slf4j
@RestController
@RequestMapping("/douyin/oauth")
@Tag(name = "抖音 OAuth 授权")
public class DouyinOAuthController extends BaseController {

    /** 抖音 OAuth 服务，负责授权地址生成、回调处理及 Token 交换 */
    private final DouyinOAuthService douyinOAuthService;

    /**
     * 构造注入抖音 OAuth 服务。
     *
     * @param douyinOAuthService 抖音 OAuth 服务实例
     */
    public DouyinOAuthController(DouyinOAuthService douyinOAuthService) {
        this.douyinOAuthService = douyinOAuthService;
    }

    /**
     * 生成抖店 OAuth 授权地址。
     * <p>
     * 管理员调用此接口获取抖店授权页面 URL，用户访问该 URL 完成授权后，
     * 抖店会将用户重定向到 callback 回调地址，并携带授权码（code）。
     * </p>
     * <ol>
     *   <li>接收可选的 appId 参数，指定使用哪个抖音应用配置</li>
     *   <li>委托 {@link DouyinOAuthService#createAuthorizeUrl} 生成带 state 参数的授权地址</li>
     *   <li>返回授权地址结果，前端拿到后引导用户跳转授权</li>
     * </ol>
     *
     * @param appId 抖音应用 appId；不传则使用系统默认应用配置
     * @return 包含授权 URL 和 state 的结果
     * @throws com.colonel.saas.common.exception.BusinessException 当应用配置不存在时
     */
    @Operation(summary = "生成抖店授权地址", description = "管理员点击后跳转抖店授权页，授权完成后由 callback 自动换取 Token。")
    @SecurityRequirement(name = "bearerAuth")
    @RequireRoles({RoleCodes.ADMIN})
    @GetMapping("/authorize-url")
    public ApiResult<DouyinOAuthService.AuthorizeUrlResult> authorizeUrl(
            @Parameter(description = "抖音应用 appId；不传则使用系统默认应用配置。") @RequestParam(name = "appId", required = false) String appId) {
        return ok(douyinOAuthService.createAuthorizeUrl(appId));
    }

    /**
     * 抖店 OAuth 回调入口（公开接口）。
     * <p>
     * 用户在抖店授权页面完成授权后，抖店平台会将浏览器重定向到此接口，
     * 并携带授权码（code）和防 CSRF 的 state 参数。
     * </p>
     * <ol>
     *   <li>接收抖店回调的 code 和 state 参数</li>
     *   <li>委托 {@link DouyinOAuthService#handleCallback} 校验 state 并用 code 换取 Token</li>
     *   <li>换取成功后，返回 302 重定向到前端授权成功页面</li>
     *   <li>换取失败时（异常捕获），记录警告日志并重定向到失败页面</li>
     * </ol>
     *
     * @param code  抖店授权码，由抖店授权服务器通过回调 URL 传递
     * @param state 防 CSRF 的 state 参数，用于校验回调合法性
     * @return 302 重定向响应，无响应体
     */
    @Operation(summary = "抖店 OAuth 回调", description = "公开回调入口。校验 state 后使用 code 换取 Token，并跳回前端联调页。")
    @GetMapping("/callback")
    public ResponseEntity<Void> callback(
            @RequestParam(name = "code", required = false) String code,
            @RequestParam(name = "state", required = false) String state) {
        try {
            // Step 1: 委托服务层校验 state 并换取 Token，获取成功跳转地址
            return redirect(douyinOAuthService.handleCallback(code, state));
        } catch (Exception e) {
            // Step 2: 换取失败，记录错误日志并跳转到失败页面
            log.warn("Douyin OAuth callback failed, errorType={}", e.getClass().getSimpleName());
            return redirect(douyinOAuthService.failureRedirectUrl());
        }
    }

    /**
     * 构建 302 重定向响应。
     *
     * @param location 重定向目标 URL
     * @return 302 重定向的 ResponseEntity
     */
    private ResponseEntity<Void> redirect(String location) {
        return ResponseEntity.status(302).location(URI.create(location)).build();
    }
}
