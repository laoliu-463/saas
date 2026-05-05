package com.colonel.saas.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.common.exception.GlobalExceptionHandler;
import com.colonel.saas.entity.OperationLog;
import com.colonel.saas.service.OperationLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OperationLogControllerTest {

    @Mock
    private OperationLogService operationLogService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        OperationLogController controller = new OperationLogController(operationLogService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void page_returnsLogs() throws Exception {
        OperationLog log = new OperationLog();
        log.setId(UUID.randomUUID());
        log.setUsername("admin");
        log.setModule("系统配置");
        log.setAction("更新配置");

        Page<OperationLog> page = new Page<>(1, 20);
        page.setRecords(List.of(log));
        page.setTotal(1);

        when(operationLogService.findPage(
                eq("系统"),
                eq("更新"),
                eq("admin"),
                eq("PUT"),
                eq(null),
                eq(null),
                eq(1L),
                eq(20L)
        )).thenReturn(page);

        mockMvc.perform(get("/operation-logs")
                        .param("module", "系统")
                        .param("action", "更新")
                        .param("username", "admin")
                        .param("requestMethod", "PUT")
                        .param("page", "1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[0].username").value("admin"));

        verify(operationLogService).findPage("系统", "更新", "admin", "PUT", null, null, 1L, 20L);
    }
}
