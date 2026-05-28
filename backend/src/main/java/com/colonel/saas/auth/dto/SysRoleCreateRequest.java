package com.colonel.saas.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建角色请求 DTO。
 *
 * <p>管理员新建系统角色时提交的请求体。角色用于控制用户的接口权限和数据访问范围。
 * 系统内置角色（如 admin）不允许通过此接口创建，仅支持自定义业务角色。
 *
 * <p>所属业务领域：用户域 / 权限管理
 *
 * @see com.colonel.saas.auth.service.SysRoleService#create
 * @see com.colonel.saas.auth.dto.SysRoleUpdateRequest
 */
@Schema(description = "创建角色请求")
public record SysRoleCreateRequest(
        /**
         * 角色编码，系统内唯一标识，用于接口鉴权判断。
         * <p>格式建议：小写下划线，如 biz_staff、channel_leader。
         * <p>校验：@NotBlank，最长 50 字符，不可与已有编码重复。
         */
        @Schema(description = "角色编码", example = "biz_staff")
        @NotBlank(message = "角色编码不能为空")
        @Size(max = 50, message = "角色编码长度不能超过50字符")
        String roleCode,

        /**
         * 角色名称，用于前端展示。
         * <p>校验：@NotBlank，最长 100 字符。
         */
        @Schema(description = "角色名称", example = "招商专员")
        @NotBlank(message = "角色名称不能为空")
        @Size(max = 100, message = "角色名称长度不能超过100字符")
        String roleName,

        /**
         * 数据权限范围，决定拥有此角色的用户可查看的数据范围。
         * <ul>
         *   <li>{@code 1} = 本人数据</li>
         *   <li>{@code 2} = 本组数据</li>
         *   <li>{@code 3} = 全部数据</li>
         * </ul>
         * <p>校验：值域 [1, 3]。
         */
        @Schema(description = "数据范围：1=本人，2=本组，3=全部", example = "1")
        @Min(value = 1, message = "数据范围值非法")
        @Max(value = 3, message = "数据范围值非法")
        Integer dataScope,

        /** 角色状态。1=启用，0=禁用。禁用后拥有此角色的用户将失去对应权限。校验：值域 [0, 1] */
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
