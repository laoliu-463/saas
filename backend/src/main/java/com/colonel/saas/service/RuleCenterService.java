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

/**
 * 规则中心管理服务：为前端规则中心页面提供配置元数据查询、配置值读写、校验、变更日志查询和事件状态追踪能力。
 * <p>
 * 主要职责：
 * <ul>
 *   <li>返回配置项 schema（分组、类型、约束），供前端渲染表单</li>
 *   <li>按分组或全局读取当前配置值</li>
 *   <li>校验配置值合法性（类型、范围、必填）并生成提成率告警</li>
 *   <li>按分组或跨分组批量更新配置值，写入变更日志并发布领域事件</li>
 *   <li>查询配置变更历史（按 configKey 或全量规则项）</li>
 *   <li>追踪领域事件的发布与消费状态</li>
 * </ul>
 */
@Service
public class RuleCenterService {

    /** 规则项 schema 注册表，提供分组和配置项元数据 */
    private final RuleCenterSchemaRegistry ruleCenterSchemaRegistry;
    /** 配置定义注册表，提供配置值校验能力 */
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

    /** 返回规则中心全部配置项的 schema（分组列表及各配置项元数据）。 */
    public RuleCenterSchemaResponse schema() {
        return new RuleCenterSchemaResponse(
                ruleCenterSchemaRegistry.groups().stream()
                        .map(RuleCenterSchemaResponse.RuleGroupView::from)
                        .toList());
    }

    /** 返回全部规则项的当前配置值，按注册顺序排列。 */
    public RuleCenterValuesResponse currentValues() {
        Map<String, String> values = new LinkedHashMap<>();
        for (RuleItemSchema item : allRuleItems()) {
            values.put(item.key(), loadValue(item.key()));
        }
        return new RuleCenterValuesResponse(values);
    }

    /**
     * 返回指定分组内规则项的当前配置值。
     *
     * @param groupCode 规则分组编码
     * @return 该分组内各配置项的当前值
     */
    public RuleCenterValuesResponse groupValues(String groupCode) {
        Map<String, String> values = new LinkedHashMap<>();
        for (RuleItemSchema item : ruleCenterSchemaRegistry.itemsInGroup(groupCode)) {
            values.put(item.key(), loadValue(item.key()));
        }
        return new RuleCenterValuesResponse(values);
    }

    /**
     * 校验一组配置值的合法性：检查配置项是否存在、是否启用、是否满足类型/范围约束，
     * 并生成提成率变更告警。
     *
     * @param values 待校验的配置键值对
     * @return 校验结果（是否通过、错误列表、告警列表）
     */
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

    /**
     * 更新指定分组内的配置值：过滤非本分组的键，校验合法性后批量持久化，
     * 写入变更日志并发布领域事件。
     *
     * @param groupCode     规则分组编码
     * @param values        待更新的配置键值对
     * @param changeReason  变更原因说明
     * @param userId        操作人用户 ID
     * @param operatorName  操作人显示名称
     * @return 更新结果（事件 ID、变更的 key 列表、告警列表）
     * @throws BusinessException 分组不存在或校验失败时抛出
     */
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

    /**
     * 跨分组批量更新配置值：过滤未启用或不存在的配置项，校验合法性后持久化，
     * 写入变更日志并发布领域事件。
     *
     * @param values        待更新的配置键值对（可跨多个分组）
     * @param changeReason  变更原因说明
     * @param userId        操作人用户 ID
     * @param operatorName  操作人显示名称
     * @return 更新结果（事件 ID、变更的 key 列表、告警列表）
     * @throws BusinessException 校验失败时抛出
     */
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

    /**
     * 分页查询配置变更日志：指定 configKey 时仅返回该配置项的变更记录，
     * 否则返回全部规则项的变更历史，按变更时间倒序。
     *
     * @param configKey 配置项 key，为 null 或空时查询全部规则项
     * @param pageNo    页码（从 1 开始）
     * @param pageSize  每页条数
     * @return 分页结果，每条记录映射为 {@link RuleCenterChangeLogView}
     */
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

    /**
     * 查询领域事件的发布与消费状态，包含各消费者的消费结果。
     *
     * @param eventId 事件 ID（来自配置变更时生成的 outbox 事件）
     * @return 事件状态详情（发布状态、重试次数、消费者列表）
     * @throws BusinessException 事件不存在时抛出
     */
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

    /** 将持久层变更日志实体转换为前端展示的视图 DTO。 */
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

    /** 过滤出允许集合内的配置键值对，全部过滤为空时抛出参数异常。 */
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

    /** 从数据库加载配置项当前值，不存在时返回空字符串。 */
    private String loadValue(String configKey) {
        return systemConfigMapper.findByConfigKey(configKey)
                .map(SystemConfig::getConfigValue)
                .orElse("");
    }

    /** 返回全部分组下的所有规则项 schema 列表（扁平化）。 */
    private List<RuleItemSchema> allRuleItems() {
        return ruleCenterSchemaRegistry.groups().stream()
                .flatMap(group -> group.items().stream())
                .toList();
    }

    /** 返回全部规则项的 key 列表，用于变更日志查询范围过滤。 */
    private List<String> allRuleKeys() {
        return allRuleItems().stream().map(RuleItemSchema::key).toList();
    }
}
