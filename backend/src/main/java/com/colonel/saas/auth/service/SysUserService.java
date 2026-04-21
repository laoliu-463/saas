package com.colonel.saas.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.auth.dto.SysUserAssignRolesRequest;
import com.colonel.saas.auth.dto.SysUserCreateRequest;
import com.colonel.saas.auth.dto.SysUserPageRequest;
import com.colonel.saas.auth.dto.SysUserResetPasswordRequest;
import com.colonel.saas.auth.dto.SysUserUpdateRequest;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.entity.SysRole;
import com.colonel.saas.entity.SysUser;
import com.colonel.saas.entity.SysUserRole;
import com.colonel.saas.mapper.SysRoleMapper;
import com.colonel.saas.mapper.SysUserMapper;
import com.colonel.saas.mapper.SysUserRoleMapper;
import com.colonel.saas.vo.SysUserVO;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class SysUserService {

    private static final int MAX_CHANNEL_CODE_LEN = 16;

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final PasswordEncoder passwordEncoder;

    public SysUserService(
            SysUserMapper sysUserMapper,
            SysRoleMapper sysRoleMapper,
            SysUserRoleMapper sysUserRoleMapper,
            PasswordEncoder passwordEncoder) {
        this.sysUserMapper = sysUserMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
        this.passwordEncoder = passwordEncoder;
    }

    public IPage<SysUserVO> findPage(
            UUID currentUserId,
            DataScope dataScope,
            SysUserPageRequest request) {
        Page<SysUserVO> page = new Page<>(request.pageNo(), request.pageSize());
        QueryWrapper<SysUser> wrapper = new QueryWrapper<>();
        IPage<SysUserVO> result = sysUserMapper.findPage(page, request, wrapper);
        fillRoleIds(result.getRecords());
        return result;
    }

    public SysUserVO getById(UUID id, UUID currentUserId, DataScope dataScope) {
        SysUser user = requireUser(id);
        assertCanAccess(user, currentUserId, dataScope);
        return toVO(user);
    }

    @Transactional(rollbackFor = Exception.class)
    public SysUserVO create(SysUserCreateRequest request) {
        sysUserMapper.findByUsername(request.username()).ifPresent(existing -> {
            throw new BusinessException("用户名已存在");
        });

        List<UUID> roleIds = normalizeRoleIds(request.roleIds());
        validateRoleIds(roleIds);

        SysUser user = new SysUser();
        user.setUsername(request.username());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRealName(request.realName());
        user.setDeptId(request.deptId());
        user.setStatus(1);
        user.setChannelCode(generateUniqueChannelCode(request.username()));
        sysUserMapper.insert(user);

        replaceUserRoles(user.getId(), roleIds);
        return toVO(user);
    }

    public SysUserVO update(
            UUID id,
            SysUserUpdateRequest request,
            UUID currentUserId,
            DataScope dataScope) {
        SysUser user = requireUser(id);
        assertCanAccess(user, currentUserId, dataScope);

        user.setRealName(request.realName());
        user.setPhone(request.phone());
        user.setEmail(request.email());
        user.setStatus(request.status());
        sysUserMapper.updateById(user);
        return toVO(user);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(UUID id, UUID currentUserId, DataScope dataScope) {
        if (id.equals(currentUserId)) {
            throw new BusinessException("不能删除当前登录用户");
        }
        SysUser user = requireUser(id);
        assertCanAccess(user, currentUserId, dataScope);
        LambdaUpdateWrapper<SysUserRole> deleteWrapper = new LambdaUpdateWrapper<>();
        deleteWrapper.eq(SysUserRole::getUserId, id);
        sysUserRoleMapper.delete(deleteWrapper);
        sysUserMapper.deleteById(id);
    }

    public void resetPassword(
            UUID id,
            SysUserResetPasswordRequest request,
            UUID currentUserId,
            DataScope dataScope) {
        SysUser user = requireUser(id);
        assertCanAccess(user, currentUserId, dataScope);
        SysUser update = new SysUser();
        update.setId(id);
        update.setPassword(passwordEncoder.encode(request.newPassword()));
        sysUserMapper.updateById(update);
    }

    @Transactional(rollbackFor = Exception.class)
    public void assignRoles(
            UUID id,
            SysUserAssignRolesRequest request,
            UUID currentUserId,
            DataScope dataScope) {
        SysUser user = requireUser(id);
        assertCanAccess(user, currentUserId, dataScope);
        List<UUID> roleIds = normalizeRoleIds(request.roleIds());
        validateRoleIds(roleIds);
        replaceUserRoles(id, roleIds);
    }

    private SysUser requireUser(UUID id) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return user;
    }

    private void assertCanAccess(SysUser user, UUID currentUserId, DataScope dataScope) {
        if (dataScope == null) {
            throw new BusinessException("无法确认数据权限，拒绝访问");
        }
        if (dataScope == DataScope.PERSONAL && !user.getId().equals(currentUserId)) {
            throw new BusinessException("无权访问该用户");
        }
    }

    private List<UUID> normalizeRoleIds(List<UUID> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return Collections.emptyList();
        }
        Set<UUID> distinct = new LinkedHashSet<>();
        for (UUID roleId : roleIds) {
            if (roleId != null) {
                distinct.add(roleId);
            }
        }
        return new ArrayList<>(distinct);
    }

    private void validateRoleIds(List<UUID> roleIds) {
        if (roleIds.isEmpty()) {
            return;
        }
        List<SysRole> roles = sysRoleMapper.selectBatchIds(roleIds);
        if (roles.size() != roleIds.size()) {
            throw new BusinessException("角色不存在或已删除");
        }
        boolean hasDisabledRole = roles.stream()
                .anyMatch(role -> role.getStatus() == null || role.getStatus() != 1);
        if (hasDisabledRole) {
            throw new BusinessException("不能分配已禁用角色");
        }
    }

    private void replaceUserRoles(UUID userId, List<UUID> roleIds) {
        LambdaUpdateWrapper<SysUserRole> deleteWrapper = new LambdaUpdateWrapper<>();
        deleteWrapper.eq(SysUserRole::getUserId, userId);
        sysUserRoleMapper.delete(deleteWrapper);

        for (UUID roleId : roleIds) {
            SysUserRole relation = new SysUserRole();
            relation.setUserId(userId);
            relation.setRoleId(roleId);
            sysUserRoleMapper.insert(relation);
        }
    }

    private SysUserVO toVO(SysUser user) {
        SysUserVO vo = new SysUserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setPhone(user.getPhone());
        vo.setEmail(user.getEmail());
        vo.setDeptId(user.getDeptId());
        vo.setStatus(user.getStatus());
        vo.setLastLoginAt(user.getLastLoginAt());
        vo.setCreateTime(user.getCreateTime());

        List<UUID> roleIds = sysUserRoleMapper.findByUserId(user.getId()).stream()
                .map(SysUserRole::getRoleId)
                .collect(Collectors.toList());
        vo.setRoleIds(roleIds);
        return vo;
    }

    private void fillRoleIds(List<SysUserVO> users) {
        if (users == null || users.isEmpty()) {
            return;
        }
        List<UUID> userIds = users.stream()
                .map(SysUserVO::getId)
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());
        if (userIds.isEmpty()) {
            return;
        }
        Map<UUID, List<UUID>> roleMap = new HashMap<>();
        for (SysUserRole relation : sysUserRoleMapper.findByUserIds(userIds)) {
            roleMap.computeIfAbsent(relation.getUserId(), key -> new ArrayList<>()).add(relation.getRoleId());
        }
        for (SysUserVO user : users) {
            user.setRoleIds(roleMap.getOrDefault(user.getId(), Collections.emptyList()));
        }
    }

    private String generateUniqueChannelCode(String username) {
        String base = normalizeChannelCode(username);
        if (base.isBlank()) {
            base = "user";
        }
        if (!channelCodeExists(base)) {
            return base;
        }
        for (int i = 0; i < 8; i++) {
            String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 4);
            int maxBaseLen = MAX_CHANNEL_CODE_LEN - suffix.length();
            String candidate = (base.length() > maxBaseLen ? base.substring(0, maxBaseLen) : base) + suffix;
            if (!channelCodeExists(candidate)) {
                return candidate;
            }
        }
        throw new BusinessException("生成用户渠道编码失败，请重试");
    }

    private String normalizeChannelCode(String username) {
        String normalized = username == null ? "" : username.trim().toLowerCase().replaceAll("[^a-z0-9_]", "");
        if (normalized.length() > MAX_CHANNEL_CODE_LEN) {
            return normalized.substring(0, MAX_CHANNEL_CODE_LEN);
        }
        return normalized;
    }

    private boolean channelCodeExists(String channelCode) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUser::getChannelCode, channelCode);
        return sysUserMapper.selectCount(wrapper) > 0;
    }
}
