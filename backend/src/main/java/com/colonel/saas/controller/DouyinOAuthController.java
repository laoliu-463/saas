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

@Slf4j
@RestController
@RequestMapping("/douyin/oauth")
@Tag(name = "抖音 OAuth 授权")
public class DouyinOAuthController extends BaseController {

    private final DouyinOAuthService douyinOAuthService;

    public DouyinOAuthController(DouyinOAuthService douyinOAuthService) {
        this.douyinOAuthService = douyinOAuthService;
    }

    @Operation(summary = "生成抖店授权地址", description = "管理员点击后跳转抖店授权页，授权完成后由 callback 自动换取 Token。")
    @SecurityRequirement(name = "bearerAuth")
    @RequireRoles({RoleCodes.ADMIN})
    @GetMapping("/authorize-url")
    public ApiResult<DouyinOAuthService.AuthorizeUrlResult> authorizeUrl(
            @Parameter(description = "抖音应用 appId；不传则使用系统默认应用配置。") @RequestParam(required = false) String appId) {
        return ok(douyinOAuthService.createAuthorizeUrl(appId));
    }

    @Operation(summary = "抖店 OAuth 回调", description = "公开回调入口。校验 state 后使用 code 换取 Token，并跳回前端联调页。")
    @GetMapping("/callback")
    public ResponseEntity<Void> callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state) {
        try {
            return redirect(douyinOAuthService.handleCallback(code, state));
        } catch (Exception e) {
            log.warn("Douyin OAuth callback failed, errorType={}", e.getClass().getSimpleName());
            return redirect(douyinOAuthService.failureRedirectUrl());
        }
    }

    private ResponseEntity<Void> redirect(String location) {
        return ResponseEntity.status(302).location(URI.create(location)).build();
    }
}
