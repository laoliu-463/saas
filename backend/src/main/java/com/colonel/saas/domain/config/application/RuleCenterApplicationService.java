package com.colonel.saas.domain.config.application;

import com.colonel.saas.config.ConfigDefinitionRegistry;
import com.colonel.saas.config.RuleCenterSchemaRegistry;
import com.colonel.saas.config.RuleCenterSchemaRegistry.RuleItemSchema;
import com.colonel.saas.dto.rulecenter.RuleCenterSchemaResponse;
import com.colonel.saas.dto.rulecenter.RuleCenterValuesResponse;
import com.colonel.saas.entity.SystemConfig;
import com.colonel.saas.mapper.SystemConfigMapper;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 规则中心管理 Application Service（DDD-CONFIG-001 Slice 1）。
 *
 * <p>从 {@code service.RuleCenterService} 切出的只读部分（Slice 1）：
 * <ul>
 *   <li>{@link #schema} —— 返回全部规则项 schema</li>
 *   <li>{@link #currentValues} —— 返回全部规则项当前配置值</li>
 *   <li>{@link #groupValues} —— 返回指定分组配置值</li>
 *   <li>{@link #loadValue} —— 私有 helper（package-private，测试可访问）</li>
 *   <li>{@link #allRuleItems} —— 私有 helper（package-private）</li>
 * </ul>
 *
 * <p>剩余 6 个方法（validate / updateGroup / batchUpdate / changeLogs / eventStatus）
 * 含事务边界 + 乐观锁 + 领域事件发布等基础设施依赖，按 Slice 2+ 渐进切。</p>
 */
@Service
public class RuleCenterApplicationService {

    private final RuleCenterSchemaRegistry ruleCenterSchemaRegistry;
    private final ConfigDefinitionRegistry configDefinitionRegistry;
    private final SystemConfigMapper systemConfigMapper;

    public RuleCenterApplicationService(
            RuleCenterSchemaRegistry ruleCenterSchemaRegistry,
            ConfigDefinitionRegistry configDefinitionRegistry,
            SystemConfigMapper systemConfigMapper) {
        this.ruleCenterSchemaRegistry = ruleCenterSchemaRegistry;
        this.configDefinitionRegistry = configDefinitionRegistry;
        this.systemConfigMapper = systemConfigMapper;
    }

    /**
     * 返回规则中心全部配置项的 schema（分组列表及各配置项元数据）。
     */
    public RuleCenterSchemaResponse schema() {
        return new RuleCenterSchemaResponse(
                ruleCenterSchemaRegistry.groups().stream()
                        .map(RuleCenterSchemaResponse.RuleGroupView::from)
                        .toList());
    }

    /**
     * 返回全部规则项的当前配置值，按注册顺序排列。
     */
    public RuleCenterValuesResponse currentValues() {
        Map<String, String> values = new LinkedHashMap<>();
        for (RuleItemSchema item : allRuleItems()) {
            values.put(item.key(), loadValue(item.key()));
        }
        return new RuleCenterValuesResponse(values);
    }

    /**
     * 返回指定分组内规则项的当前配置值。
     */
    public RuleCenterValuesResponse groupValues(String groupCode) {
        Map<String, String> values = new LinkedHashMap<>();
        for (RuleItemSchema item : ruleCenterSchemaRegistry.itemsInGroup(groupCode)) {
            values.put(item.key(), loadValue(item.key()));
        }
        return new RuleCenterValuesResponse(values);
    }

    String loadValue(String configKey) {
        return systemConfigMapper.findByConfigKey(configKey)
                .map(SystemConfig::getConfigValue)
                .orElse("");
    }

    List<RuleItemSchema> allRuleItems() {
        return ruleCenterSchemaRegistry.groups().stream()
                .flatMap(group -> group.items().stream())
                .toList();
    }
}
