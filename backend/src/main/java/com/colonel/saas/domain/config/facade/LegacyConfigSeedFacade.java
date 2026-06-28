package com.colonel.saas.domain.config.facade;

import com.colonel.saas.entity.SystemConfig;
import com.colonel.saas.mapper.SystemConfigMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 遗留配置种子写入实现。
 * <p>保持旧启动期行为：只在缺失时插入默认配置，不覆盖管理员已有配置。</p>
 */
@Service
public class LegacyConfigSeedFacade implements ConfigSeedFacade {

    private final SystemConfigMapper systemConfigMapper;
    private final ObjectMapper objectMapper;

    public LegacyConfigSeedFacade(SystemConfigMapper systemConfigMapper, ObjectMapper objectMapper) {
        this.systemConfigMapper = systemConfigMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean createJsonConfigIfMissing(String configKey, Object value, String group, String name) {
        if (systemConfigMapper.findByConfigKey(configKey).isPresent()) {
            return false;
        }
        SystemConfig config = new SystemConfig();
        config.setId(UUID.randomUUID());
        config.setConfigKey(configKey);
        config.setConfigValue(toJson(value));
        config.setConfigType("json");
        config.setConfigGroup(group);
        config.setConfigName(name);
        config.setStatus(1);
        systemConfigMapper.insert(config);
        return true;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("配置默认值 JSON 序列化失败", ex);
        }
    }
}
