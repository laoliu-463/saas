package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.config.ConfigDefinitionRegistry;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.entity.SystemConfig;
import com.colonel.saas.mapper.SystemConfigMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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

    private final SystemConfigMapper systemConfigMapper;
    private final OperationLogService operationLogService;
    private final ConfigDefinitionRegistry configDefinitionRegistry;
    private final BusinessRuleConfigService businessRuleConfigService;

    public SysConfigService(
            SystemConfigMapper systemConfigMapper,
            OperationLogService operationLogService,
            ConfigDefinitionRegistry configDefinitionRegistry,
            BusinessRuleConfigService businessRuleConfigService) {
        this.systemConfigMapper = systemConfigMapper;
        this.operationLogService = operationLogService;
        this.configDefinitionRegistry = configDefinitionRegistry;
        this.businessRuleConfigService = businessRuleConfigService;
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
            throw new BusinessException("配置项不存在");
        }
        return config;
    }

    @Transactional(rollbackFor = Exception.class)
    public SystemConfig create(SystemConfig config, UUID userId) {
        validateConfigForWrite(config);
        systemConfigMapper.findByConfigKey(config.getConfigKey()).ifPresent(existing -> {
            throw new BusinessException("配置键已存在: " + config.getConfigKey());
        });
        config.setCreateBy(userId);
        config.setUpdateBy(userId);
        config.setId(UUID.randomUUID());
        systemConfigMapper.insert(config);
        businessRuleConfigService.invalidate(config.getConfigKey());
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
        if (config.getConfigKey() != null && !config.getConfigKey().equals(existing.getConfigKey())) {
            systemConfigMapper.findByConfigKey(config.getConfigKey()).ifPresent(dup -> {
                throw new BusinessException("配置键已存在: " + config.getConfigKey());
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
        systemConfigMapper.updateById(existing);
        invalidateBusinessRuleCache(previousConfigKey, existing.getConfigKey());
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
            throw new BusinessException("配置项不存在");
        }
        invalidateBusinessRuleCache(existing.getConfigKey());
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

    private void validateConfigForWrite(SystemConfig config) {
        if (config == null || !StringUtils.hasText(config.getConfigKey())) {
            throw new BusinessException("配置键不能为空");
        }
        configDefinitionRegistry.validateOrThrow(config.getConfigKey(), config.getConfigValue());
    }

    private void invalidateBusinessRuleCache(String... configKeys) {
        if (configKeys == null || configKeys.length == 0) {
            return;
        }
        Set<String> uniqueKeys = java.util.Arrays.stream(configKeys)
                .filter(StringUtils::hasText)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        for (String configKey : uniqueKeys) {
            businessRuleConfigService.invalidate(configKey);
        }
    }
}
