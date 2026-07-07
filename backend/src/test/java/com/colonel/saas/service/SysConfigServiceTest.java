package com.colonel.saas.service;

import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.config.ConfigChangedEventFactory;
import com.colonel.saas.config.ConfigDefinitionRegistry;
import com.colonel.saas.config.RuleCenterSchemaRegistry;
import com.colonel.saas.domain.event.ConfigChangedEventPayload;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
    @Mock
    private com.colonel.saas.domain.config.event.ConfigDomainEventPublisher configDomainEventPublisher;

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
                applicationEventPublisher,
                configDomainEventPublisher
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
        ArgumentCaptor<ConfigChangedEventPayload> payloadCaptor = ArgumentCaptor.forClass(ConfigChangedEventPayload.class);
        verify(domainEventOutboxService).saveConfigChangedEvent(payloadCaptor.capture(), eq(userId));
        verify(applicationEventPublisher).publishEvent(any(ConfigChangedApplicationEvent.class));
        ArgumentCaptor<SystemConfigChangeLog> logCaptor = ArgumentCaptor.forClass(SystemConfigChangeLog.class);
        verify(systemConfigChangeLogMapper).insert(logCaptor.capture());
        assertThat(logCaptor.getValue().getChangeAction()).isEqualTo("CREATE");
        assertThat(logCaptor.getValue().getConfigId()).isEqualTo(created.getId());
        assertThat(logCaptor.getValue().getConfigKey()).isEqualTo("custom.visible");
        assertThat(logCaptor.getValue().getOldValue()).isNull();
        assertThat(logCaptor.getValue().getNewValue()).isEqualTo("on");
        assertThat(logCaptor.getValue().getSource()).isEqualTo(SysConfigService.CHANGE_SOURCE_API);
        assertThat(logCaptor.getValue().getOperatorId()).isEqualTo(userId);
        assertThat(logCaptor.getValue().getConfigVersion()).isEqualTo(1);
        assertThat(logCaptor.getValue().getEventId()).isNotNull();
        ConfigChangedEventPayload eventPayload = payloadCaptor.getValue();
        assertThat(eventPayload.operatorId()).isEqualTo(userId);
        assertThat(eventPayload.source()).isEqualTo(SysConfigService.CHANGE_SOURCE_API);
        assertThat(eventPayload.items()).singleElement()
                .satisfies(item -> {
                    assertThat(item.configKey()).isEqualTo("custom.visible");
                    assertThat(item.oldValue()).isNull();
                    assertThat(item.newValue()).isEqualTo("on");
                    assertThat(item.configVersion()).isEqualTo(1);
                });
    }

    @Test
    void create_shouldRejectBlankConfigKeyWithoutWritesAuditOrEvents() {
        UUID userId = UUID.randomUUID();
        SystemConfig payload = new SystemConfig();
        payload.setConfigValue("on");

        assertThatThrownBy(() -> sysConfigService.create(payload, userId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("配置键不能为空");

        verifyNoInteractions(
                systemConfigMapper,
                systemConfigChangeLogMapper,
                operationLogService,
                domainEventOutboxService,
                applicationEventPublisher,
                configDomainEventPublisher);
    }

    @Test
    void create_shouldRejectDuplicateConfigKeyWithoutWritesAuditOrEvents() {
        UUID userId = UUID.randomUUID();
        SystemConfig existing = new SystemConfig();
        existing.setId(UUID.randomUUID());
        existing.setConfigKey("sample.restrict_days");
        existing.setConfigValue("7");
        SystemConfig payload = new SystemConfig();
        payload.setConfigKey("sample.restrict_days");
        payload.setConfigValue("9");
        when(systemConfigMapper.findByConfigKey("sample.restrict_days")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> sysConfigService.create(payload, userId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("配置键已存在");

        verify(systemConfigMapper).findByConfigKey("sample.restrict_days");
        verify(systemConfigMapper, never()).insert(any());
        verifyNoInteractions(
                systemConfigChangeLogMapper,
                operationLogService,
                domainEventOutboxService,
                applicationEventPublisher,
                configDomainEventPublisher);
    }

    @Test
    void update_shouldBumpVersionPersistAuditPayloadAndPublishEvent() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        SystemConfig existing = new SystemConfig();
        existing.setId(id);
        existing.setConfigKey("sample.restrict_days");
        existing.setConfigValue("7");
        existing.setConfigType("INTEGER");
        existing.setConfigVersion(1);
        when(systemConfigMapper.selectById(id)).thenReturn(existing);

        SystemConfig payload = new SystemConfig();
        payload.setConfigValue("9");

        SystemConfig updated = sysConfigService.update(id, payload, userId);

        assertThat(updated.getConfigValue()).isEqualTo("9");
        assertThat(updated.getConfigVersion()).isEqualTo(2);
        assertThat(updated.getUpdateBy()).isEqualTo(userId);
        verify(systemConfigMapper).updateById(existing);

        ArgumentCaptor<SystemConfigChangeLog> logCaptor = ArgumentCaptor.forClass(SystemConfigChangeLog.class);
        verify(systemConfigChangeLogMapper).insert(logCaptor.capture());
        SystemConfigChangeLog log = logCaptor.getValue();
        assertThat(log.getConfigId()).isEqualTo(id);
        assertThat(log.getConfigKey()).isEqualTo("sample.restrict_days");
        assertThat(log.getChangeAction()).isEqualTo("UPDATE");
        assertThat(log.getOldValue()).isEqualTo("7");
        assertThat(log.getNewValue()).isEqualTo("9");
        assertThat(log.getSource()).isEqualTo(SysConfigService.CHANGE_SOURCE_API);
        assertThat(log.getOperatorId()).isEqualTo(userId);
        assertThat(log.getEventId()).isNotNull();
        assertThat(log.getConfigVersion()).isEqualTo(2);

        ArgumentCaptor<ConfigChangedEventPayload> payloadCaptor = ArgumentCaptor.forClass(ConfigChangedEventPayload.class);
        verify(domainEventOutboxService).saveConfigChangedEvent(payloadCaptor.capture(), eq(userId));
        ConfigChangedEventPayload eventPayload = payloadCaptor.getValue();
        assertThat(eventPayload.operatorId()).isEqualTo(userId);
        assertThat(eventPayload.source()).isEqualTo(SysConfigService.CHANGE_SOURCE_API);
        assertThat(eventPayload.items()).singleElement()
                .satisfies(item -> {
                    assertThat(item.configKey()).isEqualTo("sample.restrict_days");
                    assertThat(item.oldValue()).isEqualTo("7");
                    assertThat(item.newValue()).isEqualTo("9");
                    assertThat(item.configVersion()).isEqualTo(2);
                });
        verify(applicationEventPublisher).publishEvent(any(ConfigChangedApplicationEvent.class));
    }

    @Test
    void delete_shouldPersistAuditVersionFromLoadedConfigAfterSoftDelete() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        SystemConfig existing = new SystemConfig();
        existing.setId(id);
        existing.setConfigKey("custom.visible");
        existing.setConfigValue("on");
        existing.setConfigType("STRING");
        existing.setConfigVersion(3);
        when(systemConfigMapper.selectById(id)).thenReturn(existing);
        when(systemConfigMapper.softDeleteById(id, userId)).thenReturn(1);

        sysConfigService.delete(id, userId);

        ArgumentCaptor<SystemConfigChangeLog> logCaptor = ArgumentCaptor.forClass(SystemConfigChangeLog.class);
        verify(systemConfigChangeLogMapper).insert(logCaptor.capture());
        SystemConfigChangeLog log = logCaptor.getValue();
        assertThat(log.getConfigId()).isEqualTo(id);
        assertThat(log.getConfigKey()).isEqualTo("custom.visible");
        assertThat(log.getChangeAction()).isEqualTo("DELETE");
        assertThat(log.getOldValue()).isEqualTo("on");
        assertThat(log.getNewValue()).isNull();
        assertThat(log.getSource()).isEqualTo(SysConfigService.CHANGE_SOURCE_API);
        assertThat(log.getOperatorId()).isEqualTo(userId);
        assertThat(log.getConfigVersion()).isEqualTo(3);

        ArgumentCaptor<ConfigChangedEventPayload> payloadCaptor = ArgumentCaptor.forClass(ConfigChangedEventPayload.class);
        verify(domainEventOutboxService).saveConfigChangedEvent(payloadCaptor.capture(), eq(userId));
        assertThat(payloadCaptor.getValue().items()).singleElement()
                .satisfies(item -> {
                    assertThat(item.configKey()).isEqualTo("custom.visible");
                    assertThat(item.oldValue()).isEqualTo("on");
                    assertThat(item.newValue()).isNull();
                    assertThat(item.configVersion()).isEqualTo(3);
                });
    }

    @Test
    void delete_shouldRejectWhenSoftDeleteMissesWithoutAuditOrEvents() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        SystemConfig existing = new SystemConfig();
        existing.setId(id);
        existing.setConfigKey("custom.visible");
        existing.setConfigValue("on");
        existing.setConfigType("STRING");
        existing.setConfigVersion(3);
        when(systemConfigMapper.selectById(id)).thenReturn(existing);
        when(systemConfigMapper.softDeleteById(id, userId)).thenReturn(0);

        assertThatThrownBy(() -> sysConfigService.delete(id, userId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("配置项不存在");

        verify(systemConfigMapper).selectById(id);
        verify(systemConfigMapper).softDeleteById(id, userId);
        verifyNoInteractions(
                systemConfigChangeLogMapper,
                operationLogService,
                domainEventOutboxService,
                applicationEventPublisher,
                configDomainEventPublisher);
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
        assertThat(existing.getConfigValue()).isEqualTo("9");
        assertThat(existing.getConfigVersion()).isEqualTo(2);
        assertThat(existing.getUpdateBy()).isEqualTo(userId);
        verify(systemConfigMapper).updateById(existing);

        ArgumentCaptor<SystemConfigChangeLog> logCaptor = ArgumentCaptor.forClass(SystemConfigChangeLog.class);
        verify(systemConfigChangeLogMapper).insert(logCaptor.capture());
        SystemConfigChangeLog log = logCaptor.getValue();
        assertThat(log.getConfigId()).isEqualTo(existing.getId());
        assertThat(log.getConfigKey()).isEqualTo("sample.restrict_days");
        assertThat(log.getChangeAction()).isEqualTo("UPDATE");
        assertThat(log.getOldValue()).isEqualTo("7");
        assertThat(log.getNewValue()).isEqualTo("9");
        assertThat(log.getSource()).isEqualTo(SysConfigService.CHANGE_SOURCE_RULE_CENTER);
        assertThat(log.getOperatorId()).isEqualTo(userId);
        assertThat(log.getEventId()).isEqualTo(result.eventId());
        assertThat(log.getChangeReason()).isEqualTo("调整寄样限制");
        assertThat(log.getConfigVersion()).isEqualTo(2);

        ArgumentCaptor<ConfigChangedEventPayload> payloadCaptor = ArgumentCaptor.forClass(ConfigChangedEventPayload.class);
        verify(domainEventOutboxService).saveConfigChangedEvent(payloadCaptor.capture(), eq(userId));
        ConfigChangedEventPayload eventPayload = payloadCaptor.getValue();
        assertThat(eventPayload.eventId()).isEqualTo(result.eventId());
        assertThat(eventPayload.operatorId()).isEqualTo(userId);
        assertThat(eventPayload.operatorName()).isEqualTo("admin");
        assertThat(eventPayload.changeReason()).isEqualTo("调整寄样限制");
        assertThat(eventPayload.source()).isEqualTo(SysConfigService.CHANGE_SOURCE_RULE_CENTER);
        assertThat(eventPayload.items()).singleElement()
                .satisfies(item -> {
                    assertThat(item.configKey()).isEqualTo("sample.restrict_days");
                    assertThat(item.oldValue()).isEqualTo("7");
                    assertThat(item.newValue()).isEqualTo("9");
                    assertThat(item.configVersion()).isEqualTo(2);
                });
        verify(applicationEventPublisher).publishEvent(any(ConfigChangedApplicationEvent.class));
    }

    @Test
    void batchUpdateByKeys_shouldSkipUnchangedValueWithoutVersionAuditOrEvents() {
        UUID userId = UUID.randomUUID();
        SystemConfig existing = new SystemConfig();
        existing.setId(UUID.randomUUID());
        existing.setConfigKey("sample.restrict_days");
        existing.setConfigValue("7");
        existing.setConfigVersion(1);
        when(systemConfigMapper.findByConfigKey("sample.restrict_days")).thenReturn(Optional.of(existing));

        SysConfigService.BatchUpdateConfigResult result = sysConfigService.batchUpdateByKeys(
                Map.of("sample.restrict_days", "7"),
                userId,
                "admin",
                SysConfigService.CHANGE_SOURCE_RULE_CENTER,
                "未变更");

        assertThat(result.changedKeys()).isEmpty();
        assertThat(existing.getConfigVersion()).isEqualTo(1);
        verify(systemConfigMapper, never()).updateById(any());
        verify(systemConfigChangeLogMapper, never()).insert(any());
        verify(domainEventOutboxService, never()).saveConfigChangedEvent(any(), any());
        verify(applicationEventPublisher, never()).publishEvent(any());
        verify(configDomainEventPublisher, never()).publish(any());
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
