package com.colonel.saas.domain.user.infrastructure;

import com.colonel.saas.domain.user.port.DepartmentOptionLookup;
import com.colonel.saas.entity.SysDept;
import com.colonel.saas.mapper.SysDeptMapper;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 通过现有 SysDeptMapper 查询部门选项的过渡适配器。
 */
@Component
public class SysDepartmentOptionLookupAdapter implements DepartmentOptionLookup {

    private final SysDeptMapper sysDeptMapper;

    public SysDepartmentOptionLookupAdapter(SysDeptMapper sysDeptMapper) {
        this.sysDeptMapper = sysDeptMapper;
    }

    @Override
    public List<DepartmentEntry> listActive() {
        List<SysDept> departments = sysDeptMapper.findAllActive();
        if (departments == null) {
            return List.of();
        }
        return departments.stream()
                .map(this::toDepartmentEntry)
                .toList();
    }

    private DepartmentEntry toDepartmentEntry(SysDept dept) {
        return new DepartmentEntry(
                dept.getId(),
                dept.getDeptCode(),
                dept.getDeptName(),
                dept.getDeptType()
        );
    }
}
