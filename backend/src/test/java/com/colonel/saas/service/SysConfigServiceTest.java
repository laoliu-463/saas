package com.colonel.saas.service;

import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.config.ConfigDefinitionRegistry;
import com.colonel.saas.entity.SystemConfig;
import com.colonel.saas.mapper.SystemConfigMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SysConfigServiceTest {

    @Mock
    private SystemConfigMapper systemConfigMapper;

    private SysConfigService sysConfigService;
    private OperationLogService operationLogService;
    private BusinessRuleConfigService businessRuleConfigService;

    @BeforeEach
    void setUp() {
        operationLogService = mock(OperationLogService.class);
        businessRuleConfigService = mock(BusinessRuleConfigService.class);
        sysConfigService = new SysConfigService(
                systemConfigMapper,
                operationLogService,
                new ConfigDefinitionRegistry(new ObjectMapper()),
                businessRuleConfigService
        );
    }

    @Test
    void findGrouped_shouldMaskSensitiveValuesForNonAdmin() {
        SystemConfig tokenConfig = new SystemConfig();
        tokenConfig.setId(UUID.randomUUID());
        tokenConfig.setConfigGroup("douyin");
        tokenConfig.setConfigKey("douyin.access_token");
        tokenConfig.setConfigName("Access Token");
        tokenConfig.setConfigValue("secret-token");
        tokenConfig.setStatus(1);

        SystemConfig ruleConfig = new SystemConfig();
        ruleConfig.setId(UUID.randomUUID());
        ruleConfig.setConfigGroup("talent");
        ruleConfig.setConfigKey("talent.protection_days");
        ruleConfig.setConfigName("达人保护期");
        ruleConfig.setConfigValue("30");
        ruleConfig.setStatus(1);

        when(systemConfigMapper.selectList(any())).thenReturn(List.of(tokenConfig, ruleConfig));

        Map<String, List<SystemConfig>> grouped = sysConfigService.findGrouped(false);

        assertThat(grouped.get("douyin")).singleElement().extracting(SystemConfig::getConfigValue).isEqualTo("******");
        assertThat(grouped.get("talent")).singleElement().extracting(SystemConfig::getConfigValue).isEqualTo("30");
    }

    @Test
    void update_shouldRejectInvalidRegisteredIntegerConfig() {
        UUID id = UUID.randomUUID();
        SystemConfig existing = new SystemConfig();
        existing.setId(id);
        existing.setConfigKey("sample.restrict_days");
        existing.setConfigValue("7");
        when(systemConfigMapper.selectById(id)).thenReturn(existing);

        SystemConfig payload = new SystemConfig();
        payload.setConfigValue("-1");

        assertThatThrownBy(() -> sysConfigService.update(id, payload, UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("寄样限制天数");
    }

    @Test
    void create_shouldRejectInvalidJsonConfig() {
        SystemConfig payload = new SystemConfig();
        payload.setConfigKey("sample.default_standard");
        payload.setConfigValue("{\"min_30day_sales\":-1}");

        assertThatThrownBy(() -> sysConfigService.create(payload, UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("min_30day_sales");
    }

    @Test
    void update_shouldInvalidateBusinessRuleCacheForConfigKey() {
        UUID id = UUID.randomUUID();
        SystemConfig existing = new SystemConfig();
        existing.setId(id);
        existing.setConfigKey("sample.restrict_days");
        existing.setConfigValue("7");
        when(systemConfigMapper.selectById(id)).thenReturn(existing);

        SystemConfig payload = new SystemConfig();
        payload.setConfigValue("9");

        sysConfigService.update(id, payload, UUID.randomUUID());

        verify(businessRuleConfigService).invalidate("sample.restrict_days");
    }

    @Test
    void update_shouldInvalidateOldAndNewConfigKeyWhenRenamed() {
        UUID id = UUID.randomUUID();
        SystemConfig existing = new SystemConfig();
        existing.setId(id);
        existing.setConfigKey("sample.restrict_days");
        existing.setConfigValue("7");
        when(systemConfigMapper.selectById(id)).thenReturn(existing);

        SystemConfig payload = new SystemConfig();
        payload.setConfigKey("sample.restrict_days_v2");
        payload.setConfigValue("9");
        when(systemConfigMapper.findByConfigKey("sample.restrict_days_v2")).thenReturn(java.util.Optional.empty());

        sysConfigService.update(id, payload, UUID.randomUUID());

        verify(businessRuleConfigService, times(1)).invalidate("sample.restrict_days");
        verify(businessRuleConfigService, times(1)).invalidate("sample.restrict_days_v2");
    }
}
