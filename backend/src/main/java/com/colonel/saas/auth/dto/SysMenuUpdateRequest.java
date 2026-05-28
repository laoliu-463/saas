package com.colonel.saas.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 更新菜单请求 DTO。
 *
 * <p>管理员修改已有菜单/按钮/API 资源信息时提交的请求体。
 * 结构与 {@link SysMenuCreateRequest} 一致，仅更新传入的非空字段。
 *
 * <p>所属业务领域：用户域 / 权限管理
 *
 * @see com.colonel.saas.auth.service.SysMenuService#update
 * @see com.colonel.saas.auth.dto.SysMenuCreateRequest
 */
@Schema(description = "更新菜单请求")
public record SysMenuUpdateRequest(
        /** 菜单名称，用于侧边栏和面包屑导航展示。校验：@NotBlank，最长 100 字符 */
        @Schema(description = "菜单名称", example = "用户管理")
        @NotBlank(message = "菜单名称不能为空")
        @Size(max = 100, message = "菜单名称长度不能超过100字符")
        String menuName,

        /**
         * 菜单类型：MENU（页面菜单）、BUTTON（按钮权限）、API（接口权限）。
         * <p>校验：@NotBlank，最长 10 字符。
         */
        @Schema(description = "菜单类型：MENU / BUTTON / API", example = "MENU")
        @NotBlank(message = "菜单类型不能为空")
        @Size(max = 10, message = "菜单类型长度不能超过10字符")
        String menuType,

        /** 父菜单 ID（UUID），修改此值会移动菜单节点位置 */
        @Schema(description = "父菜单ID", example = "00000000-0000-0000-0000-000000000000")
        String parentId,

        /** 前端路由路径，仅 MENU 类型有效。校验：最长 200 字符 */
        @Schema(description = "路由路径", example = "/system/user")
        @Size(max = 200, message = "路由路径长度不能超过200字符")
        String path,

        /** 前端组件路径，仅 MENU 类型有效。校验：最长 200 字符 */
        @Schema(description = "前端组件路径", example = "system/UserManagement")
        @Size(max = 200, message = "组件路径长度不能超过200字符")
        String component,

        /** 菜单图标名称，仅 MENU 类型有效。校验：最长 100 字符 */
        @Schema(description = "图标", example = "user")
        @Size(max = 100, message = "图标长度不能超过100字符")
        String icon,

        /** 排序值，数值越小越靠前展示，同一层级内生效 */
        @Schema(description = "排序值", example = "1")
        Integer sortOrder,

        /**
         * 权限标识符，格式建议：模块:实体:操作（如 system:user:list）。
         * <p>校验：最长 100 字符。
         */
        @Schema(description = "权限标识", example = "system:user:list")
        @Size(max = 100, message = "权限标识长度不能超过100字符")
        String permissionCode,

        /** 是否在侧边栏可见。1=可见，0=隐藏。校验：值域 [0, 1] */
        @Schema(description = "是否可见：1=是, 0=否", example = "1")
        @Min(value = 0, message = "可见性值非法")
        @Max(value = 1, message = "可见性值非法")
        Integer visible,

        /** 菜单状态。1=启用，0=禁用。禁用后该节点及子节点均不生效。校验：值域 [0, 1] */
        @Schema(description = "状态：1=启用, 0=禁用", example = "1")
        @Min(value = 0, message = "状态值非法")
        @Max(value = 1, message = "状态值非法")
        Integer status
) {
}
