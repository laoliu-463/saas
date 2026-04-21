package com.colonel.saas.auth.service;

import com.colonel.saas.auth.dto.LoginRequest;
import com.colonel.saas.auth.dto.LoginResponse;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.entity.SysRole;
import com.colonel.saas.entity.SysUser;
import com.colonel.saas.mapper.SysRoleMapper;
import com.colonel.saas.mapper.SysUserMapper;
import com.colonel.saas.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private SysUserMapper sysUserMapper;
    @Mock
    private SysRoleMapper sysRoleMapper;
    @Mock
    private JwtTokenProvider jwtTokenProvider;

    private PasswordEncoder passwordEncoder;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        authService = new AuthService(sysUserMapper, sysRoleMapper, jwtTokenProvider, passwordEncoder);
    }

    @Test
    void login_success_shouldReturnTokenAndUserInfo() {
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        String rawPassword = "password123";
        String encodedPassword = passwordEncoder.encode(rawPassword);

        SysUser user = new SysUser();
        user.setId(userId);
        user.setDeptId(deptId);
        user.setUsername("alice");
        user.setRealName("Alice");
        user.setPassword(encodedPassword);
        user.setStatus(1);

        SysRole role = new SysRole();
        role.setRoleCode("admin");
        role.setDataScope(3);

        when(sysUserMapper.findByUsername("alice")).thenReturn(Optional.of(user));
        when(sysRoleMapper.findByUserId(userId)).thenReturn(List.of(role));
        when(jwtTokenProvider.generateToken(any(), any(), any(Integer.class), any(), any())).thenReturn("jwt.token.here");
        when(jwtTokenProvider.getExpireSeconds()).thenReturn(3600L);

        LoginRequest request = new LoginRequest();
        request.setUsername("alice");
        request.setPassword(rawPassword);
        LoginResponse response = authService.login(request);

        assertThat(response.getToken()).isEqualTo("jwt.token.here");
        assertThat(response.getUserId()).isEqualTo(userId);
        assertThat(response.getUsername()).isEqualTo("alice");
        assertThat(response.getRoleCodes()).contains("admin");
        verify(sysUserMapper).updateById(any(SysUser.class));
    }

    @Test
    void login_userNotFound_shouldThrow() {
        when(sysUserMapper.findByUsername("nobody")).thenReturn(Optional.empty());

        LoginRequest request = new LoginRequest();
        request.setUsername("nobody");
        request.setPassword("password");

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("用户名或密码错误");
    }

    @Test
    void login_wrongPassword_shouldThrow() {
        UUID userId = UUID.randomUUID();
        SysUser user = new SysUser();
        user.setId(userId);
        user.setPassword(passwordEncoder.encode("correct"));
        user.setStatus(1);

        when(sysUserMapper.findByUsername("bob")).thenReturn(Optional.of(user));

        LoginRequest request = new LoginRequest();
        request.setUsername("bob");
        request.setPassword("wrong");

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("用户名或密码错误");
    }

    @Test
    void login_inactiveUser_shouldThrow() {
        UUID userId = UUID.randomUUID();
        SysUser user = new SysUser();
        user.setId(userId);
        user.setPassword(passwordEncoder.encode("password"));
        user.setStatus(0);

        when(sysUserMapper.findByUsername("inactive")).thenReturn(Optional.of(user));

        LoginRequest request = new LoginRequest();
        request.setUsername("inactive");
        request.setPassword("password");

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("用户名或密码错误");
    }
}
