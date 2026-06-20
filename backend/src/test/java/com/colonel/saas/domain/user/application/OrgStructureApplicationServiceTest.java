package com.colonel.saas.domain.user.application;

import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.constant.DeptType;
import com.colonel.saas.domain.user.infrastructure.SysOrgDeletionConstraintLookupAdapter;
import com.colonel.saas.domain.user.infrastructure.SysOrgEnrichmentLookupAdapter;
import com.colonel.saas.domain.user.infrastructure.SysOrgLeaderCandidateLookupAdapter;
import com.colonel.saas.domain.user.infrastructure.SysOrgNodeLookupAdapter;
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * OrgStructureApplicationService 单测（DDD-USER-MIGRATION-005）。
 *
 * <p>作为 DDD 化后的薄壳 facade，集成 3 个 Policy：
 * <ul>
 *   <li>OrgAssignmentPolicy: resolveAssignment + splitAssignment</li>
 *   <li>OrgValidationPolicy: validateGroupLeader + assertCanDeleteDept</li>
 *   <li>OrgEnrichmentPolicy: enrichUser + enrichUserList + formatOrgChangeRemark</li>
 * </ul>
 *
 * <p>必须 1:1 等价于 OrgStructureService 旧行为（被 25 个 OrgStructureServiceTest 用例间接验证）。</p>
 */
@ExtendWith(MockitoExtension.class)
class OrgStructureApplicationServiceTest {

    @Mock private SysDeptMapper sysDeptMapper;
    @Mock private SysUserMapper sysUserMapper;
    @Mock private SysRoleMapper sysRoleMapper;
    @Mock private SysUserRoleMapper sysUserRoleMapper;

    private OrgStructureApplicationService applicationService;

    @BeforeEach
    void setUp() {
        OrgAssignmentPolicy assignmentPolicy = new OrgAssignmentPolicy(new SysOrgNodeLookupAdapter(sysDeptMapper));
        OrgValidationPolicy validationPolicy = new OrgValidationPolicy(
                new SysOrgLeaderCandidateLookupAdapter(sysUserMapper, sysRoleMapper, sysUserRoleMapper),
                new SysOrgDeletionConstraintLookupAdapter(sysDeptMapper));
        OrgEnrichmentPolicy enrichmentPolicy = new OrgEnrichmentPolicy(
                new SysOrgEnrichmentLookupAdapter(sysDeptMapper, sysRoleMapper),
                assignmentPolicy);
        applicationService = new OrgStructureApplicationService(
                assignmentPolicy, validationPolicy, enrichmentPolicy);
    }

    @Test
    void applicationServiceShouldDependOnPoliciesInsteadOfPersistenceAdapters() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/colonel/saas/domain/user/application/OrgStructureApplicationService.java"));

        assertThat(source).contains("OrgAssignmentPolicy");
        assertThat(source).contains("OrgValidationPolicy");
        assertThat(source).contains("OrgEnrichmentPolicy");
        assertThat(source).doesNotContain("com.colonel.saas.mapper.");
        assertThat(source).doesNotContain("com.colonel.saas.entity.");
        assertThat(source).doesNotContain("com.colonel.saas.domain.user.infrastructure.");
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

        var result = applicationService.resolveAssignment(null, groupId);
        assertThat(result.effectiveDeptId()).isEqualTo(groupId);
        assertThat(result.parentDeptId()).isEqualTo(parentId);
        assertThat(result.groupId()).isEqualTo(groupId);
    }

    @Test
    void resolveAssignment_bothNull_shouldReturnAllNull() {
        var result = applicationService.resolveAssignment(null, null);
        assertThat(result.effectiveDeptId()).isNull();
        assertThat(result.parentDeptId()).isNull();
        assertThat(result.groupId()).isNull();
    }

    // ===== splitAssignment =====

    @Test
    void splitAssignment_nullInput_shouldReturnAllNull() {
        var result = applicationService.splitAssignment(null);
        assertThat(result.parentDeptId()).isNull();
        assertThat(result.groupId()).isNull();
    }

    @Test
    void splitAssignment_deptType_shouldReturnOnlyParent() {
        UUID deptId = UUID.randomUUID();
        SysDept dept = new SysDept();
        dept.setId(deptId);
        dept.setDeptName("渠道部");
        dept.setDeptType(DeptType.DEPARTMENT);
        when(sysDeptMapper.selectById(deptId)).thenReturn(dept);

        var result = applicationService.splitAssignment(deptId);
        assertThat(result.parentDeptId()).isEqualTo(deptId);
        assertThat(result.groupId()).isNull();
    }

    // ===== enrichUser / enrichUserList =====

    @Test
    void enrichUser_nullInput_shouldReturnNull() {
        assertThat(applicationService.enrichUser(null)).isNull();
    }

    @Test
    void enrichUserList_emptyList_shouldNoOp() {
        applicationService.enrichUserList(Collections.emptyList());
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

        applicationService.enrichUser(user);

        assertThat(user.getGroupId()).isEqualTo(deptId);
        assertThat(user.getGroupName()).isEqualTo("A 组");
        assertThat(user.getGroupType()).isEqualTo("channel_group");
    }

    // ===== validateGroupLeader =====

    @Test
    void validateGroupLeader_nullLeaderId_shouldReturnNull() {
        assertThat(applicationService.validateGroupLeader(null, DeptType.RECRUITER_GROUP)).isNull();
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

        String name = applicationService.validateGroupLeader(userId, DeptType.RECRUITER_GROUP);
        assertThat(name).isEqualTo("张管理");
    }

    // ===== assertCanDeleteDept =====

    @Test
    void assertCanDeleteDept_withUsers_shouldThrow() {
        UUID deptId = UUID.randomUUID();
        when(sysDeptMapper.countUsersByDeptId(deptId)).thenReturn(3L);

        assertThatThrownBy(() -> applicationService.assertCanDeleteDept(deptId))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void assertCanDeleteDept_clean_shouldNotThrow() {
        UUID deptId = UUID.randomUUID();
        when(sysDeptMapper.countUsersByDeptId(deptId)).thenReturn(0L);
        when(sysDeptMapper.countChildGroups(deptId)).thenReturn(0L);

        applicationService.assertCanDeleteDept(deptId);
    }

    // ===== formatOrgChangeRemark =====

    @Test
    void formatOrgChangeRemark_nullDeptIds_shouldStillFormat() {
        UUID userId = UUID.randomUUID();
        UUID operatorId = UUID.randomUUID();
        String remark = applicationService.formatOrgChangeRemark(userId, null, null, operatorId);
        assertThat(remark).contains("userId=" + userId);
        assertThat(remark).contains("operatorId=" + operatorId);
    }
}
