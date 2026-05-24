package com.colonel.saas.service;

import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.config.ConfigChangedEventFactory;
import com.colonel.saas.config.ConfigDefinitionRegistry;
import com.colonel.saas.config.RuleCenterSchemaRegistry;
import com.colonel.saas.domain.event.DomainEventOutboxService;
import com.colonel.saas.entity.SystemConfig;
import com.colonel.saas.entity.SystemConfigChangeLog;
import com.colonel.saas.event.ConfigChangedApplicationEvent;
import com.colonel.saas.mapper.SystemConfigChangeLogMapper;
import com.colonel.saas.mapper.SystemConfigMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SysConfigServiceTest {

    @Mock
    private SystemConfigMapper systemConfigMapper;
    @Mock
    private SystemConfigChangeLogMapper systemConfigChangeLogMapper;
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;
    @Mock
    private DomainEventOutboxService domainEventOutboxService;

    private SysConfigService sysConfigService;
    private OperationLogService operationLogService;

    @BeforeEach
    void setUp() {
        operationLogService = mock(OperationLogService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        ConfigDefinitionRegistry configDefinitionRegistry = new ConfigDefinitionRegistry(objectMapper);
        RuleCenterSchemaRegistry ruleCenterSchemaRegistry = new RuleCenterSchemaRegistry(configDefinitionRegistry);
        ConfigChangedEventFactory configChangedEventFactory = new ConfigChangedEventFactory(ruleCenterSchemaRegistry);
        sysConfigService = new SysConfigService(
                systemConfigMapper,
                systemConfigChangeLogMapper,
                operationLogService,
                configDefinitionRegistry,
                configChangedEventFactory,
                domainEventOutboxService,
                applicationEventPublisher
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
    void create_shouldPersistConfigWriteOutboxAndPublishEvent() {
        UUID userId = UUID.randomUUID();
        SystemConfig payload = new SystemConfig();
        payload.setConfigKey("custom.visible");
        payload.setConfigValue("on");
        when(systemConfigMapper.findByConfigKey("custom.visible")).thenReturn(Optional.empty());

        SystemConfig created = sysConfigService.create(payload, userId);

        assertThat(created.getId()).isNotNull();
        assertThat(created.getConfigVersion()).isEqualTo(1);
        verify(systemConfigMapper).insert(created);
        verify(domainEventOutboxService).saveConfigChangedEvent(any(), org.mockito.ArgumentMatchers.eq(userId));
        verify(applicationEventPublisher).publishEvent(any(ConfigChangedApplicationEvent.class));
        ArgumentCaptor<SystemConfigChangeLog> logCaptor = ArgumentCaptor.forClass(SystemConfigChangeLog.class);
        verify(systemConfigChangeLogMapper).insert(logCaptor.capture());
        assertThat(logCaptor.getValue().getChangeAction()).isEqualTo("CREATE");
        assertThat(logCaptor.getValue().getEventId()).isNotNull();
    }

    @Test
    void batchUpdateByKeys_shouldWriteOutboxAndPublishEvent() {
        UUID userId = UUID.randomUUID();
        SystemConfig existing = new SystemConfig();
        existing.setId(UUID.randomUUID());
        existing.setConfigKey("sample.restrict_days");
        existing.setConfigValue("7");
        existing.setConfigVersion(1);
        when(systemConfigMapper.findByConfigKey("sample.restrict_days")).thenReturn(Optional.of(existing));

        SysConfigService.BatchUpdateConfigResult result = sysConfigService.batchUpdateByKeys(
                Map.of("sample.restrict_days", "9"),
                userId,
                "admin",
                SysConfigService.CHANGE_SOURCE_RULE_CENTER,
                "调整寄样限制");

        assertThat(result.changedKeys()).containsExactly("sample.restrict_days");
        verify(domainEventOutboxService).saveConfigChangedEvent(any(), org.mockito.ArgumentMatchers.eq(userId));
        verify(applicationEventPublisher).publishEvent(any(ConfigChangedApplicationEvent.class));
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
}
