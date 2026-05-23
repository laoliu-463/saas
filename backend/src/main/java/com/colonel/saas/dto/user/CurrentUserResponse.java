package com.colonel.saas.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;
import java.util.UUID;

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
        Map<String, Object> permissions
) {
}
