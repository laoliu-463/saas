package com.colonel.saas.controller;

import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.auth.dto.SysMenuCreateRequest;
import com.colonel.saas.auth.dto.SysMenuUpdateRequest;
import com.colonel.saas.auth.service.SysMenuService;
import com.colonel.saas.common.base.BaseController;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.vo.SysMenuVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Validated
@Tag(name = "系统菜单", description = "系统菜单管理接口，包括菜单树查询、新增、编辑与删除。")
@RestController
@RequestMapping("/menus")
public class SysMenuController extends BaseController {

    private final SysMenuService sysMenuService;

    public SysMenuController(SysMenuService sysMenuService) {
        this.sysMenuService = sysMenuService;
    }

    @Operation(summary = "当前用户菜单树", description = "根据当前用户角色返回可见菜单树。")
    @GetMapping("/tree")
    public ApiResult<List<SysMenuVO>> userTree(
            @Parameter(description = "菜单状态筛选") @RequestParam(required = false) Integer status,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope) {
        return ok(sysMenuService.findUserTreeByUserId(userId, status));
    }

    @Operation(summary = "全量菜单树", description = "查询全部菜单树，仅管理员可用。")
    @GetMapping("/all")
    @RequireRoles({RoleCodes.ADMIN})
    public ApiResult<List<SysMenuVO>> allTree(
            @Parameter(description = "菜单状态筛选") @RequestParam(required = false) Integer status,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope) {
        return ok(sysMenuService.findAllTree(status));
    }

    @Operation(summary = "新建菜单", description = "创建系统菜单节点。")
    @PostMapping
    @RequireRoles({RoleCodes.ADMIN})
    public ApiResult<SysMenuVO> create(
            @Valid @RequestBody SysMenuCreateRequest request,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope) {
        return ok(sysMenuService.create(request, userId));
    }

    @Operation(summary = "更新菜单", description = "更新系统菜单节点信息。")
    @PutMapping("/{id}")
    @RequireRoles({RoleCodes.ADMIN})
    public ApiResult<SysMenuVO> update(
            @Parameter(description = "菜单主键 ID") @PathVariable UUID id,
            @Valid @RequestBody SysMenuUpdateRequest request,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope) {
        return ok(sysMenuService.update(id, request, userId));
    }

    @Operation(summary = "删除菜单", description = "删除指定菜单节点。")
    @DeleteMapping("/{id}")
    @RequireRoles({RoleCodes.ADMIN})
    public ApiResult<Void> delete(
            @Parameter(description = "菜单主键 ID") @PathVariable UUID id,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope) {
        sysMenuService.delete(id, userId);
        return ok();
    }
}
