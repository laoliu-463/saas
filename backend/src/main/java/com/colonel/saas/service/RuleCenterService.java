package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.config.ConfigDefinitionRegistry;
import com.colonel.saas.config.RuleCenterSchemaRegistry;
import com.colonel.saas.config.RuleCenterSchemaRegistry.RuleItemSchema;
import com.colonel.saas.domain.config.application.RuleCenterApplicationService;
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
import com.colonel.saas.entity.SystemConfigChangeLog;
import com.colonel.saas.mapper.SystemConfigChangeLogMapper;
import com.colonel.saas.mapper.SystemConfigMapper;
import org.springframework.context.annotation.Lazy;
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
 * 规则中心管理服务（DDD 委派壳 + 部分切出，DDD-CONFIG-001 Slice 1）。
 *
 * <p>职责：规则中心页面的配置元数据查询、配置值读写、校验、变更日志查询和事件状态追踪。
 * Controller（{@code RuleCenterController}）当前仍直接注入本 Service。</p>
 *
 * <p><b>已切出的只读方法（DDD-CONFIG-001 Slice 1）：</b>
 * <ul>
 *   <li>{@link #schema} → {@link RuleCenterApplicationService}</li>
 *   <li>{@link #currentValues} → {@link RuleCenterApplicationService}</li>
 *   <li>{@link #groupValues} → {@link RuleCenterApplicationService}</li>
 * </ul>
 *
 * <p><b>剩余待切方法（DDD-CONFIG-001 Slice 2+ 候选）：</b>
 * <ul>
 *   <li>{@link #validate} —— 含 {@link ConfigDefinitionRegistry#validateOrThrow} 调用</li>
 *   <li>{@link #updateGroup} / {@link #batchUpdate} —— 事务边界 + 乐观锁 + 领域事件发布</li>
 *   <li>{@link #changeLogs} / {@link #eventStatus} —— 复杂查询 + DTO 装配</li>
 * </ul>
 */
@Service
public class RuleCenterService {

    private final RuleCenterSchemaRegistry ruleCenterSchemaRegistry;
    private final ConfigDefinitionRegistry configDefinitionRegistry;
    private final SystemConfigMapper systemConfigMapper;
    private final SystemConfigChangeLogMapper systemConfigChangeLogMapper;
    private final SysConfigService sysConfigService;
    private final DomainEventOutboxService domainEventOutboxService;
    private final DomainEventConsumeLogMapper domainEventConsumeLogMapper;
    private final RuleCenterApplicationService ruleCenterApplicationService;

    public RuleCenterService(
            RuleCenterSchemaRegistry ruleCenterSchemaRegistry,
            ConfigDefinitionRegistry configDefinitionRegistry,
            SystemConfigMapper systemConfigMapper,
            SystemConfigChangeLogMapper systemConfigChangeLogMapper,
            SysConfigService sysConfigService,
            DomainEventOutboxService domainEventOutboxService,
            DomainEventConsumeLogMapper domainEventConsumeLogMapper,
            @Lazy RuleCenterApplicationService ruleCenterApplicationService) {
        this.ruleCenterSchemaRegistry = ruleCenterSchemaRegistry;
        this.configDefinitionRegistry = configDefinitionRegistry;
        this.systemConfigMapper = systemConfigMapper;
        this.systemConfigChangeLogMapper = systemConfigChangeLogMapper;
        this.sysConfigService = sysConfigService;
        this.domainEventOutboxService = domainEventOutboxService;
        this.domainEventConsumeLogMapper = domainEventConsumeLogMapper;
        this.ruleCenterApplicationService = ruleCenterApplicationService;
    }

    /** 返回规则中心全部配置项的 schema —— 1-line delegate（DDD-CONFIG-001 Slice 1）。 */
    public RuleCenterSchemaResponse schema() {
        return ruleCenterApplicationService.schema();
    }

    /** 返回全部规则项的当前配置值 —— 1-line delegate（DDD-CONFIG-001 Slice 1）。 */
    public RuleCenterValuesResponse currentValues() {
        return ruleCenterApplicationService.currentValues();
    }

    /** 返回指定分组内规则项的当前配置值 —— 1-line delegate（DDD-CONFIG-001 Slice 1）。 */
    public RuleCenterValuesResponse groupValues(String groupCode) {
        return ruleCenterApplicationService.groupValues(groupCode);
    }

    /**
     * 校验一组配置值的合法性。
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
     * 更新指定分组内的配置值。
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
     * 跨分组批量更新配置值。
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
     * 分页查询配置变更日志。
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
     * 查询领域事件的发布与消费状态。
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

    /** 全部规则项的 key 列表，用于变更日志查询范围过滤。 */
    private List<String> allRuleKeys() {
        return allRuleItems().stream().map(RuleItemSchema::key).toList();
    }

    /** 全部规则项 schema 列表（扁平化）。 */
    private List<RuleItemSchema> allRuleItems() {
        return ruleCenterSchemaRegistry.groups().stream()
                .flatMap(group -> group.items().stream())
                .toList();
    }
}
