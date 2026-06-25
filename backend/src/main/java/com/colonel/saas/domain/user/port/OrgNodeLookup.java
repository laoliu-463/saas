package com.colonel.saas.domain.user.port;

import java.util.Optional;
import java.util.UUID;

/**
 * 组织节点查询端口。
 */
@FunctionalInterface
public interface OrgNodeLookup {

    Optional<OrgNode> findById(UUID orgNodeId);

    record OrgNode(UUID id, UUID parentId, String name, String type, boolean deleted) {
    }
}
