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

/**
 * 系统配置管理服务。
 * <p>
 * 提供系统配置的完整 CRUD 能力，包括分页查询、按分组查询、新建、更新、删除和批量更新。
 * 配置变更时自动记录变更日志、发布领域事件（outbox）和 Spring 应用事件，并支持敏感值脱敏显示。
 * </p>
 *
 * <ul>
 *     <li>分页查询配置列表（{@link #findPage}）</li>
 *     <li>按分组查询配置（{@link #findGrouped}）</li>
 *     <li>新建配置项（{@link #create}）</li>
 *     <li>更新配置项（{@link #update}）</li>
 *     <li>删除配置项（{@link #delete}）</li>
 *     <li>按配置键批量更新（{@link #batchUpdateByKeys}）</li>
 *     <li>按配置键获取配置值（{@link #getConfigValue}）</li>
 * </ul>
 *
 * <p><b>业务域：</b>配置域 — 系统配置管理</p>
 * <p><b>协作关系：</b></p>
 * <ul>
 *     <li>{@link SystemConfigMapper} — 配置数据访问</li>
 *     <li>{@link SystemConfigChangeLogMapper} — 配置变更日志数据访问</li>
 *     <li>{@link OperationLogService} — 操作日志记录</li>
 *     <li>{@link ConfigDefinitionRegistry} — 配置定义注册表（校验与敏感性判断）</li>
 *     <li>{@link ConfigChangedEventFactory} — 配置变更事件构建</li>
 *     <li>{@link DomainEventOutboxService} — 领域事件 outbox 持久化</li>
 *     <li>{@link ApplicationEventPublisher} — Spring 应用事件发布</li>
 * </ul>
 */
@Service
public class SysConfigService {

    /** 变更来源标识：系统配置 API */
    public static final String CHANGE_SOURCE_API = "SYS_CONFIG_API";
    /** 变更来源标识：规则中心 */
    public static final String CHANGE_SOURCE_RULE_CENTER = "RULE_CENTER";

    /** 配置数据访问 Mapper */
    private final SystemConfigMapper systemConfigMapper;
    /** 配置变更日志数据访问 Mapper */
    private final SystemConfigChangeLogMapper systemConfigChangeLogMapper;
    /** 操作日志服务 */
    private final OperationLogService operationLogService;
    /** 配置定义注册表（校验规则和敏感性配置） */
    private final ConfigDefinitionRegistry configDefinitionRegistry;
    /** 配置变更事件构建工厂 */
    private final ConfigChangedEventFactory configChangedEventFactory;
    /** 领域事件 outbox 服务（持久化事件到 outbox 表） */
    private final DomainEventOutboxService domainEventOutboxService;
    /** Spring 事件发布器（发布应用内事件通知缓存刷新等） */
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

    /**
     * 分页查询系统配置列表。
     * <p>支持按配置分组和关键词（配置键/配置名称模糊匹配）进行筛选，按分组和排序号升序。</p>
     *
     * @param configGroup 配置分组（可为 null，不做分组过滤）
     * @param keyword     搜索关键词（可为 null，模糊匹配 config_key 和 config_name）
     * @param pageNo      页码（从 1 开始）
     * @param pageSize    每页条数
     * @return 分页结果
     */
    public IPage<SystemConfig> findPage(String configGroup, String keyword, int pageNo, int pageSize) {
        // 第一步：构建分页和查询条件
        Page<SystemConfig> page = new Page<>(pageNo, pageSize);
        QueryWrapper<SystemConfig> wrapper = new QueryWrapper<>();
        if (configGroup != null && !configGroup.isBlank()) {
            wrapper.eq("config_group", configGroup);
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like("config_key", keyword).or().like("config_name", keyword));
        }
        // 第二步：按分组和排序号排序后查询
        wrapper.orderByAsc("config_group").orderByAsc("sort_order");
        return systemConfigMapper.selectPage(page, wrapper);
    }

    /**
     * 按分组查询启用状态的配置，返回分组后的映射。
     * <p>
     * 仅查询 status=1 的配置项，按 config_group 分组，保持 LinkedHashMap 有序。
     * 当 {@code includeSensitive} 为 false 时，敏感配置的值将被脱敏为 "******"。
     * </p>
     *
     * @param includeSensitive 是否包含敏感配置的原始值（true 保留原值，false 脱敏显示）
     * @return 分组名到配置列表的有序映射
     */
    public Map<String, List<SystemConfig>> findGrouped(boolean includeSensitive) {
        // 第一步：查询所有启用状态的配置
        QueryWrapper<SystemConfig> wrapper = new QueryWrapper<>();
        wrapper.eq("status", 1);
        wrapper.orderByAsc("config_group").orderByAsc("sort_order");
        List<SystemConfig> configs = systemConfigMapper.selectList(wrapper);
        // 第二步：按分组名聚合，按需脱敏
        return configs.stream().collect(Collectors.groupingBy(
                c -> c.getConfigGroup() != null ? c.getConfigGroup() : "default",
                LinkedHashMap::new,
                Collectors.mapping(config -> includeSensitive ? config : maskSensitiveValue(config), Collectors.toList())
        ));
    }

    /**
     * 对敏感配置值进行脱敏处理。
     * <p>创建配置实体的副本，将敏感字段的 configValue 替换为 "******"，
     * 其他元数据字段保持不变。</p>
     *
     * @param source 原始配置实体
     * @return 脱敏后的配置副本
     */
    private SystemConfig maskSensitiveValue(SystemConfig source) {
        if (source == null) {
            return null;
        }
        // 复制所有元数据字段，仅对 configValue 做脱敏
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

    /**
     * 判断配置项是否为敏感配置。
     * <p>
     * 综合以下条件判断：配置定义注册表标记为敏感、配置类型/键/名称/分组中包含敏感关键词。
     * </p>
     *
     * @param config 配置实体
     * @return true 表示敏感配置
     */
    private boolean isSensitive(SystemConfig config) {
        String key = normalize(config == null ? null : config.getConfigKey());
        String type = normalize(config == null ? null : config.getConfigType());
        String name = normalize(config == null ? null : config.getConfigName());
        String group = normalize(config == null ? null : config.getConfigGroup());
        // 检查配置定义注册表
        if (configDefinitionRegistry.isSensitive(key)) {
            return true;
        }
        // 检查类型字段是否包含敏感关键词
        if (containsSensitiveKeyword(type)) {
            return true;
        }
        // 检查键、名称和分组是否包含敏感关键词
        return containsSensitiveKeyword(key)
                || containsSensitiveKeyword(name)
                || containsSensitiveKeyword(group);
    }

    /**
     * 检查文本中是否包含敏感关键词（token、secret、password、credential、appkey、private_key、refresh）。
     *
     * @param text 待检查文本
     * @return true 表示包含敏感关键词
     */
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

    /**
     * 标准化字符串：trim 后转小写，null 返回空字符串。
     *
     * @param value 原始字符串
     * @return 标准化后的字符串
     */
    private String normalize(String text) {
        return text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 根据 ID 获取配置项。
     *
     * @param id 配置项 ID
     * @return 配置实体
     * @throws BusinessException 配置项不存在
     */
    public SystemConfig getById(UUID id) {
        SystemConfig config = systemConfigMapper.selectById(id);
        if (config == null) {
            throw BusinessException.notFound("配置项不存在");
        }
        return config;
    }

    /**
     * 新建配置项。
     * <p>处理流程：</p>
     * <ol>
     *     <li>校验配置键和值的合法性</li>
     *     <li>检查配置键是否已存在（不允许重复）</li>
     *     <li>生成新 ID 和初始版本号</li>
     *     <li>持久化配置</li>
     *     <li>发布配置变更事件</li>
     *     <li>记录操作日志</li>
     * </ol>
     *
     * @param config  配置实体
     * @param userId  操作人 ID
     * @return 创建后的配置实体
     * @throws BusinessException 配置键已存在或校验失败
     */
    @Transactional(rollbackFor = Exception.class)
    public SystemConfig create(SystemConfig config, UUID userId) {
        // 第一步：校验配置合法性
        validateConfigForWrite(config);
        // 第二步：检查配置键唯一性
        systemConfigMapper.findByConfigKey(config.getConfigKey()).ifPresent(existing -> {
            throw BusinessException.duplicate("配置键已存在: " + config.getConfigKey());
        });
        // 第三步：设置元数据并持久化
        config.setCreateBy(userId);
        config.setUpdateBy(userId);
        config.setId(UUID.randomUUID());
        config.setConfigVersion(1);
        systemConfigMapper.insert(config);
        // 第四步：发布配置变更事件
        publishConfigChanged(
                List.of(new ConfigChangedEventFactory.ConfigChangeContext(
                        config.getConfigKey(), null, config.getConfigValue(), 1)),
                userId,
                null,
                CHANGE_SOURCE_API,
                "CREATE");
        // 第五步：记录操作日志
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

    /**
     * 更新配置项。
     * <p>处理流程：</p>
     * <ol>
     *     <li>加载现有配置</li>
     *     <li>检查配置键唯一性（若键发生变更）</li>
     *     <li>合并更新字段（非 null 字段覆盖）</li>
     *     <li>校验配置合法性</li>
     *     <li>自增配置版本号</li>
     *     <li>持久化更新</li>
     *     <li>发布配置变更事件</li>
     *     <li>记录操作日志</li>
     * </ol>
     *
     * @param id     配置项 ID
     * @param config 配置更新数据（非 null 字段覆盖）
     * @param userId 操作人 ID
     * @return 更新后的配置实体
     * @throws BusinessException 配置不存在、键重复或校验失败
     */
    @Transactional(rollbackFor = Exception.class)
    public SystemConfig update(UUID id, SystemConfig config, UUID userId) {
        // 第一步：加载现有配置
        SystemConfig existing = getById(id);
        String previousConfigValue = existing.getConfigValue();
        // 第二步：配置键变更时检查唯一性
        if (config.getConfigKey() != null && !config.getConfigKey().equals(existing.getConfigKey())) {
            systemConfigMapper.findByConfigKey(config.getConfigKey()).ifPresent(dup -> {
                throw BusinessException.duplicate("配置键已存在: " + config.getConfigKey());
            });
            existing.setConfigKey(config.getConfigKey());
        }
        // 第三步：合并更新字段（非 null 覆盖）
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
        // 第四步：校验、版本自增并持久化
        validateConfigForWrite(existing);
        existing.setUpdateBy(userId);
        bumpConfigVersion(existing);
        systemConfigMapper.updateById(existing);
        // 第五步：发布配置变更事件
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
        // 第六步：记录操作日志
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

    /**
     * 删除配置项（逻辑删除）。
     * <p>处理流程：</p>
     * <ol>
     *     <li>加载现有配置</li>
     *     <li>执行逻辑删除</li>
     *     <li>发布配置变更事件</li>
     *     <li>记录操作日志</li>
     * </ol>
     *
     * @param id     配置项 ID
     * @param userId 操作人 ID
     * @throws BusinessException 配置项不存在
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(UUID id, UUID userId) {
        // 第一步：加载并校验配置存在
        SystemConfig existing = getById(id);
        // 第二步：执行逻辑删除
        int affected = systemConfigMapper.softDeleteById(id, userId);
        if (affected == 0) {
            throw BusinessException.notFound("配置项不存在");
        }
        // 第三步：发布配置变更事件
        publishConfigChanged(
                List.of(new ConfigChangedEventFactory.ConfigChangeContext(
                        existing.getConfigKey(), existing.getConfigValue(), null,
                        existing.getConfigVersion() == null ? 1 : existing.getConfigVersion())),
                userId,
                null,
                CHANGE_SOURCE_API,
                "DELETE");
        // 第四步：记录操作日志
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

    /**
     * 按配置键获取配置值。
     *
     * @param configKey 配置键
     * @return 配置值（配置不存在时返回 null）
     */
    public String getConfigValue(String configKey) {
        return systemConfigMapper.findByConfigKey(configKey)
                .map(SystemConfig::getConfigValue)
                .orElse(null);
    }

    /**
     * 按配置键批量更新配置值。
     * <p>处理流程：</p>
     * <ol>
     *     <li>校验更新列表非空</li>
     *     <li>逐项加载配置、校验新旧值差异、校验合法性、版本自增并持久化</li>
     *     <li>逐条记录配置变更日志</li>
     *     <li>生成领域事件 payload 并写入 outbox</li>
     *     <li>发布 Spring 应用事件（通知缓存刷新等）</li>
     *     <li>返回变更事件 ID、变更键列表和警告信息</li>
     * </ol>
     *
     * @param updates      配置键到新值的映射
     * @param userId       操作人 ID
     * @param operatorName 操作人名称
     * @param source       变更来源标识
     * @param changeReason 变更原因
     * @return 批量更新结果（事件 ID、变更键列表、警告列表）
     * @throws BusinessException 更新列表为空或配置项不存在
     */
    @Transactional(rollbackFor = Exception.class)
    public BatchUpdateConfigResult batchUpdateByKeys(
            Map<String, String> updates,
            UUID userId,
            String operatorName,
            String source,
            String changeReason) {
        // 第一步：校验输入
        if (updates == null || updates.isEmpty()) {
            throw BusinessException.param("至少修改一项配置");
        }
        // 第二步：逐项处理更新
        List<ConfigChangedEventFactory.ConfigChangeContext> changes = new java.util.ArrayList<>();
        UUID eventId = UUID.randomUUID();
        for (Map.Entry<String, String> entry : updates.entrySet()) {
            String configKey = entry.getKey();
            String newValue = entry.getValue();
            // 加载配置并校验存在性
            SystemConfig existing = systemConfigMapper.findByConfigKey(configKey)
                    .orElseThrow(() -> BusinessException.notFound("配置项不存在: " + configKey));
            String oldValue = existing.getConfigValue();
            // 跳过值未变更的配置项
            if (Objects.equals(oldValue, newValue)) {
                continue;
            }
            // 校验、版本自增并持久化
            existing.setConfigValue(newValue);
            validateConfigForWrite(existing);
            existing.setUpdateBy(userId);
            bumpConfigVersion(existing);
            systemConfigMapper.updateById(existing);
            // 记录变更日志
            recordConfigChange(existing, "UPDATE", oldValue, newValue, userId, eventId, source, changeReason);
            changes.add(new ConfigChangedEventFactory.ConfigChangeContext(
                    configKey, oldValue, newValue, existing.getConfigVersion()));
        }
        // 第三步：无实际变更时直接返回
        if (changes.isEmpty()) {
            return new BatchUpdateConfigResult(eventId, List.of(), List.of());
        }
        // 第四步：构建并持久化领域事件
        ConfigChangedEventPayload payload = configChangedEventFactory.create(
                eventId, userId, operatorName, changeReason, source, changes);
        domainEventOutboxService.saveConfigChangedEvent(payload, userId);
        // 第五步：发布 Spring 应用事件
        Set<String> changedKeys = changes.stream()
                .map(ConfigChangedEventFactory.ConfigChangeContext::configKey)
                .collect(Collectors.toSet());
        applicationEventPublisher.publishEvent(new ConfigChangedApplicationEvent(changedKeys));
        // 第六步：构建返回结果（含可能的提成比例变更警告）
        return new BatchUpdateConfigResult(
                eventId,
                List.copyOf(changedKeys),
                payload.impact().needManualRecalculate() ? List.of("提成比例变更只影响后续计算或手动重算") : List.of());
    }

    /**
     * 批量更新配置的结果记录。
     *
     * @param eventId     变更事件 ID
     * @param changedKeys 实际发生变更的配置键列表
     * @param warnings    警告信息列表（如提成比例变更需要手动重算）
     */
    public record BatchUpdateConfigResult(
            UUID eventId,
            List<String> changedKeys,
            List<String> warnings) {
    }

    /**
     * 发布配置变更事件。
     * <p>
     * 为每条变更记录写入变更日志，构建领域事件 payload 持久化到 outbox，
     * 同时发布 Spring 应用事件通知缓存刷新等下游消费者。
     * </p>
     *
     * @param changes      变更上下文列表
     * @param userId       操作人 ID
     * @param operatorName 操作人名称
     * @param source       变更来源标识
     * @param changeAction 变更操作类型（CREATE/UPDATE/DELETE）
     */
    private void publishConfigChanged(
            List<ConfigChangedEventFactory.ConfigChangeContext> changes,
            UUID userId,
            String operatorName,
            String source,
            String changeAction) {
        if (changes == null || changes.isEmpty()) {
            return;
        }
        // 第一步：生成事件 ID 并逐条记录变更日志
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
        // 第二步：构建领域事件并持久化到 outbox
        ConfigChangedEventPayload payload = configChangedEventFactory.create(
                eventId, userId, operatorName, null, source, changes);
        domainEventOutboxService.saveConfigChangedEvent(payload, userId);
        // 第三步：发布 Spring 应用事件
        Set<String> changedKeys = changes.stream()
                .map(ConfigChangedEventFactory.ConfigChangeContext::configKey)
                .collect(Collectors.toSet());
        applicationEventPublisher.publishEvent(new ConfigChangedApplicationEvent(changedKeys));
    }

    /**
     * 自增配置版本号。
     *
     * @param config 配置实体（版本号为 null 时从 0 开始自增）
     */
    private void bumpConfigVersion(SystemConfig config) {
        int current = config.getConfigVersion() == null ? 0 : config.getConfigVersion();
        config.setConfigVersion(current + 1);
    }

    /**
     * 校验配置写入的合法性。
     * <p>检查配置键非空，并通过配置定义注册表校验键和值。</p>
     *
     * @param config 配置实体
     * @throws BusinessException 配置键为空或校验失败
     */
    private void validateConfigForWrite(SystemConfig config) {
        if (config == null || !StringUtils.hasText(config.getConfigKey())) {
            throw BusinessException.param("配置键不能为空");
        }
        configDefinitionRegistry.validateOrThrow(config.getConfigKey(), config.getConfigValue());
    }

    /**
     * 记录配置变更日志。
     * <p>构建 {@link SystemConfigChangeLog} 并持久化，包含变更操作类型、
     * 新旧值、变更来源、操作人、事件 ID 和变更原因。</p>
     *
     * @param config       变更的配置实体（可为 null）
     * @param action       变更操作类型（CREATE/UPDATE/DELETE）
     * @param oldValue     变更前的配置值
     * @param newValue     变更后的配置值
     * @param operatorId   操作人 ID
     * @param eventId      关联的变更事件 ID
     * @param source       变更来源
     * @param changeReason 变更原因
     */
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
