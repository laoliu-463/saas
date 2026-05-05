package com.colonel.saas.controller;

import com.colonel.saas.annotation.RequireRoles;
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
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
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

import java.util.List;
import java.util.UUID;

@Tag(name = "系统用户", description = "系统后台用户管理接口，包括分页、详情、新增、编辑、删除、重置密码与分配角色。")
@RestController
@RequestMapping("/users")
@RequireRoles({RoleCodes.ADMIN})
public class SysUserController extends BaseController {

    private final SysUserService sysUserService;

    public SysUserController(SysUserService sysUserService) {
        this.sysUserService = sysUserService;
    }

    @Operation(summary = "分页查询用户", description = "按分页条件查询系统用户列表。筛选条件由 SysUserPageRequest 承载。")
    @GetMapping
    public ApiResult<PageResult<SysUserVO>> page(
            @Valid SysUserPageRequest request,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope) {
        return okPage(sysUserService.findPage(userId, dataScope, request));
    }

    @Operation(summary = "查询可分配负责人候选", description = "返回可用于商品分配招商弹窗的负责人候选，仅暴露最小必要字段。")
    @GetMapping("/assignable")
    @RequireRoles({RoleCodes.ADMIN, RoleCodes.BIZ_LEADER, RoleCodes.CHANNEL_LEADER})
    public ApiResult<List<SysUserVO>> assignable(
            @Parameter(description = "负责人关键字，匹配用户名或姓名。") String keyword,
            @RequestAttribute(value = "roleCodes", required = false) List<String> roleCodes,
            @RequestAttribute(value = "deptId", required = false) UUID deptId) {
        return ok(sysUserService.findAssignableUsers(keyword, roleCodes, deptId));
    }

    @Operation(summary = "用户详情", description = "查询单个系统用户详情。")
    @GetMapping("/{id}")
    public ApiResult<SysUserVO> detail(
            @Parameter(description = "用户主键 ID，使用 UUID 格式。") @PathVariable UUID id,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope) {
        return ok(sysUserService.getById(id, userId, dataScope));
    }

    @Operation(summary = "新建用户", description = "创建系统用户，并写入角色、部门等基础信息。")
    @PostMapping
    public ApiResult<SysUserVO> create(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "用户创建请求体。",
                    required = true,
                    content = @Content(examples = @ExampleObject(value = "{\"username\":\"zhangsan\",\"realName\":\"张三\",\"password\":\"123456\"}"))
            )
            @Valid @RequestBody SysUserCreateRequest request,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope) {
        return ok(sysUserService.create(request, userId));
    }

    @Operation(summary = "更新用户", description = "更新系统用户的基础资料。")
    @PutMapping("/{id}")
    public ApiResult<SysUserVO> update(
            @Parameter(description = "用户主键 ID，使用 UUID 格式。") @PathVariable UUID id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "用户更新请求体。",
                    required = true,
                    content = @Content(examples = @ExampleObject(value = "{\"realName\":\"张三-更新\"}"))
            )
            @Valid @RequestBody SysUserUpdateRequest request,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope) {
        return ok(sysUserService.update(id, request, userId, dataScope));
    }

    @Operation(summary = "删除用户", description = "删除指定系统用户。")
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(
            @Parameter(description = "用户主键 ID，使用 UUID 格式。") @PathVariable UUID id,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope) {
        sysUserService.delete(id, userId, dataScope);
        return ok();
    }

    @Operation(summary = "重置密码", description = "重置指定用户的登录密码。")
    @PutMapping("/{id}/password")
    public ApiResult<Void> resetPassword(
            @Parameter(description = "用户主键 ID，使用 UUID 格式。") @PathVariable UUID id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "重置密码请求体。",
                    required = true,
                    content = @Content(examples = @ExampleObject(value = "{\"newPassword\":\"123456\"}"))
            )
            @Valid @RequestBody SysUserResetPasswordRequest request,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope) {
        sysUserService.resetPassword(id, request, userId, dataScope);
        return ok();
    }

    @Operation(summary = "分配角色", description = "为指定用户分配角色列表。")
    @PutMapping("/{id}/roles")
    public ApiResult<Void> assignRoles(
            @Parameter(description = "用户主键 ID，使用 UUID 格式。") @PathVariable UUID id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "角色分配请求体。",
                    required = true,
                    content = @Content(examples = @ExampleObject(value = "{\"roleIds\":[\"11111111-1111-1111-1111-111111111111\"]}"))
            )
            @Valid @RequestBody SysUserAssignRolesRequest request,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope) {
        sysUserService.assignRoles(id, request, userId, dataScope);
        return ok();
    }
}
