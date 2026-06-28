package com.colonel.saas.domain.config.facade;

import com.colonel.saas.config.SystemConfigKeys;
import com.colonel.saas.entity.SystemConfig;
import com.colonel.saas.mapper.SystemConfigMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LegacyConfigSeedFacadeTest {

    @Mock
    private SystemConfigMapper systemConfigMapper;

    @Test
    void createJsonConfigIfMissing_shouldInsertSerializedJsonWhenMissing() {
        when(systemConfigMapper.findByConfigKey(SystemConfigKeys.PRESET_TALENT_TAGS))
                .thenReturn(Optional.empty());
        LegacyConfigSeedFacade facade = new LegacyConfigSeedFacade(systemConfigMapper, new ObjectMapper());

        boolean created = facade.createJsonConfigIfMissing(
                SystemConfigKeys.PRESET_TALENT_TAGS,
                List.of("高意向", "需要复盘"),
                "talent",
                "达人预设标签库");

        assertThat(created).isTrue();
        ArgumentCaptor<SystemConfig> captor = ArgumentCaptor.forClass(SystemConfig.class);
        verify(systemConfigMapper).insert(captor.capture());
        SystemConfig config = captor.getValue();
        assertThat(config.getConfigKey()).isEqualTo(SystemConfigKeys.PRESET_TALENT_TAGS);
        assertThat(config.getConfigValue()).contains("高意向").contains("需要复盘");
        assertThat(config.getConfigType()).isEqualTo("json");
        assertThat(config.getConfigGroup()).isEqualTo("talent");
        assertThat(config.getConfigName()).isEqualTo("达人预设标签库");
        assertThat(config.getStatus()).isEqualTo(1);
    }

    @Test
    void createJsonConfigIfMissing_shouldSkipExistingConfig() {
        when(systemConfigMapper.findByConfigKey(SystemConfigKeys.PRESET_TALENT_TAGS))
                .thenReturn(Optional.of(new SystemConfig()));
        LegacyConfigSeedFacade facade = new LegacyConfigSeedFacade(systemConfigMapper, new ObjectMapper());

        boolean created = facade.createJsonConfigIfMissing(
                SystemConfigKeys.PRESET_TALENT_TAGS,
                List.of("高意向"),
                "talent",
                "达人预设标签库");

        assertThat(created).isFalse();
        verify(systemConfigMapper, never()).insert(any());
    }
}
