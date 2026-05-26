package com.colonel.saas.auth.service;

import com.colonel.saas.auth.dto.LoginRequest;
import com.colonel.saas.auth.dto.LoginResponse;
import com.colonel.saas.auth.dto.LogoutRequest;
import com.colonel.saas.auth.dto.RefreshRequest;
import com.colonel.saas.auth.dto.RefreshResponse;
import com.colonel.saas.constant.SysUserStatus;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.common.result.ResultCode;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.entity.OperationLog;
import com.colonel.saas.entity.SysRole;
import com.colonel.saas.entity.SysUser;
import com.colonel.saas.mapper.SysRoleMapper;
import com.colonel.saas.mapper.SysUserMapper;
import com.colonel.saas.security.JwtTokenProvider;
import com.colonel.saas.service.BusinessRuleConfigService;
import com.colonel.saas.service.OperationLogService;
import io.jsonwebtoken.Claims;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class AuthService {

    private static final String REDIS_BLACKLIST_PREFIX = "auth:blacklist:";
    private static final String REDIS_REFRESH_PREFIX = "auth:refresh:";
    private static final String REDIS_LOGIN_FAIL_PREFIX = "auth:login:fail:";
    private static final String REDIS_LOGIN_LOCK_PREFIX = "auth:login:lock:";

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final RedisTemplate<String, Object> redisTemplate;
    private final OperationLogService operationLogService;
    private final BusinessRuleConfigService businessRuleConfigService;

    public AuthService(
            SysUserMapper sysUserMapper,
            SysRoleMapper sysRoleMapper,
            JwtTokenProvider jwtTokenProvider,
            PasswordEncoder passwordEncoder,
            RedisTemplate<String, Object> redisTemplate,
            OperationLogService operationLogService,
            BusinessRuleConfigService businessRuleConfigService) {
        this.sysUserMapper = sysUserMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.jwtTokenProvider = jwtTokenProvider;
        this.passwordEncoder = passwordEncoder;
        this.redisTemplate = redisTemplate;
        this.operationLogService = operationLogService;
        this.businessRuleConfigService = businessRuleConfigService;
    }

    public LoginResponse login(LoginRequest request) {
        String loginKey = normalizeLoginKey(request.getUsername());
        if (isLoginLocked(loginKey)) {
            recordAuthEvent(null, loginKey, "登录锁定", "POST", "/api/auth/login",
                    false, "登录失败次数过多，账号临时锁定", "登录失败次数过多，请15分钟后再试");
            throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "登录失败次数过多，请15分钟后再试");
        }

        SysUser user = sysUserMapper.findByUsername(request.getUsername())
                .orElseThrow(() -> {
                    boolean locked = recordLoginFailure(loginKey);
                    recordAuthEvent(null, loginKey, locked ? "登录锁定" : "登录失败", "POST", "/api/auth/login",
                            false,
                            locked ? "登录失败次数达到阈值，账号临时锁定" : "用户名或密码错误",
                            "用户名或密码错误");
                    return new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "用户名或密码错误");
                });

        if (!SysUserStatus.canLogin(user.getStatus())) {
            recordAuthEvent(user.getId(), user.getUsername(), "登录失败", "POST", "/api/auth/login",
                    false, "账号已停用", "账号已停用");
            throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "账号已停用");
        }

        if (!matchesPassword(request.getPassword(), user.getPassword())) {
            boolean locked = recordLoginFailure(loginKey);
            recordAuthEvent(user.getId(), user.getUsername(), locked ? "登录锁定" : "登录失败", "POST", "/api/auth/login",
                    false,
                    locked ? "登录失败次数达到阈值，账号临时锁定" : "用户名或密码错误",
                    "用户名或密码错误");
            throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "用户名或密码错误");
        }
        clearLoginFailures(loginKey);

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

        boolean pendingActivation = SysUserStatus.isPendingActivation(user.getStatus());

        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getId(),
                user.getDeptId(),
                dataScope,
                roleCodes,
                user.getUsername(),
                pendingActivation
        );

        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        SysUser update = new SysUser();
        update.setId(user.getId());
        update.setLastLoginAt(LocalDateTime.now());
        sysUserMapper.updateById(update);
        recordAuthEvent(user.getId(), user.getUsername(), "登录成功", "POST", "/api/auth/login",
                true, "用户登录成功", null);

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
                .status(user.getStatus())
                .forcePasswordChange(Boolean.TRUE.equals(user.getForcePasswordChange()))
                .pendingActivation(pendingActivation)
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
        if (user == null || !SysUserStatus.canLogin(user.getStatus())) {
            throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "账号已停用");
        }

        boolean pendingActivation = SysUserStatus.isPendingActivation(user.getStatus());

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
                user.getUsername(),
                pendingActivation
        );

        return RefreshResponse.builder()
                .accessToken(newAccessToken)
                .accessTokenExpiresIn(jwtTokenProvider.getExpireSeconds())
                .refreshToken(refreshToken)
                .refreshExpiresIn(jwtTokenProvider.getRefreshExpireSeconds())
                .build();
    }

    public void logout(LogoutRequest request) {
        String refreshToken = request.getRefreshToken();
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "refreshToken 不能为空");
        }

        Claims refreshClaims;
        try {
            refreshClaims = jwtTokenProvider.parseClaims(refreshToken);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "Refresh Token 无效或已过期");
        }

        String refreshTokenType = refreshClaims.get("type", String.class);
        if (!"refresh".equals(refreshTokenType)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "Token 类型错误，需要 refresh token");
        }

        String refreshTokenHash = jwtTokenProvider.getTokenHash(refreshToken);
        if (Boolean.TRUE.equals(redisTemplate.hasKey(REDIS_REFRESH_PREFIX + refreshTokenHash))) {
            throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "Refresh Token 已吊销");
        }
        long refreshRemaining = jwtTokenProvider.getRemainingSeconds(refreshToken);
        redisTemplate.opsForValue().set(
                REDIS_REFRESH_PREFIX + refreshTokenHash,
                "1",
                refreshRemaining,
                TimeUnit.SECONDS
        );

        Claims accessClaims = null;
        String accessToken = request.getAccessToken();
        if (accessToken != null && !accessToken.isBlank()) {
            accessClaims = revokeAccessTokenBestEffort(accessToken);
        }

        UUID userId = extractUserId(accessClaims != null ? accessClaims : refreshClaims);
        String username = extractUsername(accessClaims != null ? accessClaims : refreshClaims);
        recordAuthEvent(userId, username, "登出", "POST", "/api/auth/logout",
                true, "用户登出并吊销令牌", null);
    }

    public boolean isTokenBlacklisted(String tokenHash) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(REDIS_BLACKLIST_PREFIX + tokenHash));
    }

    private boolean isLoginLocked(String loginKey) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(loginLockKey(loginKey)));
    }

    private boolean recordLoginFailure(String loginKey) {
        int maxFailures = businessRuleConfigService.getLoginMaxFailures();
        long lockMinutes = businessRuleConfigService.getLoginLockMinutes();
        String failKey = loginFailKey(loginKey);
        Long attempts = redisTemplate.opsForValue().increment(failKey);
        if (attempts != null && attempts == 1L) {
            redisTemplate.expire(failKey, lockMinutes, TimeUnit.MINUTES);
        }
        if (attempts != null && attempts >= maxFailures) {
            redisTemplate.opsForValue().set(loginLockKey(loginKey), "1", lockMinutes, TimeUnit.MINUTES);
            redisTemplate.delete(failKey);
            return true;
        }
        return false;
    }

    private void clearLoginFailures(String loginKey) {
        redisTemplate.delete(loginFailKey(loginKey));
        redisTemplate.delete(loginLockKey(loginKey));
    }

    private String normalizeLoginKey(String username) {
        if (username == null) {
            return "";
        }
        return username.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private String loginFailKey(String loginKey) {
        return REDIS_LOGIN_FAIL_PREFIX + loginKey;
    }

    private String loginLockKey(String loginKey) {
        return REDIS_LOGIN_LOCK_PREFIX + loginKey;
    }

    private void recordAuthEvent(
            UUID userId,
            String username,
            String action,
            String method,
            String url,
            boolean success,
            String content,
            String errorMessage) {
        try {
            OperationLog log = new OperationLog();
            log.setUserId(userId);
            log.setUsername(username);
            log.setModule("用户域");
            log.setAction(action);
            log.setRequestMethod(method);
            log.setRequestUrl(url);
            log.setTargetType("Auth");
            log.setTargetId(userId == null ? username : userId.toString());
            log.setTargetName(username);
            log.setContent(content);
            log.setResponseCode(success ? "SUCCESS" : "FAILED");
            log.setRequestBody(safeAuthRequest(username));
            log.setResponseBody(Map.of("success", success));
            log.setErrorMessage(errorMessage);
            operationLogService.record(log);
        } catch (Exception ignored) {
            // 登录与登出不能因审计日志写入失败而影响认证主流程。
        }
    }

    private Map<String, Object> safeAuthRequest(String username) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (username != null && !username.isBlank()) {
            body.put("username", username);
        }
        return body;
    }

    private UUID extractUserId(Claims claims) {
        if (claims == null || claims.getSubject() == null) {
            return null;
        }
        try {
            return UUID.fromString(claims.getSubject());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private String extractUsername(Claims claims) {
        if (claims == null) {
            return null;
        }
        try {
            return claims.get("username", String.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Claims revokeAccessTokenBestEffort(String accessToken) {
        try {
            Claims accessClaims = jwtTokenProvider.parseClaims(accessToken);
            String accessTokenType = accessClaims.get("type", String.class);
            if (accessTokenType != null && !"access".equals(accessTokenType)) {
                return null;
            }
            String accessTokenHash = jwtTokenProvider.getTokenHash(accessToken);
            long remainingSeconds = jwtTokenProvider.getRemainingSeconds(accessToken);
            redisTemplate.opsForValue().set(
                    REDIS_BLACKLIST_PREFIX + accessTokenHash,
                    "1",
                    remainingSeconds,
                    TimeUnit.SECONDS
            );
            return accessClaims;
        } catch (Exception ignored) {
            return null;
        }
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
