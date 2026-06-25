package com.colonel.saas.domain.user.infrastructure;

import com.colonel.saas.domain.user.port.OrgDepartmentRepository;
import com.colonel.saas.domain.user.port.OrgDepartmentRepository.DepartmentRecord;
import com.colonel.saas.entity.SysDept;
import com.colonel.saas.mapper.SysDeptMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 通过现有 SysDeptMapper 访问组织单元的过渡适配器。
 */
@Component
public class SysOrgDepartmentRepositoryAdapter implements OrgDepartmentRepository {

    private final SysDeptMapper sysDeptMapper;

    public SysOrgDepartmentRepositoryAdapter(SysDeptMapper sysDeptMapper) {
        this.sysDeptMapper = sysDeptMapper;
    }

    @Override
    public List<DepartmentRecord> listActive() {
        return sysDeptMapper.findAllActive().stream().map(this::toRecord).toList();
    }

    @Override
    public List<DepartmentRecord> listByDeptType(String deptType) {
        return sysDeptMapper.findByDeptType(deptType).stream().map(this::toRecord).toList();
    }

    @Override
    public List<DepartmentRecord> listNonDeleted() {
        return sysDeptMapper.findAllNonDeleted().stream().map(this::toRecord).toList();
    }

    @Override
    public Optional<DepartmentRecord> findById(UUID id) {
        return Optional.ofNullable(sysDeptMapper.selectById(id)).map(this::toRecord);
    }

    @Override
    public Optional<DepartmentRecord> findByDeptCode(String deptCode) {
        return sysDeptMapper.findByDeptCode(deptCode).map(this::toRecord);
    }

    @Override
    public void insert(DepartmentRecord department) {
        sysDeptMapper.insert(toEntity(department));
    }

    @Override
    public void update(DepartmentRecord department) {
        sysDeptMapper.updateById(toEntity(department));
    }

    @Override
    public long countUsersByDeptId(UUID deptId) {
        return sysDeptMapper.countUsersByDeptId(deptId);
    }

    @Override
    public long countChildrenByParentId(UUID parentId) {
        return sysDeptMapper.countChildrenByParentId(parentId);
    }

    @Override
    public int softDeleteById(UUID id) {
        return sysDeptMapper.softDeleteById(id);
    }

    private DepartmentRecord toRecord(SysDept dept) {
        return new DepartmentRecord(
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
                dept.getRemark(),
                dept.getDeleted());
    }

    private SysDept toEntity(DepartmentRecord record) {
        SysDept dept = new SysDept();
        dept.setId(record.id());
        dept.setParentId(record.parentId());
        dept.setDeptCode(record.deptCode());
        dept.setDeptName(record.deptName());
        dept.setDeptType(record.deptType());
        dept.setLeaderUserId(record.leaderUserId());
        dept.setLeader(record.leader());
        dept.setPhone(record.phone());
        dept.setEmail(record.email());
        dept.setSortOrder(record.sortOrder());
        dept.setStatus(record.status());
        dept.setRemark(record.remark());
        dept.setDeleted(record.deleted());
        return dept;
    }
}
