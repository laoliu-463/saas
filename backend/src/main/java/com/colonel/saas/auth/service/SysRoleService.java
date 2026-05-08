package com.colonel.saas.auth.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.auth.dto.SysRoleCreateRequest;
import com.colonel.saas.auth.dto.SysRoleUpdateRequest;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.entity.SysRole;
import com.colonel.saas.mapper.SysRoleMapper;
import com.colonel.saas.mapper.SysUserRoleMapper;
import com.colonel.saas.service.OperationLogService;
import com.colonel.saas.vo.SysRoleVO;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.List;
import java.util.UUID;

@Service
public class SysRoleService {

    private static final Set<String> SYSTEM_ROLE_CODES = Set.of(
            RoleCodes.ADMIN,
            RoleCodes.BIZ_LEADER,
            RoleCodes.BIZ_STAFF,
            RoleCodes.CHANNEL_LEADER,
            RoleCodes.CHANNEL_STAFF,
            RoleCodes.OPS_STAFF,
            RoleCodes.COLONEL_LEADER
    );

    private final SysRoleMapper sysRoleMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final OperationLogService operationLogService;

    public SysRoleService(
            SysRoleMapper sysRoleMapper,
            SysUserRoleMapper sysUserRoleMapper,
            OperationLogService operationLogService) {
        this.sysRoleMapper = sysRoleMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
        this.operationLogService = operationLogService;
    }

    public IPage<SysRoleVO> findPage(long page, long size, String keyword, Integer status) {
        return sysRoleMapper.findPage(new Page<>(page, size), keyword, status);
    }

    public SysRoleVO getById(UUID id) {
        SysRole role = requireRole(id);
        return toVO(role);
    }

    public List<SysRoleVO> findAllEnabled() {
        return sysRoleMapper.findAll(1);
    }

    public SysRoleVO create(SysRoleCreateRequest request, UUID currentUserId) {
        ensureRoleCodeUnique(request.roleCode(), null);
        SysRole role = new SysRole();
        role.setRoleCode(request.roleCode());
        role.setRoleName(request.roleName());
        role.setDataScope(request.dataScope() == null ? 1 : request.dataScope());
        role.setStatus(request.status() == null ? 1 : request.status());
        role.setRemark(request.remark());
        sysRoleMapper.insert(role);
        operationLogService.recordSystemAction(
                currentUserId,
                "角色管理",
                "新建角色",
                "POST",
                "SysRole",
                role.getId() == null ? null : role.getId().toString(),
                role.getRoleCode(),
                "新建角色: " + role.getRoleCode()
        );
        return toVO(role);
    }

    public SysRoleVO update(UUID id, SysRoleUpdateRequest request, UUID currentUserId) {
        SysRole role = requireRole(id);
        ensureRoleCodeUnique(request.roleCode(), id);
        role.setRoleCode(request.roleCode());
        role.setRoleName(request.roleName());
        role.setDataScope(request.dataScope());
        role.setStatus(request.status());
        role.setRemark(request.remark());
        sysRoleMapper.updateById(role);
        operationLogService.recordSystemAction(
                currentUserId,
                "角色管理",
                "更新角色",
                "PUT",
                "SysRole",
                role.getId() == null ? null : role.getId().toString(),
                role.getRoleCode(),
                "更新角色: " + role.getRoleCode()
        );
        return toVO(role);
    }

    public void delete(UUID id, UUID currentUserId) {
        SysRole role = requireRole(id);
        if (SYSTEM_ROLE_CODES.contains(role.getRoleCode())) {
            throw new BusinessException("系统内置角色不允许删除");
        }
        if (!sysUserRoleMapper.findByRoleId(id).isEmpty()) {
            throw new BusinessException("角色仍被用户使用，不能删除");
        }
        sysRoleMapper.softDeleteById(id);
        operationLogService.recordSystemAction(
                currentUserId,
                "角色管理",
                "删除角色",
                "DELETE",
                "SysRole",
                role.getId() == null ? null : role.getId().toString(),
                role.getRoleCode(),
                "删除角色: " + role.getRoleCode()
        );
    }

    private SysRole requireRole(UUID id) {
        SysRole role = sysRoleMapper.selectById(id);
        if (role == null) {
            throw new BusinessException("角色不存在");
        }
        return role;
    }

    private void ensureRoleCodeUnique(String roleCode, UUID currentId) {
        if (roleCode == null || roleCode.isBlank()) {
            throw new BusinessException("角色编码不能为空");
        }
        sysRoleMapper.findByRoleCode(roleCode).ifPresent(exists -> {
            if (currentId == null || !exists.getId().equals(currentId)) {
                throw new BusinessException("角色编码已存在");
            }
        });
    }

    private SysRoleVO toVO(SysRole role) {
        SysRoleVO vo = new SysRoleVO();
        vo.setId(role.getId());
        vo.setRoleCode(role.getRoleCode());
        vo.setRoleName(role.getRoleName());
        vo.setDataScope(role.getDataScope());
        vo.setStatus(role.getStatus());
        vo.setRemark(role.getRemark());
        return vo;
    }
}
