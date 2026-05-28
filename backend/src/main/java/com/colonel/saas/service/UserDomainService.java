package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.colonel.saas.constant.SysUserStatus;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.constant.RoleCodes;
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
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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

    public UserDomainService(
            SysUserMapper sysUserMapper,
            SysRoleMapper sysRoleMapper,
            PasswordEncoder passwordEncoder,
            OperationLogService operationLogService) {
        this.sysUserMapper = sysUserMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.passwordEncoder = passwordEncoder;
        this.operationLogService = operationLogService;
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
        List<SysRole> roles = activeRoles(userId);
        List<String> roleCodes = resolveRoleCodes(roles, requestRoleCodes);
        // 第三步：计算数据范围
        int dataScopeCode = resolveDataScopeCode(roles, requestScope, roleCodes);
        // 第四步：构建响应
        return new CurrentUserResponse(
                user.getId(),
                user.getUsername(),
                user.getRealName(),
                user.getDeptId() == null ? deptId : user.getDeptId(),
                dataScopeCode,
                scopeName(dataScopeCode),
                roleCodes,
                mergePermissions(roles, dataScopeCode),
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
        if (!passwordEncoder.matches(request.oldPassword(), user.getPassword())) {
            throw BusinessException.forbidden("原密码错误");
        }
        // 第三步：更新密码
        SysUser update = new SysUser();
        update.setId(userId);
        update.setPassword(passwordEncoder.encode(request.newPassword()));
        // 第四步：待激活用户自动激活
        if (SysUserStatus.isPendingActivation(user.getStatus())) {
            update.setStatus(SysUserStatus.ACTIVE);
        }
        // 第五步：清除强制修改密码标记
        update.setForcePasswordChange(false);
        sysUserMapper.updateById(update);
        // 第六步：记录操作日志
        operationLogService.recordSystemAction(
                userId,
                "用户域",
                "修改密码",
                "PUT",
                "SysUser",
                userId.toString(),
                user.getUsername(),
                "用户修改自己的登录密码"
        );
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
        String resource = normalizeKey(request.resource());
        String action = normalizeKey(request.action());
        // ADMIN 角色直接放行
        if (hasAdminRole(requestRoleCodes)) {
            return new CheckPermissionResponse(resource, action, true);
        }
        // 遍历角色权限检查
        boolean allowed = permissionAllows(activeRoles(userId), resource, action);
        return new CheckPermissionResponse(resource, action, allowed);
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

    /**
     * 解析角色编码列表。
     * <p>DB 角色数据优先；DB 无角色时回退到请求上下文中的角色编码。</p>
     *
     * @param roles            DB 加载的角色列表
     * @param requestRoleCodes 请求上下文中的角色编码列表
     * @return 去重后的角色编码列表
     */
    private List<String> resolveRoleCodes(List<SysRole> roles, List<String> requestRoleCodes) {
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        if (roles != null) {
            roles.stream()
                    .map(SysRole::getRoleCode)
                    .filter(StringUtils::hasText)
                    .forEach(codes::add);
        }
        // DB 无角色时，回退到请求上下文
        if (codes.isEmpty() && requestRoleCodes != null) {
            requestRoleCodes.stream()
                    .filter(StringUtils::hasText)
                    .forEach(codes::add);
        }
        return new ArrayList<>(codes);
    }

    /**
     * 解析数据范围编码。
     * <p>ADMIN 或 OPS_STAFF 角色强制为 ALL 范围；其余取所有角色中最大的 dataScope 值。</p>
     *
     * @param roles        DB 加载的角色列表
     * @param requestScope 请求上下文中的数据范围
     * @param roleCodes    解析后的角色编码列表
     * @return 数据范围编码
     */
    private int resolveDataScopeCode(List<SysRole> roles, DataScope requestScope, List<String> roleCodes) {
        // ADMIN 和 OPS_STAFF 强制为全量范围
        if (roleCodes != null && (roleCodes.contains(RoleCodes.ADMIN) || roleCodes.contains(RoleCodes.OPS_STAFF))) {
            return DataScope.ALL.getCode();
        }
        if (roles != null && !roles.isEmpty()) {
            // 取所有角色中最大的数据范围值
            return roles.stream()
                    .map(SysRole::getDataScope)
                    .filter(scope -> scope != null && scope > 0)
                    .max(Integer::compareTo)
                    .orElse(requestScope == null ? DataScope.PERSONAL.getCode() : requestScope.getCode());
        }
        return requestScope == null ? DataScope.PERSONAL.getCode() : requestScope.getCode();
    }

    /**
     * 合并所有角色的权限（菜单 + 操作），生成统一权限映射。
     * <p>遍历每个角色的 permissions JSON，聚合 menus 列表和 operations 映射，
     * 最终返回包含 menus、operations、data_scope 三个键的权限 Map。</p>
     *
     * @param roles        角色列表
     * @param dataScopeCode 数据范围编码
     * @return 合并后的权限映射
     */
    private Map<String, Object> mergePermissions(List<SysRole> roles, int dataScopeCode) {
        LinkedHashSet<String> menus = new LinkedHashSet<>();
        Map<String, LinkedHashSet<String>> operations = new LinkedHashMap<>();
        if (roles != null) {
            for (SysRole role : roles) {
                Map<String, Object> permissions = role.getPermissions();
                if (permissions == null || permissions.isEmpty()) {
                    continue;
                }
                // 聚合菜单权限
                addValues(menus, permissions.get("menus"));
                // 聚合操作权限
                mergeOperations(operations, permissions.get("operations"));
            }
        }
        // 构建结果 Map
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("menus", new ArrayList<>(menus));
        Map<String, List<String>> operationResult = new LinkedHashMap<>();
        operations.forEach((resource, actions) -> operationResult.put(resource, new ArrayList<>(actions)));
        result.put("operations", operationResult);
        result.put("data_scope", scopeName(dataScopeCode));
        return result;
    }

    /**
     * 合并单个角色的操作权限到目标映射。
     *
     * @param target        目标操作权限映射（resource -> actions）
     * @param rawOperations 原始操作权限对象（预期为 Map 结构）
     */
    private void mergeOperations(Map<String, LinkedHashSet<String>> target, Object rawOperations) {
        if (!(rawOperations instanceof Map<?, ?> rawMap)) {
            return;
        }
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            String resource = normalizeKey(Objects.toString(entry.getKey(), ""));
            if (resource.isBlank()) {
                continue;
            }
            LinkedHashSet<String> actions = target.computeIfAbsent(resource, key -> new LinkedHashSet<>());
            addValues(actions, entry.getValue());
        }
    }

    /**
     * 将原始值（集合或单值）标准化后添加到目标集合。
     *
     * @param target 目标字符串集合
     * @param raw    原始值（Collection 或单个值）
     */
    private void addValues(Set<String> target, Object raw) {
        if (raw instanceof Collection<?> collection) {
            for (Object value : collection) {
                String text = normalizeKey(Objects.toString(value, ""));
                if (!text.isBlank()) {
                    target.add(text);
                }
            }
            return;
        }
        String text = normalizeKey(Objects.toString(raw, ""));
        if (!text.isBlank()) {
            target.add(text);
        }
    }

    /**
     * 检查用户角色列表中是否存在允许目标操作的权限。
     *
     * @param roles    角色列表
     * @param resource 资源名称
     * @param action   操作名称
     * @return true 表示至少有一个角色允许该操作
     */
    private boolean permissionAllows(List<SysRole> roles, String resource, String action) {
        if (!StringUtils.hasText(resource) || !StringUtils.hasText(action)) {
            return false;
        }
        for (SysRole role : roles) {
            Map<String, Object> permissions = role.getPermissions();
            if (permissions == null || permissions.isEmpty()) {
                continue;
            }
            if (operationAllows(permissions.get("operations"), resource, action)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查操作权限映射中是否包含目标资源的指定操作。
     * <p>同时检查精确匹配和通配符 (*) 匹配。</p>
     *
     * @param rawOperations 原始操作权限对象
     * @param resource      资源名称
     * @param action        操作名称
     * @return true 表示允许
     */
    private boolean operationAllows(Object rawOperations, String resource, String action) {
        if (!(rawOperations instanceof Map<?, ?> rawMap)) {
            return false;
        }
        // 检查精确资源匹配和通配符资源匹配
        return actionAllowed(rawMap.get(resource), action)
                || actionAllowed(rawMap.get("*"), action);
    }

    /**
     * 检查操作列表中是否包含目标操作或通配符。
     *
     * @param rawActions 原始操作值
     * @param action     目标操作名称
     * @return true 表示允许
     */
    private boolean actionAllowed(Object rawActions, String action) {
        LinkedHashSet<String> actions = new LinkedHashSet<>();
        addValues(actions, rawActions);
        // 通配符 * 或精确匹配均表示允许
        return actions.contains("*") || actions.contains(action);
    }

    /**
     * 判断角色编码列表中是否包含 ADMIN 角色。
     *
     * @param roleCodes 角色编码列表
     * @return true 表示包含 ADMIN
     */
    private boolean hasAdminRole(List<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return false;
        }
        return roleCodes.stream().anyMatch(RoleCodes.ADMIN::equals);
    }

    /**
     * 将数据范围编码转换为可读名称。
     *
     * @param code 数据范围编码
     * @return "all" / "group" / "self"
     */
    private String scopeName(int code) {
        if (code == DataScope.ALL.getCode()) {
            return "all";
        }
        if (code == DataScope.DEPT.getCode()) {
            return "group";
        }
        return "self";
    }

    /**
     * 标准化字符串键：trim 后转小写，null 返回空字符串。
     *
     * @param value 原始字符串
     * @return 标准化后的字符串
     */
    private String normalizeKey(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
