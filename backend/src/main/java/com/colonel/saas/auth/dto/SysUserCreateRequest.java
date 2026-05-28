package com.colonel.saas.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * 创建用户请求 DTO。
 *
 * <p>管理员新建系统用户时提交的请求体。创建后用户默认处于正常或待激活状态，
 * 系统会自动为用户生成唯一的渠道编码（channelCode）。
 *
 * <p>组织归属规则：
 * <ul>
 *   <li>若同时传入 parentDeptId 和 groupId，优先使用此组合确定归属</li>
 *   <li>若仅传入 deptId（兼容字段），直接设置 sys_user.dept_id</li>
 *   <li>若均未传入，用户无组织归属</li>
 * </ul>
 *
 * <p>所属业务领域：用户域 / 用户管理
 *
 * @see com.colonel.saas.auth.service.SysUserService#create
 * @see com.colonel.saas.auth.dto.SysUserUpdateRequest
 */
@Schema(description = "创建用户请求")
public record SysUserCreateRequest(
        /**
         * 登录用户名，系统内唯一。
         * <p>校验：@NotBlank，最长 50 字符。不可与已有用户名重复。
         */
        @Schema(description = "用户名", example = "zhangsan")
        @NotBlank(message = "用户名不能为空")
        @Size(max = 50, message = "用户名长度不能超过50字符")
        String username,

        /**
         * 登录密码，后端使用 BCrypt 加密存储。
         * <p>校验：@NotBlank，长度 6-128 字符。建议前端在传输前进行加密或使用 HTTPS。
         */
        @Schema(description = "登录密码", example = "Passw0rd!")
        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 128, message = "密码长度必须在6到128字符之间")
        String password,

        /** 真实姓名，用于前端展示和搜索。校验：@NotBlank，最长 100 字符 */
        @Schema(description = "真实姓名", example = "张三")
        @NotBlank(message = "真实姓名不能为空")
        @Size(max = 100, message = "真实姓名长度不能超过100字符")
        String realName,

        /** 联系电话，可选。校验：最长 20 字符 */
        @Schema(description = "手机号", example = "13800138000")
        @Size(max = 20, message = "手机号长度不能超过20字符")
        String phone,

        /** 电子邮箱，可选。校验：最长 100 字符 */
        @Schema(description = "邮箱", example = "demo@example.com")
        @Size(max = 100, message = "邮箱长度不能超过100字符")
        String email,

        /**
         * 所属部门 ID（UUID），对应 deptType=department 的 sys_dept 记录。
         * <p>与 groupId 配合使用，确定用户在组织架构中的完整归属路径。
         */
        @Schema(description = "所属部门 ID（department）")
        UUID parentDeptId,

        /**
         * 所属业务组 ID（UUID），对应 deptType 为 recruiter_group/channel_group/ops_group 的 sys_dept 记录。
         * <p>决定用户的数据权限范围和业务角色分组。
         */
        @Schema(description = "所属业务组 ID（recruiter_group/channel_group/ops_group）")
        UUID groupId,

        /**
         * 兼容字段：直接指定 sys_user.dept_id。
         * <p>若同时传入 parentDeptId 和 groupId，则忽略此字段。
         * 适用于旧接口兼容或简单场景。
         */
        @Schema(description = "兼容字段：直接指定 sys_user.dept_id，若同时传 parentDeptId/groupId 则忽略")
        UUID deptId,

        /**
         * 角色 ID 列表（UUID），创建时即绑定角色。
         * <p>校验：@NotNull，不可为空。至少需要分配一个角色。
         */
        @Schema(description = "角色 ID 列表")
        @NotNull(message = "角色列表不能为空")
        List<UUID> roleIds
) {
}
