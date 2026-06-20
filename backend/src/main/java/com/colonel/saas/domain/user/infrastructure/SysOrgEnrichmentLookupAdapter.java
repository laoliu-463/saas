package com.colonel.saas.domain.user.infrastructure;

import com.colonel.saas.domain.user.port.OrgEnrichmentLookup;
import com.colonel.saas.domain.user.port.OrgNodeLookup.OrgNode;
import com.colonel.saas.entity.SysDept;
import com.colonel.saas.entity.SysRole;
import com.colonel.saas.mapper.SysDeptMapper;
import com.colonel.saas.mapper.SysRoleMapper;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 通过现有部门/角色 Mapper 查询组织展示读模型的过渡适配器。
 */
@Component
public class SysOrgEnrichmentLookupAdapter implements OrgEnrichmentLookup {

    private final SysDeptMapper sysDeptMapper;
    private final SysRoleMapper sysRoleMapper;

    public SysOrgEnrichmentLookupAdapter(SysDeptMapper sysDeptMapper, SysRoleMapper sysRoleMapper) {
        this.sysDeptMapper = sysDeptMapper;
        this.sysRoleMapper = sysRoleMapper;
    }

    @Override
    public List<OrgNode> findActiveOrgNodes() {
        List<SysDept> depts = sysDeptMapper.findAllActive();
        if (depts == null) {
            return Collections.emptyList();
        }
        return depts.stream()
                .filter(Objects::nonNull)
                .map(dept -> new OrgNode(
                        dept.getId(),
                        dept.getParentId(),
                        dept.getDeptName(),
                        dept.getDeptType(),
                        Objects.equals(dept.getDeleted(), 1)))
                .toList();
    }

    @Override
    public List<RoleSummary> findRoles() {
        List<SysRole> roles = sysRoleMapper.selectList(null);
        if (roles == null) {
            return Collections.emptyList();
        }
        return roles.stream()
                .filter(Objects::nonNull)
                .map(role -> new RoleSummary(role.getId(), role.getRoleCode(), role.getRoleName()))
                .toList();
    }
}
