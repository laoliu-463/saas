package com.colonel.saas.controller;

import com.colonel.saas.common.base.BaseController;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.dto.user.ChangePasswordRequest;
import com.colonel.saas.dto.user.CheckPermissionRequest;
import com.colonel.saas.dto.user.CheckPermissionResponse;
import com.colonel.saas.dto.user.CurrentUserResponse;
import com.colonel.saas.dto.user.UserDataScopeResponse;
import com.colonel.saas.service.UserDomainService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Tag(name = "用户域", description = "当前登录用户、数据范围与操作权限接口。")
@RestController
@RequestMapping("/users/current")
public class CurrentUserController extends BaseController {

    private final UserDomainService userDomainService;

    public CurrentUserController(UserDomainService userDomainService) {
        this.userDomainService = userDomainService;
    }

    @Operation(summary = "获取当前用户", description = "返回当前登录用户基础信息、角色编码、数据范围和聚合权限包。")
    @GetMapping
    public ApiResult<CurrentUserResponse> currentUser(
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope,
            @RequestAttribute(value = "roleCodes", required = false) List<String> roleCodes) {
        return ok(userDomainService.getCurrentUser(
                userId,
                deptId,
                dataScope,
                roleCodes == null ? Collections.emptyList() : roleCodes
        ));
    }

    @Operation(summary = "当前用户修改密码", description = "登录用户校验原密码后修改自己的登录密码。")
    @PutMapping("/password")
    public ApiResult<Void> changePassword(
            @RequestAttribute("userId") UUID userId,
            @Valid @RequestBody ChangePasswordRequest request) {
        userDomainService.changePassword(userId, request);
        return ok();
    }

    @Operation(summary = "解析当前用户数据范围", description = "返回 self/group/all 与对应用户 ID 限制列表。")
    @GetMapping("/data-scope")
    public ApiResult<UserDataScopeResponse> dataScope(
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope) {
        return ok(userDomainService.getUserDataScope(userId, deptId, dataScope));
    }

    @Operation(summary = "检查当前用户操作权限", description = "按资源域和操作名检查当前登录用户是否具备操作权限。")
    @PostMapping("/permissions/check")
    public ApiResult<CheckPermissionResponse> checkPermission(
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "roleCodes", required = false) List<String> roleCodes,
            @Valid @RequestBody CheckPermissionRequest request) {
        return ok(userDomainService.checkPermission(
                userId,
                roleCodes == null ? Collections.emptyList() : roleCodes,
                request
        ));
    }
}
