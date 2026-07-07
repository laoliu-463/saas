package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.config.ConfigDefinitionRegistry;
import com.colonel.saas.config.RuleCenterSchemaRegistry;
import com.colonel.saas.config.SystemConfigKeys;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.domain.event.DomainEventConsumeLog;
import com.colonel.saas.domain.event.DomainEventConsumeLogMapper;
import com.colonel.saas.domain.event.DomainEventOutbox;
import com.colonel.saas.domain.event.DomainEventOutboxService;
import com.colonel.saas.entity.SystemConfig;
import com.colonel.saas.entity.SystemConfigChangeLog;
import com.colonel.saas.mapper.SystemConfigChangeLogMapper;
import com.colonel.saas.mapper.SystemConfigMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RuleCenterServiceTest {

    private final ConfigDefinitionRegistry configDefinitionRegistry =
            new ConfigDefinitionRegistry(new ObjectMapper());
    private final RuleCenterSchemaRegistry ruleCenterSchemaRegistry =
            new RuleCenterSchemaRegistry(configDefinitionRegistry);
    private final RuleCenterService ruleCenterService = new RuleCenterService(
            ruleCenterSchemaRegistry,
            configDefinitionRegistry,
            null,
            null,
            null,
            null,
            null);

    @Test
    void schemaShouldExposePresetTalentTagsInRuleCenter() {
        var talentGroup = ruleCenterSchemaRegistry.findGroup("talent").orElseThrow();

        assertThat(talentGroup.items())
                .anySatisfy(item -> {
                    assertThat(item.key()).isEqualTo(SystemConfigKeys.PRESET_TALENT_TAGS);
                    assertThat(item.label()).isEqualTo("达人预设标签库");
                    assertThat(item.valueType()).isEqualTo(ConfigDefinitionRegistry.ConfigValueType.JSON);
                    assertThat(item.enabled()).isTrue();
                });
    }

    @Test
    void validateShouldRejectInvalidPresetTalentTagsJsonThroughRuleCenter() {
        var response = ruleCenterService.validate(Map.of(SystemConfigKeys.PRESET_TALENT_TAGS, "{\"tag\":\"美妆\"}"));

        assertThat(response.valid()).isFalse();
        assertThat(response.errors()).anyMatch(error -> error.contains("达人预设标签库") && error.contains("JSON 数组"));
    }

    @Test
    void validateShouldRejectEmptyAndUnknownValues() {
        var empty = ruleCenterService.validate(Map.of());
        var unknown = ruleCenterService.validate(Map.of("unknown.key", "1"));

        assertThat(empty.valid()).isFalse();
        assertThat(empty.errors()).contains("至少提供一项配置值");
        assertThat(unknown.valid()).isFalse();
        assertThat(unknown.errors()).contains("未知配置项: unknown.key");
    }

    @Test
    void currentValuesAndGroupValuesShouldLoadStoredValuesOrEmptyDefaults() {
        SystemConfigMapper mapper = mock(SystemConfigMapper.class);
        SystemConfig stored = new SystemConfig();
        stored.setConfigKey(SystemConfigKeys.PRESET_TALENT_TAGS);
        stored.setConfigValue("[\"美妆\"]");
        when(mapper.findByConfigKey(SystemConfigKeys.PRESET_TALENT_TAGS)).thenReturn(Optional.of(stored));
        RuleCenterService service = serviceWith(mapper, null, null, null, null);

        var current = service.currentValues();
        var talent = service.groupValues("talent");

        assertThat(current.values()).containsEntry(SystemConfigKeys.PRESET_TALENT_TAGS, "[\"美妆\"]");
        assertThat(talent.values()).containsEntry(SystemConfigKeys.PRESET_TALENT_TAGS, "[\"美妆\"]");
    }

    @Test
    void updateGroupShouldFilterAllowedKeysValidateAndMergeWarnings() {
        SysConfigService sysConfigService = mock(SysConfigService.class);
        UUID userId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        when(sysConfigService.batchUpdateByKeys(
                anyMap(),
                eq(userId),
                eq("operator"),
                eq(SysConfigService.CHANGE_SOURCE_RULE_CENTER),
                eq("调整规则")))
                .thenReturn(new SysConfigService.BatchUpdateConfigResult(
                        eventId,
                        List.of(SystemConfigKeys.PRESET_TALENT_TAGS),
                        List.of("后续生效")));
        RuleCenterService service = serviceWith(null, null, sysConfigService, null, null);

        var response = service.updateGroup(
                "talent",
                Map.of(SystemConfigKeys.PRESET_TALENT_TAGS, "[\"美妆\"]", "unknown.key", "ignored"),
                "调整规则",
                userId,
                "operator");

        assertThat(response.eventId()).isEqualTo(eventId);
        assertThat(response.changedKeys()).containsExactly(SystemConfigKeys.PRESET_TALENT_TAGS);
        assertThat(response.warnings()).contains("后续生效");
        verify(sysConfigService).batchUpdateByKeys(anyMap(), eq(userId), eq("operator"),
                eq(SysConfigService.CHANGE_SOURCE_RULE_CENTER), eq("调整规则"));
    }

    @Test
    void updateGroupShouldRejectUnknownGroupOrEmptyAllowedValues() {
        RuleCenterService service = serviceWith(null, null, mock(SysConfigService.class), null, null);

        assertThatThrownBy(() -> service.updateGroup("missing", Map.of("x", "1"), null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("规则分组不存在");
        assertThatThrownBy(() -> service.updateGroup("talent", Map.of("unknown.key", "1"), null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("该分组没有可保存的配置项");
    }

    @Test
    void batchUpdateShouldIgnoreUnknownItemsAndRejectEmptyFilteredValues() {
        RuleCenterService service = serviceWith(null, null, mock(SysConfigService.class), null, null);

        assertThatThrownBy(() -> service.batchUpdate(Map.of("unknown.key", "1"), null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("至少提供一项配置值");
    }

    @Test
    void changeLogsShouldConvertLogRows() {
        SystemConfigChangeLogMapper mapper = mock(SystemConfigChangeLogMapper.class);
        UUID id = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        SystemConfigChangeLog log = new SystemConfigChangeLog();
        log.setId(id);
        log.setEventId(eventId);
        log.setConfigKey(SystemConfigKeys.PRESET_TALENT_TAGS);
        log.setChangeAction("UPDATE");
        log.setOldValue("[]");
        log.setNewValue("[\"美妆\"]");
        log.setSource("rule_center");
        log.setChangeReason("调整");
        log.setOperatorId(UUID.randomUUID());
        log.setConfigVersion(2);
        log.setChangedAt(LocalDateTime.now());
        Page<SystemConfigChangeLog> page = new Page<>(1, 10);
        page.setRecords(List.of(log));
        when(mapper.selectPage(any(), any())).thenReturn(page);
        RuleCenterService service = serviceWith(null, mapper, null, null, null);

        var result = service.changeLogs(" " + SystemConfigKeys.PRESET_TALENT_TAGS + " ", 1, 10);

        assertThat(result.getRecords()).singleElement().satisfies(view -> {
            assertThat(view.id()).isEqualTo(id);
            assertThat(view.eventId()).isEqualTo(eventId);
            assertThat(view.configKey()).isEqualTo(SystemConfigKeys.PRESET_TALENT_TAGS);
            assertThat(view.changeAction()).isEqualTo("UPDATE");
            assertThat(view.oldValue()).isEqualTo("[]");
            assertThat(view.newValue()).isEqualTo("[\"美妆\"]");
            assertThat(view.source()).isEqualTo("rule_center");
            assertThat(view.changeReason()).isEqualTo("调整");
            assertThat(view.operatorId()).isEqualTo(log.getOperatorId());
            assertThat(view.configVersion()).isEqualTo(2);
        });

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<Page<SystemConfigChangeLog>> pageCaptor = ArgumentCaptor.forClass((Class) Page.class);
        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<QueryWrapper<SystemConfigChangeLog>> wrapperCaptor =
                ArgumentCaptor.forClass((Class) QueryWrapper.class);
        verify(mapper).selectPage(pageCaptor.capture(), wrapperCaptor.capture());
        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(1);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(10);
        QueryWrapper<SystemConfigChangeLog> wrapper = wrapperCaptor.getValue();
        assertThat(normalizeSqlSegment(wrapper.getSqlSegment()))
                .contains("config_key")
                .contains("order by")
                .contains("changed_at desc");
    }

    @Test
    void eventStatusShouldJoinOutboxAndConsumerLogs() {
        DomainEventOutboxService outboxService = mock(DomainEventOutboxService.class);
        DomainEventConsumeLogMapper consumeLogMapper = mock(DomainEventConsumeLogMapper.class);
        UUID eventId = UUID.randomUUID();
        DomainEventOutbox outbox = new DomainEventOutbox();
        outbox.setEventId(eventId);
        outbox.setEventType("ConfigChangedEvent");
        outbox.setStatus("PUBLISHED");
        outbox.setRetryCount(1);
        outbox.setErrorMessage(null);
        outbox.setOccurredAt(LocalDateTime.now().minusMinutes(1));
        outbox.setPublishedAt(LocalDateTime.now());
        DomainEventConsumeLog log = new DomainEventConsumeLog();
        log.setConsumerName("rule-consumer");
        log.setStatus("SUCCESS");
        log.setConsumedAt(LocalDateTime.now());
        when(outboxService.findById(eventId)).thenReturn(outbox);
        when(consumeLogMapper.selectList(any())).thenReturn(List.of(log));
        RuleCenterService service = serviceWith(null, null, null, outboxService, consumeLogMapper);

        var response = service.eventStatus(eventId);

        assertThat(response.eventId()).isEqualTo(eventId);
        assertThat(response.eventType()).isEqualTo("ConfigChangedEvent");
        assertThat(response.consumers()).singleElement().satisfies(consumer -> {
            assertThat(consumer.consumerName()).isEqualTo("rule-consumer");
            assertThat(consumer.status()).isEqualTo("SUCCESS");
        });
    }

    @Test
    void eventStatusShouldRejectMissingOutboxEvent() {
        DomainEventOutboxService outboxService = mock(DomainEventOutboxService.class);
        UUID eventId = UUID.randomUUID();
        when(outboxService.findById(eventId)).thenReturn(null);
        RuleCenterService service = serviceWith(null, null, null, outboxService, mock(DomainEventConsumeLogMapper.class));

        assertThatThrownBy(() -> service.eventStatus(eventId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("事件不存在");
    }

    private RuleCenterService serviceWith(
            SystemConfigMapper mapper,
            SystemConfigChangeLogMapper changeLogMapper,
            SysConfigService sysConfigService,
            DomainEventOutboxService outboxService,
            DomainEventConsumeLogMapper consumeLogMapper) {
        return new RuleCenterService(
                ruleCenterSchemaRegistry,
                configDefinitionRegistry,
                mapper == null ? mock(SystemConfigMapper.class) : mapper,
                changeLogMapper == null ? mock(SystemConfigChangeLogMapper.class) : changeLogMapper,
                sysConfigService == null ? mock(SysConfigService.class) : sysConfigService,
                outboxService == null ? mock(DomainEventOutboxService.class) : outboxService,
                consumeLogMapper == null ? mock(DomainEventConsumeLogMapper.class) : consumeLogMapper);
    }

    private static String normalizeSqlSegment(String sqlSegment) {
        return sqlSegment.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }
}
