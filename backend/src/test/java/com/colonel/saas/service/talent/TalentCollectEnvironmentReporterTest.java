package com.colonel.saas.service.talent;

import com.colonel.saas.config.TalentCollectEnvironmentStatus;
import com.colonel.saas.config.TalentCollectProperties;
import com.colonel.saas.douyin.DouyinTokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TalentCollectEnvironmentReporterTest {

    @Mock
    private DouyinTokenService douyinTokenService;

    @Test
    void resolveStatus_shouldReturnMockOnlyInTestEnrichMode() {
        TalentCollectProperties properties = new TalentCollectProperties();
        properties.setMode("api_then_crawler");
        properties.getApi().setEnabled(true);
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("test");
        environment.setProperty("talent.enrich.mode", "test");
        TalentCollectEnvironmentReporter reporter = new TalentCollectEnvironmentReporter(
                properties, douyinTokenService, environment);

        assertThat(reporter.resolveStatus()).isEqualTo(TalentCollectEnvironmentStatus.MOCK_ONLY);
    }

    @Test
    void resolveStatus_shouldReturnUnsupportedWhenApiEnabledWithToken() {
        TalentCollectProperties properties = new TalentCollectProperties();
        properties.setMode("api");
        properties.getApi().setEnabled(true);
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("talent.enrich.mode", "real");
        when(douyinTokenService.getTokenStatus(null)).thenReturn(new DouyinTokenService.TokenStatus(
                "app", true, "****", true, "****", 9_999_999_999L, false, false));
        TalentCollectEnvironmentReporter reporter = new TalentCollectEnvironmentReporter(
                properties, douyinTokenService, environment);

        assertThat(reporter.resolveStatus()).isEqualTo(TalentCollectEnvironmentStatus.UNSUPPORTED);
    }
}
