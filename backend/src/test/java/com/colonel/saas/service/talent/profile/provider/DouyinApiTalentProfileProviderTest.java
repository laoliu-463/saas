package com.colonel.saas.service.talent.profile.provider;

import com.colonel.saas.config.TalentCollectProperties;
import com.colonel.saas.douyin.DouyinTokenService;
import com.colonel.saas.service.talent.profile.TalentProfileQuery;
import com.colonel.saas.service.talent.profile.TalentProfileResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DouyinApiTalentProfileProviderTest {

    @Mock
    private DouyinTokenService douyinTokenService;

    private TalentCollectProperties collectProperties;
    private DouyinApiTalentProfileProvider provider;

    @BeforeEach
    void setUp() {
        collectProperties = new TalentCollectProperties();
        collectProperties.setMode("api_then_crawler");
        collectProperties.getApi().setEnabled(true);
        provider = new DouyinApiTalentProfileProvider(collectProperties, douyinTokenService);
    }

    @Test
    void fetch_shouldReturnNotConfiguredWhenTokenMissing() {
        when(douyinTokenService.getTokenStatus(null)).thenReturn(new DouyinTokenService.TokenStatus(
                "app", false, "", false, "", 0L, false, false));

        TalentProfileResult result = provider.fetch(TalentProfileQuery.builder().input("dy_123").build());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo("NOT_CONFIGURED");
        assertThat(result.getProviderCode()).isEqualTo("API");
    }

    @Test
    void fetch_shouldReturnUnsupportedWhenTokenPresentButNoProfileApi() {
        when(douyinTokenService.getTokenStatus(null)).thenReturn(new DouyinTokenService.TokenStatus(
                "app", true, "****", true, "****", 9_999_999_999L, false, false));

        TalentProfileResult result = provider.fetch(TalentProfileQuery.builder().input("dy_123").build());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo("UNSUPPORTED");
    }

    @Test
    void supports_shouldBeFalseInMockCollectMode() {
        collectProperties.setMode("mock");
        assertThat(provider.supports(TalentProfileQuery.builder().input("dy").build())).isFalse();
    }
}
