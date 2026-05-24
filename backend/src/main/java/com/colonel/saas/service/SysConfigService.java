package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.config.ConfigChangedEventFactory;
import com.colonel.saas.config.ConfigDefinitionRegistry;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.domain.event.ConfigChangedEventPayload;
import com.colonel.saas.domain.event.DomainEventOutboxService;
import com.colonel.saas.entity.SystemConfig;
import com.colonel.saas.entity.SystemConfigChangeLog;
import com.colonel.saas.event.ConfigChangedApplicationEvent;
import com.colonel.saas.mapper.SystemConfigChangeLogMapper;
import com.colonel.saas.mapper.SystemConfigMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class SysConfigService {

    public static final String CHANGE_SOURCE_API = "SYS_CONFIG_API";
    public static final String CHANGE_SOURCE_RULE_CENTER = "RULE_CENTER";

    private final SystemConfigMapper systemConfigMapper;
    private final SystemConfigChangeLogMapper systemConfigChangeLogMapper;
    private final OperationLogService operationLogService;
    private final ConfigDefinitionRegistry configDefinitionRegistry;
    private final ConfigChangedEventFactory configChangedEventFactory;
    private final DomainEventOutboxService domainEventOutboxService;
    private final ApplicationEventPublisher applicationEventPublisher;

    public SysConfigService(
            SystemConfigMapper systemConfigMapper,
            SystemConfigChangeLogMapper systemConfigChangeLogMapper,
            OperationLogService operationLogService,
            ConfigDefinitionRegistry configDefinitionRegistry,
            ConfigChangedEventFactory configChangedEventFactory,
            DomainEventOutboxService domainEventOutboxService,
            ApplicationEventPublisher applicationEventPublisher) {
        this.systemConfigMapper = systemConfigMapper;
        this.systemConfigChangeLogMapper = systemConfigChangeLogMapper;
        this.operationLogService = operationLogService;
        this.configDefinitionRegistry = configDefinitionRegistry;
        this.configChangedEventFactory = configChangedEventFactory;
        this.domainEventOutboxService = domainEventOutboxService;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    public IPage<SystemConfig> findPage(String configGroup, String keyword, int pageNo, int pageSize) {
        Page<SystemConfig> page = new Page<>(pageNo, pageSize);
        QueryWrapper<SystemConfig> wrapper = new QueryWrapper<>();
        if (configGroup != null && !configGroup.isBlank()) {
            wrapper.eq("config_group", configGroup);
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like("config_key", keyword).or().like("config_name", keyword));
        }
        wrapper.orderByAsc("config_group").orderByAsc("sort_order");
        return systemConfigMapper.selectPage(page, wrapper);
    }

    public Map<String, List<SystemConfig>> findGrouped(boolean includeSensitive) {
        QueryWrapper<SystemConfig> wrapper = new QueryWrapper<>();
        wrapper.eq("status", 1);
        wrapper.orderByAsc("config_group").orderByAsc("sort_order");
        List<SystemConfig> configs = systemConfigMapper.selectList(wrapper);
        return configs.stream().collect(Collectors.groupingBy(
                c -> c.getConfigGroup() != null ? c.getConfigGroup() : "default",
                LinkedHashMap::new,
                Collectors.mapping(config -> includeSensitive ? config : maskSensitiveValue(config), Collectors.toList())
        ));
    }

    private SystemConfig maskSensitiveValue(SystemConfig source) {
        if (source == null) {
            return null;
        }
        SystemConfig target = new SystemConfig();
        target.setId(source.getId());
        target.setConfigKey(source.getConfigKey());
        target.setConfigName(source.getConfigName());
        target.setConfigGroup(source.getConfigGroup());
        target.setConfigType(source.getConfigType());
        target.setSortOrder(source.getSortOrder());
        target.setStatus(source.getStatus());
        target.setRemark(source.getRemark());
        target.setConfigValue(isSensitive(source) ? "******" : source.getConfigValue());
        target.setCreateTime(source.getCreateTime());
        target.setUpdateTime(source.getUpdateTime());
        target.setCreateBy(source.getCreateBy());
        target.setUpdateBy(source.getUpdateBy());
        target.setDeleted(source.getDeleted());
        return target;
    }

    private boolean isSensitive(SystemConfig config) {
        String key = normalize(config == null ? null : config.getConfigKey());
        String type = normalize(config == null ? null : config.getConfigType());
        String name = normalize(config == null ? null : config.getConfigName());
        String group = normalize(config == null ? null : config.getConfigGroup());
        if (configDefinitionRegistry.isSensitive(key)) {
            return true;
        }
        if (containsSensitiveKeyword(type)) {
            return true;
        }
        return containsSensitiveKeyword(key)
                || containsSensitiveKeyword(name)
                || containsSensitiveKeyword(group);
    }

    private boolean containsSensitiveKeyword(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        return text.contains("token")
                || text.contains("secret")
                || text.contains("password")
                || text.contains("credential")
                || text.contains("appkey")
                || text.contains("private_key")
                || text.contains("refresh");
    }

    private String normalize(String text) {
        return text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
    }

    public SystemConfig getById(UUID id) {
        SystemConfig config = systemConfigMapper.selectById(id);
        if (config == null) {
            throw BusinessException.notFound("配置项不存在");
        }
        return config;
    }

    @Transactional(rollbackFor = Exception.class)
    public SystemConfig create(SystemConfig config, UUID userId) {
        validateConfigForWrite(config);
        systemConfigMapper.findByConfigKey(config.getConfigKey()).ifPresent(existing -> {
            throw BusinessException.duplicate("配置键已存在: " + config.getConfigKey());
        });
        config.setCreateBy(userId);
        config.setUpdateBy(userId);
        config.setId(UUID.randomUUID());
        config.setConfigVersion(1);
        systemConfigMapper.insert(config);
        publishConfigChanged(
                List.of(new ConfigChangedEventFactory.ConfigChangeContext(
                        config.getConfigKey(), null, config.getConfigValue(), 1)),
                userId,
                null,
                CHANGE_SOURCE_API,
                "CREATE");
        operationLogService.recordSystemAction(
                userId,
                "系统配置",
                "新建配置",
                "POST",
                "SystemConfig",
                config.getId() == null ? null : config.getId().toString(),
                config.getConfigKey(),
                "新建配置项: " + config.getConfigKey()
        );
        return config;
    }

    @Transactional(rollbackFor = Exception.class)
    public SystemConfig update(UUID id, SystemConfig config, UUID userId) {
        SystemConfig existing = getById(id);
        String previousConfigKey = existing.getConfigKey();
        String previousConfigValue = existing.getConfigValue();
        if (config.getConfigKey() != null && !config.getConfigKey().equals(existing.getConfigKey())) {
            systemConfigMapper.findByConfigKey(config.getConfigKey()).ifPresent(dup -> {
                throw BusinessException.duplicate("配置键已存在: " + config.getConfigKey());
            });
            existing.setConfigKey(config.getConfigKey());
        }
        if (config.getConfigValue() != null) {
            existing.setConfigValue(config.getConfigValue());
        }
        if (config.getConfigType() != null) {
            existing.setConfigType(config.getConfigType());
        }
        if (config.getConfigGroup() != null) {
            existing.setConfigGroup(config.getConfigGroup());
        }
        if (config.getConfigName() != null) {
            existing.setConfigName(config.getConfigName());
        }
        if (config.getSortOrder() != null) {
            existing.setSortOrder(config.getSortOrder());
        }
        if (config.getStatus() != null) {
            existing.setStatus(config.getStatus());
        }
        if (config.getRemark() != null) {
            existing.setRemark(config.getRemark());
        }
        validateConfigForWrite(existing);
        existing.setUpdateBy(userId);
        bumpConfigVersion(existing);
        systemConfigMapper.updateById(existing);
        publishConfigChanged(
                List.of(new ConfigChangedEventFactory.ConfigChangeContext(
                        existing.getConfigKey(),
                        previousConfigValue,
                        existing.getConfigValue(),
                        existing.getConfigVersion() == null ? 1 : existing.getConfigVersion())),
                userId,
                null,
                CHANGE_SOURCE_API,
                "UPDATE");
        operationLogService.recordSystemAction(
                userId,
                "系统配置",
                "更新配置",
                "PUT",
                "SystemConfig",
                existing.getId() == null ? null : existing.getId().toString(),
                existing.getConfigKey(),
                "更新配置项: " + existing.getConfigKey()
        );
        return existing;
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(UUID id, UUID userId) {
        SystemConfig existing = getById(id);
        int affected = systemConfigMapper.softDeleteById(id, userId);
        if (affected == 0) {
            throw BusinessException.notFound("配置项不存在");
        }
        publishConfigChanged(
                List.of(new ConfigChangedEventFactory.ConfigChangeContext(
                        existing.getConfigKey(), existing.getConfigValue(), null,
                        existing.getConfigVersion() == null ? 1 : existing.getConfigVersion())),
                userId,
                null,
                CHANGE_SOURCE_API,
                "DELETE");
        operationLogService.recordSystemAction(
                userId,
                "系统配置",
                "删除配置",
                "DELETE",
                "SystemConfig",
                existing.getId() == null ? null : existing.getId().toString(),
                existing.getConfigKey(),
                "删除配置项: " + existing.getConfigKey()
        );
    }

    public String getConfigValue(String configKey) {
        return systemConfigMapper.findByConfigKey(configKey)
                .map(SystemConfig::getConfigValue)
                .orElse(null);
    }

    @Transactional(rollbackFor = Exception.class)
    public BatchUpdateConfigResult batchUpdateByKeys(
            Map<String, String> updates,
            UUID userId,
            String operatorName,
            String source,
            String changeReason) {
        if (updates == null || updates.isEmpty()) {
            throw BusinessException.param("至少修改一项配置");
        }
        List<ConfigChangedEventFactory.ConfigChangeContext> changes = new java.util.ArrayList<>();
        UUID eventId = UUID.randomUUID();
        for (Map.Entry<String, String> entry : updates.entrySet()) {
            String configKey = entry.getKey();
            String newValue = entry.getValue();
            SystemConfig existing = systemConfigMapper.findByConfigKey(configKey)
                    .orElseThrow(() -> BusinessException.notFound("配置项不存在: " + configKey));
            String oldValue = existing.getConfigValue();
            if (Objects.equals(oldValue, newValue)) {
                continue;
            }
            existing.setConfigValue(newValue);
            validateConfigForWrite(existing);
            existing.setUpdateBy(userId);
            bumpConfigVersion(existing);
            systemConfigMapper.updateById(existing);
            recordConfigChange(existing, "UPDATE", oldValue, newValue, userId, eventId, source, changeReason);
            changes.add(new ConfigChangedEventFactory.ConfigChangeContext(
                    configKey, oldValue, newValue, existing.getConfigVersion()));
        }
        if (changes.isEmpty()) {
            return new BatchUpdateConfigResult(eventId, List.of(), List.of());
        }
        ConfigChangedEventPayload payload = configChangedEventFactory.create(
                eventId, userId, operatorName, changeReason, source, changes);
        domainEventOutboxService.saveConfigChangedEvent(payload, userId);
        Set<String> changedKeys = changes.stream()
                .map(ConfigChangedEventFactory.ConfigChangeContext::configKey)
                .collect(Collectors.toSet());
        applicationEventPublisher.publishEvent(new ConfigChangedApplicationEvent(changedKeys));
        return new BatchUpdateConfigResult(
                eventId,
                List.copyOf(changedKeys),
                payload.impact().needManualRecalculate() ? List.of("提成比例变更只影响后续计算或手动重算") : List.of());
    }

    public record BatchUpdateConfigResult(
            UUID eventId,
            List<String> changedKeys,
            List<String> warnings) {
    }

    private void publishConfigChanged(
            List<ConfigChangedEventFactory.ConfigChangeContext> changes,
            UUID userId,
            String operatorName,
            String source,
            String changeAction) {
        if (changes == null || changes.isEmpty()) {
            return;
        }
        UUID eventId = UUID.randomUUID();
        for (ConfigChangedEventFactory.ConfigChangeContext change : changes) {
            SystemConfig config = systemConfigMapper.findByConfigKey(change.configKey()).orElse(null);
            recordConfigChange(
                    config,
                    changeAction,
                    change.oldValue(),
                    change.newValue(),
                    userId,
                    eventId,
                    source,
                    null);
        }
        ConfigChangedEventPayload payload = configChangedEventFactory.create(
                eventId, userId, operatorName, null, source, changes);
        domainEventOutboxService.saveConfigChangedEvent(payload, userId);
        Set<String> changedKeys = changes.stream()
                .map(ConfigChangedEventFactory.ConfigChangeContext::configKey)
                .collect(Collectors.toSet());
        applicationEventPublisher.publishEvent(new ConfigChangedApplicationEvent(changedKeys));
    }

    private void bumpConfigVersion(SystemConfig config) {
        int current = config.getConfigVersion() == null ? 0 : config.getConfigVersion();
        config.setConfigVersion(current + 1);
    }

    private void validateConfigForWrite(SystemConfig config) {
        if (config == null || !StringUtils.hasText(config.getConfigKey())) {
            throw BusinessException.param("配置键不能为空");
        }
        configDefinitionRegistry.validateOrThrow(config.getConfigKey(), config.getConfigValue());
    }

    private void recordConfigChange(
            SystemConfig config,
            String action,
            String oldValue,
            String newValue,
            UUID operatorId,
            UUID eventId,
            String source,
            String changeReason) {
        SystemConfigChangeLog log = new SystemConfigChangeLog();
        log.setId(UUID.randomUUID());
        log.setConfigId(config == null ? null : config.getId());
        log.setConfigKey(config == null ? null : config.getConfigKey());
        log.setChangeAction(action);
        log.setOldValue(oldValue);
        log.setNewValue(newValue);
        log.setSource(source == null ? CHANGE_SOURCE_API : source);
        log.setOperatorId(operatorId);
        log.setChangedAt(LocalDateTime.now());
        log.setEventId(eventId);
        log.setChangeReason(changeReason);
        log.setConfigVersion(config == null ? null : config.getConfigVersion());
        systemConfigChangeLogMapper.insert(log);
    }
}
