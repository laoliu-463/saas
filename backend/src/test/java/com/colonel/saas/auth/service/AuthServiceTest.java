package com.colonel.saas.auth.service;

import com.colonel.saas.auth.dto.LoginRequest;
import com.colonel.saas.auth.dto.LoginResponse;
import com.colonel.saas.auth.dto.LogoutRequest;
import com.colonel.saas.auth.dto.RefreshRequest;
import com.colonel.saas.auth.dto.RefreshResponse;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.domain.user.policy.CurrentUserPermissionPolicy;
import com.colonel.saas.entity.OperationLog;
import com.colonel.saas.entity.SysRole;
import com.colonel.saas.entity.SysUser;
import com.colonel.saas.mapper.SysRoleMapper;
import com.colonel.saas.mapper.SysUserMapper;
import com.colonel.saas.security.JwtTokenProvider;
import com.colonel.saas.service.BusinessRuleConfigService;
import com.colonel.saas.service.OperationLogService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private SysUserMapper sysUserMapper;
    @Mock
    private SysRoleMapper sysRoleMapper;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ValueOperations<String, Object> valueOperations;
    @Mock
    private OperationLogService operationLogService;
    @Mock
    private BusinessRuleConfigService businessRuleConfigService;

    private PasswordEncoder passwordEncoder;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        lenient().when(businessRuleConfigService.getLoginMaxFailures()).thenReturn(5);
        lenient().when(businessRuleConfigService.getLoginLockMinutes()).thenReturn(15);
        authService = new AuthService(
                sysUserMapper,
                sysRoleMapper,
                jwtTokenProvider,
                passwordEncoder,
                redisTemplate,
                operationLogService,
                businessRuleConfigService,
                new CurrentUserPermissionPolicy());
    }

    private void stubJwtTokenGeneration() {
        when(jwtTokenProvider.generateAccessToken(any(), any(), any(Integer.class), any(), any(), any(Boolean.class))).thenReturn("jwt.token.here");
        when(jwtTokenProvider.generateRefreshToken(any())).thenReturn("refresh.token.here");
        when(jwtTokenProvider.getExpireSeconds()).thenReturn(3600L);
        when(jwtTokenProvider.getRefreshExpireSeconds()).thenReturn(604800L);
    }

    private SysUser createActiveUser(String username) {
        SysUser user = new SysUser();
        user.setId(UUID.randomUUID());
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode("password"));
        user.setStatus(1);
        return user;
    }

    // ==================== Login Tests ====================

    @Test
    @DisplayName("登录成功返回令牌和用户信息")
    void login_success_shouldReturnTokenAndUserInfo() {
        SysUser user = createActiveUser("alice");
        UUID deptId = UUID.randomUUID();
        user.setDeptId(deptId);
        user.setRealName("Alice");

        SysRole role = new SysRole();
        role.setRoleCode("admin");
        role.setDataScope(3);

        when(sysUserMapper.findByUsername("alice")).thenReturn(Optional.of(user));
        when(sysRoleMapper.findByUserId(user.getId())).thenReturn(List.of(role));
        stubJwtTokenGeneration();

        LoginRequest request = new LoginRequest();
        request.setUsername("alice");
        request.setPassword("password");
        LoginResponse response = authService.login(request);

        assertThat(response.getToken()).isEqualTo("jwt.token.here");
        assertThat(response.getUserId()).isEqualTo(user.getId());
        assertThat(response.getUsername()).isEqualTo("alice");
        assertThat(response.getRoleCodes()).contains("admin");
        verify(sysUserMapper).updateById(any(SysUser.class));
        ArgumentCaptor<OperationLog> logCaptor = ArgumentCaptor.forClass(OperationLog.class);
        verify(operationLogService).record(logCaptor.capture());
        OperationLog log = logCaptor.getValue();
        assertThat(log.getModule()).isEqualTo("用户域");
        assertThat(log.getAction()).isEqualTo("登录成功");
        assertThat(log.getUsername()).isEqualTo("alice");
        assertThat(log.getResponseCode()).isEqualTo("SUCCESS");
        assertThat(log.getRequestBody()).containsEntry("username", "alice");
        assertThat(log.getRequestBody()).doesNotContainKey("password");
    }

    @Test
    @DisplayName("登录成功 - 支持真实姓名")
    void login_success_withRealName() {
        SysUser user = createActiveUser("biz_staff");
        user.setRealName("招商专员测试");

        SysRole role = new SysRole();
        role.setRoleCode("biz_staff");
        role.setDataScope(1);

        when(sysUserMapper.findByUsername("招商专员测试")).thenReturn(Optional.empty());
        when(sysUserMapper.findByRealName("招商专员测试")).thenReturn(List.of(user));
        when(sysRoleMapper.findByUserId(user.getId())).thenReturn(List.of(role));
        stubJwtTokenGeneration();

        LoginRequest request = new LoginRequest();
        request.setUsername("招商专员测试");
        request.setPassword("password");
        LoginResponse response = authService.login(request);

        assertThat(response.getUsername()).isEqualTo("biz_staff");
        assertThat(response.getRealName()).isEqualTo("招商专员测试");
    }

    @Test
    @DisplayName("登录失败 - 用户不存在")
    void login_userNotFound_shouldThrow() {
        when(sysUserMapper.findByUsername("nobody")).thenReturn(Optional.empty());
        when(sysUserMapper.findByRealName("nobody")).thenReturn(List.of());
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("auth:login:fail:nobody")).thenReturn(1L);

        LoginRequest request = new LoginRequest();
        request.setUsername("nobody");
        request.setPassword("password");

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("用户名或密码错误");
        verify(valueOperations).increment("auth:login:fail:nobody");
    }

    @Test
    @DisplayName("登录失败 - 密码错误")
    void login_wrongPassword_shouldThrow() {
        SysUser user = createActiveUser("bob");
        user.setPassword(passwordEncoder.encode("correct"));

        when(sysUserMapper.findByUsername("bob")).thenReturn(Optional.of(user));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("auth:login:fail:bob")).thenReturn(1L);

        LoginRequest request = new LoginRequest();
        request.setUsername("bob");
        request.setPassword("wrong");

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("用户名或密码错误");
        verify(valueOperations).increment("auth:login:fail:bob");
        ArgumentCaptor<OperationLog> logCaptor = ArgumentCaptor.forClass(OperationLog.class);
        verify(operationLogService).record(logCaptor.capture());
        assertThat(logCaptor.getValue().getAction()).isEqualTo("登录失败");
        assertThat(logCaptor.getValue().getResponseCode()).isEqualTo("FAILED");
        assertThat(logCaptor.getValue().getRequestBody()).doesNotContainKey("password");
    }

    @Test
    @DisplayName("登录失败次数达到阈值后锁定账号")
    void login_wrongPasswordAtThreshold_shouldLockLogin() {
        SysUser user = createActiveUser("bob");
        user.setPassword(passwordEncoder.encode("correct"));

        when(sysUserMapper.findByUsername("bob")).thenReturn(Optional.of(user));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("auth:login:fail:bob")).thenReturn(5L);

        LoginRequest request = new LoginRequest();
        request.setUsername("bob");
        request.setPassword("wrong");

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("用户名或密码错误");
        verify(valueOperations).set("auth:login:lock:bob", "1", 15L, TimeUnit.MINUTES);
        ArgumentCaptor<OperationLog> logCaptor = ArgumentCaptor.forClass(OperationLog.class);
        verify(operationLogService).record(logCaptor.capture());
        assertThat(logCaptor.getValue().getAction()).isEqualTo("登录锁定");
    }

    @Test
    @DisplayName("登录失败次数过多时直接拒绝认证")
    void login_lockedUsername_shouldRejectBeforePasswordCheck() {
        when(redisTemplate.hasKey("auth:login:lock:alice")).thenReturn(true);

        LoginRequest request = new LoginRequest();
        request.setUsername("alice");
        request.setPassword("password");

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("登录失败次数过多");
        verify(sysUserMapper, never()).findByUsername(anyString());
        ArgumentCaptor<OperationLog> logCaptor = ArgumentCaptor.forClass(OperationLog.class);
        verify(operationLogService).record(logCaptor.capture());
        assertThat(logCaptor.getValue().getAction()).isEqualTo("登录锁定");
    }

    @Test
    @DisplayName("登录失败 - 账号已停用(status=0)")
    void login_inactiveUser_shouldThrow() {
        SysUser user = createActiveUser("inactive");
        user.setStatus(0);

        when(sysUserMapper.findByUsername("inactive")).thenReturn(Optional.of(user));

        LoginRequest request = new LoginRequest();
        request.setUsername("inactive");
        request.setPassword("password");

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("账号已停用");
    }

    @Test
    @DisplayName("登录 - ops_staff角色dataScope提权到3")
    void login_opsStaff_shouldEscalateDataScopeToAll() {
        SysUser user = createActiveUser("ops");

        SysRole role = new SysRole();
        role.setRoleCode("ops_staff");
        role.setDataScope(1);

        when(sysUserMapper.findByUsername("ops")).thenReturn(Optional.of(user));
        when(sysRoleMapper.findByUserId(user.getId())).thenReturn(List.of(role));
        stubJwtTokenGeneration();

        LoginRequest request = new LoginRequest();
        request.setUsername("ops");
        request.setPassword("password");

        LoginResponse response = authService.login(request);

        assertThat(response.getDataScope()).isEqualTo(3);
    }

    @Test
    @DisplayName("登录 - 角色编码通过用户域权限策略归一后计算dataScope")
    void login_shouldResolveDataScopeWithNormalizedRoleCodes() {
        SysUser user = createActiveUser("ops");

        SysRole role = new SysRole();
        role.setRoleCode(" OPS_STAFF ");
        role.setDataScope(1);

        when(sysUserMapper.findByUsername("ops")).thenReturn(Optional.of(user));
        when(sysRoleMapper.findByUserId(user.getId())).thenReturn(List.of(role));
        stubJwtTokenGeneration();

        LoginRequest request = new LoginRequest();
        request.setUsername("ops");
        request.setPassword("password");

        LoginResponse response = authService.login(request);

        assertThat(response.getDataScope()).isEqualTo(3);
    }

    @Test
    @DisplayName("待激活用户可登录并返回受限态标记")
    void login_pendingActivationUser_shouldReturnTokenWithPendingFlag() {
        SysUser user = createActiveUser("pending");
        user.setStatus(2);
        user.setForcePasswordChange(true);

        when(sysUserMapper.findByUsername("pending")).thenReturn(Optional.of(user));
        when(sysRoleMapper.findByUserId(user.getId())).thenReturn(List.of());
        stubJwtTokenGeneration();

        LoginRequest request = new LoginRequest();
        request.setUsername("pending");
        request.setPassword("password");

        LoginResponse response = authService.login(request);

        assertThat(response.getStatus()).isEqualTo(2);
        assertThat(response.getForcePasswordChange()).isTrue();
        assertThat(response.getPendingActivation()).isTrue();
    }

    @Test
    @DisplayName("登录 - admin角色dataScope提权到3")
    void login_adminRole_shouldEscalateDataScopeToAll() {
        SysUser user = createActiveUser("admin");

        SysRole role = new SysRole();
        role.setRoleCode("admin");
        role.setDataScope(2);

        when(sysUserMapper.findByUsername("admin")).thenReturn(Optional.of(user));
        when(sysRoleMapper.findByUserId(user.getId())).thenReturn(List.of(role));
        stubJwtTokenGeneration();

        LoginRequest request = new LoginRequest();
        request.setUsername("admin");
        request.setPassword("password");

        LoginResponse response = authService.login(request);

        assertThat(response.getDataScope()).isEqualTo(3);
    }

    @Test
    @DisplayName("登录 - status为null时抛出异常")
    void login_nullStatus_shouldThrow() {
        SysUser user = createActiveUser("nullstatus");
        user.setStatus(null);

        when(sysUserMapper.findByUsername("nullstatus")).thenReturn(Optional.of(user));

        LoginRequest request = new LoginRequest();
        request.setUsername("nullstatus");
        request.setPassword("password");

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("账号已停用");
    }

    // ==================== RefreshToken Tests ====================

    @Test
    @DisplayName("刷新令牌成功返回新令牌")
    void refreshToken_success_shouldReturnNewToken() {
        UUID userId = UUID.randomUUID();
        Claims claims = org.mockito.Mockito.mock(Claims.class);
        when(claims.getSubject()).thenReturn(userId.toString());
        when(claims.get("type", String.class)).thenReturn("refresh");
        when(jwtTokenProvider.parseClaims("valid.refresh.token")).thenReturn(claims);

        SysUser user = new SysUser();
        user.setId(userId);
        user.setStatus(1);
        user.setUsername("testuser");
        when(sysUserMapper.findActiveById(userId)).thenReturn(Optional.of(user));
        when(sysRoleMapper.findByUserId(userId)).thenReturn(List.of());
        when(jwtTokenProvider.getTokenHash("valid.refresh.token")).thenReturn("refreshHash");
        when(redisTemplate.hasKey("auth:refresh:refreshHash")).thenReturn(false);
        when(jwtTokenProvider.generateAccessToken(eq(userId), any(), any(Integer.class), any(), any(), any(Boolean.class))).thenReturn("new.access.token");
        when(jwtTokenProvider.generateRefreshToken(userId)).thenReturn("new.refresh.token");
        when(jwtTokenProvider.getExpireSeconds()).thenReturn(3600L);
        when(jwtTokenProvider.getRefreshExpireSeconds()).thenReturn(604800L);
        when(jwtTokenProvider.getRemainingSeconds("valid.refresh.token")).thenReturn(604800L);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        RefreshRequest request = new RefreshRequest();
        request.setRefreshToken("valid.refresh.token");

        RefreshResponse response = authService.refreshToken(request);

        assertThat(response.getAccessToken()).isEqualTo("new.access.token");
        assertThat(response.getRefreshToken()).isEqualTo("new.refresh.token");
        assertThat(response.getAccessTokenExpiresIn()).isEqualTo(3600L);
        assertThat(response.getRefreshExpiresIn()).isEqualTo(604800L);
        verify(valueOperations).set("auth:refresh:refreshHash", "1", 604800L, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("刷新令牌 - 角色编码通过用户域权限策略归一后计算dataScope")
    void refreshToken_shouldResolveDataScopeWithNormalizedRoleCodes() {
        UUID userId = UUID.randomUUID();
        Claims claims = org.mockito.Mockito.mock(Claims.class);
        when(claims.getSubject()).thenReturn(userId.toString());
        when(claims.get("type", String.class)).thenReturn("refresh");
        when(jwtTokenProvider.parseClaims("valid.refresh.token")).thenReturn(claims);

        SysUser user = new SysUser();
        user.setId(userId);
        user.setStatus(1);
        user.setUsername("admin");
        when(sysUserMapper.findActiveById(userId)).thenReturn(Optional.of(user));

        SysRole role = new SysRole();
        role.setRoleCode(" ADMIN ");
        role.setDataScope(1);
        when(sysRoleMapper.findByUserId(userId)).thenReturn(List.of(role));

        when(jwtTokenProvider.getTokenHash("valid.refresh.token")).thenReturn("refreshHash");
        when(redisTemplate.hasKey("auth:refresh:refreshHash")).thenReturn(false);
        when(jwtTokenProvider.generateAccessToken(eq(userId), any(), any(Integer.class), any(), any(), any(Boolean.class))).thenReturn("new.access.token");
        when(jwtTokenProvider.generateRefreshToken(userId)).thenReturn("new.refresh.token");
        when(jwtTokenProvider.getExpireSeconds()).thenReturn(3600L);
        when(jwtTokenProvider.getRefreshExpireSeconds()).thenReturn(604800L);
        when(jwtTokenProvider.getRemainingSeconds("valid.refresh.token")).thenReturn(604800L);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        RefreshRequest request = new RefreshRequest();
        request.setRefreshToken("valid.refresh.token");

        authService.refreshToken(request);

        ArgumentCaptor<Integer> dataScopeCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(jwtTokenProvider).generateAccessToken(eq(userId), any(), dataScopeCaptor.capture(), any(), any(), any(Boolean.class));
        assertThat(dataScopeCaptor.getValue()).isEqualTo(3);
    }

    @Test
    @DisplayName("刷新令牌失败 - 软删除用户不能继续续期")
    void refreshToken_softDeletedUser_shouldThrow() {
        UUID userId = UUID.randomUUID();
        Claims claims = org.mockito.Mockito.mock(Claims.class);
        when(claims.getSubject()).thenReturn(userId.toString());
        when(claims.get("type", String.class)).thenReturn("refresh");
        when(jwtTokenProvider.parseClaims("valid.refresh.token")).thenReturn(claims);

        SysUser deletedUser = new SysUser();
        deletedUser.setId(userId);
        deletedUser.setUsername("deleted-user");
        deletedUser.setStatus(1);
        deletedUser.setDeleted(1);
        when(sysUserMapper.findActiveById(userId)).thenReturn(Optional.of(deletedUser));
        when(jwtTokenProvider.getTokenHash("valid.refresh.token")).thenReturn("refreshHash");
        when(redisTemplate.hasKey("auth:refresh:refreshHash")).thenReturn(false);

        RefreshRequest request = new RefreshRequest();
        request.setRefreshToken("valid.refresh.token");

        assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("账号已停用");
    }

    @Test
    @DisplayName("刷新令牌失败 - 令牌无效")
    void refreshToken_invalidToken_shouldThrow() {
        when(jwtTokenProvider.parseClaims("invalid.token")).thenThrow(new RuntimeException("invalid token"));

        RefreshRequest request = new RefreshRequest();
        request.setRefreshToken("invalid.token");

        assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Refresh Token 无效或已过期");
    }

    // ==================== Logout Tests ====================

    @Test
    @DisplayName("登出成功将令牌加入黑名单并吊销刷新令牌")
    void logout_success_shouldBlacklistTokenAndRevokeRefresh() {
        UUID userId = UUID.randomUUID();
        Claims accessClaims = org.mockito.Mockito.mock(Claims.class);
        when(accessClaims.getSubject()).thenReturn(userId.toString());
        when(accessClaims.get("username", String.class)).thenReturn("alice");
        when(accessClaims.get("type", String.class)).thenReturn("access");
        when(jwtTokenProvider.parseClaims("valid.access.token")).thenReturn(accessClaims);
        when(jwtTokenProvider.getTokenHash("valid.access.token")).thenReturn("accessHash");
        when(jwtTokenProvider.getRemainingSeconds("valid.access.token")).thenReturn(3600L);

        Claims refreshClaims = org.mockito.Mockito.mock(Claims.class);
        when(refreshClaims.get("type", String.class)).thenReturn("refresh");
        when(jwtTokenProvider.parseClaims("valid.refresh.token")).thenReturn(refreshClaims);
        when(jwtTokenProvider.getTokenHash("valid.refresh.token")).thenReturn("refreshHash");
        when(jwtTokenProvider.getRemainingSeconds("valid.refresh.token")).thenReturn(604800L);
        when(redisTemplate.hasKey("auth:refresh:refreshHash")).thenReturn(false);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        LogoutRequest request = new LogoutRequest();
        request.setAccessToken("valid.access.token");
        request.setRefreshToken("valid.refresh.token");

        authService.logout(request);

        verify(valueOperations).set(eq("auth:blacklist:accessHash"), eq("1"), eq(3600L), eq(TimeUnit.SECONDS));
        verify(valueOperations).set(eq("auth:refresh:refreshHash"), eq("1"), eq(604800L), eq(TimeUnit.SECONDS));
        ArgumentCaptor<OperationLog> logCaptor = ArgumentCaptor.forClass(OperationLog.class);
        verify(operationLogService).record(logCaptor.capture());
        assertThat(logCaptor.getValue().getAction()).isEqualTo("登出");
        assertThat(logCaptor.getValue().getUserId()).isEqualTo(userId);
        assertThat(logCaptor.getValue().getUsername()).isEqualTo("alice");
    }

    @Test
    @DisplayName("登出 - access已过期仍应吊销refresh")
    void logout_expiredAccessToken_shouldStillRevokeRefreshToken() {
        UUID userId = UUID.randomUUID();
        Claims refreshClaims = org.mockito.Mockito.mock(Claims.class);
        when(refreshClaims.getSubject()).thenReturn(userId.toString());
        when(refreshClaims.get("type", String.class)).thenReturn("refresh");
        when(refreshClaims.get("username", String.class)).thenReturn("alice");
        when(jwtTokenProvider.parseClaims("valid.refresh.token")).thenReturn(refreshClaims);
        when(jwtTokenProvider.getTokenHash("valid.refresh.token")).thenReturn("refreshHash");
        when(jwtTokenProvider.getRemainingSeconds("valid.refresh.token")).thenReturn(604800L);
        when(redisTemplate.hasKey("auth:refresh:refreshHash")).thenReturn(false);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(jwtTokenProvider.parseClaims("expired.access.token")).thenThrow(new RuntimeException("expired"));

        LogoutRequest request = new LogoutRequest();
        request.setAccessToken("expired.access.token");
        request.setRefreshToken("valid.refresh.token");

        authService.logout(request);

        verify(valueOperations).set(eq("auth:refresh:refreshHash"), eq("1"), eq(604800L), eq(TimeUnit.SECONDS));
        ArgumentCaptor<OperationLog> logCaptor = ArgumentCaptor.forClass(OperationLog.class);
        verify(operationLogService).record(logCaptor.capture());
        assertThat(logCaptor.getValue().getAction()).isEqualTo("登出");
        assertThat(logCaptor.getValue().getUserId()).isEqualTo(userId);
        assertThat(logCaptor.getValue().getUsername()).isEqualTo("alice");
    }

    @Test
    @DisplayName("登出 - 缺少refresh token时拒绝")
    void logout_missingRefreshToken_shouldThrow() {
        LogoutRequest request = new LogoutRequest();
        request.setAccessToken("valid.access.token");

        assertThatThrownBy(() -> authService.logout(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("refreshToken 不能为空");

        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    @DisplayName("登出 - refresh token吊销后不能继续刷新")
    void refreshToken_revokedByLogout_shouldThrow() {
        UUID userId = UUID.randomUUID();
        Claims refreshClaims = org.mockito.Mockito.mock(Claims.class);
        when(refreshClaims.getSubject()).thenReturn(userId.toString());
        when(refreshClaims.get("type", String.class)).thenReturn("refresh");
        when(jwtTokenProvider.parseClaims("valid.refresh.token")).thenReturn(refreshClaims);
        when(jwtTokenProvider.getTokenHash("valid.refresh.token")).thenReturn("refreshHash");
        when(jwtTokenProvider.getRemainingSeconds("valid.refresh.token")).thenReturn(604800L);
        when(redisTemplate.hasKey("auth:refresh:refreshHash")).thenReturn(false, true);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        LogoutRequest logoutRequest = new LogoutRequest();
        logoutRequest.setRefreshToken("valid.refresh.token");
        authService.logout(logoutRequest);

        RefreshRequest refreshRequest = new RefreshRequest();
        refreshRequest.setRefreshToken("valid.refresh.token");

        assertThatThrownBy(() -> authService.refreshToken(refreshRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Refresh Token 已吊销");
    }

    // ==================== IsTokenBlacklisted Tests ====================

    @Test
    @DisplayName("令牌在黑名单中返回true")
    void isTokenBlacklisted_tokenInBlacklist_shouldReturnTrue() {
        when(redisTemplate.hasKey("auth:blacklist:blacklisted.token")).thenReturn(true);

        boolean result = authService.isTokenBlacklisted("blacklisted.token");

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("令牌不在黑名单中返回false")
    void isTokenBlacklisted_tokenNotInBlacklist_shouldReturnFalse() {
        when(redisTemplate.hasKey("auth:blacklist:valid.token")).thenReturn(false);

        boolean result = authService.isTokenBlacklisted("valid.token");

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("令牌黑名单Redis返回null时返回false")
    void isTokenBlacklisted_redisReturnsNull_shouldReturnFalse() {
        when(redisTemplate.hasKey("auth:blacklist:some.token")).thenReturn(null);

        boolean result = authService.isTokenBlacklisted("some.token");

        assertThat(result).isFalse();
    }
}
