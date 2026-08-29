package com.colonel.saas.domain.user.port;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 用户有效角色编码的批量只读端口。 */
public interface UserRoleCodeLookup {

    Map<UUID, Set<String>> findActiveRoleCodesByUserIds(Collection<UUID> userIds);
}
