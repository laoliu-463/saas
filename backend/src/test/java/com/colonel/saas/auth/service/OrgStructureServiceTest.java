package com.colonel.saas.auth.service;

import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.constant.DeptType;
import com.colonel.saas.domain.user.application.OrgStructureApplicationService;
import com.colonel.saas.domain.user.infrastructure.SysOrgDeletionConstraintLookupAdapter;
import com.colonel.saas.domain.user.infrastructure.SysOrgEnrichmentLookupAdapter;
import com.colonel.saas.domain.user.infrastructure.SysOrgLeaderCandidateLookupAdapter;
import com.colonel.saas.domain.user.infrastructure.SysOrgNodeLookupAdapter;
import com.colonel.saas.domain.user.policy.CurrentUserPermissionPolicy;
import com.colonel.saas.domain.user.policy.OrgAssignmentPolicy;
import com.colonel.saas.domain.user.policy.OrgEnrichmentPolicy;
import com.colonel.saas.domain.user.policy.OrgValidationPolicy;
import com.colonel.saas.entity.SysDept;
import com.colonel.saas.entity.SysRole;
import com.colonel.saas.entity.SysUser;
import com.colonel.saas.entity.SysUserRole;
import com.colonel.saas.mapper.SysDeptMapper;
import com.colonel.saas.mapper.SysRoleMapper;
import com.colonel.saas.mapper.SysUserMapper;
import com.colonel.saas.mapper.SysUserRoleMapper;
import com.colonel.saas.vo.SysUserVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * OrgStructureService 单测（DDD-USER-MIGRATION-001）。
 *
 * <p>覆盖 8 个 public 方法，作为 DDD 迁移后的 Parity 基线。
 * 任何 DDD 重构后必须保证这些用例 100% 通过。</p>
 */
@ExtendWith(MockitoExtension.class)
class OrgStructureServiceTest {

    @Mock private SysDeptMapper sysDeptMapper;
    @Mock private SysUserMapper sysUserMapper;
    @Mock private SysRoleMapper sysRoleMapper;
    @Mock private SysUserRoleMapper sysUserRoleMapper;

    private OrgStructureService service;

    @BeforeEach
    void setUp() {
        OrgAssignmentPolicy assignmentPolicy = new OrgAssignmentPolicy(new SysOrgNodeLookupAdapter(sysDeptMapper));
        OrgValidationPolicy validationPolicy = new OrgValidationPolicy(
                new SysOrgLeaderCandidateLookupAdapter(sysUserMapper, sysRoleMapper, sysUserRoleMapper),
                new SysOrgDeletionConstraintLookupAdapter(sysDeptMapper),
                new CurrentUserPermissionPolicy());
        OrgEnrichmentPolicy enrichmentPolicy = new OrgEnrichmentPolicy(
                new SysOrgEnrichmentLookupAdapter(sysDeptMapper, sysRoleMapper),
                assignmentPolicy);
        OrgStructureApplicationService applicationService = new OrgStructureApplicationService(
                assignmentPolicy, validationPolicy, enrichmentPolicy);
        service = new OrgStructureService(applicationService);
    }

    // ===== resolveAssignment =====

    @Test
    void resolveAssignment_groupOnlyValid_shouldReturnGroup() {
        UUID groupId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        SysDept group = new SysDept();
        group.setId(groupId);
        group.setParentId(parentId);
        group.setDeptType(DeptType.RECRUITER_GROUP);
        when(sysDeptMapper.selectById(groupId)).thenReturn(group);

        OrgStructureService.ResolvedAssignment result =
                service.resolveAssignment(null, groupId);

        assertThat(result.effectiveDeptId()).isEqualTo(groupId);
        assertThat(result.parentDeptId()).isEqualTo(parentId);
        assertThat(result.groupId()).isEqualTo(groupId);
    }

    @Test
    void resolveAssignment_groupNotBelongToDept_shouldThrow() {
        UUID groupId = UUID.randomUUID();
        UUID wrongParentId = UUID.randomUUID();
        SysDept group = new SysDept();
        group.setId(groupId);
        group.setParentId(UUID.randomUUID()); // 不等于 wrongParentId
        group.setDeptType(DeptType.RECRUITER_GROUP);
        when(sysDeptMapper.selectById(groupId)).thenReturn(group);

        assertThatThrownBy(() -> service.resolveAssignment(wrongParentId, groupId))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void resolveAssignment_parentOnlyDepartment_shouldReturnDept() {
        UUID deptId = UUID.randomUUID();
        SysDept dept = new SysDept();
        dept.setId(deptId);
        dept.setDeptType(DeptType.DEPARTMENT);
        when(sysDeptMapper.selectById(deptId)).thenReturn(dept);

        OrgStructureService.ResolvedAssignment result =
                service.resolveAssignment(deptId, null);

        assertThat(result.effectiveDeptId()).isEqualTo(deptId);
        assertThat(result.parentDeptId()).isEqualTo(deptId);
        assertThat(result.groupId()).isNull();
    }

    @Test
    void resolveAssignment_bothNull_shouldReturnAllNull() {
        OrgStructureService.ResolvedAssignment result =
                service.resolveAssignment(null, null);

        assertThat(result.effectiveDeptId()).isNull();
        assertThat(result.parentDeptId()).isNull();
        assertThat(result.groupId()).isNull();
    }

    @Test
    void resolveAssignment_groupTypeNotGroup_shouldThrow() {
        UUID deptId = UUID.randomUUID();
        SysDept dept = new SysDept();
        dept.setId(deptId);
        dept.setDeptType(DeptType.DEPARTMENT); // 不是组别
        when(sysDeptMapper.selectById(deptId)).thenReturn(dept);

        assertThatThrownBy(() -> service.resolveAssignment(null, deptId))
                .isInstanceOf(BusinessException.class);
    }

    // ===== splitAssignment =====

    @Test
    void splitAssignment_nullInput_shouldReturnAllNull() {
        OrgStructureService.SplitAssignment result = service.splitAssignment(null);
        assertThat(result.parentDeptId()).isNull();
        assertThat(result.groupId()).isNull();
        assertThat(result.parentDeptName()).isNull();
        assertThat(result.groupName()).isNull();
        assertThat(result.groupType()).isNull();
    }

    @Test
    void splitAssignment_groupType_shouldReturnParentAndGroup() {
        UUID groupId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        SysDept parent = new SysDept();
        parent.setId(parentId);
        parent.setDeptName("招商部");
        SysDept group = new SysDept();
        group.setId(groupId);
        group.setParentId(parentId);
        group.setDeptName("A 组");
        group.setDeptType(DeptType.RECRUITER_GROUP);

        when(sysDeptMapper.selectById(groupId)).thenReturn(group);
        when(sysDeptMapper.selectById(parentId)).thenReturn(parent);

        OrgStructureService.SplitAssignment result = service.splitAssignment(groupId);
        assertThat(result.parentDeptId()).isEqualTo(parentId);
        assertThat(result.groupId()).isEqualTo(groupId);
        assertThat(result.parentDeptName()).isEqualTo("招商部");
        assertThat(result.groupName()).isEqualTo("A 组");
        assertThat(result.groupType()).isEqualTo("recruiter_group");
    }

    @Test
    void splitAssignment_deptType_shouldReturnOnlyParent() {
        UUID deptId = UUID.randomUUID();
        SysDept dept = new SysDept();
        dept.setId(deptId);
        dept.setDeptName("渠道部");
        dept.setDeptType(DeptType.DEPARTMENT);

        when(sysDeptMapper.selectById(deptId)).thenReturn(dept);

        OrgStructureService.SplitAssignment result = service.splitAssignment(deptId);
        assertThat(result.parentDeptId()).isEqualTo(deptId);
        assertThat(result.groupId()).isNull();
        assertThat(result.groupName()).isNull();
    }

    @Test
    void splitAssignment_deletedDept_shouldReturnAllNull() {
        UUID deptId = UUID.randomUUID();
        SysDept dept = new SysDept();
        dept.setId(deptId);
        dept.setDeleted(1);
        when(sysDeptMapper.selectById(deptId)).thenReturn(dept);

        OrgStructureService.SplitAssignment result = service.splitAssignment(deptId);
        assertThat(result.parentDeptId()).isNull();
        assertThat(result.groupId()).isNull();
    }

    // ===== enrichUserList / enrichUser =====

    @Test
    void enrichUserList_emptyList_shouldNoOp() {
        service.enrichUserList(Collections.emptyList());
        // 不抛异常即可
    }

    @Test
    void enrichUserList_nullList_shouldNoOp() {
        service.enrichUserList(null);
        // 不抛异常即可
    }

    @Test
    void enrichUser_nullInput_shouldReturnNull() {
        assertThat(service.enrichUser(null)).isNull();
    }

    @Test
    void enrichUser_withGroupDept_shouldFillOrgFields() {
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();

        SysDept parent = new SysDept();
        parent.setId(parentId);
        parent.setDeptName("渠道部");
        SysDept group = new SysDept();
        group.setId(deptId);
        group.setParentId(parentId);
        group.setDeptName("A 组");
        group.setDeptType(DeptType.CHANNEL_GROUP);

        SysUserVO user = new SysUserVO();
        user.setId(userId);
        user.setDeptId(deptId);
        user.setRoleIds(new ArrayList<>());

        when(sysDeptMapper.findAllActive()).thenReturn(List.of(parent, group));
        when(sysRoleMapper.selectList(null)).thenReturn(Collections.emptyList());

        service.enrichUser(user);

        assertThat(user.getGroupId()).isEqualTo(deptId);
        assertThat(user.getGroupName()).isEqualTo("A 组");
        assertThat(user.getGroupType()).isEqualTo("channel_group");
        assertThat(user.getParentDeptId()).isEqualTo(parentId);
        assertThat(user.getParentDeptName()).isEqualTo("渠道部");
    }

    // ===== validateGroupLeader =====

    @Test
    void validateGroupLeader_nullLeaderId_shouldReturnNull() {
        assertThat(service.validateGroupLeader(null, DeptType.RECRUITER_GROUP)).isNull();
    }

    @Test
    void validateGroupLeader_userNotExists_shouldThrow() {
        UUID userId = UUID.randomUUID();
        when(sysUserMapper.selectById(userId)).thenReturn(null);

        assertThatThrownBy(() -> service.validateGroupLeader(userId, DeptType.RECRUITER_GROUP))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void validateGroupLeader_deletedUser_shouldThrow() {
        UUID userId = UUID.randomUUID();
        SysUser user = new SysUser();
        user.setId(userId);
        user.setDeleted(1);
        when(sysUserMapper.selectById(userId)).thenReturn(user);

        assertThatThrownBy(() -> service.validateGroupLeader(userId, DeptType.RECRUITER_GROUP))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void validateGroupLeader_adminForAnyGroup_shouldReturnRealName() {
        UUID userId = UUID.randomUUID();
        SysUser user = new SysUser();
        user.setId(userId);
        user.setRealName("张管理");
        UUID adminRoleId = UUID.randomUUID();
        SysUserRole userRole = new SysUserRole();
        userRole.setRoleId(adminRoleId);
        SysRole adminRole = new SysRole();
        adminRole.setId(adminRoleId);
        adminRole.setRoleCode(com.colonel.saas.constant.RoleCodes.ADMIN);

        when(sysUserMapper.selectById(userId)).thenReturn(user);
        when(sysUserRoleMapper.findByUserId(userId)).thenReturn(List.of(userRole));
        when(sysRoleMapper.selectById(adminRoleId)).thenReturn(adminRole);

        String name = service.validateGroupLeader(userId, DeptType.RECRUITER_GROUP);
        assertThat(name).isEqualTo("张管理");
    }

    @Test
    void validateGroupLeader_channelLeaderForChannelGroup_shouldReturnRealName() {
        UUID userId = UUID.randomUUID();
        SysUser user = new SysUser();
        user.setId(userId);
        user.setRealName("李组长");
        UUID roleId = UUID.randomUUID();
        SysUserRole userRole = new SysUserRole();
        userRole.setRoleId(roleId);
        SysRole role = new SysRole();
        role.setId(roleId);
        role.setRoleCode(com.colonel.saas.constant.RoleCodes.CHANNEL_LEADER);

        when(sysUserMapper.selectById(userId)).thenReturn(user);
        when(sysUserRoleMapper.findByUserId(userId)).thenReturn(List.of(userRole));
        when(sysRoleMapper.selectById(roleId)).thenReturn(role);

        String name = service.validateGroupLeader(userId, DeptType.CHANNEL_GROUP);
        assertThat(name).isEqualTo("李组长");
    }

    @Test
    void validateGroupLeader_recruiterGroupWithChannelRole_shouldThrow() {
        UUID userId = UUID.randomUUID();
        SysUser user = new SysUser();
        user.setId(userId);
        UUID roleId = UUID.randomUUID();
        SysUserRole userRole = new SysUserRole();
        userRole.setRoleId(roleId);
        SysRole role = new SysRole();
        role.setId(roleId);
        role.setRoleCode(com.colonel.saas.constant.RoleCodes.CHANNEL_LEADER); // 渠道组长分配到招商组 → 失败

        when(sysUserMapper.selectById(userId)).thenReturn(user);
        when(sysUserRoleMapper.findByUserId(userId)).thenReturn(List.of(userRole));
        when(sysRoleMapper.selectById(roleId)).thenReturn(role);

        assertThatThrownBy(() -> service.validateGroupLeader(userId, DeptType.RECRUITER_GROUP))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void validateGroupLeader_fallbackToUsername_whenNoRealName() {
        UUID userId = UUID.randomUUID();
        SysUser user = new SysUser();
        user.setId(userId);
        user.setUsername("zhangsan");
        UUID adminRoleId = UUID.randomUUID();
        SysUserRole userRole = new SysUserRole();
        userRole.setRoleId(adminRoleId);
        SysRole adminRole = new SysRole();
        adminRole.setId(adminRoleId);
        adminRole.setRoleCode(com.colonel.saas.constant.RoleCodes.ADMIN);

        when(sysUserMapper.selectById(userId)).thenReturn(user);
        when(sysUserRoleMapper.findByUserId(userId)).thenReturn(List.of(userRole));
        when(sysRoleMapper.selectById(adminRoleId)).thenReturn(adminRole);

        String name = service.validateGroupLeader(userId, DeptType.RECRUITER_GROUP);
        assertThat(name).isEqualTo("zhangsan");
    }

    // ===== assertCanDeleteDept =====

    @Test
    void assertCanDeleteDept_withUsers_shouldThrow() {
        UUID deptId = UUID.randomUUID();
        when(sysDeptMapper.countUsersByDeptId(deptId)).thenReturn(3L);

        assertThatThrownBy(() -> service.assertCanDeleteDept(deptId))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void assertCanDeleteDept_withChildGroups_shouldThrow() {
        UUID deptId = UUID.randomUUID();
        when(sysDeptMapper.countUsersByDeptId(deptId)).thenReturn(0L);
        when(sysDeptMapper.countChildGroups(deptId)).thenReturn(2L);

        assertThatThrownBy(() -> service.assertCanDeleteDept(deptId))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void assertCanDeleteDept_clean_shouldNotThrow() {
        UUID deptId = UUID.randomUUID();
        when(sysDeptMapper.countUsersByDeptId(deptId)).thenReturn(0L);
        when(sysDeptMapper.countChildGroups(deptId)).thenReturn(0L);

        service.assertCanDeleteDept(deptId); // 不抛异常
    }

    // ===== formatOrgChangeRemark =====

    @Test
    void formatOrgChangeRemark_nullDeptIds_shouldStillFormat() {
        UUID userId = UUID.randomUUID();
        UUID operatorId = UUID.randomUUID();
        // splitAssignment(null) 返回全 null，所以 deptId/groupId 都是 "null"
        String remark = service.formatOrgChangeRemark(userId, null, null, operatorId);
        assertThat(remark).contains("userId=" + userId);
        assertThat(remark).contains("operatorId=" + operatorId);
        assertThat(remark).contains("oldDeptId=null");
        assertThat(remark).contains("newDeptId=null");
    }

    @Test
    void formatOrgChangeRemark_withDeptChanges_shouldIncludeAllFields() {
        UUID userId = UUID.randomUUID();
        UUID operatorId = UUID.randomUUID();
        UUID oldDept = UUID.randomUUID();
        UUID newDept = UUID.randomUUID();
        SysDept oldDeptEntity = new SysDept();
        oldDeptEntity.setId(oldDept);
        oldDeptEntity.setDeptName("旧部门");
        oldDeptEntity.setDeptType(DeptType.DEPARTMENT);
        SysDept newDeptEntity = new SysDept();
        newDeptEntity.setId(newDept);
        newDeptEntity.setDeptName("新部门");
        newDeptEntity.setDeptType(DeptType.DEPARTMENT);

        when(sysDeptMapper.selectById(oldDept)).thenReturn(oldDeptEntity);
        when(sysDeptMapper.selectById(newDept)).thenReturn(newDeptEntity);

        String remark = service.formatOrgChangeRemark(userId, oldDept, newDept, operatorId);
        assertThat(remark).contains("oldDeptId=" + oldDept);
        assertThat(remark).contains("newDeptId=" + newDept);
        assertThat(remark).contains("userId=" + userId);
    }
}
