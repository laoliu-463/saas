package com.colonel.saas.auth.service;

import com.colonel.saas.auth.dto.LoginRequest;
import com.colonel.saas.auth.dto.LoginResponse;
import com.colonel.saas.auth.dto.LogoutRequest;
import com.colonel.saas.auth.dto.RefreshRequest;
import com.colonel.saas.auth.dto.RefreshResponse;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.common.result.ResultCode;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.entity.SysRole;
import com.colonel.saas.entity.SysUser;
import com.colonel.saas.mapper.SysRoleMapper;
import com.colonel.saas.mapper.SysUserMapper;
import com.colonel.saas.security.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class AuthService {

    private static final String REDIS_BLACKLIST_PREFIX = "auth:blacklist:";
    private static final String REDIS_REFRESH_PREFIX = "auth:refresh:";

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final RedisTemplate<String, Object> redisTemplate;

    public AuthService(
            SysUserMapper sysUserMapper,
            SysRoleMapper sysRoleMapper,
            JwtTokenProvider jwtTokenProvider,
            PasswordEncoder passwordEncoder,
            RedisTemplate<String, Object> redisTemplate) {
        this.sysUserMapper = sysUserMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.jwtTokenProvider = jwtTokenProvider;
        this.passwordEncoder = passwordEncoder;
        this.redisTemplate = redisTemplate;
    }

    public LoginResponse login(LoginRequest request) {
        SysUser user = sysUserMapper.findByUsername(request.getUsername())
                .orElseThrow(() -> new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "用户名或密码错误"));

        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "账号已停用");
        }

        if (!matchesPassword(request.getPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "用户名或密码错误");
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

        if (roleCodes.contains(RoleCodes.OPS_STAFF)) {
            dataScope = 3;
        }
        if (roleCodes.contains(RoleCodes.ADMIN)) {
            dataScope = 3;
        }

        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getId(),
                user.getDeptId(),
                dataScope,
                roleCodes,
                user.getUsername()
        );

        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        SysUser update = new SysUser();
        update.setId(user.getId());
        update.setLastLoginAt(LocalDateTime.now());
        sysUserMapper.updateById(update);

        return LoginResponse.builder()
                .token(accessToken)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getExpireSeconds())
                .accessTokenExpiresIn(jwtTokenProvider.getExpireSeconds())
                .refreshToken(refreshToken)
                .refreshExpiresIn(jwtTokenProvider.getRefreshExpireSeconds())
                .userId(user.getId())
                .deptId(user.getDeptId())
                .dataScope(dataScope)
                .roleCodes(roleCodes)
                .username(user.getUsername())
                .realName(user.getRealName())
                .build();
    }

    public RefreshResponse refreshToken(RefreshRequest request) {
        String refreshToken = request.getRefreshToken();

        Claims claims;
        try {
            claims = jwtTokenProvider.parseClaims(refreshToken);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "Refresh Token 无效或已过期");
        }

        String tokenType = claims.get("type", String.class);
        if (!"refresh".equals(tokenType)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "Token 类型错误，需要 refresh token");
        }

        String tokenHash = jwtTokenProvider.getTokenHash(refreshToken);
        if (Boolean.TRUE.equals(redisTemplate.hasKey(REDIS_REFRESH_PREFIX + tokenHash))) {
            throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "Refresh Token 已吊销");
        }

        java.util.UUID userId = java.util.UUID.fromString(claims.getSubject());
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "账号已停用");
        }

        List<SysRole> roles = sysRoleMapper.findByUserId(userId);
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

        if (roleCodes.contains(RoleCodes.OPS_STAFF)) {
            dataScope = 3;
        }
        if (roleCodes.contains(RoleCodes.ADMIN)) {
            dataScope = 3;
        }

        String newAccessToken = jwtTokenProvider.generateAccessToken(
                userId,
                user.getDeptId(),
                dataScope,
                roleCodes,
                user.getUsername()
        );

        return RefreshResponse.builder()
                .accessToken(newAccessToken)
                .accessTokenExpiresIn(jwtTokenProvider.getExpireSeconds())
                .refreshToken(refreshToken)
                .refreshExpiresIn(jwtTokenProvider.getRefreshExpireSeconds())
                .build();
    }

    public void logout(LogoutRequest request) {
        String accessToken = request.getAccessToken();
        try {
            jwtTokenProvider.parseClaims(accessToken);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "Token 无效");
        }

        String accessTokenHash = jwtTokenProvider.getTokenHash(accessToken);
        long remainingSeconds = jwtTokenProvider.getRemainingSeconds(accessToken);
        redisTemplate.opsForValue().set(
                REDIS_BLACKLIST_PREFIX + accessTokenHash,
                "1",
                remainingSeconds,
                TimeUnit.SECONDS
        );

        String refreshToken = request.getRefreshToken();
        if (refreshToken != null && !refreshToken.isBlank()) {
            try {
                jwtTokenProvider.parseClaims(refreshToken);
                String refreshTokenHash = jwtTokenProvider.getTokenHash(refreshToken);
                long refreshRemaining = jwtTokenProvider.getRemainingSeconds(refreshToken);
                redisTemplate.opsForValue().set(
                        REDIS_REFRESH_PREFIX + refreshTokenHash,
                        "1",
                        refreshRemaining,
                        TimeUnit.SECONDS
                );
            } catch (Exception ignored) {
                // refresh token 无效时忽略，access token 已吊销
            }
        }
    }

    public boolean isTokenBlacklisted(String tokenHash) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(REDIS_BLACKLIST_PREFIX + tokenHash));
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
