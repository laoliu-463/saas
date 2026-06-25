package com.colonel.saas.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.common.base.BaseController;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.common.result.PageResult;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.domain.user.policy.CurrentUserPermissionPolicy;
import com.colonel.saas.entity.SystemConfig;
import com.colonel.saas.service.SysConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import java.util.Map;
import java.util.UUID;

/**
 * 系统配置控制器
 * <p>
 * 提供系统配置项的完整 CRUD 管理接口，支持配置项的分页查询、
 * 分组查询、详情查看、新建、更新和删除操作。
 * 配置项按 configGroup 进行分组管理，业务模块可通过分组接口
 * 直接获取所需配置。
 * </p>
 *
 * <p>API 路径前缀：{@code /configs}</p>
 * <p>所属业务领域：配置域（系统配置管理）</p>
 * <p>访问权限：管理接口仅管理员（ADMIN）；分组查询开放给多个角色</p>
 *
 * @see com.colonel.saas.service.SysConfigService
 */
@Tag(name = "系统配置", description = "系统配置管理接口，提供配置项的增删改查和分组查询。")
@RestController
@RequestMapping("/configs")
@RequireRoles({RoleCodes.ADMIN})
public class SysConfigController extends BaseController {

    /** 系统配置服务，负责配置项的增删改查和分组查询 */
    private final SysConfigService sysConfigService;
    /** 用户域权限策略，负责解释请求上下文中的角色编码集合 */
    private final CurrentUserPermissionPolicy currentUserPermissionPolicy;

    /**
     * 构造函数，注入系统配置服务依赖
     *
     * @param sysConfigService            系统配置服务实例
     * @param currentUserPermissionPolicy 当前用户权限策略
     */
    public SysConfigController(
            SysConfigService sysConfigService,
            CurrentUserPermissionPolicy currentUserPermissionPolicy) {
        this.sysConfigService = sysConfigService;
        this.currentUserPermissionPolicy = currentUserPermissionPolicy;
    }

    /**
     * 分页查询配置
     * <p>
     * 按配置分组和关键字（匹配 configKey 或 configName）筛选配置项，
     * 返回分页结果。
     * </p>
     *
     * <p>HTTP 方法：GET</p>
     * <p>请求路径：{@code /configs}</p>
     *
     * @param configGroup 配置分组名称（可选）
     * @param keyword     搜索关键字（可选），匹配 configKey 或 configName
     * @param page        页码，从 1 开始，默认值 1
     * @param size        每页条数，默认值 20
     * @return 分页后的配置项列表
     */
    @Operation(summary = "分页查询配置", description = "按分组和关键字筛选配置项。")
    @GetMapping
    public ApiResult<PageResult<SystemConfig>> page(
            @Parameter(description = "配置分组") @RequestParam(name = "configGroup", required = false) String configGroup,
            @Parameter(description = "搜索关键字，匹配 configKey 或 configName") @RequestParam(name = "keyword", required = false) String keyword,
            @Parameter(description = "页码") @RequestParam(name = "page", defaultValue = "1") int page,
            @Parameter(description = "每页条数") @RequestParam(name = "size", defaultValue = "20") int size) {
        IPage<SystemConfig> result = sysConfigService.findPage(configGroup, keyword, page, size);
        return okPage(result);
    }

    /**
     * 分组查询配置
     * <p>
     * 返回按 configGroup 分组的启用状态配置项，供业务模块直接使用。
     * 管理员角色可查看所有配置，其他角色仅查看授权范围内的配置。
     * </p>
     *
     * <p>HTTP 方法：GET</p>
     * <p>请求路径：{@code /configs/grouped}</p>
     *
     * @param roleCodes 当前用户的角色代码列表（由拦截器注入）
     * @return 按分组名聚合的配置项 Map，key 为分组名，value 为该分组下的配置项列表
     */
    @Operation(summary = "分组查询配置", description = "返回按 configGroup 分组的启用配置项，供业务模块直接使用。")
    @GetMapping("/grouped")
    @RequireRoles({RoleCodes.ADMIN, RoleCodes.BIZ_LEADER, RoleCodes.BIZ_STAFF, RoleCodes.CHANNEL_LEADER, RoleCodes.CHANNEL_STAFF, RoleCodes.OPS_STAFF})
    public ApiResult<Map<String, List<SystemConfig>>> grouped(
            @RequestAttribute(value = "roleCodes", required = false) Object roleCodes) {
        return ok(sysConfigService.findGrouped(hasAdminRole(roleCodes)));
    }

    /**
     * 判断当前用户是否拥有管理员角色
     * <p>
     * 用于控制分组查询接口返回的配置范围。管理员可查看所有配置项，
     * 非管理员仅查看授权范围内的配置项。
     * </p>
     *
     * @param roleCodes 角色代码，可能是集合或逗号分隔字符串
     * @return 当前用户包含 ADMIN 角色时返回 true，否则返回 false
     */
    private boolean hasAdminRole(Object roleCodes) {
        return currentUserPermissionPolicy.hasAnyRole(roleCodes, RoleCodes.ADMIN);
    }

    /**
     * 配置详情
     * <p>
     * 根据主键 ID 查询单个配置项的完整信息。
     * </p>
     *
     * <p>HTTP 方法：GET</p>
     * <p>请求路径：{@code /configs/{id}}</p>
     *
     * @param id 配置项主键 ID（UUID 格式）
     * @return 配置项详情
     */
    @Operation(summary = "配置详情", description = "查询单个配置项详情。")
    @GetMapping("/{id}")
    public ApiResult<SystemConfig> detail(
            @Parameter(description = "配置项主键 ID") @PathVariable("id") UUID id) {
        return ok(sysConfigService.getById(id));
    }

    /**
     * 新建配置
     * <p>
     * 创建新的系统配置项。创建时会记录操作人信息。
     * </p>
     *
     * <p>HTTP 方法：POST</p>
     * <p>请求路径：{@code /configs}</p>
     *
     * @param config 新配置项数据（请求体）
     * @param userId 当前操作用户 ID（由拦截器注入）
     * @return 创建成功的配置项详情
     */
    @Operation(summary = "新建配置", description = "创建新的配置项。")
    @PostMapping
    public ApiResult<SystemConfig> create(
            @Valid @RequestBody SystemConfig config,
            @RequestAttribute("userId") UUID userId) {
        return ok(sysConfigService.create(config, userId));
    }

    /**
     * 更新配置
     * <p>
     * 更新指定配置项的信息，更新时会记录操作人信息。
     * </p>
     *
     * <p>HTTP 方法：PUT</p>
     * <p>请求路径：{@code /configs/{id}}</p>
     *
     * @param id     配置项主键 ID（UUID 格式）
     * @param config 更新后的配置项数据（请求体）
     * @param userId 当前操作用户 ID（由拦截器注入）
     * @return 更新后的配置项详情
     */
    @Operation(summary = "更新配置", description = "更新配置项信息。")
    @PutMapping("/{id}")
    public ApiResult<SystemConfig> update(
            @Parameter(description = "配置项主键 ID") @PathVariable("id") UUID id,
            @Valid @RequestBody SystemConfig config,
            @RequestAttribute("userId") UUID userId) {
        return ok(sysConfigService.update(id, config, userId));
    }

    /**
     * 删除配置
     * <p>
     * 删除指定的系统配置项。删除时会记录操作人信息。
     * </p>
     *
     * <p>HTTP 方法：DELETE</p>
     * <p>请求路径：{@code /configs/{id}}</p>
     *
     * @param id     配置项主键 ID（UUID 格式）
     * @param userId 当前操作用户 ID（由拦截器注入）
     * @return 无返回数据
     */
    @Operation(summary = "删除配置", description = "删除指定配置项。")
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(
            @Parameter(description = "配置项主键 ID") @PathVariable("id") UUID id,
            @RequestAttribute("userId") UUID userId) {
        sysConfigService.delete(id, userId);
        return ok();
    }
}
