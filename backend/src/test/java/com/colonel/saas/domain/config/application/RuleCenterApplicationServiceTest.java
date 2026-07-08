package com.colonel.saas.domain.config.application;

import com.colonel.saas.config.ConfigDefinitionRegistry;
import com.colonel.saas.config.RuleCenterSchemaRegistry;
import com.colonel.saas.config.SystemConfigKeys;
import com.colonel.saas.entity.SystemConfig;
import com.colonel.saas.mapper.SystemConfigMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * RuleCenterApplicationService 单元测试（DDD-CONFIG-001 Slice 1）。
 *
 * <p>Service 的 {@code currentValues} / {@code groupValues} 委派壳后，业务断言已迁到本类；
 * Service 仍保留测试用例验证委派路径打通，详细断言见 {@link com.colonel.saas.service.RuleCenterServiceTest}。</p>
 */
@ExtendWith(MockitoExtension.class)
class RuleCenterApplicationServiceTest {

    @Mock
    private SystemConfigMapper systemConfigMapper;

    private RuleCenterApplicationService applicationService;

    @BeforeEach
    void setUp() {
        ConfigDefinitionRegistry configDefinitionRegistry = new ConfigDefinitionRegistry(new com.fasterxml.jackson.databind.ObjectMapper());
        RuleCenterSchemaRegistry schemaRegistry = new RuleCenterSchemaRegistry(configDefinitionRegistry);
        applicationService = new RuleCenterApplicationService(
                schemaRegistry, configDefinitionRegistry, systemConfigMapper);
    }

    @Test
    void currentValues_shouldIncludeStoredValues() {
        SystemConfig stored = new SystemConfig();
        stored.setConfigKey(SystemConfigKeys.PRESET_TALENT_TAGS);
        stored.setConfigValue("[\"美妆\"]");
        lenient().when(systemConfigMapper.findByConfigKey(SystemConfigKeys.PRESET_TALENT_TAGS))
                .thenReturn(Optional.of(stored));
        lenient().when(systemConfigMapper.findByConfigKey(org.mockito.ArgumentMatchers.argThat(k -> k != null && !k.equals(SystemConfigKeys.PRESET_TALENT_TAGS))))
                .thenReturn(Optional.empty());

        var current = applicationService.currentValues();

        assertThat(current.values()).containsEntry(SystemConfigKeys.PRESET_TALENT_TAGS, "[\"美妆\"]");
    }

    @Test
    void currentValues_shouldDefaultToEmptyWhenNotInDb() {
        lenient().when(systemConfigMapper.findByConfigKey(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Optional.empty());

        var current = applicationService.currentValues();

        assertThat(current.values()).containsEntry(SystemConfigKeys.PRESET_TALENT_TAGS, "");
    }

    @Test
    void groupValues_shouldReturnOnlyGroupItems() {
        var talent = applicationService.groupValues("talent");

        assertThat(talent.values()).containsKey(SystemConfigKeys.PRESET_TALENT_TAGS);
    }

    @Test
    void schema_shouldExposeRuleCenterGroups() {
        var schema = applicationService.schema();

        assertThat(schema.groups()).isNotEmpty();
        assertThat(schema.groups())
                .anySatisfy(group -> assertThat(group.items())
                        .anySatisfy(item -> assertThat(item.key()).isEqualTo(SystemConfigKeys.PRESET_TALENT_TAGS)));
    }
}
