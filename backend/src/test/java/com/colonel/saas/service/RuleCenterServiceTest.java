package com.colonel.saas.service;

import com.colonel.saas.config.ConfigDefinitionRegistry;
import com.colonel.saas.config.RuleCenterSchemaRegistry;
import com.colonel.saas.config.SystemConfigKeys;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RuleCenterServiceTest {

    private final ConfigDefinitionRegistry configDefinitionRegistry =
            new ConfigDefinitionRegistry(new ObjectMapper());
    private final RuleCenterSchemaRegistry ruleCenterSchemaRegistry =
            new RuleCenterSchemaRegistry(configDefinitionRegistry);
    private final RuleCenterService ruleCenterService = new RuleCenterService(
            ruleCenterSchemaRegistry,
            configDefinitionRegistry,
            null,
            null,
            null,
            null,
            null);

    @Test
    void schemaShouldExposePresetTalentTagsInRuleCenter() {
        var talentGroup = ruleCenterSchemaRegistry.findGroup("talent").orElseThrow();

        assertThat(talentGroup.items())
                .anySatisfy(item -> {
                    assertThat(item.key()).isEqualTo(SystemConfigKeys.PRESET_TALENT_TAGS);
                    assertThat(item.label()).isEqualTo("达人预设标签库");
                    assertThat(item.valueType()).isEqualTo(ConfigDefinitionRegistry.ConfigValueType.JSON);
                    assertThat(item.enabled()).isTrue();
                });
    }

    @Test
    void validateShouldRejectInvalidPresetTalentTagsJsonThroughRuleCenter() {
        var response = ruleCenterService.validate(Map.of(SystemConfigKeys.PRESET_TALENT_TAGS, "{\"tag\":\"美妆\"}"));

        assertThat(response.valid()).isFalse();
        assertThat(response.errors()).anyMatch(error -> error.contains("达人预设标签库") && error.contains("JSON 数组"));
    }
}
