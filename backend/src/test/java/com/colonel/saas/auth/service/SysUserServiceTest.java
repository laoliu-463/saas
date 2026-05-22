package com.colonel.saas.auth.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.auth.dto.*;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.entity.SysRole;
import com.colonel.saas.entity.SysUser;
import com.colonel.saas.entity.SysUserRole;
import com.colonel.saas.mapper.SysRoleMapper;
import com.colonel.saas.mapper.SysUserMapper;
import com.colonel.saas.mapper.SysUserRoleMapper;
import com.colonel.saas.service.OperationLogService;
import com.colonel.saas.vo.SysUserVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SysUserServiceTest {

    @Spy private SysUserMapper sysUserMapper;
    @Spy private SysRoleMapper sysRoleMapper;
    @Spy private SysUserRoleMapper sysUserRoleMapper;
    @Spy private PasswordEncoder passwordEncoder;
    @Mock private OperationLogService operationLogService;

    private SysUserService sysUserService;

    private SysUser testUser;
    private final UUID userId = UUID.randomUUID();
    private final UUID deptId = UUID.randomUUID();
    private final UUID roleId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        sysUserService = new SysUserService(
                sysUserMapper,
                sysRoleMapper,
                sysUserRoleMapper,
                passwordEncoder,
                operationLogService
        );
        testUser = new SysUser();
        testUser.setId(userId);
        testUser.setUsername("testadmin");
        testUser.setRealName("测试管理员");
        testUser.setPhone("13800138000");
        testUser.setEmail("admin@test.com");
        testUser.setDeptId(deptId);
        testUser.setStatus(1);
        testUser.setChannelCode("testadmin");
    }

    @Test
    void findPage_returnsPageWithData() {
        SysUserPageRequest request = new SysUserPageRequest(1, 10, null, null, null);
        SysUserVO vo = new SysUserVO();
        vo.setId(userId);
        vo.setUsername("testadmin");
        Page<SysUserVO> page = new Page<>(1, 10);
        page.setRecords(List.of(vo));
        page.setTotal(1);

        when(sysUserMapper.findPage(any(), any(), any())).thenReturn(page);
        when(sysUserRoleMapper.findByUserIds(anyList())).thenReturn(Collections.emptyList());

        IPage<SysUserVO> result = sysUserService.findPage(userId, DataScope.ALL, request);

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRecords()).hasSize(1);
        verify(sysUserMapper).findPage(any(), any(), any());
    }

    @Test
    void findAssignableUsers_returnsEnabledUsers() {
        SysUser assignee = new SysUser();
        assignee.setId(UUID.randomUUID());
        assignee.setUsername("bizstaff");
        assignee.setRealName("招商专员");
        assignee.setStatus(1);
        assignee.setChannelCode("bizstaff");
        when(sysUserMapper.selectList(any())).thenReturn(List.of(assignee));
        SysUserRole relation = new SysUserRole();
        relation.setUserId(assignee.getId());
        relation.setRoleId(roleId);
        when(sysUserRoleMapper.findByUserId(assignee.getId())).thenReturn(List.of(relation));

        SysRole assignableRole = new SysRole();
        assignableRole.setId(roleId);
        assignableRole.setRoleCode(RoleCodes.BIZ_STAFF);
        assignableRole.setStatus(1);
        when(sysRoleMapper.selectBatchIds(any())).thenReturn(List.of(assignableRole));

        assignee.setDeptId(deptId);

        List<SysUserVO> result = sysUserService.findAssignableUsers("招商", List.of(RoleCodes.BIZ_LEADER), deptId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUsername()).isEqualTo("bizstaff");
        verify(sysUserMapper).selectList(any());
    }

    @Test
    void findAssignableUsers_filtersOutNonBusinessRoles() {
        SysUser assignee = new SysUser();
        assignee.setId(UUID.randomUUID());
        assignee.setUsername("opsstaff");
        assignee.setRealName("运营专员");
        assignee.setStatus(1);

        SysUserRole relation = new SysUserRole();
        relation.setUserId(assignee.getId());
        relation.setRoleId(roleId);

        SysRole opsRole = new SysRole();
        opsRole.setId(roleId);
        opsRole.setRoleCode(RoleCodes.OPS_STAFF);
        opsRole.setStatus(1);

        when(sysUserMapper.selectList(any())).thenReturn(List.of(assignee));
        when(sysUserRoleMapper.findByUserId(assignee.getId())).thenReturn(List.of(relation));
        when(sysRoleMapper.selectBatchIds(any())).thenReturn(List.of(opsRole));

        List<SysUserVO> result = sysUserService.findAssignableUsers(null, List.of(RoleCodes.ADMIN), null);

        assertThat(result).isEmpty();
    }

    @Test
    void findAssignableUsers_returnsEmptyWhenCurrentRoleContextMissing() {
        SysUser assignee = new SysUser();
        assignee.setId(UUID.randomUUID());
        assignee.setUsername("bizstaff");
        assignee.setStatus(1);
        when(sysUserMapper.selectList(any())).thenReturn(List.of(assignee));

        List<SysUserVO> result = sysUserService.findAssignableUsers(null, null, deptId);

        assertThat(result).isEmpty();
        verify(sysUserRoleMapper, never()).findByUserId(any());
    }

    @Test
    void findAssignableUsers_shouldRestrictLeaderToOwnDept() {
        SysUser sameDept = new SysUser();
        sameDept.setId(UUID.randomUUID());
        sameDept.setUsername("bizstaff-a");
        sameDept.setRealName("招商A");
        sameDept.setStatus(1);
        sameDept.setDeptId(deptId);

        SysUser otherDept = new SysUser();
        otherDept.setId(UUID.randomUUID());
        otherDept.setUsername("bizstaff-b");
        otherDept.setRealName("招商B");
        otherDept.setStatus(1);
        otherDept.setDeptId(UUID.randomUUID());

        SysUserRole sameRelation = new SysUserRole();
        sameRelation.setUserId(sameDept.getId());
        sameRelation.setRoleId(roleId);
        SysUserRole otherRelation = new SysUserRole();
        otherRelation.setUserId(otherDept.getId());
        otherRelation.setRoleId(roleId);

        SysRole assignableRole = new SysRole();
        assignableRole.setId(roleId);
        assignableRole.setRoleCode(RoleCodes.BIZ_STAFF);
        assignableRole.setStatus(1);

        when(sysUserMapper.selectList(any())).thenReturn(List.of(sameDept, otherDept));
        when(sysUserRoleMapper.findByUserId(sameDept.getId())).thenReturn(List.of(sameRelation));
        when(sysUserRoleMapper.findByUserId(otherDept.getId())).thenReturn(List.of(otherRelation));
        when(sysRoleMapper.selectBatchIds(any())).thenReturn(List.of(assignableRole));

        List<SysUserVO> result = sysUserService.findAssignableUsers(null, List.of(RoleCodes.BIZ_LEADER), deptId);

        assertThat(result).extracting(SysUserVO::getUsername).containsExactly("bizstaff-a");
    }

    @Test
    void findAssignableUsers_shouldAllowColonelLeaderToAssignOwnDeptBizStaff() {
        SysUser sameDept = new SysUser();
        sameDept.setId(UUID.randomUUID());
        sameDept.setUsername("bizstaff-a");
        sameDept.setRealName("招商A");
        sameDept.setStatus(1);
        sameDept.setDeptId(deptId);

        SysUser otherDept = new SysUser();
        otherDept.setId(UUID.randomUUID());
        otherDept.setUsername("bizstaff-b");
        otherDept.setRealName("招商B");
        otherDept.setStatus(1);
        otherDept.setDeptId(UUID.randomUUID());

        SysUserRole sameRelation = new SysUserRole();
        sameRelation.setUserId(sameDept.getId());
        sameRelation.setRoleId(roleId);
        SysUserRole otherRelation = new SysUserRole();
        otherRelation.setUserId(otherDept.getId());
        otherRelation.setRoleId(roleId);

        SysRole assignableRole = new SysRole();
        assignableRole.setId(roleId);
        assignableRole.setRoleCode(RoleCodes.BIZ_STAFF);
        assignableRole.setStatus(1);

        when(sysUserMapper.selectList(any())).thenReturn(List.of(sameDept, otherDept));
        when(sysUserRoleMapper.findByUserId(sameDept.getId())).thenReturn(List.of(sameRelation));
        when(sysUserRoleMapper.findByUserId(otherDept.getId())).thenReturn(List.of(otherRelation));
        when(sysRoleMapper.selectBatchIds(any())).thenReturn(List.of(assignableRole));

        List<SysUserVO> result = sysUserService.findAssignableUsers(null, List.of(RoleCodes.COLONEL_LEADER), deptId);

        assertThat(result).extracting(SysUserVO::getUsername).containsExactly("bizstaff-a");
    }

    @Test
    void findAssignableUsers_shouldAllowChannelLeaderToAssignOwnDeptChannelStaff() {
        SysUser sameDept = new SysUser();
        sameDept.setId(UUID.randomUUID());
        sameDept.setUsername("channelstaff-a");
        sameDept.setRealName("渠道A");
        sameDept.setStatus(1);
        sameDept.setDeptId(deptId);

        SysUserRole relation = new SysUserRole();
        relation.setUserId(sameDept.getId());
        relation.setRoleId(roleId);

        SysRole assignableRole = new SysRole();
        assignableRole.setId(roleId);
        assignableRole.setRoleCode(RoleCodes.CHANNEL_STAFF);
        assignableRole.setStatus(1);

        when(sysUserMapper.selectList(any())).thenReturn(List.of(sameDept));
        when(sysUserRoleMapper.findByUserId(sameDept.getId())).thenReturn(List.of(relation));
        when(sysRoleMapper.selectBatchIds(any())).thenReturn(List.of(assignableRole));

        List<SysUserVO> result = sysUserService.findAssignableUsers(null, List.of(RoleCodes.CHANNEL_LEADER), deptId);

        assertThat(result).extracting(SysUserVO::getUsername).containsExactly("channelstaff-a");
    }

    @Test
    void assertAssignableUser_allowsBizLeaderAssigningOwnDeptBizStaff() {
        UUID assigneeId = UUID.randomUUID();
        SysUser assignee = new SysUser();
        assignee.setId(assigneeId);
        assignee.setUsername("bizstaff-a");
        assignee.setDeptId(deptId);

        SysUserRole relation = new SysUserRole();
        relation.setUserId(assigneeId);
        relation.setRoleId(roleId);

        SysRole role = new SysRole();
        role.setId(roleId);
        role.setRoleCode(RoleCodes.BIZ_STAFF);
        role.setStatus(1);

        when(sysUserMapper.selectById(assigneeId)).thenReturn(assignee);
        when(sysUserRoleMapper.findByUserId(assigneeId)).thenReturn(List.of(relation));
        when(sysRoleMapper.selectBatchIds(any())).thenReturn(List.of(role));

        assertThatCode(() -> sysUserService.assertAssignableUser(
                assigneeId,
                List.of(RoleCodes.BIZ_LEADER),
                deptId
        )).doesNotThrowAnyException();
    }

    @Test
    void assertAssignableUser_rejectsBizLeaderAssigningOtherDeptUser() {
        UUID assigneeId = UUID.randomUUID();
        SysUser assignee = new SysUser();
        assignee.setId(assigneeId);
        assignee.setUsername("bizstaff-b");
        assignee.setDeptId(UUID.randomUUID());

        when(sysUserMapper.selectById(assigneeId)).thenReturn(assignee);

        assertThatThrownBy(() -> sysUserService.assertAssignableUser(
                assigneeId,
                List.of(RoleCodes.BIZ_LEADER),
                deptId
        )).isInstanceOf(RuntimeException.class)
                .hasMessageContaining("本组招商下属");
    }

    @Test
    void assertAssignableUser_rejectsMissingRoleContextAndRolelessTarget() {
        UUID assigneeId = UUID.randomUUID();
        SysUser assignee = new SysUser();
        assignee.setId(assigneeId);
        assignee.setUsername("bizstaff");
        assignee.setDeptId(deptId);

        assertThatThrownBy(() -> sysUserService.assertAssignableUser(assigneeId, List.of(), deptId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("当前角色不允许分配负责人");

        when(sysUserMapper.selectById(assigneeId)).thenReturn(assignee);
        when(sysUserRoleMapper.findByUserId(assigneeId)).thenReturn(List.of());

        assertThatThrownBy(() -> sysUserService.assertAssignableUser(assigneeId, List.of(RoleCodes.BIZ_LEADER), deptId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("目标负责人未配置可分配角色");
    }

    @Test
    void getById_returnsUserWhenExists() {
        when(sysUserMapper.selectById(userId)).thenReturn(testUser);
        when(sysUserRoleMapper.findByUserId(userId)).thenReturn(Collections.emptyList());

        SysUserVO result = sysUserService.getById(userId, userId, DataScope.ALL);

        assertThat(result.getUsername()).isEqualTo("testadmin");
        verify(sysUserMapper).selectById(userId);
    }

    @Test
    void getById_throwsWhenUserNotFound() {
        when(sysUserMapper.selectById(userId)).thenReturn(null);

        assertThatThrownBy(() -> sysUserService.getById(userId, userId, DataScope.ALL))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("用户不存在");
    }

    @Test
    void create_encryptsPasswordAndInsertsUser() {
        when(sysUserMapper.findByUsername("newuser")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("PlainPassword123")).thenReturn("$2a$10$encoded");
        when(sysUserMapper.insert(any())).thenAnswer(invocation -> {
            SysUser user = invocation.getArgument(0);
            user.setId(UUID.randomUUID());
            return 1;
        });
        
        SysRole mockRole = new SysRole();
        mockRole.setId(roleId);
        mockRole.setStatus(1);
        when(sysRoleMapper.selectBatchIds(anyList())).thenReturn(List.of(mockRole));
        
        when(sysUserRoleMapper.findByUserId(any())).thenReturn(Collections.emptyList());

        SysUserCreateRequest request = new SysUserCreateRequest(
                "newuser", "PlainPassword123", "新用户", "13800138000", "newuser@test.com", deptId, List.of(roleId));

        SysUserVO result = sysUserService.create(request, UUID.randomUUID());

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(sysUserMapper).insert(captor.capture());
        assertThat(captor.getValue().getId()).isNotNull();
        assertThat(captor.getValue().getPassword()).isEqualTo("$2a$10$encoded");
        assertThat(captor.getValue().getPhone()).isEqualTo("13800138000");
        assertThat(captor.getValue().getEmail()).isEqualTo("newuser@test.com");
        assertThat(result.getUsername()).isEqualTo("newuser");
    }

    @Test
    void create_throwsWhenUsernameExists() {
        when(sysUserMapper.findByUsername("existing")).thenReturn(Optional.of(testUser));

        SysUserCreateRequest request = new SysUserCreateRequest(
                "existing", "password123", "测试", null, null, deptId, List.of(roleId));

        assertThatThrownBy(() -> sysUserService.create(request, UUID.randomUUID()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("用户名已存在");
    }

    @Test
    void create_rejectsSecondAdminUser() {
        UUID adminRoleId = UUID.randomUUID();
        SysRole adminRole = new SysRole();
        adminRole.setId(adminRoleId);
        adminRole.setRoleCode(RoleCodes.ADMIN);
        adminRole.setStatus(1);

        SysUser existingAdmin = new SysUser();
        existingAdmin.setId(UUID.randomUUID());
        existingAdmin.setUsername("admin");

        SysUserRole adminRelation = new SysUserRole();
        adminRelation.setUserId(existingAdmin.getId());
        adminRelation.setRoleId(adminRoleId);

        when(sysUserMapper.findByUsername("newadmin")).thenReturn(Optional.empty());
        when(sysRoleMapper.selectBatchIds(anyList())).thenReturn(List.of(adminRole));
        when(sysUserRoleMapper.findByRoleId(adminRoleId)).thenReturn(List.of(adminRelation));
        when(sysUserMapper.selectBatchIds(List.of(existingAdmin.getId()))).thenReturn(List.of(existingAdmin));

        SysUserCreateRequest request = new SysUserCreateRequest(
                "newadmin", "password123", "第二管理员", null, null, deptId, List.of(adminRoleId));

        assertThatThrownBy(() -> sysUserService.create(request, UUID.randomUUID()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("管理员账号已存在");
    }

    @Test
    void update_successfullyUpdatesUser() {
        when(sysUserMapper.selectById(userId)).thenReturn(testUser);
        when(sysUserRoleMapper.findByUserId(userId)).thenReturn(Collections.emptyList());

        SysUserUpdateRequest request = new SysUserUpdateRequest("新名字", "13900139000", "new@test.com", 1);

        SysUserVO result = sysUserService.update(userId, request, userId, DataScope.ALL);

        verify(sysUserMapper).updateById(any());
        assertThat(result.getRealName()).isEqualTo("新名字");
    }

    @Test
    void update_throwsWhenUserNotFound() {
        when(sysUserMapper.selectById(userId)).thenReturn(null);

        SysUserUpdateRequest request = new SysUserUpdateRequest("新名字", null, null, 1);

        assertThatThrownBy(() -> sysUserService.update(userId, request, userId, DataScope.ALL))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("用户不存在");
    }

    @Test
    void resetPassword_successfullyResetsPassword() {
        when(sysUserMapper.selectById(userId)).thenReturn(testUser);
        when(passwordEncoder.encode("NewPassword123")).thenReturn("$2a$10$newencoded");

        SysUserResetPasswordRequest request = new SysUserResetPasswordRequest("NewPassword123");

        sysUserService.resetPassword(userId, request, userId, DataScope.ALL);

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(sysUserMapper).updateById(captor.capture());
        assertThat(captor.getValue().getPassword()).isEqualTo("$2a$10$newencoded");
    }

    @Test
    void delete_successfullyDeletesUser() {
        UUID otherUserId = UUID.randomUUID();
        when(sysUserMapper.selectById(otherUserId)).thenReturn(testUser);
        testUser.setId(otherUserId);

        sysUserService.delete(otherUserId, userId, DataScope.ALL);

        verify(sysUserRoleMapper).deleteByUserIdPhysical(otherUserId);
        verify(sysUserMapper).softDeleteById(otherUserId);
    }

    @Test
    void delete_throwsWhenDeletingCurrentUser() {
        assertThatThrownBy(() -> sysUserService.delete(userId, userId, DataScope.ALL))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("不能删除当前登录用户");
    }

    @Test
    void assignRoles_successfullyAssignsRoles() {
        when(sysUserMapper.selectById(userId)).thenReturn(testUser);
        when(sysRoleMapper.selectBatchIds(anyList())).thenReturn(List.of(new SysRole()));

        SysUserAssignRolesRequest request = new SysUserAssignRolesRequest(List.of(roleId));

        sysUserService.assignRoles(userId, request, userId, DataScope.ALL);

        verify(sysUserRoleMapper).deleteByUserIdPhysical(userId);
        ArgumentCaptor<com.colonel.saas.entity.SysUserRole> relationCaptor = ArgumentCaptor.forClass(com.colonel.saas.entity.SysUserRole.class);
        verify(sysUserRoleMapper, times(1)).insert(relationCaptor.capture());
        assertThat(relationCaptor.getValue().getId()).isNotNull();
    }

    @Test
    void assignRoles_rejectsPromotingAnotherUserToAdmin() {
        UUID adminRoleId = UUID.randomUUID();
        UUID existingAdminId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();

        SysRole adminRole = new SysRole();
        adminRole.setId(adminRoleId);
        adminRole.setRoleCode(RoleCodes.ADMIN);
        adminRole.setStatus(1);

        SysUser targetUser = new SysUser();
        targetUser.setId(targetUserId);
        targetUser.setUsername("target");

        SysUser existingAdmin = new SysUser();
        existingAdmin.setId(existingAdminId);
        existingAdmin.setUsername("admin");

        SysUserRole existingAdminRelation = new SysUserRole();
        existingAdminRelation.setUserId(existingAdminId);
        existingAdminRelation.setRoleId(adminRoleId);

        when(sysUserMapper.selectById(targetUserId)).thenReturn(targetUser);
        when(sysRoleMapper.selectBatchIds(anyList())).thenReturn(List.of(adminRole));
        when(sysUserRoleMapper.findByUserId(targetUserId)).thenReturn(Collections.emptyList());
        when(sysUserRoleMapper.findByRoleId(adminRoleId)).thenReturn(List.of(existingAdminRelation));
        when(sysUserMapper.selectBatchIds(List.of(existingAdminId))).thenReturn(List.of(existingAdmin));

        SysUserAssignRolesRequest request = new SysUserAssignRolesRequest(List.of(adminRoleId));

        assertThatThrownBy(() -> sysUserService.assignRoles(targetUserId, request, userId, DataScope.ALL))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("管理员账号已存在");
    }
}
