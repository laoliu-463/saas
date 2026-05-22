package com.colonel.saas.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.auth.dto.SysUserAssignRolesRequest;
import com.colonel.saas.auth.dto.SysUserCreateRequest;
import com.colonel.saas.auth.dto.SysUserPageRequest;
import com.colonel.saas.auth.dto.SysUserResetPasswordRequest;
import com.colonel.saas.auth.dto.SysUserUpdateRequest;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.entity.SysRole;
import com.colonel.saas.entity.SysUser;
import com.colonel.saas.entity.SysUserRole;
import com.colonel.saas.mapper.SysRoleMapper;
import com.colonel.saas.mapper.SysUserMapper;
import com.colonel.saas.mapper.SysUserRoleMapper;
import com.colonel.saas.service.OperationLogService;
import com.colonel.saas.vo.SysUserVO;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class SysUserService {

    private static final int MAX_CHANNEL_CODE_LEN = 16;
    private static final Set<String> ASSIGNABLE_BIZ_ROLE_CODES = Set.of(
            RoleCodes.BIZ_LEADER,
            RoleCodes.BIZ_STAFF,
            RoleCodes.CHANNEL_LEADER,
            RoleCodes.CHANNEL_STAFF
    );

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final OperationLogService operationLogService;

    public SysUserService(
            SysUserMapper sysUserMapper,
            SysRoleMapper sysRoleMapper,
            SysUserRoleMapper sysUserRoleMapper,
            PasswordEncoder passwordEncoder,
            OperationLogService operationLogService) {
        this.sysUserMapper = sysUserMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
        this.passwordEncoder = passwordEncoder;
        this.operationLogService = operationLogService;
    }

    public IPage<SysUserVO> findPage(
            UUID currentUserId,
            DataScope dataScope,
            SysUserPageRequest request) {
        Page<SysUserVO> page = new Page<>(request.pageNo(), request.pageSize());
        QueryWrapper<SysUser> wrapper = new QueryWrapper<>();
        IPage<SysUserVO> result = sysUserMapper.findPage(page, request, wrapper);
        fillRoleIds(result.getRecords());
        return result;
    }

    public List<SysUserVO> findAssignableUsers(String keyword, List<String> currentRoleCodes, UUID currentDeptId) {
        QueryWrapper<SysUser> wrapper = new QueryWrapper<>();
        wrapper.eq("deleted", 0)
                .eq("status", 1)
                .orderByAsc("real_name")
                .orderByAsc("username")
                .last("limit 20");
        if (keyword != null && !keyword.trim().isEmpty()) {
            String safeKeyword = keyword.trim();
            wrapper.and(query -> query.like("username", safeKeyword).or().like("real_name", safeKeyword));
        }

        List<SysUser> users = sysUserMapper.selectList(wrapper);
        if (users.isEmpty()) {
            return Collections.emptyList();
        }
        AssignableScope scope = resolveAssignableScope(currentRoleCodes, currentDeptId);
        Set<String> allowedRoleCodes = scope.allowedRoleCodes();
        if (allowedRoleCodes.isEmpty()) {
            return Collections.emptyList();
        }
        Map<UUID, List<SysUserRole>> relationMap = users.stream()
                .collect(Collectors.toMap(
                        SysUser::getId,
                        user -> sysUserRoleMapper.findByUserId(user.getId())
                ));
        Set<UUID> roleIds = relationMap.values().stream()
                .flatMap(List::stream)
                .map(SysUserRole::getRoleId)
                .filter(roleId -> roleId != null)
                .collect(Collectors.toSet());
        Map<UUID, SysRole> roleMap = roleIds.isEmpty()
                ? Collections.emptyMap()
                : sysRoleMapper.selectBatchIds(roleIds).stream()
                .collect(Collectors.toMap(SysRole::getId, role -> role));

        return users.stream()
                .filter(user -> scope.deptId() == null || scope.allowCrossDept() || Objects.equals(scope.deptId(), user.getDeptId()))
                .filter(user -> matchesAssignableRole(user.getId(), relationMap, roleMap, allowedRoleCodes))
                .map(this::toVO)
                .toList();
    }

    public void assertAssignableUser(UUID targetUserId, List<String> currentRoleCodes, UUID currentDeptId) {
        if (targetUserId == null) {
            throw BusinessException.param("负责人不能为空");
        }
        AssignableScope scope = resolveAssignableScope(currentRoleCodes, currentDeptId);
        if (scope.allowedRoleCodes().isEmpty()) {
            throw BusinessException.stateInvalid("当前角色不允许分配负责人");
        }
        SysUser targetUser = requireUser(targetUserId);
        if (scope.deptId() != null && !scope.allowCrossDept() && !Objects.equals(scope.deptId(), targetUser.getDeptId())) {
            throw BusinessException.forbidden("只能分配给本组招商下属");
        }

        List<SysUserRole> relations = sysUserRoleMapper.findByUserId(targetUserId);
        if (relations == null || relations.isEmpty()) {
            throw BusinessException.stateInvalid("目标负责人未配置可分配角色");
        }
        Set<UUID> roleIds = relations.stream()
                .map(SysUserRole::getRoleId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, SysRole> roleMap = roleIds.isEmpty()
                ? Collections.emptyMap()
                : sysRoleMapper.selectBatchIds(roleIds).stream()
                .collect(Collectors.toMap(SysRole::getId, role -> role));
        if (!matchesAssignableRole(targetUserId, Map.of(targetUserId, relations), roleMap, scope.allowedRoleCodes())) {
            throw BusinessException.forbidden("只能分配给符合规则的招商下属");
        }
    }

    public SysUserVO getById(UUID id, UUID currentUserId, DataScope dataScope) {
        SysUser user = requireUser(id);
        assertCanAccess(user, currentUserId, dataScope);
        return toVO(user);
    }

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
        user.setDeptId(request.deptId());
        user.setStatus(1);
        user.setChannelCode(generateUniqueChannelCode(request.username()));
        sysUserMapper.insert(user);

        replaceUserRoles(user.getId(), roleIds);
        operationLogService.recordSystemAction(
                currentUserId,
                "用户管理",
                "新建用户",
                "POST",
                "SysUser",
                user.getId() == null ? null : user.getId().toString(),
                user.getUsername(),
                "新建用户: " + user.getUsername()
        );
        return toVO(user);
    }

    public SysUserVO update(
            UUID id,
            SysUserUpdateRequest request,
            UUID currentUserId,
            DataScope dataScope) {
        SysUser user = requireUser(id);
        assertCanAccess(user, currentUserId, dataScope);

        user.setRealName(request.realName());
        user.setPhone(request.phone());
        user.setEmail(request.email());
        user.setStatus(request.status());
        sysUserMapper.updateById(user);
        operationLogService.recordSystemAction(
                currentUserId,
                "用户管理",
                "更新用户",
                "PUT",
                "SysUser",
                user.getId() == null ? null : user.getId().toString(),
                user.getUsername(),
                "更新用户: " + user.getUsername()
        );
        return toVO(user);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(UUID id, UUID currentUserId, DataScope dataScope) {
        if (id.equals(currentUserId)) {
            throw BusinessException.stateInvalid("不能删除当前登录用户");
        }
        SysUser user = requireUser(id);
        assertCanAccess(user, currentUserId, dataScope);
        sysUserRoleMapper.deleteByUserIdPhysical(id);
        sysUserMapper.softDeleteById(id);
        operationLogService.recordSystemAction(
                currentUserId,
                "用户管理",
                "删除用户",
                "DELETE",
                "SysUser",
                user.getId() == null ? null : user.getId().toString(),
                user.getUsername(),
                "删除用户: " + user.getUsername()
        );
    }

    public void resetPassword(
            UUID id,
            SysUserResetPasswordRequest request,
            UUID currentUserId,
            DataScope dataScope) {
        SysUser user = requireUser(id);
        assertCanAccess(user, currentUserId, dataScope);
        SysUser update = new SysUser();
        update.setId(id);
        update.setPassword(passwordEncoder.encode(request.newPassword()));
        sysUserMapper.updateById(update);
        operationLogService.recordSystemAction(
                currentUserId,
                "用户管理",
                "重置密码",
                "PUT",
                "SysUser",
                user.getId() == null ? null : user.getId().toString(),
                user.getUsername(),
                "重置用户密码: " + user.getUsername()
        );
    }

    @Transactional(rollbackFor = Exception.class)
    public void assignRoles(
            UUID id,
            SysUserAssignRolesRequest request,
            UUID currentUserId,
            DataScope dataScope) {
        SysUser user = requireUser(id);
        assertCanAccess(user, currentUserId, dataScope);
        List<UUID> roleIds = normalizeRoleIds(request.roleIds());
        validateRoleIds(roleIds, id);
        replaceUserRoles(id, roleIds);
        operationLogService.recordSystemAction(
                currentUserId,
                "用户管理",
                "分配角色",
                "PUT",
                "SysUser",
                user.getId() == null ? null : user.getId().toString(),
                user.getUsername(),
                "更新用户角色: " + user.getUsername()
        );
    }

    private SysUser requireUser(UUID id) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            throw BusinessException.notFound("用户不存在");
        }
        return user;
    }

    private void assertCanAccess(SysUser user, UUID currentUserId, DataScope dataScope) {
        if (dataScope == null) {
            throw BusinessException.forbidden("无法确认数据权限，拒绝访问");
        }
        if (dataScope == DataScope.PERSONAL && !user.getId().equals(currentUserId)) {
            throw BusinessException.forbidden("无权访问该用户");
        }
    }

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

    private void validateRoleIds(List<UUID> roleIds, UUID targetUserId) {
        if (roleIds.isEmpty()) {
            return;
        }
        List<SysRole> roles = sysRoleMapper.selectBatchIds(roleIds);
        if (roles.size() != roleIds.size()) {
            throw BusinessException.notFound("角色不存在或已删除");
        }
        boolean hasDisabledRole = roles.stream()
                .anyMatch(role -> role.getStatus() == null || role.getStatus() != 1);
        if (hasDisabledRole) {
            throw BusinessException.stateInvalid("不能分配已禁用角色");
        }
        assertSingleAdminUser(roles, targetUserId);
    }

    private void assertSingleAdminUser(List<SysRole> roles, UUID targetUserId) {
        SysRole adminRole = roles.stream()
                .filter(role -> RoleCodes.ADMIN.equals(role.getRoleCode()))
                .findFirst()
                .orElse(null);
        if (adminRole == null || adminRole.getId() == null) {
            return;
        }
        if (targetUserId != null) {
            boolean targetAlreadyAdmin = sysUserRoleMapper.findByUserId(targetUserId).stream()
                    .anyMatch(relation -> adminRole.getId().equals(relation.getRoleId()));
            if (targetAlreadyAdmin) {
                return;
            }
        }

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

    private void replaceUserRoles(UUID userId, List<UUID> roleIds) {
        sysUserRoleMapper.deleteByUserIdPhysical(userId);

        for (UUID roleId : roleIds) {
            SysUserRole relation = new SysUserRole();
            relation.setId(UUID.randomUUID());
            relation.setUserId(userId);
            relation.setRoleId(roleId);
            sysUserRoleMapper.insert(relation);
        }
    }

    private SysUserVO toVO(SysUser user) {
        SysUserVO vo = new SysUserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setPhone(user.getPhone());
        vo.setEmail(user.getEmail());
        vo.setDeptId(user.getDeptId());
        vo.setStatus(user.getStatus());
        vo.setLastLoginAt(user.getLastLoginAt());
        vo.setCreateTime(user.getCreateTime());

        List<UUID> roleIds = sysUserRoleMapper.findByUserId(user.getId()).stream()
                .map(SysUserRole::getRoleId)
                .collect(Collectors.toList());
        vo.setRoleIds(roleIds);
        return vo;
    }

    private void fillRoleIds(List<SysUserVO> users) {
        if (users == null || users.isEmpty()) {
            return;
        }
        List<UUID> userIds = users.stream()
                .map(SysUserVO::getId)
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());
        if (userIds.isEmpty()) {
            return;
        }
        Map<UUID, List<UUID>> roleMap = new HashMap<>();
        for (SysUserRole relation : sysUserRoleMapper.findByUserIds(userIds)) {
            roleMap.computeIfAbsent(relation.getUserId(), key -> new ArrayList<>()).add(relation.getRoleId());
        }
        for (SysUserVO user : users) {
            user.setRoleIds(roleMap.getOrDefault(user.getId(), Collections.emptyList()));
        }
    }

    private AssignableScope resolveAssignableScope(List<String> currentRoleCodes, UUID currentDeptId) {
        if (currentRoleCodes == null || currentRoleCodes.isEmpty()) {
            return AssignableScope.empty();
        }
        LinkedHashSet<String> normalized = currentRoleCodes.stream()
                .filter(code -> code != null && !code.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (normalized.contains(RoleCodes.ADMIN)) {
            return new AssignableScope(ASSIGNABLE_BIZ_ROLE_CODES, null, true);
        }
        if (normalized.contains(RoleCodes.BIZ_LEADER)) {
            return new AssignableScope(Set.of(RoleCodes.BIZ_STAFF), currentDeptId, false);
        }
        if (normalized.contains(RoleCodes.CHANNEL_LEADER)) {
            return new AssignableScope(Set.of(RoleCodes.CHANNEL_STAFF), currentDeptId, false);
        }
        if (normalized.contains(RoleCodes.COLONEL_LEADER)) {
            // 招商组长可将商品分配给招商专员（BIZ_STAFF）
            return new AssignableScope(Set.of(RoleCodes.BIZ_STAFF), currentDeptId, false);
        }
        return AssignableScope.empty();
    }

    private boolean matchesAssignableRole(
            UUID userId,
            Map<UUID, List<SysUserRole>> relationMap,
            Map<UUID, SysRole> roleMap,
            Set<String> allowedRoleCodes) {
        List<SysUserRole> relations = relationMap.getOrDefault(userId, Collections.emptyList());
        if (relations.isEmpty()) {
            return false;
        }
        for (SysUserRole relation : relations) {
            SysRole role = roleMap.get(relation.getRoleId());
            if (role == null || role.getStatus() == null || role.getStatus() != 1) {
                continue;
            }
            if (allowedRoleCodes.contains(role.getRoleCode())) {
                return true;
            }
        }
        return false;
    }

    private record AssignableScope(Set<String> allowedRoleCodes, UUID deptId, boolean allowCrossDept) {
        private static AssignableScope empty() {
            return new AssignableScope(Collections.emptySet(), null, false);
        }
    }

    private String generateUniqueChannelCode(String username) {
        String base = normalizeChannelCode(username);
        if (base.isBlank()) {
            base = "user";
        }
        if (!channelCodeExists(base)) {
            return base;
        }
        for (int i = 0; i < 8; i++) {
            String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 4);
            int maxBaseLen = MAX_CHANNEL_CODE_LEN - suffix.length();
            String candidate = (base.length() > maxBaseLen ? base.substring(0, maxBaseLen) : base) + suffix;
            if (!channelCodeExists(candidate)) {
                return candidate;
            }
        }
        throw BusinessException.conflict("生成用户渠道编码失败，请重试");
    }

    private String normalizeChannelCode(String username) {
        String normalized = username == null ? "" : username.trim().toLowerCase().replaceAll("[^a-z0-9_]", "");
        if (normalized.length() > MAX_CHANNEL_CODE_LEN) {
            return normalized.substring(0, MAX_CHANNEL_CODE_LEN);
        }
        return normalized;
    }

    private boolean channelCodeExists(String channelCode) {
        return sysUserMapper.existsByChannelCodeIncludingDeleted(channelCode);
    }
}
