package com.colonel.saas.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.common.exception.GlobalExceptionHandler;
import com.colonel.saas.domain.user.policy.CurrentUserPermissionPolicy;
import com.colonel.saas.entity.SystemConfig;
import com.colonel.saas.service.SysConfigService;
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
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
class SysConfigControllerTest {

    @Mock
    private SysConfigService sysConfigService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private final UUID configId = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private final UUID userId = UUID.fromString("55555555-5555-5555-5555-555555555555");

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new SysConfigController(sysConfigService, new CurrentUserPermissionPolicy()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void page_shouldReturnPagedConfigs() throws Exception {
        SystemConfig config = config("sample.restrict_days", "7");
        Page<SystemConfig> page = new Page<>(1, 20);
        page.setTotal(1);
        page.setRecords(List.of(config));
        when(sysConfigService.findPage("sample", "restrict", 1, 20)).thenReturn(page);

        mockMvc.perform(get("/configs")
                        .param("configGroup", "sample")
                        .param("keyword", "restrict"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].configKey").value("sample.restrict_days"));
    }

    @Test
    void grouped_shouldPassAdminFlagFromListRoleAttribute() throws Exception {
        SystemConfig config = config("douyin.access_token", "secret");
        when(sysConfigService.findGrouped(true)).thenReturn(Map.of("douyin", List.of(config)));

        mockMvc.perform(get("/configs/grouped")
                        .requestAttr("roleCodes", List.of("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.douyin[0].configKey").value("douyin.access_token"));
    }

    @Test
    void grouped_shouldUseUserPermissionPolicyNormalizationForAdminRoleAttribute() throws Exception {
        when(sysConfigService.findGrouped(true)).thenReturn(Map.of());

        mockMvc.perform(get("/configs/grouped")
                        .requestAttr("roleCodes", List.of(" ADMIN ")))
                .andExpect(status().isOk());

        verify(sysConfigService).findGrouped(true);
    }

    @Test
    void grouped_shouldPassAdminFlagFromStringRoleAttributeAndFalseWhenMissing() throws Exception {
        when(sysConfigService.findGrouped(true)).thenReturn(Map.of());
        when(sysConfigService.findGrouped(false)).thenReturn(Map.of("sample", List.of(config("sample.restrict_days", "7"))));

        mockMvc.perform(get("/configs/grouped")
                        .requestAttr("roleCodes", "admin,channel_staff"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/configs/grouped"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sample[0].configValue").value("7"));
    }

    @Test
    void detailCreateUpdateDelete_shouldDelegateToService() throws Exception {
        SystemConfig config = config("sample.timeout_days", "14");
        when(sysConfigService.getById(configId)).thenReturn(config);
        when(sysConfigService.create(any(SystemConfig.class), eq(userId))).thenReturn(config);
        when(sysConfigService.update(eq(configId), any(SystemConfig.class), eq(userId))).thenReturn(config);
        doNothing().when(sysConfigService).delete(configId, userId);

        mockMvc.perform(get("/configs/{id}", configId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.configKey").value("sample.timeout_days"));
        mockMvc.perform(post("/configs")
                        .requestAttr("userId", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(config)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.configValue").value("14"));
        mockMvc.perform(put("/configs/{id}", configId)
                        .requestAttr("userId", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(config)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.configValue").value("14"));
        mockMvc.perform(delete("/configs/{id}", configId)
                        .requestAttr("userId", userId))
                .andExpect(status().isOk());

        verify(sysConfigService).delete(configId, userId);
    }

    private static SystemConfig config(String key, String value) {
        SystemConfig config = new SystemConfig();
        config.setId(UUID.randomUUID());
        config.setConfigGroup(key.substring(0, key.indexOf('.')));
        config.setConfigKey(key);
        config.setConfigName(key);
        config.setConfigValue(value);
        return config;
    }
}
