package com.colonel.saas.service;

import com.colonel.saas.config.SystemConfigKeys;
import com.colonel.saas.entity.SystemConfig;
import com.colonel.saas.mapper.SystemConfigMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * 达人预设标签库初始化引导。
 * <p>
 * 应用启动时检查系统配置表中是否已存在 {@link SystemConfigKeys#PRESET_TALENT_TAGS}，
 * 仅在缺失时写入默认预设标签列表，不覆盖管理员已有的自定义配置。
 * 默认标签涵盖跟单状态、达人类型和商业能力等维度。
 * </p>
 *
 * <ul>
 *     <li>应用启动时自动执行初始化检查（{@link #run}）</li>
 *     <li>仅当配置缺失时写入默认 12 项预设标签</li>
 * </ul>
 *
 * <p><b>业务域：</b>达人域 — 预设标签初始化</p>
 * <p><b>协作关系：</b></p>
 * <ul>
 *     <li>{@link SystemConfigMapper} — 系统配置数据访问</li>
 *     <li>{@link ObjectMapper} — JSON 序列化</li>
 * </ul>
 *
 * @see SystemConfigKeys#PRESET_TALENT_TAGS
 */
@Slf4j
@Component
@ConditionalOnProperty(
        name = "talent.preset-tags.bootstrap.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class TalentPresetTagsBootstrap implements ApplicationRunner {

    /** 默认预设标签列表：涵盖跟单状态、达人类型和商业能力等维度 */
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

    /** 系统配置数据访问 Mapper */
    private final SystemConfigMapper systemConfigMapper;
    /** JSON 序列化工具 */
    private final ObjectMapper objectMapper;

    public TalentPresetTagsBootstrap(SystemConfigMapper systemConfigMapper, ObjectMapper objectMapper) {
        this.systemConfigMapper = systemConfigMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 应用启动时执行预设标签初始化。
     * <p>处理流程：</p>
     * <ol>
     *     <li>查询系统配置表中是否存在 {@link SystemConfigKeys#PRESET_TALENT_TAGS}</li>
     *     <li>若已存在则跳过，不覆盖管理员自定义配置</li>
     *     <li>若不存在则构建系统配置实体，将默认标签列表序列化为 JSON 并持久化</li>
     * </ol>
     *
     * @param args 应用启动参数
     * @throws Exception JSON 序列化或数据库操作异常
     */
    @Override
    public void run(ApplicationArguments args) throws Exception {
        // 第一步：检查预设标签配置是否已存在
        if (systemConfigMapper.findByConfigKey(SystemConfigKeys.PRESET_TALENT_TAGS).isPresent()) {
            return;
        }
        // 第二步：构建系统配置实体并写入默认标签
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
