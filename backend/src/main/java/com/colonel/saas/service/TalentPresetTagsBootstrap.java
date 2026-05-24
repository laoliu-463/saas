package com.colonel.saas.service;

import com.colonel.saas.config.SystemConfigKeys;
import com.colonel.saas.entity.SystemConfig;
import com.colonel.saas.mapper.SystemConfigMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * 仅在 {@link SystemConfigKeys#PRESET_TALENT_TAGS} 缺失时写入默认预设标签，不覆盖管理员已有配置。
 */
@Slf4j
@Component
public class TalentPresetTagsBootstrap implements ApplicationRunner {

    static final List<String> DEFAULT_PRESET_TAGS = List.of(
            "高意向",
            "已合作",
            "待跟进",
            "寄样中",
            "已出单",
            "低意向",
            "重点达人",
            "直播达人",
            "短视频达人",
            "带货能力强",
            "价格敏感",
            "需要复盘");

    private final SystemConfigMapper systemConfigMapper;
    private final ObjectMapper objectMapper;

    public TalentPresetTagsBootstrap(SystemConfigMapper systemConfigMapper, ObjectMapper objectMapper) {
        this.systemConfigMapper = systemConfigMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (systemConfigMapper.findByConfigKey(SystemConfigKeys.PRESET_TALENT_TAGS).isPresent()) {
            return;
        }
        SystemConfig config = new SystemConfig();
        config.setId(UUID.randomUUID());
        config.setConfigKey(SystemConfigKeys.PRESET_TALENT_TAGS);
        config.setConfigValue(objectMapper.writeValueAsString(DEFAULT_PRESET_TAGS));
        config.setConfigType("json");
        config.setConfigGroup("talent");
        config.setConfigName("达人预设标签库");
        config.setStatus(1);
        systemConfigMapper.insert(config);
        log.info("Initialized preset talent tags ({} items)", DEFAULT_PRESET_TAGS.size());
    }
}
