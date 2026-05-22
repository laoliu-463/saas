package com.colonel.saas.service.talent;

import com.colonel.saas.common.enums.TalentDataSource;
import com.colonel.saas.entity.Talent;
import com.colonel.saas.service.talent.provider.InternalBusinessTalentProvider;
import com.colonel.saas.service.talent.provider.ThirdPartyTalentProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
        assertThat(thirdParty.supports(talentContext)).isTrue();
        assertThat(thirdParty.enrich(talentContext).message()).contains("third party");
    }
}
