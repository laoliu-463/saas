package com.colonel.saas.controller;

import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.auth.dto.DeptMemberPageRequest;
import com.colonel.saas.auth.dto.GroupMemberMutationRequest;
import com.colonel.saas.auth.dto.SysDeptCreateRequest;
import com.colonel.saas.auth.dto.SysDeptUpdateRequest;
import com.colonel.saas.common.result.PageResult;
import com.colonel.saas.auth.service.SysDeptService;
import com.colonel.saas.auth.service.SysUserService;
import com.colonel.saas.common.base.BaseController;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.vo.DeptStatsVO;
import com.colonel.saas.vo.SysDeptVO;
import com.colonel.saas.vo.SysUserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
@Tag(name = "系统部门", description = "组织/业务组（sys_dept）管理。")
@RestController
@RequestMapping({"/depts", "/departments"})
@RequireRoles({RoleCodes.ADMIN})
public class SysDeptController extends BaseController {

    private final SysDeptService sysDeptService;
    private final SysUserService sysUserService;

    public SysDeptController(SysDeptService sysDeptService, SysUserService sysUserService) {
        this.sysDeptService = sysDeptService;
        this.sysUserService = sysUserService;
    }

    @Operation(summary = "部门树", description = "返回未删除部门的树形结构。")
    @GetMapping("/tree")
    public ApiResult<List<SysDeptVO>> tree() {
        return ok(sysDeptService.findTree());
    }

    @Operation(summary = "部门列表", description = "返回未删除部门的扁平列表。")
    @GetMapping
    public ApiResult<List<SysDeptVO>> list() {
        return ok(sysDeptService.findAll());
    }

    @Operation(summary = "部门详情")
    @GetMapping("/{id}")
    public ApiResult<SysDeptVO> detail(
            @Parameter(description = "部门 ID") @PathVariable UUID id) {
        return ok(sysDeptService.getById(id));
    }

    @Operation(summary = "新建部门")
    @PostMapping
    public ApiResult<SysDeptVO> create(
            @Valid @RequestBody SysDeptCreateRequest request,
            @RequestAttribute(value = "userId", required = false) UUID userId) {
        return ok(sysDeptService.create(request, userId));
    }

    @Operation(summary = "更新部门")
    @PutMapping("/{id}")
    public ApiResult<SysDeptVO> update(
            @PathVariable UUID id,
            @Valid @RequestBody SysDeptUpdateRequest request,
            @RequestAttribute(value = "userId", required = false) UUID userId) {
        return ok(sysDeptService.update(id, request, userId));
    }

    @Operation(summary = "删除部门")
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(
            @PathVariable UUID id,
            @RequestAttribute(value = "userId", required = false) UUID userId) {
        sysDeptService.delete(id, userId);
        return ok();
    }

    @Operation(summary = "部门统计", description = "成员数、招商组数、渠道组数")
    @GetMapping("/{id}/stats")
    public ApiResult<DeptStatsVO> stats(@PathVariable UUID id) {
        return ok(sysDeptService.getStats(id));
    }

    @Operation(summary = "部门成员分页")
    @GetMapping("/{id}/members")
    public ApiResult<PageResult<SysUserVO>> members(
            @PathVariable UUID id,
            @Valid DeptMemberPageRequest request) {
        return okPage(sysDeptService.findMembers(id, request));
    }

    @Operation(summary = "部门下业务组列表")
    @GetMapping("/{id}/groups")
    public ApiResult<List<SysDeptVO>> groups(
            @PathVariable UUID id,
            @RequestParam(required = false) String deptType) {
        return ok(sysDeptService.findGroupsByParent(id, deptType));
    }

    @Operation(summary = "添加组成员")
    @PostMapping("/groups/{groupId}/members")
    public ApiResult<Void> addGroupMembers(
            @PathVariable UUID groupId,
            @Valid @RequestBody GroupMemberMutationRequest request,
            @RequestAttribute(value = "userId", required = false) UUID userId) {
        sysUserService.assignUsersToGroup(groupId, request.userIds(), userId);
        return ok();
    }

    @Operation(summary = "移除组成员")
    @DeleteMapping("/groups/{groupId}/members")
    public ApiResult<Void> removeGroupMembers(
            @PathVariable UUID groupId,
            @Valid @RequestBody GroupMemberMutationRequest request,
            @RequestAttribute(value = "userId", required = false) UUID userId) {
        sysUserService.removeUsersFromGroup(groupId, request.userIds(), userId);
        return ok();
    }
}
