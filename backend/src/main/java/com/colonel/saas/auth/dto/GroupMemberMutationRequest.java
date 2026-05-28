package com.colonel.saas.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

/**
 * 业务组成员变更请求 DTO。
 *
 * <p>用于批量将用户添加到指定业务组或从业务组中移除。
 * 支持的操作包括：添加成员到组、从组中移除成员。
 *
 * <p>所属业务领域：用户域 / 组织架构管理
 *
 * @see com.colonel.saas.auth.service.SysUserService#assignUsersToGroup
 * @see com.colonel.saas.auth.service.SysUserService#removeUsersFromGroup
 */
@Schema(description = "业务组成员变更")
public record GroupMemberMutationRequest(
        /** 需要变更的用户 ID 列表，不能为空 */
        @NotEmpty(message = "userIds cannot be empty")
        List<UUID> userIds
) {
}
