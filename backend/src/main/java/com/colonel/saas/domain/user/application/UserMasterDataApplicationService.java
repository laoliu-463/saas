package com.colonel.saas.domain.user.application;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.domain.user.policy.CurrentUserPermissionPolicy;
import com.colonel.saas.dto.user.UserOptionResponse;
import com.colonel.saas.entity.SysRole;
import com.colonel.saas.entity.SysUser;
import com.colonel.saas.entity.SysUserRole;
import com.colonel.saas.mapper.SysRoleMapper;
import com.colonel.saas.mapper.SysUserMapper;
import com.colonel.saas.mapper.SysUserRoleMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 用户主数据应用服务（DDD-COMPLETE-100-USER-06）。
 *
 * <p>按角色维度提供用户下拉选项查询，用于渠道人员选择、招募人员选择和组成员列表。
 * 委派壳 {@code UserMasterDataService} 调到这里，业务逻辑在本类。</p>
 */
@Service
public class UserMasterDataApplicationService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 100;
    private static final List<String> CHANNEL_ROLE_CODES = List.of(RoleCodes.CHANNEL_LEADER, RoleCodes.CHANNEL_STAFF);
    private static final List<String> RECRUITER_ROLE_CODES = List.of(RoleCodes.BIZ_LEADER, RoleCodes.BIZ_STAFF);

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final CurrentUserPermissionPolicy currentUserPermissionPolicy;

    public UserMasterDataApplicationService(
            SysUserMapper sysUserMapper,
            SysRoleMapper sysRoleMapper,
            SysUserRoleMapper sysUserRoleMapper,
            CurrentUserPermissionPolicy currentUserPermissionPolicy) {
        this.sysUserMapper = sysUserMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
        this.currentUserPermissionPolicy = currentUserPermissionPolicy;
    }

    public List<UserOptionResponse> listChannels(String keyword, Integer limit) {
        return listByRoleCodes(CHANNEL_ROLE_CODES, keyword, limit);
    }

    public List<UserOptionResponse> listRecruiters(String keyword, Integer limit) {
        return listByRoleCodes(RECRUITER_ROLE_CODES, keyword, limit);
    }

    public List<UserOptionResponse> listGroupMembers(
            UUID requestedDeptId,
            UUID currentDeptId,
            List<String> roleCodes,
            String keyword,
            Integer limit) {
        UUID resolvedDeptId = canViewRequestedDept(roleCodes) && requestedDeptId != null
                ? requestedDeptId
                : currentDeptId;
        if (resolvedDeptId == null) {
            return Collections.emptyList();
        }
        QueryWrapper<SysUser> wrapper = new QueryWrapper<>();
        wrapper.eq("deleted", 0)
                .eq("status", 1)
                .eq("dept_id", resolvedDeptId);
        List<SysUser> users = sysUserMapper.selectList(wrapper);
        return toOptions(users, null, keyword, limit);
    }

    private List<UserOptionResponse> listByRoleCodes(Collection<String> roleCodes, String keyword, Integer limit) {
        List<String> normalizedRoleCodes = currentUserPermissionPolicy.normalizeRoleCodes(roleCodes);
        List<SysRole> roles = normalizedRoleCodes.stream()
                .map(sysRoleMapper::findByRoleCode)
                .flatMap(optional -> optional.stream())
                .filter(role -> role.getStatus() == null || role.getStatus() == 1)
                .toList();
        if (roles.isEmpty()) {
            return Collections.emptyList();
        }
        Set<UUID> roleIds = roles.stream()
                .map(SysRole::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<UUID> userIds = new LinkedHashSet<>();
        for (UUID roleId : roleIds) {
            sysUserRoleMapper.findByRoleId(roleId).stream()
                    .map(SysUserRole::getUserId)
                    .filter(Objects::nonNull)
                    .forEach(userIds::add);
        }
        if (userIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<SysUser> users = sysUserMapper.selectBatchIds(userIds);
        return toOptions(users, normalizedRoleCodes, keyword, limit);
    }

    private List<UserOptionResponse> toOptions(
            List<SysUser> users,
            Collection<String> allowedRoleCodes,
            String keyword,
            Integer limit) {
        if (users == null || users.isEmpty()) {
            return Collections.emptyList();
        }
        Map<UUID, List<String>> roleCodesByUser = loadRoleCodes(users);
        String normalizedKeyword = normalize(keyword);
        int safeLimit = safeLimit(limit);
        Set<String> allowed = allowedRoleCodes == null
                ? Collections.emptySet()
                : currentUserPermissionPolicy.normalizeRoleCodes(allowedRoleCodes).stream().collect(Collectors.toSet());
        return users.stream()
                .filter(this::isActive)
                .map(user -> new UserOptionResponse(
                        user.getId(),
                        user.getUsername(),
                        user.getRealName(),
                        user.getDeptId(),
                        roleCodesByUser.getOrDefault(user.getId(), Collections.emptyList()),
                        user.getChannelCode()
                ))
                .filter(option -> allowed.isEmpty() || option.roleCodes().stream().anyMatch(allowed::contains))
                .filter(option -> matchesKeyword(option, normalizedKeyword))
                .sorted(Comparator
                        .comparing((UserOptionResponse option) -> firstNonBlank(option.realName(), option.username()))
                        .thenComparing(UserOptionResponse::username, Comparator.nullsLast(String::compareTo)))
                .limit(safeLimit)
                .toList();
    }

    private Map<UUID, List<String>> loadRoleCodes(List<SysUser> users) {
        List<UUID> userIds = users.stream()
                .map(SysUser::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<SysUserRole> relations = sysUserRoleMapper.findByUserIds(userIds);
        if (relations == null || relations.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<UUID> roleIds = relations.stream()
                .map(SysUserRole::getRoleId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, SysRole> roleMap = roleIds.isEmpty()
                ? Collections.emptyMap()
                : sysRoleMapper.selectBatchIds(roleIds).stream()
                .collect(Collectors.toMap(SysRole::getId, role -> role, (left, right) -> left));
        Map<UUID, List<String>> result = relations.stream()
                .filter(relation -> relation.getUserId() != null && relation.getRoleId() != null)
                .collect(Collectors.groupingBy(
                        SysUserRole::getUserId,
                        Collectors.mapping(relation -> {
                            SysRole role = roleMap.get(relation.getRoleId());
                            return role == null ? null : role.getRoleCode();
                        }, Collectors.toList())
                ));
        result.replaceAll((userId, codes) -> codes.stream()
                .filter(StringUtils::hasText)
                .distinct()
                .toList());
        result.replaceAll((userId, codes) -> currentUserPermissionPolicy.normalizeRoleCodes(codes));
        return result;
    }

    private boolean isActive(SysUser user) {
        return user != null
                && user.getId() != null
                && (user.getDeleted() == null || user.getDeleted() == 0)
                && (user.getStatus() == null || user.getStatus() == 1);
    }

    private boolean matchesKeyword(UserOptionResponse option, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return true;
        }
        return normalize(option.username()).contains(keyword)
                || normalize(option.realName()).contains(keyword);
    }

    private boolean canViewRequestedDept(List<String> roleCodes) {
        return currentUserPermissionPolicy.hasAnyRole(roleCodes, RoleCodes.ADMIN);
    }

    private int safeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private String firstNonBlank(String first, String second) {
        if (StringUtils.hasText(first)) {
            return first;
        }
        return second == null ? "" : second;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}