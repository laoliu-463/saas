package com.colonel.saas.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "创建菜单请求")
public record SysMenuCreateRequest(
        @Schema(description = "菜单名称", example = "用户管理")
        @NotBlank(message = "菜单名称不能为空")
        @Size(max = 100, message = "菜单名称长度不能超过100字符")
        String menuName,

        @Schema(description = "菜单类型：MENU / BUTTON / API", example = "MENU")
        @NotBlank(message = "菜单类型不能为空")
        @Size(max = 10, message = "菜单类型长度不能超过10字符")
        String menuType,

        @Schema(description = "父菜单ID，顶级菜单传全零UUID", example = "00000000-0000-0000-0000-000000000000")
        String parentId,

        @Schema(description = "路由路径", example = "/system/user")
        @Size(max = 200, message = "路由路径长度不能超过200字符")
        String path,

        @Schema(description = "前端组件路径", example = "system/UserManagement")
        @Size(max = 200, message = "组件路径长度不能超过200字符")
        String component,

        @Schema(description = "图标", example = "user")
        @Size(max = 100, message = "图标长度不能超过100字符")
        String icon,

        @Schema(description = "排序值", example = "1")
        Integer sortOrder,

        @Schema(description = "权限标识", example = "system:user:list")
        @Size(max = 100, message = "权限标识长度不能超过100字符")
        String permissionCode,

        @Schema(description = "是否可见：1=是, 0=否", example = "1")
        @Min(value = 0, message = "可见性值非法")
        @Max(value = 1, message = "可见性值非法")
        Integer visible,

        @Schema(description = "状态：1=启用, 0=禁用", example = "1")
        @Min(value = 0, message = "状态值非法")
        @Max(value = 1, message = "状态值非法")
        Integer status
) {
}
