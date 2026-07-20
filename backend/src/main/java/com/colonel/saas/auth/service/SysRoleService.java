package com.colonel.saas.auth.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.colonel.saas.auth.dto.SysRoleCreateRequest;
import com.colonel.saas.auth.dto.SysRoleUpdateRequest;
import com.colonel.saas.domain.user.application.SysRoleApplication;
import com.colonel.saas.vo.SysRoleVO;
import com.colonel.saas.vo.AuthorizationPermissionVO;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * 角色服务兼容入口；业务规则收口到用户域应用服务。
 */
@Service
public class SysRoleService {

    private final SysRoleApplication sysRoleApplication;

    public SysRoleService(SysRoleApplication sysRoleApplication) {
        this.sysRoleApplication = sysRoleApplication;
    }

    public IPage<SysRoleVO> findPage(long page, long size, String keyword, Integer status) {
        return sysRoleApplication.findPage(page, size, keyword, status);
    }

    public SysRoleVO getById(UUID id) {
        return sysRoleApplication.getById(id);
    }

    public List<SysRoleVO> findAllEnabled() {
        return sysRoleApplication.findAllEnabled();
    }

    public List<AuthorizationPermissionVO> findPermissionCatalog() {
        return sysRoleApplication.findPermissionCatalog();
    }

    public List<String> findPermissionCodes(UUID roleId) {
        return sysRoleApplication.findPermissionCodes(roleId);
    }

    public void assignPermissions(UUID roleId, List<String> permissionCodes, UUID currentUserId) {
        sysRoleApplication.assignPermissions(roleId, permissionCodes, currentUserId);
    }

    public SysRoleVO create(SysRoleCreateRequest request, UUID currentUserId) {
        return sysRoleApplication.create(request, currentUserId);
    }

    public SysRoleVO update(UUID id, SysRoleUpdateRequest request, UUID currentUserId) {
        return sysRoleApplication.update(id, request, currentUserId);
    }

    public void delete(UUID id, UUID currentUserId) {
        sysRoleApplication.delete(id, currentUserId);
    }
}
