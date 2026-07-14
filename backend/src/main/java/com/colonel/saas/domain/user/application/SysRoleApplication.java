package com.colonel.saas.domain.user.application;

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
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 用户域角色管理应用服务。
 */
@Service
public class SysRoleApplication {

    private static final Set<String> SYSTEM_ROLE_CODES = Set.of(
            RoleCodes.ADMIN,
            RoleCodes.BIZ_LEADER,
            RoleCodes.BIZ_STAFF,
            RoleCodes.CHANNEL_LEADER,
            RoleCodes.CHANNEL_STAFF,
            RoleCodes.OPS_STAFF
    );

    private static final Pattern ROLE_CODE_ASCII_SLUG = Pattern.compile("^[a-z][a-z0-9_]*$");

    private final SysRoleMapper sysRoleMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final OperationLogService operationLogService;
    private final AuthorizationVersionApplicationService authorizationVersionService;

    public SysRoleApplication(
            SysRoleMapper sysRoleMapper,
            SysUserRoleMapper sysUserRoleMapper,
            OperationLogService operationLogService,
            AuthorizationVersionApplicationService authorizationVersionService) {
        this.sysRoleMapper = sysRoleMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
        this.operationLogService = operationLogService;
        this.authorizationVersionService = authorizationVersionService;
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

    @Transactional
    public SysRoleVO create(SysRoleCreateRequest request, UUID currentUserId) {
        String roleCode = resolveRoleCodeForCreate(request.roleCode(), request.roleName());
        ensureReservedRoleCodeNotUsedForCreate(roleCode);
        if (roleCode == null || roleCode.isBlank()) {
            roleCode = "custom_auto_" + System.currentTimeMillis();
        }
        ensureRoleCodeUnique(roleCode, null);

        SysRole role = new SysRole();
        role.setRoleCode(roleCode);
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

    @Transactional
    public SysRoleVO update(UUID id, SysRoleUpdateRequest request, UUID currentUserId) {
        SysRole role = requireRole(id);
        String roleCode = resolveRoleCodeForUpdate(role, request.roleCode());
        ensureSystemRoleCodeImmutable(role.getRoleCode(), roleCode);
        ensureRoleCodeUnique(roleCode, id);

        role.setRoleCode(roleCode);
        role.setRoleName(request.roleName());
        role.setDataScope(request.dataScope());
        role.setStatus(request.status());
        role.setRemark(request.remark());
        sysRoleMapper.updateById(role);
        authorizationVersionService.incrementUsersByRole(
                id,
                "ROLE_UPDATED",
                currentUserId);

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

    @Transactional
    public void delete(UUID id, UUID currentUserId) {
        SysRole role = requireRole(id);
        if (SYSTEM_ROLE_CODES.contains(role.getRoleCode())) {
            throw BusinessException.stateInvalid("系统内置角色不允许删除");
        }
        if (!sysUserRoleMapper.findByRoleId(id).isEmpty()) {
            throw BusinessException.stateInvalid("角色仍被用户使用，不能删除");
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
            throw BusinessException.notFound("角色不存在");
        }
        return role;
    }

    private void ensureRoleCodeUnique(String roleCode, UUID currentId) {
        if (roleCode == null || roleCode.isBlank()) {
            return;
        }
        sysRoleMapper.findByRoleCode(roleCode).ifPresent(exists -> {
            if (currentId == null || !exists.getId().equals(currentId)) {
                throw BusinessException.duplicate("角色编码已存在");
            }
        });
    }

    private static String normalizeRoleCode(String roleCode) {
        return roleCode == null ? "" : roleCode.trim().toLowerCase(Locale.ROOT);
    }

    private String resolveRoleCodeForCreate(String requestedCode, String roleName) {
        String normalized = normalizeRoleCode(requestedCode);
        if (!normalized.isBlank()) {
            return normalized;
        }
        return generateRoleCode(roleName);
    }

    private String resolveRoleCodeForUpdate(SysRole existing, String requestedCode) {
        String normalized = normalizeRoleCode(requestedCode);
        if (normalized.isBlank()) {
            return normalizeRoleCode(existing.getRoleCode());
        }
        return normalized;
    }

    private String generateRoleCode(String roleName) {
        String base = slugifyRoleName(roleName);
        if (base == null) {
            base = "custom";
        }
        String candidate = base;
        int suffix = 1;
        while (sysRoleMapper.findByRoleCode(candidate).isPresent()) {
            String suffixToken = "_" + suffix;
            int maxBaseLen = Math.max(1, 50 - suffixToken.length());
            candidate = base.substring(0, Math.min(base.length(), maxBaseLen)) + suffixToken;
            suffix++;
        }
        return candidate;
    }

    private String slugifyRoleName(String roleName) {
        if (roleName == null || roleName.isBlank()) {
            return null;
        }
        String slug = roleName.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        if (slug.isBlank() || !ROLE_CODE_ASCII_SLUG.matcher(slug).matches()) {
            return null;
        }
        return slug.length() > 50 ? slug.substring(0, 50) : slug;
    }

    private void ensureReservedRoleCodeNotUsedForCreate(String roleCode) {
        if (SYSTEM_ROLE_CODES.contains(roleCode)) {
            throw BusinessException.stateInvalid("不能使用系统预置角色编码，请使用其他编码");
        }
    }

    private void ensureSystemRoleCodeImmutable(String existingRoleCode, String requestedRoleCode) {
        String existing = normalizeRoleCode(existingRoleCode);
        if (SYSTEM_ROLE_CODES.contains(existing) && !existing.equals(requestedRoleCode)) {
            throw BusinessException.stateInvalid("系统内置角色编码不允许修改");
        }
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
