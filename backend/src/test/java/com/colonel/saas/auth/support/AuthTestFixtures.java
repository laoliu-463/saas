package com.colonel.saas.auth.support;

import com.colonel.saas.auth.dto.SysUserCreateRequest;
import com.colonel.saas.auth.dto.SysUserPageRequest;
import com.colonel.saas.auth.dto.SysUserUpdateRequest;

import java.util.List;
import java.util.UUID;

public final class AuthTestFixtures {

    private AuthTestFixtures() {
    }

    public static SysUserPageRequest pageRequest(
            Integer page,
            Integer size,
            String keyword,
            Integer status,
            UUID deptId) {
        return new SysUserPageRequest(page, size, keyword, status, deptId, null, null, null);
    }

    public static SysUserCreateRequest createRequest(
            String username,
            String password,
            String realName,
            String phone,
            String email,
            UUID deptId,
            List<UUID> roleIds) {
        return new SysUserCreateRequest(username, password, realName, phone, email, null, null, deptId, roleIds);
    }

    public static SysUserUpdateRequest updateRequest(
            String realName,
            String phone,
            String email,
            Integer status,
            UUID deptId) {
        return new SysUserUpdateRequest(realName, phone, email, status, null, null, deptId);
    }

    public static SysUserUpdateRequest updateOrgRequest(UUID parentDeptId, UUID groupId) {
        return new SysUserUpdateRequest(null, null, null, null, parentDeptId, groupId, null);
    }
}
