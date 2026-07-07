package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.colonel.saas.constant.SysUserStatus;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.domain.user.policy.CurrentUserPermissionChecker;
import com.colonel.saas.domain.user.policy.CurrentUserPermissionPolicy;
import com.colonel.saas.domain.user.policy.CurrentUserPermissionPolicy.RolePermission;
import com.colonel.saas.domain.user.policy.UserCredentialPolicy;
import com.colonel.saas.domain.user.policy.UserCredentialPolicy.CredentialUser;
import com.colonel.saas.domain.user.policy.UserCredentialPolicy.PasswordChangeUpdate;
import com.colonel.saas.dto.user.ChangePasswordRequest;
import com.colonel.saas.dto.user.CheckPermissionRequest;
import com.colonel.saas.dto.user.CheckPermissionResponse;
import com.colonel.saas.dto.user.CurrentUserResponse;
import com.colonel.saas.dto.user.UserDataScopeResponse;
import com.colonel.saas.entity.SysRole;
import com.colonel.saas.entity.SysUser;
import com.colonel.saas.mapper.SysRoleMapper;
import com.colonel.saas.mapper.SysUserMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 用户域核心服务。
 * <p>
 * 提供当前用户信息查询、密码修改、数据范围解析和权限检查等用户域核心能力。
 * 负责从数据库加载用户、角色和权限数据，构建完整的用户上下文信息。
 * </p>
 *
 * <ul>
 *     <li>获取当前登录用户完整信息（{@link #getCurrentUser}）</li>
 *     <li>修改密码并激活待激活用户（{@link #changePassword}）</li>
 *     <li>获取用户数据范围（{@link #getUserDataScope}）</li>
 *     <li>检查用户操作权限（{@link #checkPermission}）</li>
 * </ul>
 *
 * <p><b>业务域：</b>用户域 — 核心服务</p>
 * <p><b>协作关系：</b></p>
 * <ul>
 *     <li>{@link SysUserMapper} — 用户数据访问</li>
 *     <li>{@link SysRoleMapper} — 角色数据访问</li>
 *     <li>{@link PasswordEncoder} — 密码编码与验证</li>
 *     <li>{@link OperationLogService} — 操作日志记录</li>
 * </ul>
 */
@Service
public class UserDomainService {

    /** 用户数据访问 Mapper */
    private final SysUserMapper sysUserMapper;
    /** 角色数据访问 Mapper */
    private final SysRoleMapper sysRoleMapper;
    /** 密码编码器（BCrypt） */
    private final PasswordEncoder passwordEncoder;
    /** 操作日志服务 */
    private final OperationLogService operationLogService;
    /** 当前用户权限与数据范围检查入口 */
    private final CurrentUserPermissionChecker currentUserPermissionChecker;
    /** 当前用户凭证策略 */
    private final UserCredentialPolicy userCredentialPolicy;

    public UserDomainService(
            SysUserMapper sysUserMapper,
            SysRoleMapper sysRoleMapper,
            PasswordEncoder passwordEncoder,
            OperationLogService operationLogService,
            CurrentUserPermissionChecker currentUserPermissionChecker,
            UserCredentialPolicy userCredentialPolicy) {
        this.sysUserMapper = sysUserMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.passwordEncoder = passwordEncoder;
        this.operationLogService = operationLogService;
        this.currentUserPermissionChecker = currentUserPermissionChecker;
        this.userCredentialPolicy = userCredentialPolicy;
    }

    /**
     * 获取当前登录用户的完整信息。
     * <p>处理流程：</p>
     * <ol>
     *     <li>校验用户是否存在且可登录</li>
     *     <li>加载用户激活状态的角色列表</li>
     *     <li>解析角色编码（DB 优先，请求回退）</li>
     *     <li>计算数据范围（ADMIN/OPS_STAFF 全量，其余取角色最大值）</li>
     *     <li>合并所有角色的菜单与操作权限</li>
     *     <li>构建并返回当前用户信息响应</li>
     * </ol>
     *
     * @param userId          用户 ID
     * @param deptId          请求上下文中的部门 ID（DB 无值时的回退）
     * @param requestScope    请求上下文中的数据范围（DB 无值时的回退）
     * @param requestRoleCodes 请求上下文中的角色编码列表（DB 无值时的回退）
     * @return 当前用户完整信息响应
     * @throws BusinessException 用户不存在或已停用
     */
    public CurrentUserResponse getCurrentUser(
            UUID userId,
            UUID deptId,
            DataScope requestScope,
            List<String> requestRoleCodes) {
        // 第一步：校验用户登录资格
        SysUser user = requireLoginEligibleUser(userId);
        // 第二步：加载激活角色并解析角色编码
        List<RolePermission> roles = activeRolePermissions(userId);
        List<String> roleCodes = currentUserPermissionChecker.resolveRoleCodes(roles, requestRoleCodes);
        // 第三步：计算数据范围
        int dataScopeCode = currentUserPermissionChecker.resolveDataScopeCode(roles, requestScope, roleCodes);
        // 第四步：构建响应
        return new CurrentUserResponse(
                user.getId(),
                user.getUsername(),
                user.getRealName(),
                user.getDeptId() == null ? deptId : user.getDeptId(),
                dataScopeCode,
                currentUserPermissionChecker.scopeName(dataScopeCode),
                roleCodes,
                currentUserPermissionChecker.mergePermissions(roles, dataScopeCode),
                user.getStatus() == null ? SysUserStatus.ACTIVE : user.getStatus(),
                Boolean.TRUE.equals(user.getForcePasswordChange())
        );
    }

    /**
     * 修改用户密码。
     * <p>处理流程：</p>
     * <ol>
     *     <li>校验用户登录资格</li>
     *     <li>验证原密码是否匹配</li>
     *     <li>编码新密码并更新用户记录</li>
     *     <li>若用户处于待激活状态，自动激活账号</li>
     *     <li>清除强制修改密码标记</li>
     *     <li>记录操作日志</li>
     * </ol>
     *
     * @param userId  用户 ID
     * @param request 密码修改请求（包含旧密码和新密码）
     * @throws BusinessException 原密码错误或用户状态异常
     */
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        // 第一步：校验用户登录资格
        SysUser user = requireLoginEligibleUser(userId);
        // 第二步：验证原密码
        userCredentialPolicy.assertOldPasswordMatched(
                passwordEncoder.matches(request.oldPassword(), user.getPassword()));
        // 第三步：更新密码
        CredentialUser credentialUser = credentialUser(user);
        PasswordChangeUpdate passwordChangeUpdate = userCredentialPolicy.buildPasswordChangeUpdate(
                userId,
                credentialUser,
                passwordEncoder.encode(request.newPassword()));
        sysUserMapper.updateById(passwordChangeEntity(passwordChangeUpdate));
        // 第四步：记录操作日志
        UserCredentialPolicy.PasswordChangeAudit audit = userCredentialPolicy.passwordChangeAudit(userId, credentialUser);
        operationLogService.recordSystemAction(
                audit.userId(),
                audit.domain(),
                audit.action(),
                audit.method(),
                audit.entityType(),
                audit.entityId(),
                audit.entityName(),
                audit.description()
        );
    }

    private static CredentialUser credentialUser(SysUser user) {
        return new CredentialUser(user.getStatus(), user.getUsername());
    }

    private static SysUser passwordChangeEntity(PasswordChangeUpdate passwordChangeUpdate) {
        SysUser update = new SysUser();
        update.setId(passwordChangeUpdate.userId());
        update.setPassword(passwordChangeUpdate.encodedPassword());
        update.setStatus(passwordChangeUpdate.status());
        update.setForcePasswordChange(passwordChangeUpdate.forcePasswordChange());
        return update;
    }

    /**
     * 获取用户数据范围。
     * <p>处理流程：</p>
     * <ol>
     *     <li>ALL 范围：返回空用户列表，表示可查看全部数据</li>
     *     <li>DEPT 范围：查询同部门下所有活跃用户 ID 列表</li>
     *     <li>PERSONAL 范围：仅包含当前用户自身 ID</li>
     * </ol>
     *
     * @param userId    当前用户 ID
     * @param deptId    当前用户所属部门 ID
     * @param dataScope 数据范围枚举（null 时默认为 PERSONAL）
     * @return 数据范围响应（包含范围类型和可访问的用户 ID 列表）
     */
    public UserDataScopeResponse getUserDataScope(UUID userId, UUID deptId, DataScope dataScope) {
        DataScope resolved = dataScope == null ? DataScope.PERSONAL : dataScope;
        if (resolved == DataScope.ALL) {
            // ALL 范围：不指定具体用户列表，表示全部可见
            return new UserDataScopeResponse("all", DataScope.ALL.getCode(), Collections.emptyList());
        }
        if (resolved == DataScope.DEPT) {
            if (deptId == null) {
                // 无部门时回退为仅个人可见
                return new UserDataScopeResponse("group", DataScope.DEPT.getCode(), List.of(userId));
            }
            // 查询同部门下所有活跃用户的 ID
            QueryWrapper<SysUser> wrapper = new QueryWrapper<>();
            wrapper.eq("deleted", 0)
                    .eq("status", 1)
                    .eq("dept_id", deptId);
            List<UUID> userIds = sysUserMapper.selectList(wrapper).stream()
                    .map(SysUser::getId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            return new UserDataScopeResponse("group", DataScope.DEPT.getCode(), userIds);
        }
        // PERSONAL 范围：仅自身
        return new UserDataScopeResponse("self", DataScope.PERSONAL.getCode(), List.of(userId));
    }

    /**
     * 检查用户是否具有指定资源的操作权限。
     * <p>处理流程：</p>
     * <ol>
     *     <li>标准化资源和操作名称</li>
     *     <li>ADMIN 角色直接返回允许</li>
     *     <li>遍历用户所有激活角色的权限配置，检查是否包含目标操作</li>
     * </ol>
     *
     * @param userId          用户 ID
     * @param requestRoleCodes 请求上下文中的角色编码列表
     * @param request         权限检查请求（资源 + 操作）
     * @return 权限检查结果（资源、操作、是否允许）
     */
    public CheckPermissionResponse checkPermission(
            UUID userId,
            List<String> requestRoleCodes,
            CheckPermissionRequest request) {
        return currentUserPermissionChecker.checkPermission(requestRoleCodes, activeRolePermissions(userId), request);
    }

    /**
     * 校验用户是否具备登录资格。
     * <p>依次检查用户 ID 非空、用户存在且未删除、用户状态可登录。</p>
     *
     * @param userId 用户 ID
     * @return 用户实体
     * @throws BusinessException 用户不存在或已停用
     */
    private SysUser requireLoginEligibleUser(UUID userId) {
        if (userId == null) {
            throw BusinessException.forbidden("无法识别当前用户");
        }
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null || user.getDeleted() != null && user.getDeleted() != 0) {
            throw BusinessException.notFound("用户不存在");
        }
        if (!SysUserStatus.canLogin(user.getStatus())) {
            throw BusinessException.forbidden("账号已停用");
        }
        return user;
    }

    /**
     * 加载用户的激活状态角色列表。
     *
     * @param userId 用户 ID
     * @return 激活角色列表（状态为 null 或 1 的角色）
     */
    private List<SysRole> activeRoles(UUID userId) {
        if (userId == null) {
            return Collections.emptyList();
        }
        List<SysRole> roles = sysRoleMapper.findByUserId(userId);
        if (roles == null || roles.isEmpty()) {
            return Collections.emptyList();
        }
        // 过滤出激活状态的角色
        return roles.stream()
                .filter(role -> role != null && (role.getStatus() == null || role.getStatus() == 1))
                .toList();
    }

    private List<RolePermission> activeRolePermissions(UUID userId) {
        return activeRoles(userId).stream()
                .map(role -> new RolePermission(role.getRoleCode(), role.getDataScope(), role.getPermissions()))
                .toList();
    }

}
