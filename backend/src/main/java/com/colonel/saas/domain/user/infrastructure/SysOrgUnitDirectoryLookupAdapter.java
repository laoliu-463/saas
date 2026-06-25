package com.colonel.saas.domain.user.infrastructure;

import com.colonel.saas.domain.user.port.OrgUnitDirectoryLookup;
import com.colonel.saas.entity.SysDept;
import com.colonel.saas.mapper.SysDeptMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 通过现有 SysDeptMapper 查询组织单元目录的过渡适配器。
 */
@Component
public class SysOrgUnitDirectoryLookupAdapter implements OrgUnitDirectoryLookup {

    private final SysDeptMapper sysDeptMapper;

    public SysOrgUnitDirectoryLookupAdapter(SysDeptMapper sysDeptMapper) {
        this.sysDeptMapper = sysDeptMapper;
    }

    @Override
    public List<OrgUnitEntry> listActive() {
        return sysDeptMapper.findAllActive().stream()
                .map(this::toEntry)
                .toList();
    }

    @Override
    public Optional<OrgUnitEntry> findActiveById(UUID id) {
        SysDept dept = sysDeptMapper.selectById(id);
        if (dept == null || Objects.equals(dept.getDeleted(), 1)) {
            return Optional.empty();
        }
        return Optional.of(toEntry(dept));
    }

    @Override
    public List<OrgUnitEntry> findChildren(UUID parentId) {
        return sysDeptMapper.findByParentId(parentId).stream()
                .map(this::toEntry)
                .toList();
    }

    @Override
    public long countMembersUnderOrgUnit(UUID orgUnitId) {
        return sysDeptMapper.countMembersUnderDept(orgUnitId);
    }

    @Override
    public long countChildGroupsByType(UUID parentId, String deptType) {
        return sysDeptMapper.countChildGroupsByType(parentId, deptType);
    }

    private OrgUnitEntry toEntry(SysDept dept) {
        return new OrgUnitEntry(
                dept.getId(),
                dept.getParentId(),
                dept.getDeptCode(),
                dept.getDeptName(),
                dept.getDeptType(),
                dept.getLeaderUserId(),
                dept.getLeader(),
                dept.getPhone(),
                dept.getEmail(),
                dept.getSortOrder(),
                dept.getStatus(),
                dept.getRemark());
    }
}
