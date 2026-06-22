package com.colonel.saas.auth.service;

import com.colonel.saas.auth.dto.LoginRequest;
import com.colonel.saas.auth.dto.LoginResponse;
import com.colonel.saas.auth.dto.LogoutRequest;
import com.colonel.saas.auth.dto.RefreshRequest;
import com.colonel.saas.auth.dto.RefreshResponse;
import com.colonel.saas.constant.SysUserStatus;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.common.result.ResultCode;
import com.colonel.saas.domain.user.policy.CurrentUserPermissionPolicy;
import com.colonel.saas.domain.user.policy.CurrentUserPermissionPolicy.RolePermission;
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
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 认证核心服务，负责用户登录、令牌刷新与登出。
 *
 * <ul>
 *   <li>用户名+密码认证，支持 BCrypt 密码比对</li>
 *   <li>登录失败次数限制与账号临时锁定（Redis 计数）</li>
 *   <li>Access Token + Refresh Token 双令牌签发</li>
 *   <li>令牌刷新（Refresh Token 换新 Access Token）</li>
 *   <li>登出时将令牌加入 Redis 黑名单以实现吊销</li>
 *   <li>审计日志记录（登录/登出事件）</li>
 * </ul>
 *
 * <p>所属业务领域：用户域 / 认证中心
 *
 * @see com.colonel.saas.auth.controller.AuthController
 * @see com.colonel.saas.security.JwtTokenProvider
 */
@Service
public class AuthService {

    // ==================== Redis Key 前缀常量 ====================

    /** Access Token 吊销黑名单前缀，完整 key = auth:blacklist:{tokenHash} */
    private static final String REDIS_BLACKLIST_PREFIX = "auth:blacklist:";

    /** Refresh Token 吊销黑名单前缀，完整 key = auth:refresh:{tokenHash} */
    private static final String REDIS_REFRESH_PREFIX = "auth:refresh:";

    /** 登录失败计数前缀，完整 key = auth:login:fail:{normalizedUsername} */
    private static final String REDIS_LOGIN_FAIL_PREFIX = "auth:login:fail:";

    /** 登录锁定标记前缀，完整 key = auth:login:lock:{normalizedUsername} */
    private static final String REDIS_LOGIN_LOCK_PREFIX = "auth:login:lock:";

    /** 用户数据访问，提供按用户名查询、按 ID 查询等能力 */
    private final SysUserMapper sysUserMapper;

    /** 角色数据访问，提供按用户 ID 查询角色列表 */
    private final SysRoleMapper sysRoleMapper;

    /** JWT 令牌工具，提供令牌签发、解析、哈希计算等能力 */
    private final JwtTokenProvider jwtTokenProvider;

    /** 密码编码器（BCrypt），用于密码比对验证 */
    private final PasswordEncoder passwordEncoder;

    /** Redis 操作模板，用于令牌黑名单、登录失败计数、账号锁定 */
    private final RedisTemplate<String, Object> redisTemplate;

    /** 审计日志服务，记录登录/登出事件 */
    private final OperationLogService operationLogService;

    /** 业务规则配置服务，读取登录失败阈值、锁定时长等动态配置 */
    private final BusinessRuleConfigService businessRuleConfigService;

    /** 当前用户权限策略，用于统一登录上下文中的数据范围解析 */
    private final CurrentUserPermissionPolicy currentUserPermissionPolicy;

    /**
     * 构造注入所有依赖项。
     *
     * @param sysUserMapper          用户数据访问
     * @param sysRoleMapper          角色数据访问
     * @param jwtTokenProvider       JWT 令牌工具
     * @param passwordEncoder        BCrypt 密码编码器
     * @param redisTemplate          Redis 操作模板
     * @param operationLogService    审计日志服务
     * @param businessRuleConfigService 业务规则配置服务
     * @param currentUserPermissionPolicy 当前用户权限策略
     */
    public AuthService(
            SysUserMapper sysUserMapper,
            SysRoleMapper sysRoleMapper,
            JwtTokenProvider jwtTokenProvider,
            PasswordEncoder passwordEncoder,
            RedisTemplate<String, Object> redisTemplate,
            OperationLogService operationLogService,
            BusinessRuleConfigService businessRuleConfigService,
            CurrentUserPermissionPolicy currentUserPermissionPolicy) {
        this.sysUserMapper = sysUserMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.jwtTokenProvider = jwtTokenProvider;
        this.passwordEncoder = passwordEncoder;
        this.redisTemplate = redisTemplate;
        this.operationLogService = operationLogService;
        this.businessRuleConfigService = businessRuleConfigService;
        this.currentUserPermissionPolicy = currentUserPermissionPolicy;
    }

    /**
     * 用户登录认证。
     *
     * <p>处理流程：
     * <ol>
     *   <li>检查账号是否因连续登录失败被临时锁定</li>
     *   <li>根据用户名查询 sys_user，不存在则记录失败次数</li>
     *   <li>校验账号状态是否允许登录（非禁用状态）</li>
     *   <li>BCrypt 比对密码，不匹配则记录失败次数</li>
     *   <li>登录成功后清除失败计数</li>
     *   <li>查询用户角色列表，计算数据权限范围</li>
     *   <li>签发 Access Token 和 Refresh Token</li>
     *   <li>更新用户最后登录时间，记录审计日志</li>
     *   <li>组装并返回 LoginResponse</li>
     * </ol>
     *
     * @param request 登录请求参数（用户名、密码）
     * @return 登录响应，包含双令牌、用户信息和权限数据
     * @throws BusinessException 用户名不存在、密码错误、账号已停用或登录被锁定时抛出
     */
    public LoginResponse login(LoginRequest request) {
        String account = request.getUsername() == null ? "" : request.getUsername().trim();
        final String accountLockKey = normalizeLoginKey(account);
        if (isLoginLocked(accountLockKey)) {
            recordAuthEvent(null, accountLockKey, "登录锁定", "POST", "/api/auth/login",
                    false, "登录失败次数过多，账号临时锁定", "登录失败次数过多，请15分钟后再试");
            throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "登录失败次数过多，请15分钟后再试");
        }

        Optional<SysUser> resolvedUser = resolveLoginUser(account);
        SysUser user = resolvedUser.orElseThrow(() -> {
            boolean locked = recordLoginFailure(accountLockKey);
            recordAuthEvent(null, accountLockKey, locked ? "登录锁定" : "登录失败", "POST", "/api/auth/login",
                    false,
                    locked ? "登录失败次数达到阈值，账号临时锁定" : "用户名或密码错误",
                    "用户名或密码错误");
            return new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "用户名或密码错误");
        });

        String userLockKey = normalizeLoginKey(user.getUsername());
        if (!userLockKey.equals(accountLockKey) && isLoginLocked(userLockKey)) {
            recordAuthEvent(user.getId(), user.getUsername(), "登录锁定", "POST", "/api/auth/login",
                    false, "登录失败次数过多，账号临时锁定", "登录失败次数过多，请15分钟后再试");
            throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "登录失败次数过多，请15分钟后再试");
        }

        if (!SysUserStatus.canLogin(user.getStatus())) {
            recordAuthEvent(user.getId(), user.getUsername(), "登录失败", "POST", "/api/auth/login",
                    false, "账号已停用", "账号已停用");
            throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "账号已停用");
        }

        if (!matchesPassword(request.getPassword(), user.getPassword())) {
            boolean locked = recordLoginFailure(userLockKey);
            recordAuthEvent(user.getId(), user.getUsername(), locked ? "登录锁定" : "登录失败", "POST", "/api/auth/login",
                    false,
                    locked ? "登录失败次数达到阈值，账号临时锁定" : "用户名或密码错误",
                    "用户名或密码错误");
            throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "用户名或密码错误");
        }
        clearLoginFailures(userLockKey);

        List<SysRole> roles = sysRoleMapper.findByUserId(user.getId());
        List<String> roleCodes = roles.isEmpty()
                ? Collections.emptyList()
                : roles.stream()
                .map(SysRole::getRoleCode)
                .collect(Collectors.toList());
        int dataScope = resolveDataScope(roles, roleCodes);

        // 待激活状态的用户登录后需要在前端完成激活流程
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

    /**
     * 刷新令牌，使用 Refresh Token 换取新的 Access Token。
     *
     * <p>处理流程：
     * <ol>
     *   <li>解析并校验 Refresh Token 的签名和有效期</li>
     *   <li>校验 Token 类型必须为 "refresh"</li>
     *   <li>检查 Refresh Token 是否已被吊销（Redis 黑名单）</li>
     *   <li>查询用户信息，校验账号状态是否允许登录</li>
     *   <li>重新查询用户角色，计算最新数据权限范围</li>
     *   <li>签发新的 Access Token 和 Refresh Token</li>
     *   <li>将旧 Refresh Token 加入 Redis 黑名单（TTL = 剩余有效期）</li>
     *   <li>返回新的 Access Token、Refresh Token 及其有效期</li>
     * </ol>
     *
     * @param request 刷新请求参数（Refresh Token）
     * @return 刷新响应，包含新 Access Token 和新 Refresh Token
     * @throws BusinessException Token 无效、已过期、已吊销或账号已停用时抛出
     */
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
        List<String> roleCodes = roles.isEmpty()
                ? Collections.emptyList()
                : roles.stream()
                .map(SysRole::getRoleCode)
                .collect(Collectors.toList());
        int dataScope = resolveDataScope(roles, roleCodes);

        String newAccessToken = jwtTokenProvider.generateAccessToken(
                userId,
                user.getDeptId(),
                dataScope,
                roleCodes,
                user.getUsername(),
                pendingActivation
        );
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(userId);
        long refreshRemaining = jwtTokenProvider.getRemainingSeconds(refreshToken);
        if (refreshRemaining > 0) {
            redisTemplate.opsForValue().set(
                    REDIS_REFRESH_PREFIX + tokenHash,
                    "1",
                    refreshRemaining,
                    TimeUnit.SECONDS
            );
        }

        return RefreshResponse.builder()
                .accessToken(newAccessToken)
                .accessTokenExpiresIn(jwtTokenProvider.getExpireSeconds())
                .refreshToken(newRefreshToken)
                .refreshExpiresIn(jwtTokenProvider.getRefreshExpireSeconds())
                .build();
    }

    /**
     * 用户登出，吊销 Access Token 和 Refresh Token。
     *
     * <p>处理流程：
     * <ol>
     *   <li>校验 Refresh Token 非空</li>
     *   <li>解析并校验 Refresh Token 的签名和有效期</li>
     *   <li>校验 Token 类型必须为 "refresh"</li>
     *   <li>检查 Refresh Token 是否已被吊销</li>
     *   <li>将 Refresh Token 加入 Redis 黑名单（TTL = 剩余有效期）</li>
     *   <li>若提供了 Access Token，尽力将其加入黑名单</li>
     *   <li>记录登出审计日志</li>
     * </ol>
     *
     * @param request 登出请求参数（Refresh Token 必填，Access Token 可选）
     * @throws BusinessException Token 无效、已过期、已吊销时抛出
     */
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

    /**
     * 检查指定 Token 哈希是否在 Access Token 黑名单中。
     * <p>由 JWT 过滤器调用，用于判断已登出的 Token 是否应被拒绝。
     *
     * @param tokenHash Token 的哈希值
     * @return true 表示已被吊销（在黑名单中），false 表示未吊销
     */
    public boolean isTokenBlacklisted(String tokenHash) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(REDIS_BLACKLIST_PREFIX + tokenHash));
    }

    /**
     * 检查指定用户是否因连续登录失败被临时锁定。
     *
     * @param loginKey 归一化后的用户名（小写、去首尾空格）
     * @return true 表示账号已被锁定，false 表示未锁定
     */
    private boolean isLoginLocked(String loginKey) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(loginLockKey(loginKey)));
    }

    /**
     * 记录一次登录失败并判断是否需要锁定账号。
     *
     * <p>在 Redis 中以 INCR 方式递增失败计数器，首次写入时设置过期时间。
     * 当失败次数达到业务规则配置的阈值时，创建锁定标记并清除失败计数器。
     *
     * @param loginKey 归一化后的用户名
     * @return true 表示已触发锁定，false 表示尚未达到锁定阈值
     */
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

    /**
     * 清除登录失败计数器和锁定标记。
     *
     * <p>登录成功后调用，删除 Redis 中的失败计数 key 和锁定标记 key，
     * 使后续登录不再受到之前失败记录的影响。
     *
     * @param loginKey 归一化后的用户名
     */
    private void clearLoginFailures(String loginKey) {
        redisTemplate.delete(loginFailKey(loginKey));
        redisTemplate.delete(loginLockKey(loginKey));
    }

    /**
     * 按账号解析用户：先匹配用户名，再匹配真实姓名（精确、trim）。
     *
     * @param account 登录账号（用户名或姓名）
     * @return 唯一匹配的用户；无匹配为空；姓名重复匹配时抛出业务异常
     */
    private Optional<SysUser> resolveLoginUser(String account) {
        if (account == null || account.isBlank()) {
            return Optional.empty();
        }
        String trimmed = account.trim();
        Optional<SysUser> byUsername = sysUserMapper.findByUsername(trimmed);
        if (byUsername.isPresent()) {
            return byUsername;
        }
        List<SysUser> byRealName = sysUserMapper.findByRealName(trimmed);
        if (byRealName.isEmpty()) {
            return Optional.empty();
        }
        if (byRealName.size() > 1) {
            throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "存在多个同名账号，请使用用户名登录");
        }
        return Optional.of(byRealName.get(0));
    }

    /**
     * 将用户名归一化为统一的登录 key。
     *
     * <p>执行 trim + toLowerCase 操作，确保同一用户名的不同大小写形式
     * 映射到同一个 Redis key，防止通过大小写变体绕过登录失败计数。
     *
     * @param username 原始用户名，可能为 null
     * @return 归一化后的用户名（小写、去首尾空格），null 输入返回空字符串
     */
    private String normalizeLoginKey(String username) {
        if (username == null) {
            return "";
        }
        return username.trim().toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * 拼接登录失败计数的 Redis key。
     *
     * @param loginKey 归一化后的用户名
     * @return 格式为 "auth:login:fail:{loginKey}" 的 Redis key
     */
    private String loginFailKey(String loginKey) {
        return REDIS_LOGIN_FAIL_PREFIX + loginKey;
    }

    /**
     * 拼接登录锁定标记的 Redis key。
     *
     * @param loginKey 归一化后的用户名
     * @return 格式为 "auth:login:lock:{loginKey}" 的 Redis key
     */
    private String loginLockKey(String loginKey) {
        return REDIS_LOGIN_LOCK_PREFIX + loginKey;
    }

    /**
     * 记录认证事件的审计日志。
     *
     * <p>将登录/登出事件写入 operation_log 表，包含用户标识、操作类型、
     * 请求方式、URL、成功/失败状态、摘要说明和错误信息。
     * 方法内部捕获所有异常，确保审计日志写入失败不影响认证主流程。
     *
     * @param userId      用户 ID，登录失败时可能为 null
     * @param username    用户名
     * @param action      操作描述（如"登录成功"、"登录失败"、"登出"）
     * @param method      HTTP 请求方法（POST）
     * @param url         请求 URL
     * @param success     操作是否成功
     * @param content     审计日志摘要说明
     * @param errorMessage 错误信息，成功时为 null
     */
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

    /**
     * 构建安全的认证请求体用于审计日志记录。
     *
     * <p>仅包含用户名字段，不记录密码等敏感信息。
     * 空用户名时不添加任何字段，返回空 Map。
     *
     * @param username 用户名，可为 null
     * @return 仅含 username 字段的安全请求体 Map
     */
    private Map<String, Object> safeAuthRequest(String username) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (username != null && !username.isBlank()) {
            body.put("username", username);
        }
        return body;
    }

    /**
     * 从 JWT Claims 中提取用户 ID。
     *
     * <p>解析 Claims 的 subject 字段为 UUID，解析失败时返回 null。
     * 用于登出时从 Access Token 或 Refresh Token 中获取用户标识。
     *
     * @param claims JWT Claims 对象，可为 null
     * @return 用户 ID（UUID），提取失败时返回 null
     */
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

    /**
     * 从 JWT Claims 中提取用户名。
     *
     * <p>读取 Claims 中自定义的 "username" 字段，读取失败时返回 null。
     * 用于登出时从 Access Token 中获取用户名用于审计日志记录。
     *
     * @param claims JWT Claims 对象，可为 null
     * @return 用户名，提取失败时返回 null
     */
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

    /**
     * 尽力将 Access Token 加入 Redis 黑名单（best-effort）。
     *
     * <p>解析 Token 的签名和类型，仅处理 type="access" 的 Token。
     * 根据 Token 剩余有效期设置黑名单 TTL，过期后自动清除。
     * 解析失败或类型不匹配时静默返回 null，不影响登出主流程。
     *
     * @param accessToken 要吊销的 Access Token 字符串
     * @return 解析成功的 Claims 对象，失败时返回 null
     */
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

    private int resolveDataScope(List<SysRole> roles, List<String> roleCodes) {
        List<RolePermission> rolePermissions = roles.stream()
                .map(role -> new RolePermission(role.getRoleCode(), role.getDataScope(), Collections.emptyMap()))
                .toList();
        return currentUserPermissionPolicy.resolveDataScopeCode(rolePermissions, null, roleCodes);
    }

    /**
     * 使用 BCrypt 校验明文密码与数据库存储的哈希密码是否匹配。
     *
     * <p>数据库密码为空或空白时直接返回 false，避免 NullPointerException。
     *
     * @param rawPassword 用户输入的明文密码
     * @param dbPassword  数据库中存储的 BCrypt 哈希密码
     * @return true 表示密码匹配，false 表示不匹配
     */
    private boolean matchesPassword(String rawPassword, String dbPassword) {
        if (dbPassword == null || dbPassword.isBlank()) {
            return false;
        }
        return passwordEncoder.matches(rawPassword, dbPassword);
    }
}
