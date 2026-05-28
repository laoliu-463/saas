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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * 系统用户控制器
 * <p>
 * 提供系统后台用户的完整管理接口，包括用户的分页查询、详情查看、
 * 新建、编辑、删除、重置密码和分配角色等操作。
 * 同时提供可分配负责人候选查询接口，供商品分配招商弹窗使用。
 * </p>
 *
 * <p>API 路径前缀：{@code /users}</p>
 * <p>所属业务领域：权限管理（用户管理）</p>
 * <p>访问权限：基础管理接口仅管理员（ADMIN）；部分接口开放给多角色</p>
 *
 * @see com.colonel.saas.auth.service.SysUserService
 */
@Tag(name = "系统用户", description = "系统后台用户管理接口，包括分页、详情、新增、编辑、删除、重置密码与分配角色。")
@RestController
@RequestMapping("/users")
@RequireRoles({RoleCodes.ADMIN, RoleCodes.CHANNEL_LEADER})
public class SysUserController extends BaseController {

    /** 系统用户服务，负责用户的增删改查和角色分配 */
    private final SysUserService sysUserService;

    /**
     * 构造函数，注入系统用户服务依赖
     *
     * @param sysUserService 系统用户服务实例
     */
    public SysUserController(SysUserService sysUserService) {
        this.sysUserService = sysUserService;
    }

    /**
     * 分页查询用户
     * <p>
     * 按分页条件查询系统用户列表。筛选条件由 SysUserPageRequest 承载，
     * 支持按用户名、姓名、部门等维度进行筛选。
     * 查询结果受当前用户的数据权限范围控制。
     * </p>
     *
     * <p>HTTP 方法：GET</p>
     * <p>请求路径：{@code /users}</p>
     *
     * @param request   用户分页查询请求参数（包含筛选条件和分页参数）
     * @param userId    当前用户 ID（由拦截器注入）
     * @param deptId    当前用户所属部门 ID（由拦截器注入，可选）
     * @param dataScope 数据权限范围（由拦截器注入，可选），控制查询结果的可见范围
     * @return 分页后的用户列表
     */
    @Operation(summary = "分页查询用户", description = "按分页条件查询系统用户列表。筛选条件由 SysUserPageRequest 承载。")
    @GetMapping
    public ApiResult<PageResult<SysUserVO>> page(
            @Valid SysUserPageRequest request,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope) {
        return okPage(sysUserService.findPage(userId, dataScope, request));
    }

    /**
     * 查询可分配负责人候选
     * <p>
     * 返回可用于商品分配招商弹窗的负责人候选列表。仅暴露最小必要字段
     * （如用户名、姓名），不包含敏感信息。支持按关键字搜索，
     * 匹配用户名或姓名。
     * </p>
     *
     * <p>HTTP 方法：GET</p>
     * <p>请求路径：{@code /users/assignable}</p>
     *
     * @param keyword   负责人搜索关键字（可选），匹配用户名或姓名
     * @param roleCodes 当前用户的角色代码列表（由拦截器注入，可选）
     * @param deptId    当前用户所属部门 ID（由拦截器注入，可选）
     * @return 可分配的负责人候选列表
     */
    @Operation(summary = "查询可分配负责人候选", description = "返回可用于商品分配招商弹窗的负责人候选，仅暴露最小必要字段。")
    @GetMapping("/assignable")
    @RequireRoles({RoleCodes.ADMIN, RoleCodes.BIZ_LEADER, RoleCodes.CHANNEL_LEADER, RoleCodes.COLONEL_LEADER})
    public ApiResult<List<SysUserVO>> assignable(
            @Parameter(description = "负责人关键字，匹配用户名或姓名。") @RequestParam(name = "keyword", required = false) String keyword,
            @RequestAttribute(value = "roleCodes", required = false) List<String> roleCodes,
            @RequestAttribute(value = "deptId", required = false) UUID deptId) {
        return ok(sysUserService.findAssignableUsers(keyword, roleCodes, deptId));
    }

    /**
     * 用户详情
     * <p>
     * 根据主键 ID 查询单个系统用户的详细信息。
     * 查询受当前用户的数据权限范围控制。
     * </p>
     *
     * <p>HTTP 方法：GET</p>
     * <p>请求路径：{@code /users/{id}}</p>
     *
     * @param id        用户主键 ID（UUID 格式）
     * @param userId    当前用户 ID（由拦截器注入）
     * @param dataScope 数据权限范围（由拦截器注入，可选）
     * @return 用户详情
     */
    @Operation(summary = "用户详情", description = "查询单个系统用户详情。")
    @GetMapping("/{id}")
    public ApiResult<SysUserVO> detail(
            @Parameter(description = "用户主键 ID，使用 UUID 格式。") @PathVariable("id") UUID id,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope) {
        return ok(sysUserService.getById(id, userId, dataScope));
    }

    /**
     * 新建用户
     * <p>
     * 创建新的系统后台用户，并写入角色、部门等基础信息。
     * 创建时记录操作人信息。
     * </p>
     *
     * <p>HTTP 方法：POST</p>
     * <p>请求路径：{@code /users}</p>
     *
     * @param request   用户创建请求体（包含用户名 username、真实姓名 realName、密码 password 等）
     * @param userId    当前操作用户 ID（由拦截器注入）
     * @param dataScope 数据权限范围（由拦截器注入，可选）
     * @return 创建成功的用户详情
     */
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

    /**
     * 更新用户
     * <p>
     * 更新指定系统用户的基础资料，如真实姓名等。
     * 更新操作受数据权限范围控制。
     * </p>
     *
     * <p>HTTP 方法：PUT</p>
     * <p>请求路径：{@code /users/{id}}</p>
     *
     * @param id        用户主键 ID（UUID 格式）
     * @param request   用户更新请求体
     * @param userId    当前操作用户 ID（由拦截器注入）
     * @param dataScope 数据权限范围（由拦截器注入，可选）
     * @return 更新后的用户详情
     */
    @Operation(summary = "更新用户", description = "更新系统用户的基础资料。")
    @PutMapping("/{id}")
    public ApiResult<SysUserVO> update(
            @Parameter(description = "用户主键 ID，使用 UUID 格式。") @PathVariable("id") UUID id,
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

    /**
     * 删除用户
     * <p>
     * 删除指定的系统后台用户。删除操作受数据权限范围控制。
     * </p>
     *
     * <p>HTTP 方法：DELETE</p>
     * <p>请求路径：{@code /users/{id}}</p>
     *
     * @param id        用户主键 ID（UUID 格式）
     * @param userId    当前操作用户 ID（由拦截器注入）
     * @param dataScope 数据权限范围（由拦截器注入，可选）
     * @return 无返回数据
     */
    @Operation(summary = "删除用户", description = "删除指定系统用户。")
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(
            @Parameter(description = "用户主键 ID，使用 UUID 格式。") @PathVariable("id") UUID id,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope) {
        sysUserService.delete(id, userId, dataScope);
        return ok();
    }

    /**
     * 重置密码
     * <p>
     * 重置指定用户的登录密码。密码重置操作受数据权限范围控制。
     * </p>
     *
     * <p>HTTP 方法：PUT</p>
     * <p>请求路径：{@code /users/{id}/password}</p>
     *
     * @param id        用户主键 ID（UUID 格式）
     * @param request   重置密码请求体（包含新密码 newPassword）
     * @param userId    当前操作用户 ID（由拦截器注入）
     * @param dataScope 数据权限范围（由拦截器注入，可选）
     * @return 无返回数据
     */
    @Operation(summary = "重置密码", description = "重置指定用户的登录密码。")
    @PutMapping("/{id}/password")
    public ApiResult<Void> resetPassword(
            @Parameter(description = "用户主键 ID，使用 UUID 格式。") @PathVariable("id") UUID id,
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

    /**
     * 分配角色
     * <p>
     * 为指定用户分配角色列表。该操作会覆盖用户原有的角色关联关系，
     * 即以传入的 roleIds 列表为准。分配操作受数据权限范围控制。
     * </p>
     *
     * <p>HTTP 方法：PUT</p>
     * <p>请求路径：{@code /users/{id}/roles}</p>
     *
     * @param id        用户主键 ID（UUID 格式）
     * @param request   角色分配请求体（包含角色 ID 列表 roleIds）
     * @param userId    当前操作用户 ID（由拦截器注入）
     * @param dataScope 数据权限范围（由拦截器注入，可选）
     * @return 无返回数据
     */
    @Operation(summary = "分配角色", description = "为指定用户分配角色列表。")
    @PutMapping("/{id}/roles")
    public ApiResult<Void> assignRoles(
            @Parameter(description = "用户主键 ID，使用 UUID 格式。") @PathVariable("id") UUID id,
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
