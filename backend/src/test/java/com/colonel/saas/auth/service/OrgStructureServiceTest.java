package com.colonel.saas.auth.service;

import com.colonel.saas.constant.DeptType;
import com.colonel.saas.entity.SysDept;
import com.colonel.saas.mapper.SysDeptMapper;
import com.colonel.saas.mapper.SysRoleMapper;
import com.colonel.saas.mapper.SysUserMapper;
import com.colonel.saas.mapper.SysUserRoleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrgStructureServiceTest {

    @Mock
    SysDeptMapper sysDeptMapper;
    @Mock
    SysUserMapper sysUserMapper;
    @Mock
    SysRoleMapper sysRoleMapper;
    @Mock
    SysUserRoleMapper sysUserRoleMapper;

    private OrgStructureService service;
    private UUID parentId;
    private UUID groupId;

    @BeforeEach
    void setUp() {
        service = new OrgStructureService(sysDeptMapper, sysUserMapper, sysRoleMapper, sysUserRoleMapper);
        parentId = UUID.randomUUID();
        groupId = UUID.randomUUID();
    }

    @Test
    void resolveAssignment_prefersGroupOverDepartment() {
        SysDept parent = dept(parentId, null, DeptType.DEPARTMENT, "招商一部");
        SysDept group = dept(groupId, parentId, DeptType.RECRUITER_GROUP, "招商一组");
        when(sysDeptMapper.selectById(groupId)).thenReturn(group);

        OrgStructureService.ResolvedAssignment assignment = service.resolveAssignment(parentId, groupId);

        assertThat(assignment.effectiveDeptId()).isEqualTo(groupId);
        assertThat(assignment.groupId()).isEqualTo(groupId);
        assertThat(assignment.parentDeptId()).isEqualTo(parentId);
    }

    @Test
    void splitAssignment_mapsGroupMembership() {
        SysDept parent = dept(parentId, null, DeptType.DEPARTMENT, "招商一部");
        SysDept group = dept(groupId, parentId, DeptType.RECRUITER_GROUP, "招商一组");
        when(sysDeptMapper.selectById(groupId)).thenReturn(group);
        when(sysDeptMapper.selectById(parentId)).thenReturn(parent);

        OrgStructureService.SplitAssignment split = service.splitAssignment(groupId);

        assertThat(split.groupId()).isEqualTo(groupId);
        assertThat(split.parentDeptId()).isEqualTo(parentId);
        assertThat(split.groupType()).isEqualTo(DeptType.RECRUITER_GROUP);
    }

    private static SysDept dept(UUID id, UUID parentId, String type, String name) {
        SysDept dept = new SysDept();
        dept.setId(id);
        dept.setParentId(parentId);
        dept.setDeptType(type);
        dept.setDeptName(name);
        dept.setDeleted(0);
        return dept;
    }
}
