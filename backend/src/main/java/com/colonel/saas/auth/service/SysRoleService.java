package com.colonel.saas.auth.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.colonel.saas.auth.dto.SysRoleCreateRequest;
import com.colonel.saas.auth.dto.SysRoleUpdateRequest;
import com.colonel.saas.domain.user.application.SysRoleApplication;
import com.colonel.saas.vo.SysRoleVO;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * 系统角色管理服务（Legacy 委派壳）。
 *
 * <p>SysRoleService 的 DDD 化已完成（DDD-USER-MIGRATION-014，Issue #23）。
 * 本类仅保留 6 个 public 签名作为兼容入口，所有实现委派给用户域 DDD 应用服务
 * {@link SysRoleApplication}。Controller 与其它调用方无需改动。</p>
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
