package com.colonel.saas.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.UUID;

/**
 * 用户分页查询参数 DTO。
 *
 * <p>管理员查询用户列表时提交的分页与筛选参数。
 * 支持按用户名/姓名关键词模糊搜索，按状态、部门、角色等条件筛选。
 *
 * <p>所属业务领域：用户域 / 用户管理
 *
 * @see com.colonel.saas.auth.service.SysUserService#findPage
 */
@Schema(description = "用户分页查询参数")
public record SysUserPageRequest(
        /**
         * 页码，从 1 开始。
         * <p>校验：@Min(1)，为空时默认为 1（由 pageNo() 方法处理）。
         */
        @Schema(description = "页码，从 1 开始", example = "1")
        @Min(value = 1, message = "页码必须大于等于1")
        Integer page,

        /**
         * 每页记录数。
         * <p>校验：值域 [1, 100]，为空时默认为 10（由 pageSize() 方法处理）。
         */
        @Schema(description = "每页大小", example = "10")
        @Min(value = 1, message = "每页大小必须大于等于1")
        @Max(value = 100, message = "每页大小不能超过100")
        Integer size,

        /**
         * 搜索关键词，模糊匹配用户名（username）和真实姓名（real_name）。
         * <p>为空时不施加关键词过滤条件。
         */
        @Schema(description = "关键词，匹配用户名/姓名", example = "admin")
        String keyword,

        /**
         * 账号状态筛选。0=已禁用，1=正常，2=待激活。
         * <p>校验：值域 [0, 2]，为空时不筛选状态。
         */
        @Schema(description = "状态：2=待激活，1=正常，0=已禁用", example = "1")
        @Min(value = 0, message = "状态值非法")
        @Max(value = 2, message = "状态值非法")
        Integer status,

        /**
         * 部门 ID（UUID），筛选该部门及其下属业务组的所有成员。
         * <p>与 groupId 互斥：若同时传入，以 groupId 为准。
         */
        @Schema(description = "部门 ID（含其下业务组成员）")
        UUID deptId,

        /**
         * 业务组 ID（UUID），精确匹配 sys_user.dept_id。
         * <p>优先级高于 deptId。
         */
        @Schema(description = "业务组 ID（精确匹配 sys_user.dept_id）")
        UUID groupId,

        /** 角色 ID（UUID），筛选拥有该角色的用户 */
        @Schema(description = "角色 ID")
        UUID roleId,

        /** 角色编码，筛选拥有该编码角色的用户。与 roleId 二选一 */
        @Schema(description = "角色编码")
        String roleCode
) {
    /**
     * 获取安全的页码值，为空时默认返回 1。
     *
     * @return 页码，最小值为 1
     */
    public long pageNo() {
        return page == null ? 1L : page;
    }

    /**
     * 获取安全的每页大小，为空时默认返回 10。
     *
     * @return 每页记录数，范围 [1, 100]
     */
    public long pageSize() {
        return size == null ? 10L : size;
    }
}
