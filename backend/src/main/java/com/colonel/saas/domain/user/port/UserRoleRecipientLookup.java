package com.colonel.saas.domain.user.port;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * 按角色查找可接收业务通知的启用用户。
 */
public interface UserRoleRecipientLookup {

    List<UUID> findActiveUserIdsByRoleCodes(Collection<String> roleCodes);
}
