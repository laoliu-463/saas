package com.colonel.saas.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.common.base.BaseController;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.common.result.PageResult;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.entity.SystemConfig;
import com.colonel.saas.service.SysConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "系统配置", description = "系统配置管理接口，提供配置项的增删改查和分组查询。")
@RestController
@RequestMapping("/configs")
@RequireRoles({RoleCodes.ADMIN})
public class SysConfigController extends BaseController {

    private final SysConfigService sysConfigService;

    public SysConfigController(SysConfigService sysConfigService) {
        this.sysConfigService = sysConfigService;
    }

    @Operation(summary = "分页查询配置", description = "按分组和关键字筛选配置项。")
    @GetMapping
    public ApiResult<PageResult<SystemConfig>> page(
            @Parameter(description = "配置分组") @RequestParam(required = false) String configGroup,
            @Parameter(description = "搜索关键字，匹配 configKey 或 configName") @RequestParam(required = false) String keyword,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "20") int size) {
        IPage<SystemConfig> result = sysConfigService.findPage(configGroup, keyword, page, size);
        return okPage(result);
    }

    @Operation(summary = "分组查询配置", description = "返回按 configGroup 分组的启用配置项，供业务模块直接使用。")
    @GetMapping("/grouped")
    @RequireRoles({RoleCodes.ADMIN, RoleCodes.BIZ_LEADER, RoleCodes.BIZ_STAFF, RoleCodes.CHANNEL_LEADER, RoleCodes.CHANNEL_STAFF, RoleCodes.OPS_STAFF})
    public ApiResult<Map<String, List<SystemConfig>>> grouped(
            @RequestAttribute(value = "roleCodes", required = false) Object roleCodes) {
        return ok(sysConfigService.findGrouped(hasAdminRole(roleCodes)));
    }

    private boolean hasAdminRole(Object roleCodes) {
        if (roleCodes instanceof List<?> roles) {
            return roles.stream().anyMatch(role -> RoleCodes.ADMIN.equals(String.valueOf(role)));
        }
        return roleCodes != null && String.valueOf(roleCodes).contains(RoleCodes.ADMIN);
    }

    @Operation(summary = "配置详情", description = "查询单个配置项详情。")
    @GetMapping("/{id}")
    public ApiResult<SystemConfig> detail(
            @Parameter(description = "配置项主键 ID") @PathVariable UUID id) {
        return ok(sysConfigService.getById(id));
    }

    @Operation(summary = "新建配置", description = "创建新的配置项。")
    @PostMapping
    public ApiResult<SystemConfig> create(
            @RequestBody SystemConfig config,
            @RequestAttribute("userId") UUID userId) {
        return ok(sysConfigService.create(config, userId));
    }

    @Operation(summary = "更新配置", description = "更新配置项信息。")
    @PutMapping("/{id}")
    public ApiResult<SystemConfig> update(
            @Parameter(description = "配置项主键 ID") @PathVariable UUID id,
            @RequestBody SystemConfig config,
            @RequestAttribute("userId") UUID userId) {
        return ok(sysConfigService.update(id, config, userId));
    }

    @Operation(summary = "删除配置", description = "删除指定配置项。")
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(
            @Parameter(description = "配置项主键 ID") @PathVariable UUID id,
            @RequestAttribute("userId") UUID userId) {
        sysConfigService.delete(id, userId);
        return ok();
    }
}
