package com.colonel.saas.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.auth.dto.SysRoleCreateRequest;
import com.colonel.saas.auth.dto.SysRoleUpdateRequest;
import com.colonel.saas.auth.service.SysRoleService;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.exception.GlobalExceptionHandler;
import com.colonel.saas.vo.SysRoleVO;
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
import static org.mockito.ArgumentMatchers.anyLong;
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
class SysRoleControllerTest {

    @Mock
    private SysRoleService sysRoleService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private final UUID roleId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        SysRoleController controller = new SysRoleController(sysRoleService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void page_returnsRoleList() throws Exception {
        SysRoleVO vo = new SysRoleVO();
        vo.setId(roleId);
        vo.setRoleCode("TEST_ROLE");

        Page<SysRoleVO> page = new Page<>(1, 10);
        page.setRecords(List.of(vo));
        page.setTotal(1);

        when(sysRoleService.findPage(anyLong(), anyLong(), any(), any())).thenReturn(page);

        mockMvc.perform(get("/sys/roles/page")
                        .requestAttr("userId", UUID.randomUUID())
                        .requestAttr("deptId", UUID.randomUUID())
                        .requestAttr("dataScope", DataScope.ALL)
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void detail_returnsRole() throws Exception {
        SysRoleVO vo = new SysRoleVO();
        vo.setId(roleId);
        vo.setRoleCode("TEST_ROLE");
        vo.setRoleName("测试角色");

        when(sysRoleService.getById(roleId)).thenReturn(vo);

        mockMvc.perform(get("/sys/roles/{id}", roleId)
                        .requestAttr("userId", UUID.randomUUID())
                        .requestAttr("dataScope", DataScope.ALL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roleCode").value("TEST_ROLE"));
    }

    @Test
    void all_returnsEnabledRoles() throws Exception {
        SysRoleVO vo = new SysRoleVO();
        vo.setId(roleId);
        vo.setStatus(1);

        when(sysRoleService.findAllEnabled()).thenReturn(List.of(vo));

        mockMvc.perform(get("/sys/roles/all")
                        .requestAttr("userId", UUID.randomUUID())
                        .requestAttr("dataScope", DataScope.ALL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value(1));
    }

    @Test
    void create_returnsCreatedRole() throws Exception {
        SysRoleCreateRequest request = new SysRoleCreateRequest(
                "NEW_ROLE", "新角色", 1, 1, "备注");
        SysRoleVO vo = new SysRoleVO();
        vo.setId(UUID.randomUUID());
        vo.setRoleCode("NEW_ROLE");

        when(sysRoleService.create(any())).thenReturn(vo);

        mockMvc.perform(post("/sys/roles")
                        .requestAttr("userId", UUID.randomUUID())
                        .requestAttr("dataScope", DataScope.ALL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roleCode").value("NEW_ROLE"));
    }

    @Test
    void update_returnsUpdatedRole() throws Exception {
        SysRoleUpdateRequest request = new SysRoleUpdateRequest(
                "UPDATED_ROLE", "更新角色", 2, 1, "更新备注");
        SysRoleVO vo = new SysRoleVO();
        vo.setId(roleId);
        vo.setRoleName("更新角色");

        when(sysRoleService.update(any(), any())).thenReturn(vo);

        mockMvc.perform(put("/sys/roles/{id}", roleId)
                        .requestAttr("userId", UUID.randomUUID())
                        .requestAttr("dataScope", DataScope.ALL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roleName").value("更新角色"));
    }

    @Test
    void delete_successfullyDeletesRole() throws Exception {
        doNothing().when(sysRoleService).delete(any());

        mockMvc.perform(delete("/sys/roles/{id}", roleId)
                        .requestAttr("userId", UUID.randomUUID())
                        .requestAttr("dataScope", DataScope.ALL))
                .andExpect(status().isOk());

        verify(sysRoleService).delete(any());
    }
}

