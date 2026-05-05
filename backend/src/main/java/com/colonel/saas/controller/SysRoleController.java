package com.colonel.saas.controller;

import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.auth.dto.SysRoleCreateRequest;
import com.colonel.saas.auth.dto.SysRoleUpdateRequest;
import com.colonel.saas.auth.service.SysRoleService;
import com.colonel.saas.common.base.BaseController;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.common.result.PageResult;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.vo.SysRoleVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Validated
@Tag(name = "系统角色", description = "系统角色管理接口，包括分页、详情、启用列表、新增、编辑与删除。")
@RestController
@RequestMapping("/roles")
@RequireRoles({RoleCodes.ADMIN})
public class SysRoleController extends BaseController {

    private final SysRoleService sysRoleService;

    public SysRoleController(SysRoleService sysRoleService) {
        this.sysRoleService = sysRoleService;
    }

    @Operation(summary = "分页查询角色", description = "按关键字与状态分页查询角色列表。")
    @GetMapping
    public ApiResult<PageResult<SysRoleVO>> page(
            @Parameter(description = "页码，从 1 开始。") @RequestParam(defaultValue = "1") @Min(1) long page,
            @Parameter(description = "每页条数。") @RequestParam(defaultValue = "10") @Min(1) @Max(100) long size,
            @Parameter(description = "角色关键字。") @RequestParam(required = false) String keyword,
            @Parameter(description = "角色状态。待确认：取值含义请联系产品。") @RequestParam(required = false) Integer status,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope) {
        return okPage(sysRoleService.findPage(page, size, keyword, status));
    }

    @Operation(summary = "角色详情", description = "查询单个角色详情。")
    @GetMapping("/{id}")
    public ApiResult<SysRoleVO> detail(
            @Parameter(description = "角色主键 ID，使用 UUID 格式。") @PathVariable UUID id,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope) {
        return ok(sysRoleService.getById(id));
    }

    @Operation(summary = "全量角色列表", description = "查询当前启用的全部角色，用于用户分配角色下拉框。")
    @GetMapping("/enabled")
    public ApiResult<List<SysRoleVO>> all(
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope) {
        return ok(sysRoleService.findAllEnabled());
    }

    @Operation(summary = "新建角色", description = "创建系统角色。")
    @PostMapping
    public ApiResult<SysRoleVO> create(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "角色创建请求体。",
                    required = true,
                    content = @Content(examples = @ExampleObject(value = "{\"name\":\"渠道专员\",\"code\":\"channel_staff\"}"))
            )
            @Valid @RequestBody SysRoleCreateRequest request,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope) {
        return ok(sysRoleService.create(request, userId));
    }

    @Operation(summary = "更新角色", description = "更新系统角色信息。")
    @PutMapping("/{id}")
    public ApiResult<SysRoleVO> update(
            @Parameter(description = "角色主键 ID，使用 UUID 格式。") @PathVariable UUID id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "角色更新请求体。",
                    required = true,
                    content = @Content(examples = @ExampleObject(value = "{\"name\":\"渠道专员-更新\"}"))
            )
            @Valid @RequestBody SysRoleUpdateRequest request,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope) {
        return ok(sysRoleService.update(id, request, userId));
    }

    @Operation(summary = "删除角色", description = "删除指定角色。")
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(
            @Parameter(description = "角色主键 ID，使用 UUID 格式。") @PathVariable UUID id,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope) {
        sysRoleService.delete(id, userId);
        return ok();
    }
}
