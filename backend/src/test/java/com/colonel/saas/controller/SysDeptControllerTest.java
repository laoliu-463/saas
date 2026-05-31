package com.colonel.saas.controller;

import com.colonel.saas.auth.dto.SysDeptCreateRequest;
import com.colonel.saas.auth.service.SysDeptService;
import com.colonel.saas.auth.service.SysUserService;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.exception.GlobalExceptionHandler;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.vo.SysDeptVO;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SysDeptControllerTest {

    @Mock
    private SysDeptService sysDeptService;
    @Mock
    private SysUserService sysUserService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private final UUID deptId = UUID.randomUUID();
    private final UUID adminId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        SysDeptController controller = new SysDeptController(sysDeptService, sysUserService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void list_returnsActiveDepts() throws Exception {
        SysDeptVO dept = new SysDeptVO();
        dept.setId(deptId);
        dept.setDeptCode("BIZ");
        dept.setDeptName("招商组");
        dept.setDeptType("recruiter");

        when(sysDeptService.findAll()).thenReturn(List.of(dept));

        mockMvc.perform(get("/depts")
                        .requestAttr("userId", adminId)
                        .requestAttr("roleCodes", List.of(RoleCodes.CHANNEL_STAFF)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].deptCode").value("BIZ"));
    }

    @Test
    void create_returnsDeptVo() throws Exception {
        SysDeptVO vo = new SysDeptVO();
        vo.setId(deptId);
        vo.setDeptCode("BIZ_EAST");
        vo.setDeptType("recruiter");

        SysDeptCreateRequest request = new SysDeptCreateRequest(
                null, "BIZ_EAST", "招商一组", null,
                null, null, 10, 1, null, "recruiter", null);

        when(sysDeptService.create(any(), eq(adminId))).thenReturn(vo);

        mockMvc.perform(post("/depts")
                        .requestAttr("userId", adminId)
                        .requestAttr("roleCodes", List.of(RoleCodes.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deptCode").value("BIZ_EAST"));

        verify(sysDeptService).create(any(), eq(adminId));
    }

    @Test
    void delete_invokesService() throws Exception {
        doNothing().when(sysDeptService).delete(deptId, adminId, List.of(RoleCodes.ADMIN));

        mockMvc.perform(delete("/depts/{id}", deptId)
                        .requestAttr("userId", adminId)
                        .requestAttr("roleCodes", List.of(RoleCodes.ADMIN)))
                .andExpect(status().isOk());

        verify(sysDeptService).delete(deptId, adminId, List.of(RoleCodes.ADMIN));
    }
}
