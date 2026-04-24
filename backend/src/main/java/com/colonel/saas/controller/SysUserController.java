package com.colonel.saas.controller;

import com.colonel.saas.annotation.RequireRoles;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.colonel.saas.auth.dto.SysUserAssignRolesRequest;
import com.colonel.saas.auth.dto.SysUserCreateRequest;
import com.colonel.saas.auth.dto.SysUserPageRequest;
import com.colonel.saas.auth.dto.SysUserResetPasswordRequest;
import com.colonel.saas.auth.dto.SysUserUpdateRequest;
import com.colonel.saas.auth.service.SysUserService;
import com.colonel.saas.common.base.BaseController;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.common.result.PageResult;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.vo.SysUserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "系统用户")
@RestController
@RequestMapping("/users")
@RequireRoles({RoleCodes.ADMIN})
public class SysUserController extends BaseController {

    private final SysUserService sysUserService;

    public SysUserController(SysUserService sysUserService) {
        this.sysUserService = sysUserService;
    }

    @Operation(summary = "分页查询用户")
    @GetMapping
    public ApiResult<PageResult<SysUserVO>> page(
            @Valid SysUserPageRequest request,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope) {
        IPage<SysUserVO> page = sysUserService.findPage(userId, dataScope, request);
        return okPage(page);
    }

    @Operation(summary = "用户详情")
    @GetMapping("/{id}")
    public ApiResult<SysUserVO> detail(
            @PathVariable UUID id,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope) {
        return ok(sysUserService.getById(id, userId, dataScope));
    }

    @Operation(summary = "新建用户")
    @PostMapping
    public ApiResult<SysUserVO> create(
            @Valid @RequestBody SysUserCreateRequest request,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope) {
        return ok(sysUserService.create(request));
    }

    @Operation(summary = "更新用户")
    @PutMapping("/{id}")
    public ApiResult<SysUserVO> update(
            @PathVariable UUID id,
            @Valid @RequestBody SysUserUpdateRequest request,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope) {
        return ok(sysUserService.update(id, request, userId, dataScope));
    }

    @Operation(summary = "删除用户")
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(
            @PathVariable UUID id,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope) {
        sysUserService.delete(id, userId, dataScope);
        return ok();
    }

    @Operation(summary = "重置密码")
    @PutMapping("/{id}/password")
    public ApiResult<Void> resetPassword(
            @PathVariable UUID id,
            @Valid @RequestBody SysUserResetPasswordRequest request,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope) {
        sysUserService.resetPassword(id, request, userId, dataScope);
        return ok();
    }

    @Operation(summary = "分配角色")
    @PutMapping("/{id}/roles")
    public ApiResult<Void> assignRoles(
            @PathVariable UUID id,
            @Valid @RequestBody SysUserAssignRolesRequest request,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope) {
        sysUserService.assignRoles(id, request, userId, dataScope);
        return ok();
    }
}
