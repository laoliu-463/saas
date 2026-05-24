package com.colonel.saas.service;

import com.colonel.saas.config.SystemConfigKeys;
import com.colonel.saas.entity.SystemConfig;
import com.colonel.saas.mapper.SystemConfigMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TalentPresetTagsBootstrapTest {

    @Mock
    private SystemConfigMapper systemConfigMapper;

    @Test
    void run_shouldInsertDefaultTagsWhenMissing() throws Exception {
        when(systemConfigMapper.findByConfigKey(SystemConfigKeys.PRESET_TALENT_TAGS)).thenReturn(Optional.empty());
        TalentPresetTagsBootstrap bootstrap = new TalentPresetTagsBootstrap(systemConfigMapper, new ObjectMapper());

        bootstrap.run(new DefaultApplicationArguments(new String[0]));

        ArgumentCaptor<SystemConfig> captor = ArgumentCaptor.forClass(SystemConfig.class);
        verify(systemConfigMapper).insert(captor.capture());
        assertThat(captor.getValue().getConfigKey()).isEqualTo(SystemConfigKeys.PRESET_TALENT_TAGS);
        assertThat(captor.getValue().getConfigValue()).contains("高意向");
        assertThat(captor.getValue().getConfigValue()).contains("需要复盘");
    }

    @Test
    void run_shouldNotOverwriteExistingConfig() throws Exception {
        when(systemConfigMapper.findByConfigKey(SystemConfigKeys.PRESET_TALENT_TAGS))
                .thenReturn(Optional.of(new SystemConfig()));
        TalentPresetTagsBootstrap bootstrap = new TalentPresetTagsBootstrap(systemConfigMapper, new ObjectMapper());

        bootstrap.run(new DefaultApplicationArguments(new String[0]));

        verify(systemConfigMapper, never()).insert(any());
    }
}
