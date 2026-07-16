package com.colonel.saas.service.talent;

import com.colonel.saas.common.enums.TalentDataSource;
import com.colonel.saas.entity.Talent;
import com.colonel.saas.entity.TalentFieldSource;
import com.colonel.saas.mapper.TalentFieldSourceMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class TalentEnrichOrchestratorTest {

    @Mock
    private TalentFieldSourceMapper talentFieldSourceMapper;

    @Test
    void enrich_returnsNoDataWhenTalentIsNull() {
        TalentEnrichOrchestrator orchestrator = new TalentEnrichOrchestrator(List.of(), talentFieldSourceMapper);

        TalentEnrichOrchestrator.OrchestrateResult result = orchestrator.enrich(null, false);

        assertThat(result.updated()).isFalse();
        assertThat(result.sourceType()).isNull();
        assertThat(result.message()).isEqualTo("talent is null");
        verifyNoInteractions(talentFieldSourceMapper);
    }

    @Test
    void enrich_appliesFirstSupportedProviderWithFieldsInPriorityOrder() {
        UUID talentId = UUID.randomUUID();
        Talent talent = new Talent();
        talent.setId(talentId);
        talent.setUnsupportedFields(List.of("talentLevel", "sales30d"));
        List<String> calls = new ArrayList<>();
        StubProvider emptyFirst = new StubProvider(
                "empty",
                TalentDataSource.PUBLIC_PAGE,
                10,
                true,
                TalentEnrichResult.empty(TalentDataSource.PUBLIC_PAGE, "empty"),
                calls
        );
        StubProvider unsupportedSecond = new StubProvider(
                "unsupported",
                TalentDataSource.OFFICIAL_API,
                20,
                false,
                TalentEnrichResult.of(TalentDataSource.OFFICIAL_API, Map.of("nickname", "should-not-apply"), "ignored"),
                calls
        );
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("nickname", "补全达人");
        fields.put("avatarUrl", "https://avatar.test/u.png");
        fields.put("fans", "12345");
        fields.put("likesCount", 678);
        fields.put("followingCount", " ");
        fields.put("worksCount", "bad-number");
        fields.put("ipLocation", "广东");
        fields.put("talentLevel", "LV2");
        fields.put("sales30d", "68000");
        fields.put("unsupportedField", "kept-as-source");
        fields.put("nullField", null);
        StubProvider updateThird = new StubProvider(
                "update",
                TalentDataSource.MANUAL,
                30,
                true,
                TalentEnrichResult.of(TalentDataSource.MANUAL, fields, "manual enrich"),
                calls
        );
        TalentEnrichOrchestrator orchestrator = new TalentEnrichOrchestrator(
                List.of(updateThird, unsupportedSecond, emptyFirst),
                talentFieldSourceMapper
        );

        TalentEnrichOrchestrator.OrchestrateResult result = orchestrator.enrich(talent, true);

        assertThat(calls).containsExactly(
                "empty.supports:true",
                "empty.enrich:true",
                "unsupported.supports:true",
                "update.supports:true",
                "update.enrich:true"
        );
        assertThat(result.updated()).isTrue();
        assertThat(result.sourceType()).isEqualTo("MANUAL");
        assertThat(result.message()).isEqualTo("manual enrich");
        assertThat(talent.getNickname()).isEqualTo("补全达人");
        assertThat(talent.getAvatarUrl()).isEqualTo("https://avatar.test/u.png");
        assertThat(talent.getFans()).isEqualTo(12345L);
        assertThat(talent.getLikesCount()).isEqualTo(678L);
        assertThat(talent.getFollowingCount()).isNull();
        assertThat(talent.getWorksCount()).isNull();
        assertThat(talent.getIpLocation()).isEqualTo("广东");
        assertThat(talent.getTalentLevel()).isEqualTo("LV2");
        assertThat(talent.getSales30d()).isEqualTo(68000L);
        assertThat(talent.getUnsupportedFields()).isEmpty();
        assertThat(talent.getDataSource()).isEqualTo("MANUAL");
        assertThat(talent.getEnrichStatus()).isEqualTo("SUCCESS");
        assertThat(talent.getLastEnrichTime()).isNotNull();

        ArgumentCaptor<TalentFieldSource> sourceCaptor = ArgumentCaptor.forClass(TalentFieldSource.class);
        verify(talentFieldSourceMapper, times(10)).insert(sourceCaptor.capture());
        assertThat(sourceCaptor.getAllValues())
                .allSatisfy(source -> {
                    assertThat(source.getTalentId()).isEqualTo(talentId);
                    assertThat(source.getSourceType()).isEqualTo("MANUAL");
                    assertThat(source.getVerifiedTime()).isNotNull();
                })
                .extracting(TalentFieldSource::getFieldName)
                .containsExactly(
                        "nickname",
                        "avatarUrl",
                        "fans",
                        "likesCount",
                        "followingCount",
                        "worksCount",
                        "ipLocation",
                        "talentLevel",
                        "sales30d",
                        "unsupportedField"
                );
    }

    @Test
    void enrich_marksNoDataWhenProvidersAreUnsupportedNullOrEmpty() {
        Talent talent = new Talent();
        List<String> calls = new ArrayList<>();
        StubProvider unsupported = new StubProvider(
                "unsupported",
                TalentDataSource.OFFICIAL_API,
                1,
                false,
                TalentEnrichResult.of(TalentDataSource.OFFICIAL_API, Map.of("nickname", "ignored"), "ignored"),
                calls
        );
        StubProvider nullResult = new StubProvider("null-result", TalentDataSource.THIRD_PARTY, 2, true, null, calls);
        StubProvider emptyResult = new StubProvider(
                "empty-result",
                TalentDataSource.PUBLIC_PAGE,
                3,
                true,
                TalentEnrichResult.empty(TalentDataSource.PUBLIC_PAGE, "empty"),
                calls
        );
        TalentEnrichOrchestrator orchestrator = new TalentEnrichOrchestrator(
                List.of(emptyResult, unsupported, nullResult),
                talentFieldSourceMapper
        );

        TalentEnrichOrchestrator.OrchestrateResult result = orchestrator.enrich(talent, false);

        assertThat(result.updated()).isFalse();
        assertThat(result.message()).isEqualTo("no provider returned data");
        assertThat(talent.getEnrichStatus()).isEqualTo("NO_DATA");
        assertThat(talent.getLastEnrichTime()).isNotNull();
        assertThat(calls).containsExactly(
                "unsupported.supports:false",
                "null-result.supports:false",
                "null-result.enrich:false",
                "empty-result.supports:false",
                "empty-result.enrich:false"
        );
        verify(talentFieldSourceMapper, never()).insert(any());
    }

    @Test
    void enrich_skipsFieldSourceRecordsWhenTalentIdIsMissing() {
        Talent talent = new Talent();
        TalentEnrichOrchestrator orchestrator = new TalentEnrichOrchestrator(
                List.of(new StubProvider(
                        "manual",
                        TalentDataSource.MANUAL,
                        1,
                        true,
                        TalentEnrichResult.of(TalentDataSource.MANUAL, Map.of("nickname", "无ID达人"), "manual"),
                        new ArrayList<>()
                )),
                talentFieldSourceMapper
        );

        TalentEnrichOrchestrator.OrchestrateResult result = orchestrator.enrich(talent, false);

        assertThat(result.updated()).isTrue();
        assertThat(talent.getNickname()).isEqualTo("无ID达人");
        assertThat(talent.getDataSource()).isEqualTo("MANUAL");
        verify(talentFieldSourceMapper, never()).insert(any());
    }

    private static final class StubProvider implements TalentDataProvider {
        private final String name;
        private final TalentDataSource source;
        private final int priority;
        private final boolean supports;
        private final TalentEnrichResult result;
        private final List<String> calls;

        private StubProvider(
                String name,
                TalentDataSource source,
                int priority,
                boolean supports,
                TalentEnrichResult result,
                List<String> calls) {
            this.name = name;
            this.source = source;
            this.priority = priority;
            this.supports = supports;
            this.result = result;
            this.calls = calls;
        }

        @Override
        public TalentDataSource source() {
            return source;
        }

        @Override
        public int priority() {
            return priority;
        }

        @Override
        public boolean supports(TalentEnrichContext context) {
            calls.add(name + ".supports:" + context.forceRefresh());
            return supports;
        }

        @Override
        public TalentEnrichResult enrich(TalentEnrichContext context) {
            calls.add(name + ".enrich:" + context.forceRefresh());
            return result;
        }
    }
}
