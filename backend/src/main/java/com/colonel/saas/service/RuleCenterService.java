package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.config.ConfigDefinitionRegistry;
import com.colonel.saas.config.RuleCenterSchemaRegistry;
import com.colonel.saas.config.RuleCenterSchemaRegistry.RuleItemSchema;
import com.colonel.saas.domain.event.DomainEventConsumeLog;
import com.colonel.saas.domain.event.DomainEventConsumeLogMapper;
import com.colonel.saas.domain.event.DomainEventOutbox;
import com.colonel.saas.domain.event.DomainEventOutboxService;
import com.colonel.saas.dto.rulecenter.RuleCenterChangeLogView;
import com.colonel.saas.dto.rulecenter.RuleCenterEventStatusResponse;
import com.colonel.saas.dto.rulecenter.RuleCenterSchemaResponse;
import com.colonel.saas.dto.rulecenter.RuleCenterUpdateResponse;
import com.colonel.saas.dto.rulecenter.RuleCenterValidateResponse;
import com.colonel.saas.dto.rulecenter.RuleCenterValuesResponse;
import com.colonel.saas.entity.SystemConfig;
import com.colonel.saas.entity.SystemConfigChangeLog;
import com.colonel.saas.mapper.SystemConfigChangeLogMapper;
import com.colonel.saas.mapper.SystemConfigMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class RuleCenterService {

    private final RuleCenterSchemaRegistry ruleCenterSchemaRegistry;
    private final ConfigDefinitionRegistry configDefinitionRegistry;
    private final SystemConfigMapper systemConfigMapper;
    private final SystemConfigChangeLogMapper systemConfigChangeLogMapper;
    private final SysConfigService sysConfigService;
    private final DomainEventOutboxService domainEventOutboxService;
    private final DomainEventConsumeLogMapper domainEventConsumeLogMapper;

    public RuleCenterService(
            RuleCenterSchemaRegistry ruleCenterSchemaRegistry,
            ConfigDefinitionRegistry configDefinitionRegistry,
            SystemConfigMapper systemConfigMapper,
            SystemConfigChangeLogMapper systemConfigChangeLogMapper,
            SysConfigService sysConfigService,
            DomainEventOutboxService domainEventOutboxService,
            DomainEventConsumeLogMapper domainEventConsumeLogMapper) {
        this.ruleCenterSchemaRegistry = ruleCenterSchemaRegistry;
        this.configDefinitionRegistry = configDefinitionRegistry;
        this.systemConfigMapper = systemConfigMapper;
        this.systemConfigChangeLogMapper = systemConfigChangeLogMapper;
        this.sysConfigService = sysConfigService;
        this.domainEventOutboxService = domainEventOutboxService;
        this.domainEventConsumeLogMapper = domainEventConsumeLogMapper;
    }

    public RuleCenterSchemaResponse schema() {
        return new RuleCenterSchemaResponse(
                ruleCenterSchemaRegistry.groups().stream()
                        .map(RuleCenterSchemaResponse.RuleGroupView::from)
                        .toList());
    }

    public RuleCenterValuesResponse currentValues() {
        Map<String, String> values = new LinkedHashMap<>();
        for (RuleItemSchema item : allRuleItems()) {
            values.put(item.key(), loadValue(item.key()));
        }
        return new RuleCenterValuesResponse(values);
    }

    public RuleCenterValuesResponse groupValues(String groupCode) {
        Map<String, String> values = new LinkedHashMap<>();
        for (RuleItemSchema item : ruleCenterSchemaRegistry.itemsInGroup(groupCode)) {
            values.put(item.key(), loadValue(item.key()));
        }
        return new RuleCenterValuesResponse(values);
    }

    public RuleCenterValidateResponse validate(Map<String, String> values) {
        List<String> errors = new ArrayList<>();
        if (values == null || values.isEmpty()) {
            errors.add("至少提供一项配置值");
            return new RuleCenterValidateResponse(false, errors, List.of());
        }
        for (Map.Entry<String, String> entry : values.entrySet()) {
            RuleItemSchema item = ruleCenterSchemaRegistry.findItem(entry.getKey())
                    .orElse(null);
            if (item == null) {
                errors.add("未知配置项: " + entry.getKey());
                continue;
            }
            if (!item.enabled()) {
                errors.add(item.label() + " 当前未启用，不能修改");
                continue;
            }
            try {
                configDefinitionRegistry.validateOrThrow(entry.getKey(), entry.getValue());
            } catch (BusinessException ex) {
                errors.add(item.label() + ": " + ex.getMessage());
            }
        }
        List<String> warnings = ruleCenterSchemaRegistry.validateCommissionWarning(values);
        return new RuleCenterValidateResponse(errors.isEmpty(), List.copyOf(errors), warnings);
    }

    public RuleCenterUpdateResponse updateGroup(
            String groupCode,
            Map<String, String> values,
            String changeReason,
            UUID userId,
            String operatorName) {
        Set<String> allowedKeys = ruleCenterSchemaRegistry.keysInGroup(groupCode);
        if (allowedKeys.isEmpty()) {
            throw BusinessException.notFound("规则分组不存在: " + groupCode);
        }
        Map<String, String> filtered = filterAllowed(values, allowedKeys);
        RuleCenterValidateResponse validation = validate(filtered);
        if (!validation.valid()) {
            throw BusinessException.param(String.join("; ", validation.errors()));
        }
        SysConfigService.BatchUpdateConfigResult result = sysConfigService.batchUpdateByKeys(
                filtered,
                userId,
                operatorName,
                SysConfigService.CHANGE_SOURCE_RULE_CENTER,
                changeReason);
        List<String> warnings = new ArrayList<>(validation.warnings());
        warnings.addAll(result.warnings());
        return new RuleCenterUpdateResponse(result.eventId(), result.changedKeys(), List.copyOf(warnings));
    }

    public RuleCenterUpdateResponse batchUpdate(
            Map<String, String> values,
            String changeReason,
            UUID userId,
            String operatorName) {
        Map<String, String> filtered = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            ruleCenterSchemaRegistry.findItem(entry.getKey()).ifPresent(item -> {
                if (item.enabled()) {
                    filtered.put(entry.getKey(), entry.getValue());
                }
            });
        }
        RuleCenterValidateResponse validation = validate(filtered);
        if (!validation.valid()) {
            throw BusinessException.param(String.join("; ", validation.errors()));
        }
        SysConfigService.BatchUpdateConfigResult result = sysConfigService.batchUpdateByKeys(
                filtered,
                userId,
                operatorName,
                SysConfigService.CHANGE_SOURCE_RULE_CENTER,
                changeReason);
        List<String> warnings = new ArrayList<>(validation.warnings());
        warnings.addAll(result.warnings());
        return new RuleCenterUpdateResponse(result.eventId(), result.changedKeys(), List.copyOf(warnings));
    }

    public IPage<RuleCenterChangeLogView> changeLogs(String configKey, int pageNo, int pageSize) {
        Page<SystemConfigChangeLog> page = new Page<>(pageNo, pageSize);
        QueryWrapper<SystemConfigChangeLog> wrapper = new QueryWrapper<>();
        if (StringUtils.hasText(configKey)) {
            wrapper.eq("config_key", configKey.trim());
        } else {
            wrapper.in("config_key", allRuleKeys());
        }
        wrapper.orderByDesc("changed_at");
        IPage<SystemConfigChangeLog> result = systemConfigChangeLogMapper.selectPage(page, wrapper);
        return result.convert(this::toChangeLogView);
    }

    public RuleCenterEventStatusResponse eventStatus(UUID eventId) {
        DomainEventOutbox outbox = domainEventOutboxService.findById(eventId);
        if (outbox == null) {
            throw BusinessException.notFound("事件不存在");
        }
        QueryWrapper<DomainEventConsumeLog> wrapper = new QueryWrapper<>();
        wrapper.eq("event_id", eventId).orderByAsc("consumer_name");
        List<DomainEventConsumeLog> consumeLogs = domainEventConsumeLogMapper.selectList(wrapper);
        List<RuleCenterEventStatusResponse.ConsumerStatusView> consumers = consumeLogs.stream()
                .map(log -> new RuleCenterEventStatusResponse.ConsumerStatusView(
                        log.getConsumerName(),
                        log.getStatus(),
                        log.getErrorMessage(),
                        log.getConsumedAt()))
                .toList();
        return new RuleCenterEventStatusResponse(
                outbox.getEventId(),
                outbox.getEventType(),
                outbox.getStatus(),
                outbox.getRetryCount(),
                outbox.getErrorMessage(),
                outbox.getOccurredAt(),
                outbox.getPublishedAt(),
                consumers);
    }

    private RuleCenterChangeLogView toChangeLogView(SystemConfigChangeLog log) {
        return new RuleCenterChangeLogView(
                log.getId(),
                log.getEventId(),
                log.getConfigKey(),
                log.getChangeAction(),
                log.getOldValue(),
                log.getNewValue(),
                log.getSource(),
                log.getChangeReason(),
                log.getOperatorId(),
                log.getConfigVersion(),
                log.getChangedAt());
    }

    private Map<String, String> filterAllowed(Map<String, String> values, Set<String> allowedKeys) {
        if (values == null) {
            return Map.of();
        }
        Map<String, String> filtered = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (allowedKeys.contains(entry.getKey())) {
                filtered.put(entry.getKey(), entry.getValue());
            }
        }
        if (filtered.isEmpty()) {
            throw BusinessException.param("该分组没有可保存的配置项");
        }
        return filtered;
    }

    private String loadValue(String configKey) {
        return systemConfigMapper.findByConfigKey(configKey)
                .map(SystemConfig::getConfigValue)
                .orElse("");
    }

    private List<RuleItemSchema> allRuleItems() {
        return ruleCenterSchemaRegistry.groups().stream()
                .flatMap(group -> group.items().stream())
                .toList();
    }

    private List<String> allRuleKeys() {
        return allRuleItems().stream().map(RuleItemSchema::key).toList();
    }
}
