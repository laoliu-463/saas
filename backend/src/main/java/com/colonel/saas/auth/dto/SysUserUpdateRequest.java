package com.colonel.saas.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * 更新用户请求 DTO。
 *
 * <p>管理员修改已有用户信息时提交的请求体。
 * 支持修改基本信息（姓名、手机、邮箱）、账号状态和组织归属。
 *
 * <p>组织归属变更规则与 {@link SysUserCreateRequest} 一致：
 * parentDeptId + groupId 优先，deptId 为兼容字段。
 *
 * <p>所属业务领域：用户域 / 用户管理
 *
 * @see com.colonel.saas.auth.service.SysUserService#update
 * @see com.colonel.saas.auth.dto.SysUserCreateRequest
 */
@Schema(description = "更新用户请求")
public record SysUserUpdateRequest(
        /** 真实姓名。校验：最长 100 字符，为空时不更新 */
        @Schema(description = "真实姓名", example = "张三")
        @Size(max = 100, message = "真实姓名长度不能超过100字符")
        String realName,

        /** 联系电话。校验：最长 20 字符，为空时不更新 */
        @Schema(description = "手机号", example = "13800138000")
        @Size(max = 20, message = "手机号长度不能超过20字符")
        String phone,

        /** 电子邮箱。校验：最长 100 字符，为空时不更新 */
        @Schema(description = "邮箱", example = "demo@example.com")
        @Size(max = 100, message = "邮箱长度不能超过100字符")
        String email,

        /**
         * 账号状态。0=已禁用，1=正常，2=待激活。
         * <p>将状态从正常改为禁用时，会触发用户禁用事件。
         * <p>校验：值域 [0, 2]。
         */
        @Schema(description = "状态：2=待激活，1=正常，0=已禁用", example = "1")
        @Min(value = 0, message = "状态值非法")
        @Max(value = 2, message = "状态值非法")
        Integer status,

        /**
         * 所属部门 ID（UUID），对应 deptType=department 的 sys_dept 记录。
         * <p>修改此值会触发组织变更事件。
         */
        @Schema(description = "所属部门 ID")
        UUID parentDeptId,

        /** 所属业务组 ID（UUID），修改后用户数据权限范围将随之变化 */
        @Schema(description = "所属业务组 ID")
        UUID groupId,

        /**
         * 兼容字段：直接指定 sys_user.dept_id。
         * <p>若同时传入 parentDeptId 和 groupId，则忽略此字段。
         */
        @Schema(description = "兼容字段：直接指定 sys_user.dept_id")
        UUID deptId
) {
}
