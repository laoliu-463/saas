package com.colonel.saas.domain.user.port;

import java.util.Optional;
import java.util.UUID;

/**
 * 用户部门归属查询端口。
 */
@FunctionalInterface
public interface UserDepartmentLookup {

    Optional<UUID> findDepartmentId(UUID userId);
}
