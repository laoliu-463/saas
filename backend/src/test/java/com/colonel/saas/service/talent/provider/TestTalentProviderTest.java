package com.colonel.saas.service.talent.provider;

import com.colonel.saas.common.enums.TalentDataSource;
import com.colonel.saas.entity.Talent;
import com.colonel.saas.service.talent.TalentEnrichContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestTalentProviderTest {

    private final TestTalentProvider provider = new TestTalentProvider();

    @Test
    void sourcePriorityAndSupportsUseTestModeContract() {
        assertThat(provider.source()).isEqualTo(TalentDataSource.TEST);
        assertThat(provider.priority()).isEqualTo(1);
        assertThat(provider.supports(null)).isFalse();
        assertThat(provider.supports(new TalentEnrichContext(null, false))).isFalse();
        assertThat(provider.supports(new TalentEnrichContext(new Talent(), false))).isTrue();
    }

    @Test
    void enrich_generatesDeterministicDemoFieldsFromFirstAvailableInput() {
        Talent talent = new Talent();
        talent.setDouyinUid(" demo_uid ");

        var result = provider.enrich(new TalentEnrichContext(talent, false));

        assertThat(result.source()).isEqualTo(TalentDataSource.TEST);
        assertThat(result.message()).contains("generated demo");
        assertThat(result.fields())
                .containsKeys("nickname", "avatarUrl", "fans", "likesCount", "followingCount", "worksCount", "ipLocation");
        assertThat(result.fields().get("nickname")).asString().startsWith("演示达人-");
    }

    @Test
    void enrich_canReturnEmptyOrPartialOrFailForScenarioMarkers() {
        Talent empty = talentWithDouyinNo("test_empty_case");
        Talent partial = talentWithDouyinNo("test_partial_case");
        Talent fail = talentWithDouyinNo("test_fail_case");

        assertThat(provider.enrich(new TalentEnrichContext(empty, false)).hasFields()).isFalse();

        var partialResult = provider.enrich(new TalentEnrichContext(partial, false));
        assertThat(partialResult.fields()).doesNotContainKeys("ipLocation", "followingCount");
        assertThat(partialResult.fields()).containsKeys("nickname", "fans", "worksCount");

        assertThatThrownBy(() -> provider.enrich(new TalentEnrichContext(fail, false)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("simulated failure");
    }

    @Test
    void enrich_fallsBackThroughAllSupportedTalentIdentifiers() {
        Talent uid = new Talent();
        uid.setUid("uid-value");
        Talent secUid = new Talent();
        secUid.setSecUid("sec-value");
        Talent profileUrl = new Talent();
        profileUrl.setProfileUrl("https://example.test/profile");
        Talent empty = new Talent();

        assertThat(provider.enrich(new TalentEnrichContext(uid, false)).fields()).isNotEmpty();
        assertThat(provider.enrich(new TalentEnrichContext(secUid, false)).fields()).isNotEmpty();
        assertThat(provider.enrich(new TalentEnrichContext(profileUrl, false)).fields()).isNotEmpty();
        assertThat(provider.enrich(new TalentEnrichContext(empty, false)).fields()).isNotEmpty();
    }

    private static Talent talentWithDouyinNo(String douyinNo) {
        Talent talent = new Talent();
        talent.setDouyinNo(douyinNo);
        return talent;
    }
}
