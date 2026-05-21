package com.colonel.saas.controller;

import com.colonel.saas.auth.dto.SysDeptCreateRequest;
import com.colonel.saas.auth.service.SysDeptService;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.exception.GlobalExceptionHandler;
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

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private final UUID deptId = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        SysDeptController controller = new SysDeptController(sysDeptService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void tree_returnsDeptTree() throws Exception {
        SysDeptVO vo = new SysDeptVO();
        vo.setId(deptId);
        vo.setDeptCode("BIZ");
        vo.setDeptName("招商组");
        when(sysDeptService.findTree()).thenReturn(List.of(vo));

        mockMvc.perform(get("/depts/tree")
                        .requestAttr("userId", UUID.randomUUID())
                        .requestAttr("deptId", UUID.randomUUID())
                        .requestAttr("dataScope", DataScope.ALL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].deptName").value("招商组"));
    }

    @Test
    void create_returnsCreatedDept() throws Exception {
        SysDeptVO vo = new SysDeptVO();
        vo.setId(deptId);
        vo.setDeptCode("BIZ_A");
        vo.setDeptName("招商一组");
        when(sysDeptService.create(any(SysDeptCreateRequest.class), any())).thenReturn(vo);

        SysDeptCreateRequest request = new SysDeptCreateRequest(
                null, "BIZ_A", "招商一组", null, null, null, 10, 1, null);

        mockMvc.perform(post("/depts")
                        .requestAttr("userId", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deptCode").value("BIZ_A"));
    }

    @Test
    void delete_invokesService() throws Exception {
        doNothing().when(sysDeptService).delete(eq(deptId), any());

        mockMvc.perform(delete("/depts/{id}", deptId)
                        .requestAttr("userId", UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(sysDeptService).delete(eq(deptId), any());
    }
}
