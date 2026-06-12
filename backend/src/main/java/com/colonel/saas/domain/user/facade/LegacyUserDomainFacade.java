package com.colonel.saas.domain.user.facade;

import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.domain.user.facade.dto.DepartmentOption;
import com.colonel.saas.dto.user.CheckPermissionRequest;
import com.colonel.saas.dto.user.CurrentUserResponse;
import com.colonel.saas.dto.user.UserDataScopeResponse;
import com.colonel.saas.dto.user.UserOptionResponse;
import com.colonel.saas.entity.SysDept;
import com.colonel.saas.entity.SysUser;
import com.colonel.saas.mapper.SysUserMapper;
import com.colonel.saas.service.SysDeptService;
import com.colonel.saas.service.UserDomainService;
import com.colonel.saas.service.UserMasterDataService;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * {@link UserDomainFacade} 遗留实现：委派现有用户域服务，零行为变更（DDD-USER-001）。
 */
@Service
public class LegacyUserDomainFacade implements UserDomainFacade {

    private final UserDomainService userDomainService;
    private final UserMasterDataService userMasterDataService;
    private final SysDeptService sysDeptService;
    private final SysUserMapper sysUserMapper;

    public LegacyUserDomainFacade(
            UserDomainService userDomainService,
            UserMasterDataService userMasterDataService,
            SysDeptService sysDeptService,
            SysUserMapper sysUserMapper) {
        this.userDomainService = userDomainService;
        this.userMasterDataService = userMasterDataService;
        this.sysDeptService = sysDeptService;
        this.sysUserMapper = sysUserMapper;
    }

    @Override
    public UserDataScopeResponse resolveDataScope(UUID userId) {
        CurrentUserResponse current = userDomainService.getCurrentUser(userId, null, null, null);
        DataScope scope = DataScope.fromCode(current.dataScope());
        return userDomainService.getUserDataScope(userId, current.deptId(), scope);
    }

    @Override
    public List<UserOptionResponse> listChannels(String keyword) {
        return userMasterDataService.listChannels(keyword, null);
    }

    @Override
    public List<UserOptionResponse> listRecruiters(String keyword) {
        return userMasterDataService.listRecruiters(keyword, null);
    }

    @Override
    public List<DepartmentOption> listDepartments() {
        return sysDeptService.listActive().stream()
                .map(this::toDepartmentOption)
                .sorted(Comparator.comparing(DepartmentOption::deptName, Comparator.nullsLast(String::compareTo)))
                .toList();
    }

    @Override
    public List<UserOptionResponse> listGroupMembers(UUID groupId, UUID currentUserId) {
        CurrentUserResponse current = userDomainService.getCurrentUser(currentUserId, null, null, null);
        return userMasterDataService.listGroupMembers(
                groupId,
                current.deptId(),
                current.roleCodes(),
                null,
                null);
    }

    @Override
    public boolean hasPermission(UUID userId, String resource, String action) {
        CurrentUserResponse current = userDomainService.getCurrentUser(userId, null, null, null);
        return userDomainService.checkPermission(
                userId,
                current.roleCodes(),
                new CheckPermissionRequest(resource, action)
        ).allowed();
    }

    private DepartmentOption toDepartmentOption(SysDept dept) {
        return new DepartmentOption(
                dept.getId(),
                dept.getDeptCode(),
                dept.getDeptName(),
                dept.getDeptType()
        );
    }

    @Override
    public String getUserName(UUID userId) {
        if (userId == null) {
            return null;
        }
        SysUser user = sysUserMapper.selectById(userId);
        return user == null ? null : user.getRealName();
    }

    @Override
    public Map<UUID, String> loadUserNamesByIds(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        List<UUID> distinct = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return Map.of();
        }
        return sysUserMapper.selectBatchIds(distinct).stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(SysUser::getId, SysUser::getRealName, (a, b) -> a));
    }
    @Override
    public UserOptionResponse getUserById(UUID userId) {
        if (userId == null) {
            return null;
        }
        SysUser user = sysUserMapper.selectById(userId);
        return toUserOptionResponse(user);
    }

    @Override
    public List<UserOptionResponse> getUsersByIds(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<UUID> distinct = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return List.of();
        }
        return sysUserMapper.selectBatchIds(distinct).stream()
                .filter(Objects::nonNull)
                .map(this::toUserOptionResponse)
                .toList();
    }

    private UserOptionResponse toUserOptionResponse(SysUser user) {
        if (user == null) {
            return null;
        }
        return new UserOptionResponse(
                user.getId(),
                user.getUsername(),
                user.getRealName(),
                user.getDeptId(),
                List.of(), // Roles omitted for cross-domain basic usages
                user.getChannelCode()
        );
    }

    @Override
    public List<DepartmentOption> listDepartments(Collection<String> deptTypes) {
        if (deptTypes == null || deptTypes.isEmpty()) {
            return listDepartments();
        }
        return sysDeptService.listActive().stream()
                .filter(dept -> deptTypes.contains(dept.getDeptType()))
                .map(this::toDepartmentOption)
                .sorted(Comparator.comparing(DepartmentOption::deptName, Comparator.nullsLast(String::compareTo)))
                .toList();
    }
}
