package com.colonel.saas.domain.user.facade.dto;

import java.util.UUID;

/**
 * 用户归属引用：跨业务域分配负责人时只暴露用户 ID 和主组织单元。
 */
public record UserOwnershipReference(UUID userId, UUID deptId) {
}
