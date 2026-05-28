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

/**
 * 系统菜单控制器
 * <p>
 * 提供系统菜单的树形结构管理接口，支持当前用户可见菜单树查询、
 * 全量菜单树查询（仅管理员）、新建菜单节点、更新菜单节点和删除菜单节点。
 * 菜单数据以树形结构组织，前端侧边栏和权限路由均基于此数据渲染。
 * </p>
 *
 * <p>API 路径前缀：{@code /menus}</p>
 * <p>所属业务领域：权限管理（菜单管理）</p>
 * <p>访问权限：菜单树查询根据用户角色返回可见范围；增删改操作仅管理员</p>
 *
 * @see com.colonel.saas.auth.service.SysMenuService
 */
@Validated
@Tag(name = "系统菜单", description = "系统菜单管理接口，包括菜单树查询、新增、编辑与删除。")
@RestController
@RequestMapping("/menus")
public class SysMenuController extends BaseController {

    /** 菜单服务，负责菜单的树形查询、创建、更新和删除 */
    private final SysMenuService sysMenuService;

    /**
     * 构造函数，注入菜单服务依赖
     *
     * @param sysMenuService 菜单服务实例
     */
    public SysMenuController(SysMenuService sysMenuService) {
        this.sysMenuService = sysMenuService;
    }

    /**
     * 当前用户菜单树
     * <p>
     * 根据当前登录用户的角色权限，返回该用户可见的菜单树形结构。
     * 菜单按父子关系组织为树形，前端用于渲染侧边栏导航。
     * 支持按菜单状态进行筛选。
     * </p>
     *
     * <p>HTTP 方法：GET</p>
     * <p>请求路径：{@code /menus/tree}</p>
     *
     * @param status    菜单状态筛选条件（可选）
     * @param userId    当前用户 ID（由拦截器注入）
     * @param deptId    当前用户所属部门 ID（由拦截器注入，可选）
     * @param dataScope 数据权限范围（由拦截器注入，可选）
     * @return 当前用户可见的菜单树形列表
     */
    @Operation(summary = "当前用户菜单树", description = "根据当前用户角色返回可见菜单树。")
    @GetMapping("/tree")
    public ApiResult<List<SysMenuVO>> userTree(
            @Parameter(description = "菜单状态筛选") @RequestParam(name = "status", required = false) Integer status,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope) {
        return ok(sysMenuService.findUserTreeByUserId(userId, status));
    }

    /**
     * 全量菜单树
     * <p>
     * 查询系统中所有的菜单节点，构建完整的菜单树形结构。
     * 仅管理员可用，用于菜单管理页面展示和编辑。
     * </p>
     *
     * <p>HTTP 方法：GET</p>
     * <p>请求路径：{@code /menus/all}</p>
     *
     * @param status    菜单状态筛选条件（可选）
     * @param userId    当前用户 ID（由拦截器注入）
     * @param deptId    当前用户所属部门 ID（由拦截器注入，可选）
     * @param dataScope 数据权限范围（由拦截器注入，可选）
     * @return 完整的菜单树形列表
     */
    @Operation(summary = "全量菜单树", description = "查询全部菜单树，仅管理员可用。")
    @GetMapping("/all")
    @RequireRoles({RoleCodes.ADMIN})
    public ApiResult<List<SysMenuVO>> allTree(
            @Parameter(description = "菜单状态筛选") @RequestParam(name = "status", required = false) Integer status,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope) {
        return ok(sysMenuService.findAllTree(status));
    }

    /**
     * 新建菜单
     * <p>
     * 创建新的系统菜单节点。创建时记录操作人信息。
     * 仅管理员可用。
     * </p>
     *
     * <p>HTTP 方法：POST</p>
     * <p>请求路径：{@code /menus}</p>
     *
     * @param request  菜单创建请求体（包含菜单名称、路径、图标、排序等）
     * @param userId   当前操作用户 ID（由拦截器注入）
     * @param deptId   当前用户所属部门 ID（由拦截器注入，可选）
     * @param dataScope 数据权限范围（由拦截器注入，可选）
     * @return 创建成功的菜单详情
     */
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

    /**
     * 更新菜单
     * <p>
     * 更新指定菜单节点的信息，包括名称、路径、图标、排序等。
     * 仅管理员可用。
     * </p>
     *
     * <p>HTTP 方法：PUT</p>
     * <p>请求路径：{@code /menus/{id}}</p>
     *
     * @param id       菜单主键 ID（UUID 格式）
     * @param request  菜单更新请求体
     * @param userId   当前操作用户 ID（由拦截器注入）
     * @param deptId   当前用户所属部门 ID（由拦截器注入，可选）
     * @param dataScope 数据权限范围（由拦截器注入，可选）
     * @return 更新后的菜单详情
     */
    @Operation(summary = "更新菜单", description = "更新系统菜单节点信息。")
    @PutMapping("/{id}")
    @RequireRoles({RoleCodes.ADMIN})
    public ApiResult<SysMenuVO> update(
            @Parameter(description = "菜单主键 ID") @PathVariable("id") UUID id,
            @Valid @RequestBody SysMenuUpdateRequest request,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope) {
        return ok(sysMenuService.update(id, request, userId));
    }

    /**
     * 删除菜单
     * <p>
     * 删除指定的菜单节点。仅管理员可用。
     * </p>
     *
     * <p>HTTP 方法：DELETE</p>
     * <p>请求路径：{@code /menus/{id}}</p>
     *
     * @param id       菜单主键 ID（UUID 格式）
     * @param userId   当前操作用户 ID（由拦截器注入）
     * @param deptId   当前用户所属部门 ID（由拦截器注入，可选）
     * @param dataScope 数据权限范围（由拦截器注入，可选）
     * @return 无返回数据
     */
    @Operation(summary = "删除菜单", description = "删除指定菜单节点。")
    @DeleteMapping("/{id}")
    @RequireRoles({RoleCodes.ADMIN})
    public ApiResult<Void> delete(
            @Parameter(description = "菜单主键 ID") @PathVariable("id") UUID id,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope) {
        sysMenuService.delete(id, userId);
        return ok();
    }
}
