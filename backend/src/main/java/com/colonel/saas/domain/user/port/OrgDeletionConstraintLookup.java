package com.colonel.saas.domain.user.port;

import java.util.UUID;

/**
 * 组织删除依赖查询端口。
 */
public interface OrgDeletionConstraintLookup {

    long countDirectUsers(UUID deptId);

    long countChildGroups(UUID deptId);
}
