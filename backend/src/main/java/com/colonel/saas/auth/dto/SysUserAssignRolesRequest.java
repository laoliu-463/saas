package com.colonel.saas.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * 用户分配角色请求 DTO。
 *
 * <p>管理员为指定用户批量分配角色时提交的请求体。
 * 该操作为全量替换：传入的角色 ID 列表将完全覆盖用户当前的角色关联。
 *
 * <p>所属业务领域：用户域 / 权限管理
 *
 * @see com.colonel.saas.auth.service.SysUserService#assignRoles
 */
@Schema(description = "用户分配角色请求")
public record SysUserAssignRolesRequest(

        /**
         * 角色 ID 列表（UUID），全量替换用户当前角色。
         * <p>校验：@NotNull，不可为空。传入空列表将清除用户所有角色。
         */
        @Schema(description = "角色 ID 列表")
        @NotNull(message = "角色列表不能为空")
        List<UUID> roleIds
) {
}
