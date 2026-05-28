package com.colonel.saas.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 更新角色请求 DTO。
 *
 * <p>管理员修改已有角色信息时提交的请求体。
 * 结构与 {@link SysRoleCreateRequest} 一致，仅更新传入的非空字段。
 * 系统内置角色编码（admin 等）的编码字段不允许修改。
 *
 * <p>所属业务领域：用户域 / 权限管理
 *
 * @see com.colonel.saas.auth.service.SysRoleService#update
 * @see com.colonel.saas.auth.dto.SysRoleCreateRequest
 */
@Schema(description = "更新角色请求")
public record SysRoleUpdateRequest(
        /**
         * 角色编码，系统内唯一标识。
         * <p>校验：@NotBlank，最长 50 字符。系统内置角色编码不可修改。
         */
        @Schema(description = "角色编码", example = "biz_staff")
        @NotBlank(message = "角色编码不能为空")
        @Size(max = 50, message = "角色编码长度不能超过50字符")
        String roleCode,

        /** 角色名称，用于前端展示。校验：@NotBlank，最长 100 字符 */
        @Schema(description = "角色名称", example = "招商专员")
        @NotBlank(message = "角色名称不能为空")
        @Size(max = 100, message = "角色名称长度不能超过100字符")
        String roleName,

        /**
         * 数据权限范围。1=本人，2=本组，3=全部。
         * <p>修改此值会影响拥有该角色的所有用户的数据可见范围。
         * <p>校验：值域 [1, 3]。
         */
        @Schema(description = "数据范围：1=本人，2=本组，3=全部", example = "1")
        @Min(value = 1, message = "数据范围值非法")
        @Max(value = 3, message = "数据范围值非法")
        Integer dataScope,

        /** 角色状态。1=启用，0=禁用。校验：值域 [0, 1] */
        @Schema(description = "状态：1=启用，0=禁用", example = "1")
        @Min(value = 0, message = "状态值非法")
        @Max(value = 1, message = "状态值非法")
        Integer status,

        /** 备注说明，最长 255 字符 */
        @Schema(description = "备注")
        @Size(max = 255, message = "备注长度不能超过255字符")
        String remark
) {
}
