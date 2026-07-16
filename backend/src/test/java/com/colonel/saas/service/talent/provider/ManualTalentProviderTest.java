package com.colonel.saas.service.talent.provider;

import com.colonel.saas.common.enums.TalentDataSource;
import com.colonel.saas.entity.Talent;
import com.colonel.saas.service.talent.TalentEnrichContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ManualTalentProviderTest {

    private final ManualTalentProvider provider = new ManualTalentProvider();

    @Test
    void sourcePriorityAndSupportsUseManualContract() {
        assertThat(provider.source()).isEqualTo(TalentDataSource.MANUAL);
        assertThat(provider.priority()).isEqualTo(90);
        assertThat(provider.supports(null)).isFalse();
        assertThat(provider.supports(new TalentEnrichContext(null, false))).isFalse();
        assertThat(provider.supports(new TalentEnrichContext(new Talent(), false))).isTrue();
    }

    @Test
    void enrichShouldTrimTextFieldsAndCopyAvailableMetrics() {
        Talent talent = new Talent();
        talent.setNickname("  手动达人  ");
        talent.setAvatarUrl("  https://example.test/avatar.png  ");
        talent.setFans(12000L);
        talent.setLikesCount(300L);
        talent.setFollowingCount(12L);
        talent.setWorksCount(40L);
        talent.setIpLocation("  上海  ");
        talent.setTalentLevel(" LV2 ");
        talent.setSales30d(68000L);

        var result = provider.enrich(new TalentEnrichContext(talent, false));

        assertThat(result.source()).isEqualTo(TalentDataSource.MANUAL);
        assertThat(result.message()).isEqualTo("manual data merged");
        assertThat(result.fields())
                .containsEntry("nickname", "手动达人")
                .containsEntry("avatarUrl", "https://example.test/avatar.png")
                .containsEntry("fans", 12000L)
                .containsEntry("likesCount", 300L)
                .containsEntry("followingCount", 12L)
                .containsEntry("worksCount", 40L)
                .containsEntry("ipLocation", "上海")
                .containsEntry("talentLevel", "LV2")
                .containsEntry("sales30d", 68000L);
    }

    @Test
    void enrichShouldReturnEmptyWhenManualTalentHasNoUsableFields() {
        Talent talent = new Talent();
        talent.setNickname(" ");
        talent.setAvatarUrl("");

        var result = provider.enrich(new TalentEnrichContext(talent, false));

        assertThat(result.source()).isEqualTo(TalentDataSource.MANUAL);
        assertThat(result.hasFields()).isFalse();
        assertThat(result.message()).isEqualTo("manual data is empty");
    }
}
