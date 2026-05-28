package com.colonel.saas.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.UUID;

/**
 * 部门成员分页查询请求 DTO。
 *
 * <p>用于部门管理页面查询指定部门下的成员列表，支持按关键词、状态、
 * 业务组、角色等条件进行筛选和分页。
 *
 * <p>所属业务领域：用户域 / 部门管理
 *
 * @see com.colonel.saas.auth.service.SysDeptService#findMembers
 * @see com.colonel.saas.auth.dto.SysUserPageRequest
 */
@Schema(description = "部门成员分页查询")
public record DeptMemberPageRequest(
        /** 页码，从 1 开始；为空时默认为 1 */
        @Min(1) Integer page,
        /** 每页大小，范围 1-100；为空时默认为 20 */
        @Min(1) @Max(100) Integer size,
        /** 搜索关键词，模糊匹配用户名或真实姓名 */
        String keyword,
        /** 用户状态筛选：0=已禁用，1=正常，2=待激活 */
        @Min(0) @Max(2) Integer status,
        /** 业务组 ID，筛选指定组下的成员 */
        UUID groupId,
        /** 角色 ID，筛选拥有指定角色的成员 */
        UUID roleId,
        /** 角色编码，筛选拥有指定角色编码的成员（与 roleId 二选一） */
        String roleCode
) {
    /**
     * 获取页码，默认值为 1。
     *
     * @return 页码，保证不为 null
     */
    public long pageNo() {
        return page == null ? 1L : page;
    }

    /**
     * 获取每页大小，默认值为 20。
     *
     * @return 每页大小，保证不为 null
     */
    public long pageSize() {
        return size == null ? 20L : size;
    }
}
