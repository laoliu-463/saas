package com.colonel.saas.domain.user.application;

import com.colonel.saas.auth.dto.SysUserCreateRequest;
import com.colonel.saas.auth.service.OrgStructureService;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.constant.SysUserStatus;
import com.colonel.saas.domain.user.policy.UserAccessPolicy;
import com.colonel.saas.domain.user.policy.UserChannelCodePolicy;
import com.colonel.saas.entity.SysRole;
import com.colonel.saas.entity.SysUser;
import com.colonel.saas.entity.SysUserRole;
import com.colonel.saas.mapper.SysRoleMapper;
import com.colonel.saas.mapper.SysUserMapper;
import com.colonel.saas.mapper.SysUserRoleMapper;
import com.colonel.saas.service.OperationLogService;
import com.colonel.saas.service.UserDomainEventPublisher;
import com.colonel.saas.vo.SysUserVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SysUserCRUDApplicationA 单元测试（DDD-USER-MIGRATION-012，Issue #21）。
 *
 * <p>覆盖 4 个核心场景：
 * <ul>
 *   <li>getById 正常返回（带数据权限校验 + 组织归属 enrichment）</li>
 *   <li>getById 用户不存在抛 notFound</li>
 *   <li>create 正常创建（用户名校验 + 角色替换 + 渠道编码生成 + 域事件发布）</li>
 *   <li>create 用户名重复抛 duplicate</li>
 * </ul>
 *
 * <p>SysUserService baseline 26 用例由 SysUserServiceTest 单独保证（不修改）。</p>
 */
@ExtendWith(MockitoExtension.class)
class SysUserCRUDApplicationATest {

    @Mock
    private SysUserMapper sysUserMapper;
    @Mock
    private SysRoleMapper sysRoleMapper;
    @Mock
    private SysUserRoleMapper sysUserRoleMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private OperationLogService operationLogService;
    @Mock
    private UserDomainEventPublisher userDomainEventPublisher;
    @Mock
    private OrgStructureService orgStructureService;
    @Mock
    private UserAccessPolicy userAccessPolicy;
    @Mock
    private UserChannelCodePolicy userChannelCodePolicy;

    private SysUserCRUDApplicationA applicationA;

    @BeforeEach
    void setUp() {
        applicationA = new SysUserCRUDApplicationA(
                sysUserMapper,
                sysRoleMapper,
                sysUserRoleMapper,
                passwordEncoder,
                operationLogService,
                userDomainEventPublisher,
                orgStructureService,
                userAccessPolicy,
                userChannelCodePolicy);
    }

    // ===== getById =====

    @Test
    void getById_normalCase_returnsEnrichedVO() {
        UUID userId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        SysUser user = new SysUser();
        user.setId(userId);
        user.setUsername("alice");
        user.setStatus(1);
        user.setDeptId(deptId);

        when(sysUserMapper.selectById(userId)).thenReturn(user);
        when(sysUserRoleMapper.findByUserId(userId)).thenReturn(new ArrayList<>());
        SysUserVO enriched = new SysUserVO();
        enriched.setId(userId);
        when(orgStructureService.enrichUser(any(SysUserVO.class))).thenReturn(enriched);

        SysUserVO result = applicationA.getById(userId, currentUserId, DataScope.ALL);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(userId);
        verify(userAccessPolicy).assertCanAccess(any(UserAccessPolicy.AccessibleUser.class), eq(currentUserId), eq(DataScope.ALL));
        verify(orgStructureService).enrichUser(any(SysUserVO.class));
    }

    @Test
    void getById_userNotFound_throwsNotFound() {
        UUID userId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        when(sysUserMapper.selectById(userId)).thenReturn(null);

        assertThatThrownBy(() -> applicationA.getById(userId, currentUserId, DataScope.ALL))
                .isInstanceOf(BusinessException.class)
                .extracting(t -> ((BusinessException) t).getCode())
                .isEqualTo(com.colonel.saas.common.result.ResultCode.NOT_FOUND.getCode());

        verify(orgStructureService, never()).enrichUser(any());
    }

    // ===== create =====

    @Test
    void create_normalCase_persistsUserAndPublishesEvent() {
        UUID operatorId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        List<UUID> roleIds = List.of(roleId);

        SysUserCreateRequest request = new SysUserCreateRequest(
                "newuser", "Passw0rd!", "新用户", "13800000000", "u@x.com",
                null, null, deptId, roleIds);

        SysRole role = new SysRole();
        role.setId(roleId);
        role.setRoleCode(RoleCodes.BIZ_STAFF);
        role.setStatus(1);

        when(sysUserMapper.findByUsername("newuser")).thenReturn(Optional.empty());
        when(sysRoleMapper.selectBatchIds(roleIds)).thenReturn(List.of(role));
        lenient().when(sysUserRoleMapper.findByRoleId(role.getId())).thenReturn(new ArrayList<>());
        lenient().when(sysUserRoleMapper.deleteByUserIdPhysical(any(UUID.class))).thenReturn(0);
        lenient().when(sysUserRoleMapper.insert(any(SysUserRole.class))).thenReturn(1);
        when(passwordEncoder.encode("Passw0rd!")).thenReturn("encoded-pwd");
        when(userChannelCodePolicy.generateUnique("newuser")).thenReturn("newuser");
        when(orgStructureService.splitAssignment(deptId)).thenReturn(
                new OrgStructureService.SplitAssignment(deptId, deptId, "D", "G", "biz"));
        when(sysUserRoleMapper.findByUserId(any(UUID.class))).thenReturn(new ArrayList<>());
        when(orgStructureService.enrichUser(any(SysUserVO.class))).thenAnswer(inv -> inv.getArgument(0));

        SysUserVO result = applicationA.create(request, operatorId);

        assertThat(result).isNotNull();
        verify(sysUserMapper).insert(any(SysUser.class));
        verify(sysUserRoleMapper).deleteByUserIdPhysical(any(UUID.class));
        verify(sysUserRoleMapper).insert(any(SysUserRole.class));
        verify(operationLogService).recordSystemAction(
                eq(operatorId), eq("用户管理"), eq("新建用户"), eq("POST"),
                eq("SysUser"), any(String.class), eq("newuser"), any(String.class));
        verify(userDomainEventPublisher).publishUserCreated(
                any(UUID.class), eq("newuser"), eq("新用户"),
                eq(roleId), eq(RoleCodes.BIZ_STAFF),
                eq(deptId), eq(deptId), eq(Integer.valueOf(SysUserStatus.PENDING_ACTIVATION)), eq(operatorId));
    }

    @Test
    void create_duplicateUsername_throwsDuplicate() {
        UUID operatorId = UUID.randomUUID();
        UUID existingId = UUID.randomUUID();
        SysUser existing = new SysUser();
        existing.setId(existingId);
        existing.setUsername("alice");

        when(sysUserMapper.findByUsername("alice")).thenReturn(Optional.of(existing));

        SysUserCreateRequest request = new SysUserCreateRequest(
                "alice", "Passw0rd!", "Alice", "13800000000", "a@x.com",
                null, null, UUID.randomUUID(), List.of());

        assertThatThrownBy(() -> applicationA.create(request, operatorId))
                .isInstanceOf(BusinessException.class)
                .extracting(t -> ((BusinessException) t).getCode())
                .isEqualTo(com.colonel.saas.common.result.ResultCode.DUPLICATE.getCode());

        verify(sysUserMapper, never()).insert(any());
        verify(userDomainEventPublisher, never()).publishUserCreated(
                any(), any(), any(), any(), any(), any(), any(), any(), any());
    }
}
