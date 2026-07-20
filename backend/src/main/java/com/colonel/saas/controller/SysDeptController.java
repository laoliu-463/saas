package com.colonel.saas.controller;

import com.colonel.saas.annotation.RequirePermission;
import com.colonel.saas.auth.dto.DeptMemberPageRequest;
import com.colonel.saas.auth.dto.GroupMemberMutationRequest;
import com.colonel.saas.auth.dto.SysDeptCreateRequest;
import com.colonel.saas.auth.dto.SysDeptUpdateRequest;
import com.colonel.saas.common.result.PageResult;
import com.colonel.saas.auth.service.SysDeptService;
import com.colonel.saas.auth.service.SysUserService;
import com.colonel.saas.common.base.BaseController;
import com.colonel.saas.common.result.ApiResult;
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

/**
 * 系统部门管理控制器，供管理员管理组织架构和业务组。
 *
 * <ul>
 *   <li>查询部门树形结构和扁平列表</li>
 *   <li>查询部门详情、统计信息和成员列表</li>
 *   <li>创建、更新和删除部门</li>
 *   <li>查询部门下的业务组列表</li>
 *   <li>添加和移除业务组成员</li>
 * </ul>
 *
 * <p>所属业务领域：用户域 / 组织架构管理
 * <p>API 路径前缀：{@code /depts} 或 {@code /departments}
 * <p>访问权限：管理员（{@link com.colonel.saas.constant.RoleCodes#ADMIN}）和组长（{@link com.colonel.saas.constant.RoleCodes#CHANNEL_LEADER}）；
 *     组长仅可查看和编辑自己负责的部门，写操作由 {@link SysDeptService} 做 leader 归属校验。
 *
 * @see com.colonel.saas.auth.service.SysDeptService
 * @see com.colonel.saas.auth.service.SysUserService
 */
@Validated
@Tag(name = "系统部门", description = "组织/业务组（sys_dept）管理。")
@RestController
@RequestMapping({"/depts", "/departments"})
@RequirePermission("sys-dept:access")
public class SysDeptController extends BaseController {

    /** 系统部门服务，负责部门的增删改查、树形结构和统计查询 */
    private final SysDeptService sysDeptService;

    /** 系统用户服务，负责业务组成员的添加和移除 */
    private final SysUserService sysUserService;

    /**
     * 构造注入系统部门服务和系统用户服务。
     *
     * @param sysDeptService 系统部门服务实例
     * @param sysUserService 系统用户服务实例
     */
    public SysDeptController(SysDeptService sysDeptService, SysUserService sysUserService) {
        this.sysDeptService = sysDeptService;
        this.sysUserService = sysUserService;
    }

    /**
     * 查询部门树形结构。
     *
     * <p>处理流程：
     * <ol>
     *   <li>查询所有未删除的部门记录</li>
     *   <li>按父子关系组装树形结构</li>
     *   <li>返回部门树形视图对象列表</li>
     * </ol>
     *
     * <p>HTTP 方法与路径：{@code GET /depts/tree} 或 {@code GET /departments/tree}
     *
     * @return 部门树形结构列表
     */
    @Operation(summary = "部门树", description = "返回未删除部门的树形结构。")
    @GetMapping("/tree")
    public ApiResult<List<SysDeptVO>> tree() {
        return ok(sysDeptService.findTree());
    }

    /**
     * 查询部门扁平列表。
     *
     * <p>处理流程：
     * <ol>
     *   <li>查询所有未删除的部门记录</li>
     *   <li>返回扁平化的部门列表</li>
     * </ol>
     *
     * <p>HTTP 方法与路径：{@code GET /depts} 或 {@code GET /departments}
     *
     * @return 未删除部门的扁平列表
     */
    @Operation(summary = "部门列表", description = "返回未删除部门的扁平列表。")
    @GetMapping
    public ApiResult<List<SysDeptVO>> list() {
        return ok(sysDeptService.findAll());
    }

    /**
     * 查询部门详情。
     *
     * <p>处理流程：
     * <ol>
     *   <li>根据部门 ID 查询部门记录</li>
     *   <li>验证部门是否存在，不存在则抛出业务异常</li>
     *   <li>返回部门详情视图对象</li>
     * </ol>
     *
     * <p>HTTP 方法与路径：{@code GET /depts/{id}} 或 {@code GET /departments/{id}}
     *
     * @param id 部门 ID
     * @return 部门详情视图对象
     * @throws com.colonel.saas.common.exception.BusinessException 部门不存在
     */
    @Operation(summary = "部门详情")
    @GetMapping("/{id}")
    public ApiResult<SysDeptVO> detail(
            @Parameter(description = "部门 ID") @PathVariable("id") UUID id) {
        return ok(sysDeptService.getById(id));
    }

    /**
     * 创建新部门。
     *
     * <p>处理流程：
     * <ol>
     *   <li>校验创建请求参数的合法性</li>
     *   <li>验证父部门是否存在</li>
     *   <li>创建部门记录</li>
     *   <li>返回新创建的部门详情</li>
     * </ol>
     *
     * <p>HTTP 方法与路径：{@code POST /depts} 或 {@code POST /departments}
     *
     * @param request 部门创建请求，包含部门名称、类型和父部门 ID 等
     * @param userId  当前操作人 ID（从 JWT 解析），可为空
     * @return 新创建的部门详情视图对象
     * @throws com.colonel.saas.common.exception.BusinessException 参数校验失败或父部门不存在
     */
    @Operation(summary = "新建部门")
    @PostMapping
    public ApiResult<SysDeptVO> create(
            @Valid @RequestBody SysDeptCreateRequest request,
            @RequestAttribute(value = "userId", required = false) UUID userId) {
        return ok(sysDeptService.create(request, userId));
    }

    /**
     * 更新部门信息。
     *
     * <p>处理流程：
     * <ol>
     *   <li>根据 ID 查找部门记录</li>
     *   <li>校验更新请求参数的合法性</li>
     *   <li>更新部门信息</li>
     *   <li>返回更新后的部门详情</li>
     * </ol>
     *
     * <p>HTTP 方法与路径：{@code PUT /depts/{id}} 或 {@code PUT /departments/{id}}
     *
     * @param id      部门 ID
     * @param request 部门更新请求，包含待更新的字段
     * @param userId  当前操作人 ID（从 JWT 解析），可为空
     * @return 更新后的部门详情视图对象
     * @throws com.colonel.saas.common.exception.BusinessException 部门不存在或参数校验失败
     */
    @Operation(summary = "更新部门")
    @PutMapping("/{id}")
    public ApiResult<SysDeptVO> update(
            @PathVariable("id") UUID id,
            @Valid @RequestBody SysDeptUpdateRequest request,
            @RequestAttribute(value = "userId", required = false) UUID userId,
            @RequestAttribute(value = "roleCodes", required = false) List<String> roleCodes) {
        return ok(sysDeptService.update(id, request, userId, roleCodes));
    }

    /**
     * 删除部门（逻辑删除）。
     *
     * <p>处理流程：
     * <ol>
     *   <li>根据 ID 查找部门记录</li>
     *   <li>验证部门是否可删除（是否有子部门、是否有成员等）</li>
     *   <li>执行逻辑删除</li>
     * </ol>
     *
     * <p>HTTP 方法与路径：{@code DELETE /depts/{id}} 或 {@code DELETE /departments/{id}}
     *
     * @param id     部门 ID
     * @param userId 当前操作人 ID（从 JWT 解析），可为空
     * @return 操作成功（无数据体）
     * @throws com.colonel.saas.common.exception.BusinessException 部门不存在或不可删除
     */
    @Operation(summary = "删除部门")
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(
            @PathVariable("id") UUID id,
            @RequestAttribute(value = "userId", required = false) UUID userId,
            @RequestAttribute(value = "roleCodes", required = false) List<String> roleCodes) {
        sysDeptService.delete(id, userId, roleCodes);
        return ok();
    }

    /**
     * 查询部门统计信息。
     *
     * <p>处理流程：
     * <ol>
     *   <li>根据部门 ID 查询部门</li>
     *   <li>统计部门下的成员数、招商组数和渠道组数</li>
     *   <li>返回部门统计视图对象</li>
     * </ol>
     *
     * <p>HTTP 方法与路径：{@code GET /depts/{id}/stats} 或 {@code GET /departments/{id}/stats}
     *
     * @param id 部门 ID
     * @return 部门统计信息，包含成员数、招商组数和渠道组数
     */
    @Operation(summary = "部门统计", description = "成员数、招商组数、渠道组数")
    @GetMapping("/{id}/stats")
    public ApiResult<DeptStatsVO> stats(@PathVariable("id") UUID id) {
        return ok(sysDeptService.getStats(id));
    }

    /**
     * 分页查询部门成员列表。
     *
     * <p>处理流程：
     * <ol>
     *   <li>根据部门 ID 查询部门</li>
     *   <li>按分页参数和筛选条件查询部门成员</li>
     *   <li>返回分页后的成员列表</li>
     * </ol>
     *
     * <p>HTTP 方法与路径：{@code GET /depts/{id}/members} 或 {@code GET /departments/{id}/members}
     *
     * @param id      部门 ID
     * @param request 部门成员分页请求，包含页码、每页数量等筛选条件
     * @return 分页后的成员列表
     */
    @Operation(summary = "部门成员分页")
    @GetMapping("/{id}/members")
    public ApiResult<PageResult<SysUserVO>> members(
            @PathVariable("id") UUID id,
            @Valid DeptMemberPageRequest request) {
        return okPage(sysDeptService.findMembers(id, request));
    }

    /**
     * 查询部门下的业务组列表。
     *
     * <p>处理流程：
     * <ol>
     *   <li>根据父部门 ID 查询其下属的业务组</li>
     *   <li>可选按业务组类型筛选</li>
     *   <li>返回业务组列表</li>
     * </ol>
     *
     * <p>HTTP 方法与路径：{@code GET /depts/{id}/groups} 或 {@code GET /departments/{id}/groups}
     *
     * @param id       父部门 ID
     * @param deptType 业务组类型筛选，可为空
     * @return 部门下的业务组列表
     */
    @Operation(summary = "部门下业务组列表")
    @GetMapping("/{id}/groups")
    public ApiResult<List<SysDeptVO>> groups(
            @PathVariable("id") UUID id,
            @RequestParam(name = "deptType", required = false) String deptType) {
        return ok(sysDeptService.findGroupsByParent(id, deptType));
    }

    /**
     * 添加业务组成员。
     *
     * <p>处理流程：
     * <ol>
     *   <li>校验成员变更请求参数的合法性</li>
     *   <li>验证业务组是否存在</li>
     *   <li>将指定用户添加到业务组</li>
     * </ol>
     *
     * <p>HTTP 方法与路径：{@code POST /depts/groups/{groupId}/members} 或 {@code POST /departments/groups/{groupId}/members}
     *
     * @param groupId 业务组 ID
     * @param request 组成员变更请求，包含待添加的用户 ID 列表
     * @param userId  当前操作人 ID（从 JWT 解析），可为空
     * @return 操作成功（无数据体）
     * @throws com.colonel.saas.common.exception.BusinessException 业务组不存在或用户不存在
     */
    @Operation(summary = "添加组成员")
    @PostMapping("/groups/{groupId}/members")
    public ApiResult<Void> addGroupMembers(
            @PathVariable("groupId") UUID groupId,
            @Valid @RequestBody GroupMemberMutationRequest request,
            @RequestAttribute(value = "userId", required = false) UUID userId) {
        sysUserService.assignUsersToGroup(groupId, request.userIds(), userId);
        return ok();
    }

    /**
     * 移除业务组成员。
     *
     * <p>处理流程：
     * <ol>
     *   <li>校验成员变更请求参数的合法性</li>
     *   <li>验证业务组是否存在</li>
     *   <li>将指定用户从业务组中移除</li>
     * </ol>
     *
     * <p>HTTP 方法与路径：{@code DELETE /depts/groups/{groupId}/members} 或 {@code DELETE /departments/groups/{groupId}/members}
     *
     * @param groupId 业务组 ID
     * @param request 组成员变更请求，包含待移除的用户 ID 列表
     * @param userId  当前操作人 ID（从 JWT 解析），可为空
     * @return 操作成功（无数据体）
     * @throws com.colonel.saas.common.exception.BusinessException 业务组不存在或用户不在组内
     */
    @Operation(summary = "移除组成员")
    @DeleteMapping("/groups/{groupId}/members")
    public ApiResult<Void> removeGroupMembers(
            @PathVariable("groupId") UUID groupId,
            @Valid @RequestBody GroupMemberMutationRequest request,
            @RequestAttribute(value = "userId", required = false) UUID userId) {
        sysUserService.removeUsersFromGroup(groupId, request.userIds(), userId);
        return ok();
    }
}
