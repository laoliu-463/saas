package com.colonel.saas.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 当前登录用户与权限包响应 DTO。
 * <p>
 * 返回当前登录用户的完整身份信息和权限数据，包括用户 ID、姓名、部门、数据范围、
 * 角色列表、聚合权限包、账号状态以及是否需要强制修改密码等。
 * 关联业务领域：用户域（User）。
 * </p>
 */
@Schema(description = "当前登录用户与权限包")
public record CurrentUserResponse(
        @Schema(description = "用户ID")
        UUID userId,

        @Schema(description = "用户名")
        String username,

        @Schema(description = "真实姓名")
        String realName,

        @Schema(description = "部门ID")
        UUID deptId,

        @Schema(description = "数据范围编码：1=自己，2=本组，3=全部")
        int dataScope,

        @Schema(description = "数据范围名称：self/group/all")
        String dataScopeName,

        @Schema(description = "角色编码列表")
        List<String> roleCodes,

        @Schema(description = "聚合后的权限包")
        Map<String, Object> permissions,

        @Schema(description = "账号状态：2=待激活，1=正常，0=已禁用")
        int status,

        @Schema(description = "是否必须修改密码")
        boolean forcePasswordChange
) {
}
