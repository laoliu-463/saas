package com.colonel.saas.auth.service;

import com.colonel.saas.auth.dto.LoginRequest;
import com.colonel.saas.auth.dto.LoginResponse;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.entity.SysRole;
import com.colonel.saas.entity.SysUser;
import com.colonel.saas.mapper.SysRoleMapper;
import com.colonel.saas.mapper.SysUserMapper;
import com.colonel.saas.security.JwtTokenProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AuthService {

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;

    public AuthService(
            SysUserMapper sysUserMapper,
            SysRoleMapper sysRoleMapper,
            JwtTokenProvider jwtTokenProvider,
            PasswordEncoder passwordEncoder) {
        this.sysUserMapper = sysUserMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.jwtTokenProvider = jwtTokenProvider;
        this.passwordEncoder = passwordEncoder;
    }

    public LoginResponse login(LoginRequest request) {
        SysUser user = sysUserMapper.findByUsername(request.getUsername())
                .orElseThrow(() -> new BusinessException("用户名或密码错误"));

        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException("用户名或密码错误");
        }

        if (!matchesPassword(request.getPassword(), user.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }

        List<SysRole> roles = sysRoleMapper.findByUserId(user.getId());
        int dataScope = roles.stream()
                .map(SysRole::getDataScope)
                .filter(scope -> scope != null && scope > 0)
                .max(Integer::compareTo)
                .orElse(1);

        List<String> roleCodes = roles.isEmpty()
                ? Collections.emptyList()
                : roles.stream()
                .map(SysRole::getRoleCode)
                .collect(Collectors.toList());

        String token = jwtTokenProvider.generateToken(
                user.getId(),
                user.getDeptId(),
                dataScope,
                roleCodes,
                user.getUsername()
        );

        SysUser update = new SysUser();
        update.setId(user.getId());
        update.setLastLoginAt(LocalDateTime.now());
        sysUserMapper.updateById(update);

        return LoginResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getExpireSeconds())
                .userId(user.getId())
                .deptId(user.getDeptId())
                .dataScope(dataScope)
                .roleCodes(roleCodes)
                .username(user.getUsername())
                .realName(user.getRealName())
                .build();
    }

    /**
     * 密码统一使用 BCrypt。
     */
    private boolean matchesPassword(String rawPassword, String dbPassword) {
        if (dbPassword == null || dbPassword.isBlank()) {
            return false;
        }
        return passwordEncoder.matches(rawPassword, dbPassword);
    }
}
