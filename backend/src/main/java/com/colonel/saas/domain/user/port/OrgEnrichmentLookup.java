package com.colonel.saas.domain.user.port;

import com.colonel.saas.domain.user.port.OrgNodeLookup.OrgNode;

import java.util.List;
import java.util.UUID;

/**
 * 组织展示填充所需读模型查询端口。
 */
public interface OrgEnrichmentLookup {

    List<OrgNode> findActiveOrgNodes();

    List<RoleSummary> findRoles();

    record RoleSummary(UUID id, String code, String name) {
    }
}
