package com.colonel.saas.auth.service;

import com.colonel.saas.auth.dto.SysMenuCreateRequest;
import com.colonel.saas.auth.dto.SysMenuUpdateRequest;
import com.colonel.saas.domain.user.application.SysMenuApplication;
import com.colonel.saas.vo.SysMenuVO;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * 系统菜单管理服务（Legacy 委派壳）。
 *
 * <p>SysMenuService 的 DDD 化已完成（DDD-USER-MIGRATION-013，Issue #22）。
 * 本类仅保留 9 个 public 签名作为兼容入口，所有实现委派给用户域 DDD 应用服务
 * {@link SysMenuApplication}。Controller 与其它调用方无需改动。</p>
 *
 * @see SysMenuApplication DDD 应用服务（实现所在）
 */
@Service
public class SysMenuService {

    private final SysMenuApplication sysMenuApplication;

    public SysMenuService(SysMenuApplication sysMenuApplication) {
        this.sysMenuApplication = sysMenuApplication;
    }

    public List<SysMenuVO> findAllTree(Integer status) {
        return sysMenuApplication.findAllTree(status);
    }

    public List<SysMenuVO> findUserTreeByUserId(UUID userId, Integer status) {
        return sysMenuApplication.findUserTreeByUserId(userId, status);
    }

    public List<SysMenuVO> findUserTree(UUID userId, List<UUID> roleIds, Integer status) {
        return sysMenuApplication.findUserTree(userId, roleIds, status);
    }

    public List<UUID> getMenuIdsByRoleId(UUID roleId) {
        return sysMenuApplication.getMenuIdsByRoleId(roleId);
    }

    public void assignMenusToRole(UUID roleId, List<UUID> menuIds, UUID currentUserId) {
        sysMenuApplication.assignMenusToRole(roleId, menuIds, currentUserId);
    }

    public SysMenuVO create(SysMenuCreateRequest request, UUID currentUserId) {
        return sysMenuApplication.create(request, currentUserId);
    }

    public SysMenuVO update(UUID id, SysMenuUpdateRequest request, UUID currentUserId) {
        return sysMenuApplication.update(id, request, currentUserId);
    }

    public void delete(UUID id, UUID currentUserId) {
        sysMenuApplication.delete(id, currentUserId);
    }

    public List<SysMenuVO> buildTree(List<SysMenuVO> list) {
        return sysMenuApplication.buildTree(list);
    }
}
