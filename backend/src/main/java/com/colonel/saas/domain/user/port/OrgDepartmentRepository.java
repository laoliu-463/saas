package com.colonel.saas.domain.user.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 组织单元持久化端口。
 */
public interface OrgDepartmentRepository {

    List<DepartmentRecord> listActive();

    List<DepartmentRecord> listByDeptType(String deptType);

    List<DepartmentRecord> listNonDeleted();

    Optional<DepartmentRecord> findById(UUID id);

    Optional<DepartmentRecord> findByDeptCode(String deptCode);

    void insert(DepartmentRecord department);

    void update(DepartmentRecord department);

    long countUsersByDeptId(UUID deptId);

    long countChildrenByParentId(UUID parentId);

    int softDeleteById(UUID id);

    record DepartmentRecord(
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
            String remark,
            Integer deleted) {

        public boolean isDeleted() {
            return deleted != null && deleted != 0;
        }
    }
}
