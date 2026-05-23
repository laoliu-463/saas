package com.colonel.saas.config;

import com.colonel.saas.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigDefinitionRegistryTest {

    private final ConfigDefinitionRegistry registry = new ConfigDefinitionRegistry(new ObjectMapper());

    @Test
    void findShouldNormalizeKeysAndExposeDefinitionMetadata() {
        var definition = registry.find(" SAMPLE.RESTRICT_DAYS ").orElseThrow();

        assertThat(definition.key()).isEqualTo(SystemConfigKeys.SAMPLE_RESTRICT_DAYS);
        assertThat(definition.valueType()).isEqualTo(ConfigDefinitionRegistry.ConfigValueType.INTEGER);
        assertThat(definition.runtimeEditable()).isTrue();
        assertThat(definition.sensitive()).isFalse();
        assertThat(registry.isSensitive(SystemConfigKeys.SAMPLE_RESTRICT_DAYS)).isFalse();
    }

    @Test
    void validateOrThrowShouldAcceptBooleanAliasesAndRejectInvalidValues() {
        registry.validateOrThrow(SystemConfigKeys.SAMPLE_RESTRICT_ENABLED, "1");
        registry.validateOrThrow(SystemConfigKeys.SAMPLE_RESTRICT_ENABLED, " FALSE ");

        assertThatThrownBy(() -> registry.validateOrThrow(SystemConfigKeys.SAMPLE_RESTRICT_ENABLED, "yes"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("布尔值");
    }

    @Test
    void validateOrThrowShouldRejectInvalidJsonShapes() {
        assertThatThrownBy(() -> registry.validateOrThrow(SystemConfigKeys.SAMPLE_DEFAULT_STANDARD, "[]"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("JSON 对象");
        assertThatThrownBy(() -> registry.validateOrThrow(SystemConfigKeys.SAMPLE_DEFAULT_STANDARD, "{\"min_level\":\"gold\"}"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("min_level");
    }

    @Test
    void validateOrThrowShouldValidatePickExtraRule() {
        registry.validateOrThrow(
                SystemConfigKeys.PROMOTION_PICK_EXTRA_RULE,
                "{\"format\":\"channel_{channel_code}_{product_id}\",\"encode\":\"none\"}");

        assertThatThrownBy(() -> registry.validateOrThrow(SystemConfigKeys.PROMOTION_PICK_EXTRA_RULE, "[]"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("JSON 对象");
        assertThatThrownBy(() -> registry.validateOrThrow(
                SystemConfigKeys.PROMOTION_PICK_EXTRA_RULE,
                "{\"format\":\"\",\"encode\":\"none\"}"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("format");
    }

    @Test
    void validateOrThrowShouldValidatePresetTalentTags() {
        registry.validateOrThrow(SystemConfigKeys.PRESET_TALENT_TAGS, "[\"美妆\",\"高转化\"]");

        assertThatThrownBy(() -> registry.validateOrThrow(SystemConfigKeys.PRESET_TALENT_TAGS, "{\"tag\":\"美妆\"}"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("JSON 数组");
        assertThatThrownBy(() -> registry.validateOrThrow(SystemConfigKeys.PRESET_TALENT_TAGS, "[\"\"]"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能为空");
    }

    @Test
    void configDefinitionShouldRejectRuntimeWritesWhenNotEditable() {
        var definition = new ConfigDefinitionRegistry.ConfigDefinition(
                "readonly.key",
                ConfigDefinitionRegistry.ConfigValueType.STRING,
                false,
                true,
                value -> {
                });

        assertThat(definition.sensitive()).isTrue();
        assertThatThrownBy(() -> definition.validate("value"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不允许运行时修改");
    }
}
