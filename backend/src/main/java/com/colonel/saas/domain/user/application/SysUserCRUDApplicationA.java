package com.colonel.saas.domain.user.application;

import com.colonel.saas.auth.dto.SysUserCreateRequest;
import com.colonel.saas.auth.service.OrgStructureService;
import com.colonel.saas.auth.service.OrgStructureService.ResolvedAssignment;
import com.colonel.saas.auth.service.OrgStructureService.SplitAssignment;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.constant.SysUserStatus;
import com.colonel.saas.domain.user.policy.UserAccessPolicy;
import com.colonel.saas.domain.user.policy.UserAccessPolicy.AccessibleUser;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 系统用户 CRUD 应用服务 A（DDD-USER-MIGRATION-012，Issue #21）。
 *
 * <p>用户域 / 用户管理 —— {@code getById} 与 {@code create} 的 DDD 薄壳 facade。
 * 当前为过渡实现 —— 业务逻辑保留在 SysUserService 旧实现，
 * 本 ApplicationService 作为 DDD 入口（参考模板 SysDeptApplicationService）。
 * 后续可拆分为独立 Policy。</p>
 *
 * <p><b>行为 1:1 等价</b>于 SysUserService.getById + SysUserService.create
 * （被 SysUserServiceTest baseline + 本测试类覆盖）。</p>
 *
 * <p><b>2 个 public 方法</b>：
 * <ul>
 *   <li>getById —— 单个查询（带数据权限校验）</li>
 *   <li>create —— 新建（事务性，校验用户名唯一 + 角色合法性 + 单一管理员保护）</li>
 * </ul>
 *
 * <p>所属业务领域：用户域 / 用户管理</p>
 */
@Service
public class SysUserCRUDApplicationA {

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final OperationLogService operationLogService;
    private final UserDomainEventPublisher userDomainEventPublisher;
    private final OrgStructureService orgStructureService;
    private final UserAccessPolicy userAccessPolicy;
    private final UserChannelCodePolicy userChannelCodePolicy;

    public SysUserCRUDApplicationA(
            SysUserMapper sysUserMapper,
            SysRoleMapper sysRoleMapper,
            SysUserRoleMapper sysUserRoleMapper,
            PasswordEncoder passwordEncoder,
            OperationLogService operationLogService,
            UserDomainEventPublisher userDomainEventPublisher,
            OrgStructureService orgStructureService,
            UserAccessPolicy userAccessPolicy,
            UserChannelCodePolicy userChannelCodePolicy) {
        this.sysUserMapper = sysUserMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
        this.passwordEncoder = passwordEncoder;
        this.operationLogService = operationLogService;
        this.userDomainEventPublisher = userDomainEventPublisher;
        this.orgStructureService = orgStructureService;
        this.userAccessPolicy = userAccessPolicy;
        this.userChannelCodePolicy = userChannelCodePolicy;
    }

    // ===== 查询 =====

    public SysUserVO getById(UUID id, UUID currentUserId, DataScope dataScope) {
        SysUser user = requireUser(id);
        userAccessPolicy.assertCanAccess(accessibleUser(user), currentUserId, dataScope);
        return orgStructureService.enrichUser(toVO(user));
    }

    // ===== CRUD（事务性）=====

    @Transactional(rollbackFor = Exception.class)
    public SysUserVO create(SysUserCreateRequest request, UUID currentUserId) {
        sysUserMapper.findByUsername(request.username()).ifPresent(existing -> {
            throw BusinessException.duplicate("用户名已存在");
        });

        List<UUID> roleIds = normalizeRoleIds(request.roleIds());
        validateRoleIds(roleIds, null);

        SysUser user = new SysUser();
        user.setId(UUID.randomUUID());
        user.setUsername(request.username());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRealName(request.realName());
        user.setPhone(request.phone());
        user.setEmail(request.email());
        ResolvedAssignment assignment = resolveAssignment(
                request.parentDeptId(),
                request.groupId(),
                request.deptId());
        user.setDeptId(assignment.effectiveDeptId());
        user.setStatus(SysUserStatus.PENDING_ACTIVATION);
        user.setForcePasswordChange(true);
        user.setChannelCode(userChannelCodePolicy.generateUnique(request.username()));
        sysUserMapper.insert(user);

        replaceUserRoles(user.getId(), roleIds);
        operationLogService.recordSystemAction(
                currentUserId,
                "用户管理",
                "新建用户",
                "POST",
                "SysUser",
                user.getId().toString(),
                user.getUsername(),
                "新建用户: " + user.getUsername()
        );
        SysRole primaryRole = resolvePrimaryRole(roleIds);
        userDomainEventPublisher.publishUserCreated(
                user.getId(),
                user.getUsername(),
                user.getRealName(),
                primaryRole == null ? null : primaryRole.getId(),
                primaryRole == null ? null : primaryRole.getRoleCode(),
                user.getDeptId(),
                user.getDeptId(),
                user.getStatus(),
                currentUserId);
        return orgStructureService.enrichUser(toVO(user));
    }

    // ===== private helpers（从 SysUserService 1:1 复制）=====

    private SysUser requireUser(UUID id) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            throw BusinessException.notFound("用户不存在");
        }
        return user;
    }

    private static AccessibleUser accessibleUser(SysUser user) {
        return new AccessibleUser(user.getId(), user.getDeptId());
    }

    /**
     * 规范化角色 ID 列表：去重并过滤 null 值。
     */
    private List<UUID> normalizeRoleIds(List<UUID> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return Collections.emptyList();
        }
        Set<UUID> distinct = new LinkedHashSet<>();
        for (UUID roleId : roleIds) {
            if (roleId != null) {
                distinct.add(roleId);
            }
        }
        return new ArrayList<>(distinct);
    }

    /**
     * 校验角色 ID 列表的有效性：存在性 + 启用状态 + 单一管理员约束。
     */
    private void validateRoleIds(List<UUID> roleIds, UUID targetUserId) {
        if (roleIds.isEmpty()) {
            return;
        }
        // 第一步：校验角色存在性
        List<SysRole> roles = sysRoleMapper.selectBatchIds(roleIds);
        if (roles.size() != roleIds.size()) {
            throw BusinessException.notFound("角色不存在或已删除");
        }
        // 第二步：校验角色是否已禁用
        boolean hasDisabledRole = roles.stream()
                .anyMatch(role -> role.getStatus() == null || role.getStatus() != 1);
        if (hasDisabledRole) {
            throw BusinessException.stateInvalid("不能分配已禁用角色");
        }
        // 第三步：单一管理员保护
        assertSingleAdminUser(roles, targetUserId);
    }

    /**
     * 校验单一管理员约束：系统全局只允许一个未删除的 ADMIN 角色用户。
     */
    private void assertSingleAdminUser(List<SysRole> roles, UUID targetUserId) {
        // 第一步：检查待分配角色中是否包含 ADMIN
        SysRole adminRole = roles.stream()
                .filter(role -> RoleCodes.ADMIN.equals(role.getRoleCode()))
                .findFirst()
                .orElse(null);
        if (adminRole == null || adminRole.getId() == null) {
            return;
        }
        // 第二步：若目标用户已经是管理员，允许重新分配（不抛异常）
        if (targetUserId != null) {
            boolean targetAlreadyAdmin = sysUserRoleMapper.findByUserId(targetUserId).stream()
                    .anyMatch(relation -> adminRole.getId().equals(relation.getRoleId()));
            if (targetAlreadyAdmin) {
                return;
            }
        }
        // 第三步：检查数据库中是否已存在其他未删除的管理员
        List<UUID> adminUserIds = sysUserRoleMapper.findByRoleId(adminRole.getId()).stream()
                .map(SysUserRole::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (adminUserIds.isEmpty()) {
            return;
        }
        boolean hasExistingAdmin = sysUserMapper.selectBatchIds(adminUserIds).stream()
                .filter(Objects::nonNull)
                .anyMatch(user -> user.getDeleted() == null || user.getDeleted() == 0);
        if (hasExistingAdmin) {
            throw BusinessException.duplicate("管理员账号已存在，不能新增或转配第二个管理员");
        }
    }

    /**
     * 全量替换用户的角色关联（先删后插）。
     */
    private void replaceUserRoles(UUID userId, List<UUID> roleIds) {
        // 第一步：物理删除旧的角色关联
        sysUserRoleMapper.deleteByUserIdPhysical(userId);
        // 第二步：插入新的角色关联
        for (UUID roleId : roleIds) {
            SysUserRole relation = new SysUserRole();
            relation.setId(UUID.randomUUID());
            relation.setUserId(userId);
            relation.setRoleId(roleId);
            sysUserRoleMapper.insert(relation);
        }
    }

    /**
     * 将用户实体转换为视图对象（VO），同时查询该用户关联的角色 ID 列表填充。
     */
    private SysUserVO toVO(SysUser user) {
        SysUserVO vo = new SysUserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setPhone(user.getPhone());
        vo.setEmail(user.getEmail());
        vo.setDeptId(user.getDeptId());
        vo.setStatus(user.getStatus());
        vo.setForcePasswordChange(user.getForcePasswordChange());
        vo.setLastLoginAt(user.getLastLoginAt());
        vo.setCreateTime(user.getCreateTime());
        // 查询并填充角色 ID 列表
        List<UUID> roleIds = sysUserRoleMapper.findByUserId(user.getId()).stream()
                .map(SysUserRole::getRoleId)
                .collect(Collectors.toList());
        vo.setRoleIds(roleIds);
        return vo;
    }

    /**
     * 解析创建/更新请求中的组织归属：parentDeptId + groupId 新模式优先，
     * 否则尝试 legacyDeptId 旧模式。
     */
    private ResolvedAssignment resolveAssignment(UUID parentDeptId, UUID groupId, UUID legacyDeptId) {
        if (parentDeptId != null || groupId != null) {
            return orgStructureService.resolveAssignment(parentDeptId, groupId);
        }
        if (legacyDeptId != null) {
            SplitAssignment split = orgStructureService.splitAssignment(legacyDeptId);
            return new ResolvedAssignment(
                    legacyDeptId,
                    split.parentDeptId() != null ? split.parentDeptId() : legacyDeptId,
                    split.groupId());
        }
        return new ResolvedAssignment(null, null, null);
    }

    /**
     * 解析主角色：从角色 ID 列表取第一个角色作为主角色。
     */
    private SysRole resolvePrimaryRole(List<UUID> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return null;
        }
        List<SysRole> roles = sysRoleMapper.selectBatchIds(roleIds);
        if (roles == null || roles.isEmpty()) {
            return null;
        }
        return roles.get(0);
    }
}