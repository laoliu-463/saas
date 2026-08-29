package com.colonel.saas.domain.user.facade;

import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.domain.user.facade.dto.DepartmentOption;
import com.colonel.saas.domain.user.facade.dto.UserOwnershipReference;
import com.colonel.saas.domain.user.port.DepartmentOptionLookup;
import com.colonel.saas.domain.user.port.DepartmentOptionLookup.DepartmentEntry;
import com.colonel.saas.domain.user.port.UserBasicLookup;
import com.colonel.saas.domain.user.port.UserBasicLookup.BasicUser;
import com.colonel.saas.domain.user.port.UserRoleCodeLookup;
import com.colonel.saas.domain.user.policy.CurrentUserPermissionChecker;
import com.colonel.saas.dto.user.CheckPermissionRequest;
import com.colonel.saas.dto.user.CurrentUserResponse;
import com.colonel.saas.dto.user.UserDataScopeResponse;
import com.colonel.saas.dto.user.UserOptionResponse;
import com.colonel.saas.service.UserDomainService;
import com.colonel.saas.service.UserMasterDataService;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * {@link UserDomainFacade} 遗留实现：委派现有用户域服务，零行为变更（DDD-USER-001）。
 */
@Service
public class LegacyUserDomainFacade implements UserDomainFacade {

    private final UserDomainService userDomainService;
    private final UserMasterDataService userMasterDataService;
    private final DepartmentOptionLookup departmentOptionLookup;
    private final UserBasicLookup userBasicLookup;
    private final CurrentUserPermissionChecker currentUserPermissionChecker;
    private final UserRoleCodeLookup userRoleCodeLookup;

    public LegacyUserDomainFacade(
            UserDomainService userDomainService,
            UserMasterDataService userMasterDataService,
            DepartmentOptionLookup departmentOptionLookup,
            UserBasicLookup userBasicLookup,
            CurrentUserPermissionChecker currentUserPermissionChecker,
            UserRoleCodeLookup userRoleCodeLookup) {
        this.userDomainService = userDomainService;
        this.userMasterDataService = userMasterDataService;
        this.departmentOptionLookup = departmentOptionLookup;
        this.userBasicLookup = userBasicLookup;
        this.currentUserPermissionChecker = currentUserPermissionChecker;
        this.userRoleCodeLookup = userRoleCodeLookup;
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
        return departmentOptionLookup.listActive().stream()
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

    @Override
    public boolean hasAnyRole(Object roleCodes, String... expectedRoles) {
        return currentUserPermissionChecker.hasAnyRole(roleCodes, expectedRoles);
    }

    @Override
    public List<String> normalizeRoleCodes(Object roleCodes) {
        return currentUserPermissionChecker.normalizeRoleCodes(roleCodes);
    }

    private DepartmentOption toDepartmentOption(DepartmentEntry dept) {
        return new DepartmentOption(
                dept.id(),
                dept.deptCode(),
                dept.deptName(),
                dept.deptType()
        );
    }

    @Override
    public String getUserName(UUID userId) {
        if (userId == null) {
            return null;
        }
        return userBasicLookup.findById(userId)
                .map(BasicUser::realName)
                .orElse(null);
    }

    @Override
    public String getUsername(UUID userId) {
        if (userId == null) {
            return null;
        }
        return userBasicLookup.findById(userId)
                .map(BasicUser::username)
                .orElse(null);
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
        return userBasicLookup.findByIds(distinct).stream()
                .collect(Collectors.toMap(BasicUser::id, BasicUser::realName, (a, b) -> a));
    }

    @Override
    public Map<UUID, String> loadUserDisplayNamesByIds(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        List<UUID> distinct = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> names = new LinkedHashMap<>();
        for (BasicUser user : userBasicLookup.findByIds(distinct)) {
            if (user == null || user.id() == null) {
                continue;
            }
            String name = formatDisplayName(user);
            if (hasText(name)) {
                names.putIfAbsent(user.id(), name);
            }
        }
        return names;
    }

    @Override
    public Map<UUID, String> loadUserDisplayLabelsByIds(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        List<UUID> distinct = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return Map.of();
        }
        return userBasicLookup.findByIds(distinct).stream()
                .map(user -> Map.entry(user.id(), formatDisplayLabel(user)))
                .filter(entry -> hasText(entry.getValue()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a));
    }

    @Override
    public Map<UUID, String> loadUserChannelCodesByIds(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        List<UUID> distinct = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return Map.of();
        }
        return userBasicLookup.findByIds(distinct).stream()
                .filter(user -> user != null && user.id() != null && hasText(user.channelCode()))
                .collect(Collectors.toMap(
                        BasicUser::id,
                        user -> user.channelCode().trim(),
                        (left, right) -> left
                ));
    }

    @Override
    public Map<UUID, Set<String>> loadActiveRoleCodesByUserIds(Collection<UUID> ids) {
        return userRoleCodeLookup.findActiveRoleCodesByUserIds(ids);
    }

    @Override
    public Map<UUID, UserOwnershipReference> loadUserOwnershipReferencesByIds(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        List<UUID> distinct = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return Map.of();
        }
        Map<UUID, UserOwnershipReference> references = new LinkedHashMap<>();
        for (BasicUser user : userBasicLookup.findByIds(distinct)) {
            if (user == null || user.id() == null) {
                continue;
            }
            references.putIfAbsent(user.id(), new UserOwnershipReference(user.id(), user.deptId()));
        }
        return references;
    }

    @Override
    public UserOptionResponse getUserById(UUID userId) {
        if (userId == null) {
            return null;
        }
        return userBasicLookup.findById(userId)
                .map(this::toUserOptionResponse)
                .orElse(null);
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
        return userBasicLookup.findByIds(distinct).stream()
                .map(this::toUserOptionResponse)
                .toList();
    }

    private UserOptionResponse toUserOptionResponse(BasicUser user) {
        if (user == null) {
            return null;
        }
        return new UserOptionResponse(
                user.id(),
                user.username(),
                user.realName(),
                user.deptId(),
                List.of(), // Roles omitted for cross-domain basic usages
                user.channelCode()
        );
    }

    private static String formatDisplayLabel(BasicUser user) {
        if (user == null) {
            return "";
        }
        String realName = user.realName() == null ? "" : user.realName().trim();
        String username = user.username() == null ? "" : user.username().trim();
        if (hasText(realName) && hasText(username)) {
            return realName + " (" + username + ")";
        }
        if (hasText(realName)) {
            return realName;
        }
        if (hasText(username)) {
            return username;
        }
        return "";
    }

    private static String formatDisplayName(BasicUser user) {
        if (user == null) {
            return "";
        }
        String realName = user.realName() == null ? "" : user.realName().trim();
        if (hasText(realName)) {
            return realName;
        }
        return user.username() == null ? "" : user.username().trim();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    @Override
    public List<DepartmentOption> listDepartments(Collection<String> deptTypes) {
        if (deptTypes == null || deptTypes.isEmpty()) {
            return listDepartments();
        }
        return departmentOptionLookup.listActive().stream()
                .filter(dept -> deptTypes.contains(dept.deptType()))
                .map(this::toDepartmentOption)
                .sorted(Comparator.comparing(DepartmentOption::deptName, Comparator.nullsLast(String::compareTo)))
                .toList();
    }
}
