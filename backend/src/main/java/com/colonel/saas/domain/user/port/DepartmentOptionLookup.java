package com.colonel.saas.domain.user.port;

import java.util.List;
import java.util.UUID;

/**
 * 部门选项查询端口。
 */
public interface DepartmentOptionLookup {

    List<DepartmentEntry> listActive();

    record DepartmentEntry(UUID id, String deptCode, String deptName, String deptType) {
    }
}
