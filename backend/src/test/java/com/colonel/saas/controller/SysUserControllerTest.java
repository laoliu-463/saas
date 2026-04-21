package com.colonel.saas.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.auth.dto.SysUserAssignRolesRequest;
import com.colonel.saas.auth.dto.SysUserCreateRequest;
import com.colonel.saas.auth.dto.SysUserResetPasswordRequest;
import com.colonel.saas.auth.dto.SysUserUpdateRequest;
import com.colonel.saas.auth.service.SysUserService;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.exception.GlobalExceptionHandler;
import com.colonel.saas.vo.SysUserVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SysUserControllerTest {

    @Mock
    private SysUserService sysUserService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private final UUID userId = UUID.randomUUID();
    private final UUID deptId = UUID.randomUUID();
    private final UUID testUserId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        SysUserController controller = new SysUserController(sysUserService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void page_returnsUserList() throws Exception {
        SysUserVO vo = new SysUserVO();
        vo.setId(testUserId);
        vo.setUsername("testuser");

        Page<SysUserVO> page = new Page<>(1, 10);
        page.setRecords(List.of(vo));
        page.setTotal(1);

        when(sysUserService.findPage(any(), any(), any())).thenReturn(page);

        mockMvc.perform(get("/sys/users/page")
                        .requestAttr("userId", userId)
                        .requestAttr("dataScope", DataScope.ALL)
                        .param("pageNo", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(sysUserService).findPage(any(), any(), any());
    }

    @Test
    void detail_returnsUser() throws Exception {
        SysUserVO vo = new SysUserVO();
        vo.setId(testUserId);
        vo.setUsername("testuser");

        when(sysUserService.getById(any(), any(), any())).thenReturn(vo);

        mockMvc.perform(get("/sys/users/{id}", testUserId)
                        .requestAttr("userId", userId)
                        .requestAttr("dataScope", DataScope.ALL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("testuser"));
    }

    @Test
    void create_returnsCreatedUser() throws Exception {
        SysUserCreateRequest request = new SysUserCreateRequest(
                "newuser", "password123", "新用户", deptId, List.of(UUID.randomUUID()));

        SysUserVO vo = new SysUserVO();
        vo.setId(UUID.randomUUID());
        vo.setUsername("newuser");

        when(sysUserService.create(any())).thenReturn(vo);

        mockMvc.perform(post("/sys/users")
                        .requestAttr("userId", userId)
                        .requestAttr("dataScope", DataScope.ALL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("newuser"));
    }

    @Test
    void update_returnsUpdatedUser() throws Exception {
        SysUserUpdateRequest request = new SysUserUpdateRequest(
                "新名字", "13900139000", "new@test.com", 1);

        SysUserVO vo = new SysUserVO();
        vo.setId(testUserId);
        vo.setRealName("新名字");

        when(sysUserService.update(any(), any(), any(), any())).thenReturn(vo);

        mockMvc.perform(put("/sys/users/{id}", testUserId)
                        .requestAttr("userId", userId)
                        .requestAttr("dataScope", DataScope.ALL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.realName").value("新名字"));
    }

    @Test
    void delete_successfullyDeletesUser() throws Exception {
        doNothing().when(sysUserService).delete(any(), any(), any());

        mockMvc.perform(delete("/sys/users/{id}", testUserId)
                        .requestAttr("userId", userId)
                        .requestAttr("dataScope", DataScope.ALL))
                .andExpect(status().isOk());

        verify(sysUserService).delete(any(), any(), any());
    }

    @Test
    void resetPassword_successfullyResetsPassword() throws Exception {
        SysUserResetPasswordRequest request = new SysUserResetPasswordRequest("NewPassword123");
        doNothing().when(sysUserService).resetPassword(any(), any(), any(), any());

        mockMvc.perform(put("/sys/users/{id}/reset-password", testUserId)
                        .requestAttr("userId", userId)
                        .requestAttr("dataScope", DataScope.ALL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void assignRoles_successfullyAssignsRoles() throws Exception {
        SysUserAssignRolesRequest request = new SysUserAssignRolesRequest(List.of(UUID.randomUUID()));
        doNothing().when(sysUserService).assignRoles(any(), any(), any(), any());

        mockMvc.perform(put("/sys/users/{id}/roles", testUserId)
                        .requestAttr("userId", userId)
                        .requestAttr("dataScope", DataScope.ALL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }
}

