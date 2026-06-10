package com.colonel.saas.domain.user.facade;

import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.domain.user.facade.dto.DepartmentOption;
import com.colonel.saas.dto.user.CheckPermissionRequest;
import com.colonel.saas.dto.user.CurrentUserResponse;
import com.colonel.saas.dto.user.UserDataScopeResponse;
import com.colonel.saas.dto.user.UserOptionResponse;
import com.colonel.saas.entity.SysDept;
import com.colonel.saas.service.SysDeptService;
import com.colonel.saas.service.UserDomainService;
import com.colonel.saas.service.UserMasterDataService;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * {@link UserDomainFacade} 遗留实现：委派现有用户域服务，零行为变更（DDD-USER-001）。
 */
@Service
public class LegacyUserDomainFacade implements UserDomainFacade {

    private final UserDomainService userDomainService;
    private final UserMasterDataService userMasterDataService;
    private final SysDeptService sysDeptService;

    public LegacyUserDomainFacade(
            UserDomainService userDomainService,
            UserMasterDataService userMasterDataService,
            SysDeptService sysDeptService) {
        this.userDomainService = userDomainService;
        this.userMasterDataService = userMasterDataService;
        this.sysDeptService = sysDeptService;
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
}
