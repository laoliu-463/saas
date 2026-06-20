package com.colonel.saas.domain.user.infrastructure;

import com.colonel.saas.domain.user.port.OrgDeletionConstraintLookup;
import com.colonel.saas.mapper.SysDeptMapper;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 通过现有部门 Mapper 查询组织删除依赖的过渡适配器。
 */
@Component
public class SysOrgDeletionConstraintLookupAdapter implements OrgDeletionConstraintLookup {

    private final SysDeptMapper sysDeptMapper;

    public SysOrgDeletionConstraintLookupAdapter(SysDeptMapper sysDeptMapper) {
        this.sysDeptMapper = sysDeptMapper;
    }

    @Override
    public long countDirectUsers(UUID deptId) {
        return sysDeptMapper.countUsersByDeptId(deptId);
    }

    @Override
    public long countChildGroups(UUID deptId) {
        return sysDeptMapper.countChildGroups(deptId);
    }
}
