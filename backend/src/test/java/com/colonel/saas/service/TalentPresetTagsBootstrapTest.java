package com.colonel.saas.service;

import com.colonel.saas.config.SystemConfigKeys;
import com.colonel.saas.domain.config.facade.ConfigSeedFacade;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TalentPresetTagsBootstrapTest {

    @Mock
    private ConfigSeedFacade configSeedFacade;

    @Test
    void run_shouldCreateDefaultTagsThroughConfigSeedFacadeWhenMissing() throws Exception {
        when(configSeedFacade.createJsonConfigIfMissing(
                eq(SystemConfigKeys.PRESET_TALENT_TAGS),
                eq(TalentPresetTagsBootstrap.DEFAULT_PRESET_TAGS),
                eq("talent"),
                eq("达人预设标签库"))).thenReturn(true);
        TalentPresetTagsBootstrap bootstrap = new TalentPresetTagsBootstrap(configSeedFacade);

        bootstrap.run(new DefaultApplicationArguments(new String[0]));

        verify(configSeedFacade).createJsonConfigIfMissing(
                SystemConfigKeys.PRESET_TALENT_TAGS,
                TalentPresetTagsBootstrap.DEFAULT_PRESET_TAGS,
                "talent",
                "达人预设标签库");
    }

    @Test
    void run_shouldDelegateExistingConfigDecisionToConfigSeedFacade() throws Exception {
        when(configSeedFacade.createJsonConfigIfMissing(
                eq(SystemConfigKeys.PRESET_TALENT_TAGS),
                eq(TalentPresetTagsBootstrap.DEFAULT_PRESET_TAGS),
                eq("talent"),
                eq("达人预设标签库"))).thenReturn(false);
        TalentPresetTagsBootstrap bootstrap = new TalentPresetTagsBootstrap(configSeedFacade);

        bootstrap.run(new DefaultApplicationArguments(new String[0]));

        verify(configSeedFacade).createJsonConfigIfMissing(
                SystemConfigKeys.PRESET_TALENT_TAGS,
                TalentPresetTagsBootstrap.DEFAULT_PRESET_TAGS,
                "talent",
                "达人预设标签库");
    }
}
