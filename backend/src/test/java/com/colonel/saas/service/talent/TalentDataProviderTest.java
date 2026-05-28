package com.colonel.saas.service.talent;

import com.colonel.saas.common.enums.TalentDataSource;
import com.colonel.saas.entity.Talent;
import com.colonel.saas.service.talent.provider.InternalBusinessTalentProvider;
import com.colonel.saas.service.talent.provider.ThirdPartyTalentProvider;
import com.colonel.saas.service.talent.profile.TalentProfileResult;
import com.colonel.saas.service.talent.profile.provider.ConfigurableHttpTalentProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TalentDataProviderTest {

    @Test
    void defaultPriorityShouldBeOneHundred() {
        TalentDataProvider provider = new TalentDataProvider() {
            @Override
            public TalentDataSource source() {
                return TalentDataSource.MANUAL;
            }

            @Override
            public boolean supports(TalentEnrichContext context) {
                return false;
            }

            @Override
            public TalentEnrichResult enrich(TalentEnrichContext context) {
                return TalentEnrichResult.empty(source(), "empty");
            }
        };

        assertThat(provider.priority()).isEqualTo(100);
    }

    @Test
    void internalAndThirdPartyProvidersShouldExposePlaceholderContracts() {
        TalentEnrichContext emptyContext = new TalentEnrichContext(null, false);
        TalentEnrichContext talentContext = new TalentEnrichContext(new Talent(), true);
        InternalBusinessTalentProvider internal = new InternalBusinessTalentProvider();
        ThirdPartyTalentProvider thirdParty = new ThirdPartyTalentProvider();

        assertThat(internal.source()).isEqualTo(TalentDataSource.INTERNAL_BUSINESS);
        assertThat(internal.priority()).isEqualTo(20);
        assertThat(internal.supports(null)).isFalse();
        assertThat(internal.supports(emptyContext)).isFalse();
        assertThat(internal.supports(talentContext)).isTrue();
        assertThat(internal.enrich(talentContext).message()).contains("internal business");

        assertThat(thirdParty.source()).isEqualTo(TalentDataSource.THIRD_PARTY);
        assertThat(thirdParty.priority()).isEqualTo(10);
        assertThat(thirdParty.supports(null)).isFalse();
        assertThat(thirdParty.supports(emptyContext)).isFalse();
        assertThat(thirdParty.supports(talentContext)).isFalse();
        assertThat(thirdParty.enrich(talentContext).message()).contains("not configured");
    }

    @Test
    void thirdPartyProviderShouldDelegateToConfiguredHttpProfileProvider() {
        ConfigurableHttpTalentProvider httpProvider = mock(ConfigurableHttpTalentProvider.class);
        ThirdPartyTalentProvider thirdParty = new ThirdPartyTalentProvider(httpProvider);
        Talent talent = new Talent();
        talent.setDouyinNo("douyin-user");
        TalentEnrichContext context = new TalentEnrichContext(talent, true);
        when(httpProvider.supports(any())).thenReturn(true);
        when(httpProvider.fetch(any())).thenReturn(TalentProfileResult.builder()
                .success(true)
                .providerCode("configurable_http")
                .nickname("达人 A")
                .avatarUrl("https://example.test/avatar.png")
                .fansCount(1234L)
                .likeCount(5678L)
                .followingCount(12L)
                .worksCount(34L)
                .ipLocation("上海")
                .build());

        assertThat(thirdParty.supports(context)).isTrue();
        TalentEnrichResult result = thirdParty.enrich(context);

        assertThat(result.source()).isEqualTo(TalentDataSource.THIRD_PARTY);
        assertThat(result.fields())
                .containsEntry("nickname", "达人 A")
                .containsEntry("avatarUrl", "https://example.test/avatar.png")
                .containsEntry("fans", 1234L)
                .containsEntry("likesCount", 5678L)
                .containsEntry("followingCount", 12L)
                .containsEntry("worksCount", 34L)
                .containsEntry("ipLocation", "上海");
        assertThat(result.message()).contains("configurable_http");
        verify(httpProvider).fetch(any());
    }
}
