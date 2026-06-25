package com.colonel.saas.domain.user.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 组织单元目录查询端口。
 */
public interface OrgUnitDirectoryLookup {

    List<OrgUnitEntry> listActive();

    Optional<OrgUnitEntry> findActiveById(UUID id);

    List<OrgUnitEntry> findChildren(UUID parentId);

    long countMembersUnderOrgUnit(UUID orgUnitId);

    long countChildGroupsByType(UUID parentId, String deptType);

    record OrgUnitEntry(
            UUID id,
            UUID parentId,
            String deptCode,
            String deptName,
            String deptType,
            UUID leaderUserId,
            String leader,
            String phone,
            String email,
            Integer sortOrder,
            Integer status,
            String remark) {
    }
}
