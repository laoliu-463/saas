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
import com.colonel.saas.domain.user.port.UserCrudMutationStore;
import com.colonel.saas.domain.user.port.UserCrudMutationStore.ManagedRole;
import com.colonel.saas.domain.user.port.UserCrudMutationStore.ManagedUser;
import com.colonel.saas.domain.user.port.UserCrudMutationStore.NewUser;
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

/**
 * 系统用户 CRUD 应用服务 A（DDD-USER-MIGRATION-012，Issue #21）。
 *
 * <p>用户域 / 用户管理 —— {@code getById} 与 {@code create} 的 DDD 薄壳 facade。
 * 当前为过渡实现 —— 业务逻辑保留在 SysUserService 旧实现，
 * 本 ApplicationService 作为 DDD 入口（参考模板 SysDeptApplicationService）。
 * 后续可拆分为独立 Policy。</p>
 *
 * <p><b>行为 1:1 等价</b>于 SysUserService.getById + SysUserService.create
 * （被本测试类和 true-route 委托测试覆盖）。</p>
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

    private final UserCrudMutationStore userStore;
    private final PasswordEncoder passwordEncoder;
    private final OperationLogService operationLogService;
    private final UserDomainEventPublisher userDomainEventPublisher;
    private final OrgStructureService orgStructureService;
    private final UserAccessPolicy userAccessPolicy;
    private final UserChannelCodePolicy userChannelCodePolicy;

    public SysUserCRUDApplicationA(
            UserCrudMutationStore userStore,
            PasswordEncoder passwordEncoder,
            OperationLogService operationLogService,
            UserDomainEventPublisher userDomainEventPublisher,
            OrgStructureService orgStructureService,
            UserAccessPolicy userAccessPolicy,
            UserChannelCodePolicy userChannelCodePolicy) {
        this.userStore = userStore;
        this.passwordEncoder = passwordEncoder;
        this.operationLogService = operationLogService;
        this.userDomainEventPublisher = userDomainEventPublisher;
        this.orgStructureService = orgStructureService;
        this.userAccessPolicy = userAccessPolicy;
        this.userChannelCodePolicy = userChannelCodePolicy;
    }

    // ===== 查询 =====

    public SysUserVO getById(UUID id, UUID currentUserId, DataScope dataScope) {
        ManagedUser user = requireUser(id);
        userAccessPolicy.assertCanAccess(accessibleUser(user), currentUserId, dataScope);
        return orgStructureService.enrichUser(toVO(user));
    }

    // ===== CRUD（事务性）=====

    @Transactional(rollbackFor = Exception.class)
    public SysUserVO create(SysUserCreateRequest request, UUID currentUserId) {
        userStore.findByUsernameIncludingDeleted(request.username()).ifPresent(existing -> {
            throw BusinessException.duplicate("用户名已存在");
        });

        List<UUID> roleIds = normalizeRoleIds(request.roleIds());
        validateRoleIds(roleIds, null);

        ResolvedAssignment assignment = resolveAssignment(
                request.parentDeptId(),
                request.groupId(),
                request.deptId());
        NewUser user = new NewUser(
                UUID.randomUUID(),
                request.username(),
                passwordEncoder.encode(request.password()),
                request.realName(),
                request.phone(),
                request.email(),
                assignment.effectiveDeptId(),
                SysUserStatus.PENDING_ACTIVATION,
                true,
                userChannelCodePolicy.generateUnique(request.username()));
        userStore.insertUser(user);

        userStore.replaceUserRoles(user.id(), roleIds);
        operationLogService.recordSystemAction(
                currentUserId,
                "用户管理",
                "新建用户",
                "POST",
                "SysUser",
                user.id().toString(),
                user.username(),
                "新建用户: " + user.username()
        );
        ManagedRole primaryRole = resolvePrimaryRole(roleIds);
        userDomainEventPublisher.publishUserCreated(
                user.id(),
                user.username(),
                user.realName(),
                primaryRole == null ? null : primaryRole.id(),
                primaryRole == null ? null : primaryRole.roleCode(),
                user.deptId(),
                user.deptId(),
                user.status(),
                currentUserId);
        return orgStructureService.enrichUser(toVO(user.toManagedUser()));
    }

    // ===== private helpers（从 SysUserService 1:1 复制）=====

    private ManagedUser requireUser(UUID id) {
        return userStore.findUser(id)
                .orElseThrow(() -> BusinessException.notFound("用户不存在"));
    }

    private static AccessibleUser accessibleUser(ManagedUser user) {
        return new AccessibleUser(user.id(), user.deptId());
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
        List<ManagedRole> roles = userStore.findRolesByIds(roleIds);
        if (roles.size() != roleIds.size()) {
            throw BusinessException.notFound("角色不存在或已删除");
        }
        // 第二步：校验角色是否已禁用
        boolean hasDisabledRole = roles.stream()
                .anyMatch(role -> role.status() == null || role.status() != 1);
        if (hasDisabledRole) {
            throw BusinessException.stateInvalid("不能分配已禁用角色");
        }
        // 第三步：单一管理员保护
        assertSingleAdminUser(roles, targetUserId);
    }

    /**
     * 校验单一管理员约束：系统全局只允许一个未删除的 ADMIN 角色用户。
     */
    private void assertSingleAdminUser(List<ManagedRole> roles, UUID targetUserId) {
        // 第一步：检查待分配角色中是否包含 ADMIN
        ManagedRole adminRole = roles.stream()
                .filter(role -> RoleCodes.ADMIN.equals(role.roleCode()))
                .findFirst()
                .orElse(null);
        if (adminRole == null || adminRole.id() == null) {
            return;
        }
        // 第二步：若目标用户已经是管理员，允许重新分配（不抛异常）
        if (targetUserId != null) {
            boolean targetAlreadyAdmin = userStore.findRoleIdsByUserId(targetUserId).stream()
                    .anyMatch(roleId -> adminRole.id().equals(roleId));
            if (targetAlreadyAdmin) {
                return;
            }
        }
        // 第三步：检查数据库中是否已存在其他未删除的管理员
        List<UUID> adminUserIds = userStore.findUserIdsByRoleId(adminRole.id());
        if (adminUserIds.isEmpty()) {
            return;
        }
        boolean hasExistingAdmin = userStore.findUsersByIds(adminUserIds).stream()
                .filter(Objects::nonNull)
                .anyMatch(user -> user.deleted() == null || user.deleted() == 0);
        if (hasExistingAdmin) {
            throw BusinessException.duplicate("管理员账号已存在，不能新增或转配第二个管理员");
        }
    }

    /**
     * 将用户实体转换为视图对象（VO），同时查询该用户关联的角色 ID 列表填充。
     */
    private SysUserVO toVO(ManagedUser user) {
        SysUserVO vo = new SysUserVO();
        vo.setId(user.id());
        vo.setUsername(user.username());
        vo.setRealName(user.realName());
        vo.setPhone(user.phone());
        vo.setEmail(user.email());
        vo.setDeptId(user.deptId());
        vo.setStatus(user.status());
        vo.setForcePasswordChange(user.forcePasswordChange());
        vo.setLastLoginAt(user.lastLoginAt());
        vo.setCreateTime(user.createTime());
        // 查询并填充角色 ID 列表
        List<UUID> roleIds = userStore.findRoleIdsByUserId(user.id());
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
    private ManagedRole resolvePrimaryRole(List<UUID> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return null;
        }
        List<ManagedRole> roles = userStore.findRolesByIds(roleIds);
        if (roles == null || roles.isEmpty()) {
            return null;
        }
        return roles.get(0);
    }
}
