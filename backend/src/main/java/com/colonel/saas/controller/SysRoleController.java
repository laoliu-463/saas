package com.colonel.saas.controller;

import com.colonel.saas.annotation.RequirePermission;
import com.colonel.saas.auth.dto.SysRoleCreateRequest;
import com.colonel.saas.auth.dto.SysRoleUpdateRequest;
import com.colonel.saas.auth.service.SysMenuService;
import com.colonel.saas.auth.service.SysRoleService;
import com.colonel.saas.common.base.BaseController;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.common.result.PageResult;
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

/**
 * 系统角色控制器
 * <p>
 * 提供系统角色的完整管理接口，包括角色的分页查询、详情查看、
 * 启用列表查询、新建、编辑、删除，以及角色与菜单的权限分配。
 * 角色是系统权限模型的核心，决定了用户可访问的菜单和功能。
 * </p>
 *
 * <p>API 路径前缀：{@code /roles}</p>
 * <p>所属业务领域：权限管理（角色管理）</p>
 * <p>访问权限：仅管理员（ADMIN）</p>
 *
 * @see com.colonel.saas.auth.service.SysRoleService
 * @see com.colonel.saas.auth.service.SysMenuService
 */
@Validated
@Tag(name = "系统角色", description = "系统角色管理接口，包括分页、详情、启用列表、新增、编辑与删除。")
@RestController
@RequestMapping("/roles")
@RequirePermission("sys-role:access")
public class SysRoleController extends BaseController {

    /** 角色服务，负责角色的增删改查 */
    private final SysRoleService sysRoleService;

    /** 菜单服务，负责角色与菜单的关联管理 */
    private final SysMenuService sysMenuService;

    /**
     * 构造函数，注入角色服务和菜单服务依赖
     *
     * @param sysRoleService 角色服务实例
     * @param sysMenuService 菜单服务实例
     */
    public SysRoleController(SysRoleService sysRoleService, SysMenuService sysMenuService) {
        this.sysRoleService = sysRoleService;
        this.sysMenuService = sysMenuService;
    }

    /**
     * 分页查询角色
     * <p>
     * 按关键字和状态分页查询系统角色列表，用于角色管理页面展示。
     * </p>
     *
     * <p>HTTP 方法：GET</p>
     * <p>请求路径：{@code /roles}</p>
     *
     * @param page      页码，从 1 开始，默认值 1
     * @param size      每页条数，默认值 10，最大 100
     * @param keyword   角色关键字（可选），模糊匹配角色名称
     * @param status    角色状态（可选），取值含义请联系产品确认
     * @param userId    当前用户 ID（由拦截器注入）
     * @param deptId    当前用户所属部门 ID（由拦截器注入，可选）
     * @param dataScope 数据权限范围（由拦截器注入，可选）
     * @return 分页后的角色列表
     * @throws jakarta.validation.ConstraintViolationException 当 page < 1 或 size 超出范围时
     */
    @Operation(summary = "分页查询角色", description = "按关键字与状态分页查询角色列表。")
    @GetMapping
    public ApiResult<PageResult<SysRoleVO>> page(
            @Parameter(description = "页码，从 1 开始。") @RequestParam(name = "page", defaultValue = "1") @Min(1) long page,
            @Parameter(description = "每页条数。") @RequestParam(name = "size", defaultValue = "10") @Min(1) @Max(100) long size,
            @Parameter(description = "角色关键字。") @RequestParam(name = "keyword", required = false) String keyword,
            @Parameter(description = "角色状态。待确认：取值含义请联系产品。") @RequestParam(name = "status", required = false) Integer status,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope) {
        return okPage(sysRoleService.findPage(page, size, keyword, status));
    }

    /**
     * 角色详情
     * <p>
     * 根据主键 ID 查询单个角色的详细信息。
     * </p>
     *
     * <p>HTTP 方法：GET</p>
     * <p>请求路径：{@code /roles/{id}}</p>
     *
     * @param id        角色主键 ID（UUID 格式）
     * @param userId    当前用户 ID（由拦截器注入）
     * @param deptId    当前用户所属部门 ID（由拦截器注入，可选）
     * @param dataScope 数据权限范围（由拦截器注入，可选）
     * @return 角色详情
     */
    @Operation(summary = "角色详情", description = "查询单个角色详情。")
    @GetMapping("/{id}")
    public ApiResult<SysRoleVO> detail(
            @Parameter(description = "角色主键 ID，使用 UUID 格式。") @PathVariable("id") UUID id,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope) {
        return ok(sysRoleService.getById(id));
    }

    /**
     * 全量角色列表（启用状态）
     * <p>
     * 查询所有已启用的角色，用于用户分配角色时的下拉框选择。
     * 返回不分页的完整列表。
     * </p>
     *
     * <p>HTTP 方法：GET</p>
     * <p>请求路径：{@code /roles/enabled}</p>
     *
     * @param userId    当前用户 ID（由拦截器注入）
     * @param deptId    当前用户所属部门 ID（由拦截器注入，可选）
     * @param dataScope 数据权限范围（由拦截器注入，可选）
     * @return 启用状态的角色列表
     */
    @Operation(summary = "全量角色列表", description = "查询当前启用的全部角色，用于用户分配角色下拉框。")
    @GetMapping("/enabled")
    public ApiResult<List<SysRoleVO>> all(
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope) {
        return ok(sysRoleService.findAllEnabled());
    }

    /**
     * 新建角色
     * <p>
     * 创建新的系统角色。创建时记录操作人信息。
     * </p>
     *
     * <p>HTTP 方法：POST</p>
     * <p>请求路径：{@code /roles}</p>
     *
     * @param request  角色创建请求体（包含角色名称 name 和角色编码 code）
     * @param userId   当前操作用户 ID（由拦截器注入）
     * @param deptId   当前用户所属部门 ID（由拦截器注入，可选）
     * @param dataScope 数据权限范围（由拦截器注入，可选）
     * @return 创建成功的角色详情
     */
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

    /**
     * 更新角色
     * <p>
     * 更新指定角色的信息，如角色名称等。
     * </p>
     *
     * <p>HTTP 方法：PUT</p>
     * <p>请求路径：{@code /roles/{id}}</p>
     *
     * @param id        角色主键 ID（UUID 格式）
     * @param request   角色更新请求体
     * @param userId    当前操作用户 ID（由拦截器注入）
     * @param deptId    当前用户所属部门 ID（由拦截器注入，可选）
     * @param dataScope 数据权限范围（由拦截器注入，可选）
     * @return 更新后的角色详情
     */
    @Operation(summary = "更新角色", description = "更新系统角色信息。")
    @PutMapping("/{id}")
    public ApiResult<SysRoleVO> update(
            @Parameter(description = "角色主键 ID，使用 UUID 格式。") @PathVariable("id") UUID id,
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

    /**
     * 删除角色
     * <p>
     * 删除指定的系统角色。
     * </p>
     *
     * <p>HTTP 方法：DELETE</p>
     * <p>请求路径：{@code /roles/{id}}</p>
     *
     * @param id        角色主键 ID（UUID 格式）
     * @param userId    当前操作用户 ID（由拦截器注入）
     * @param deptId    当前用户所属部门 ID（由拦截器注入，可选）
     * @param dataScope 数据权限范围（由拦截器注入，可选）
     * @return 无返回数据
     */
    @Operation(summary = "删除角色", description = "删除指定角色。")
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(
            @Parameter(description = "角色主键 ID，使用 UUID 格式。") @PathVariable("id") UUID id,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope) {
        sysRoleService.delete(id, userId);
        return ok();
    }

    /**
     * 查询角色菜单 ID 列表
     * <p>
     * 查询指定角色当前绑定的所有菜单 ID 列表，
     * 用于角色菜单权限编辑页面的初始数据回显。
     * </p>
     *
     * <p>HTTP 方法：GET</p>
     * <p>请求路径：{@code /roles/{id}/menus}</p>
     *
     * @param id        角色主键 ID（UUID 格式）
     * @param userId    当前用户 ID（由拦截器注入）
     * @param deptId    当前用户所属部门 ID（由拦截器注入，可选）
     * @param dataScope 数据权限范围（由拦截器注入，可选）
     * @return 该角色绑定的菜单 ID 列表
     */
    @Operation(summary = "查询角色菜单ID列表", description = "查询指定角色绑定的菜单ID列表。")
    @GetMapping("/{id}/menus")
    public ApiResult<List<UUID>> getRoleMenus(
            @Parameter(description = "角色主键 ID") @PathVariable("id") UUID id,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope) {
        return ok(sysMenuService.getMenuIdsByRoleId(id));
    }

    /**
     * 分配角色菜单
     * <p>
     * 为指定角色分配菜单权限。该操作会覆盖角色原有的菜单关联关系，
     * 即以传入的 menuIds 列表为准。用于角色权限编辑页面的保存操作。
     * </p>
     *
     * <p>HTTP 方法：PUT</p>
     * <p>请求路径：{@code /roles/{id}/menus}</p>
     *
     * @param id        角色主键 ID（UUID 格式）
     * @param menuIds   要分配的菜单 ID 列表（覆盖原有关联）
     * @param userId    当前操作用户 ID（由拦截器注入）
     * @param deptId    当前用户所属部门 ID（由拦截器注入，可选）
     * @param dataScope 数据权限范围（由拦截器注入，可选）
     * @return 无返回数据
     */
    @Operation(summary = "分配角色菜单", description = "为指定角色分配菜单权限，会覆盖原有菜单关联。")
    @PutMapping("/{id}/menus")
    public ApiResult<Void> assignRoleMenus(
            @Parameter(description = "角色主键 ID") @PathVariable("id") UUID id,
            @RequestBody List<UUID> menuIds,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope) {
        sysMenuService.assignMenusToRole(id, menuIds, userId);
        return ok();
    }
}
