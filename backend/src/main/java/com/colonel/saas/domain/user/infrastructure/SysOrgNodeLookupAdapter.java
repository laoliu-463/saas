package com.colonel.saas.domain.user.infrastructure;

import com.colonel.saas.domain.user.port.OrgNodeLookup;
import com.colonel.saas.entity.SysDept;
import com.colonel.saas.mapper.SysDeptMapper;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 通过现有部门 Mapper 查询组织节点的过渡适配器。
 */
@Component
public class SysOrgNodeLookupAdapter implements OrgNodeLookup {

    private final SysDeptMapper sysDeptMapper;

    public SysOrgNodeLookupAdapter(SysDeptMapper sysDeptMapper) {
        this.sysDeptMapper = sysDeptMapper;
    }

    @Override
    public Optional<OrgNode> findById(UUID orgNodeId) {
        if (orgNodeId == null) {
            return Optional.empty();
        }
        SysDept dept = sysDeptMapper.selectById(orgNodeId);
        if (dept == null) {
            return Optional.empty();
        }
        return Optional.of(new OrgNode(
                dept.getId(),
                dept.getParentId(),
                dept.getDeptName(),
                dept.getDeptType(),
                Objects.equals(dept.getDeleted(), 1)));
    }
}
