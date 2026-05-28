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

/**
 * 当前用户控制器.
 *
 * <p>提供当前登录用户的操作接口，包括用户信息查询、密码修改、
 * 数据范围解析和操作权限检查，属于用户域。</p>
 *
 * <p>API 路径前缀：{@code /users/current}</p>
 *
 * <p>本控制器不设置 {@code @RequireRoles}，所有已认证用户均可访问。</p>
 *
 * @see UserDomainService
 */
@Tag(name = "用户域", description = "当前登录用户、数据范围与操作权限接口。")
@RestController
@RequestMapping("/users/current")
public class CurrentUserController extends BaseController {

    /** 用户域服务，提供用户信息查询、密码修改与权限检查 */
    private final UserDomainService userDomainService;

    /**
     * 构造注入.
     *
     * @param userDomainService 用户域服务
     */
    public CurrentUserController(UserDomainService userDomainService) {
        this.userDomainService = userDomainService;
    }

    /**
     * 获取当前登录用户信息.
     *
     * <p>返回当前登录用户的基础信息、角色编码列表、数据范围（self/group/all）
     * 和聚合权限包，供前端初始化用户上下文。</p>
     *
     * @param userId    当前登录用户 ID
     * @param deptId    当前用户所属部门 ID（可选）
     * @param dataScope 当前用户数据范围枚举（可选）
     * @param roleCodes 当前用户角色编码列表（可选）
     * @return 用户信息响应，包含基础信息、角色、数据范围和权限包
     */
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
                // 角色列表为空时降级为空集合，避免 NPE
                roleCodes == null ? Collections.emptyList() : roleCodes
        ));
    }

    /**
     * 修改当前用户密码.
     *
     * <p>登录用户验证原密码后修改自己的登录密码。
     * 请求体必须包含原密码和新密码，由 {@code @Valid} 触发参数校验。</p>
     *
     * @param userId  当前登录用户 ID
     * @param request 密码修改请求，包含原密码和新密码
     * @return 操作结果（无返回数据）
     * @throws com.colonel.saas.common.exception.BusinessException 原密码错误或新密码不符合规则时抛出
     */
    @Operation(summary = "当前用户修改密码", description = "登录用户校验原密码后修改自己的登录密码。")
    @PutMapping("/password")
    public ApiResult<Void> changePassword(
            @RequestAttribute("userId") UUID userId,
            @Valid @RequestBody ChangePasswordRequest request) {
        userDomainService.changePassword(userId, request);
        return ok();
    }

    /**
     * 解析当前用户数据范围.
     *
     * <p>返回当前用户的数据范围级别（self/group/all）及对应的用户 ID 限制列表，
     * 供前端根据数据范围控制数据展示粒度。</p>
     *
     * @param userId    当前登录用户 ID
     * @param deptId    当前用户所属部门 ID（可选）
     * @param dataScope 当前用户数据范围枚举（可选）
     * @return 数据范围响应，包含范围级别和用户 ID 限制列表
     */
    @Operation(summary = "解析当前用户数据范围", description = "返回 self/group/all 与对应用户 ID 限制列表。")
    @GetMapping("/data-scope")
    public ApiResult<UserDataScopeResponse> dataScope(
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope) {
        return ok(userDomainService.getUserDataScope(userId, deptId, dataScope));
    }

    /**
     * 检查当前用户操作权限.
     *
     * <p>按资源域（domain）和操作名（action）检查当前登录用户是否具备指定操作权限，
     * 用于前端按钮级别的权限控制。</p>
     *
     * @param userId    当前登录用户 ID
     * @param roleCodes 当前用户角色编码列表（可选）
     * @param request   权限检查请求，包含资源域和操作名
     * @return 权限检查响应，包含是否有权限
     */
    @Operation(summary = "检查当前用户操作权限", description = "按资源域和操作名检查当前登录用户是否具备操作权限。")
    @PostMapping("/permissions/check")
    public ApiResult<CheckPermissionResponse> checkPermission(
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "roleCodes", required = false) List<String> roleCodes,
            @Valid @RequestBody CheckPermissionRequest request) {
        return ok(userDomainService.checkPermission(
                userId,
                // 角色列表为空时降级为空集合
                roleCodes == null ? Collections.emptyList() : roleCodes,
                request
        ));
    }
}
