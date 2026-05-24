package com.colonel.saas.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

@Schema(description = "创建用户请求")
public record SysUserCreateRequest(
        @Schema(description = "用户名", example = "zhangsan")
        @NotBlank(message = "用户名不能为空")
        @Size(max = 50, message = "用户名长度不能超过50字符")
        String username,

        @Schema(description = "登录密码", example = "Passw0rd!")
        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 128, message = "密码长度必须在6到128字符之间")
        String password,

        @Schema(description = "真实姓名", example = "张三")
        @NotBlank(message = "真实姓名不能为空")
        @Size(max = 100, message = "真实姓名长度不能超过100字符")
        String realName,

        @Schema(description = "手机号", example = "13800138000")
        @Size(max = 20, message = "手机号长度不能超过20字符")
        String phone,

        @Schema(description = "邮箱", example = "demo@example.com")
        @Size(max = 100, message = "邮箱长度不能超过100字符")
        String email,

        @Schema(description = "所属部门 ID（department）")
        UUID parentDeptId,

        @Schema(description = "所属业务组 ID（recruiter_group/channel_group/ops_group）")
        UUID groupId,

        @Schema(description = "兼容字段：直接指定 sys_user.dept_id，若同时传 parentDeptId/groupId 则忽略")
        UUID deptId,

        @Schema(description = "角色 ID 列表")
        @NotNull(message = "角色列表不能为空")
        List<UUID> roleIds
) {
}
