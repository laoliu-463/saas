package com.colonel.saas.domain.talent.application;

import com.colonel.saas.entity.CrawlerTalentInfo;
import com.colonel.saas.entity.Talent;
import com.colonel.saas.entity.TalentEnrichTask;
import com.colonel.saas.mapper.TalentClaimMapper;
import com.colonel.saas.mapper.TalentEnrichTaskMapper;
import com.colonel.saas.mapper.TalentMapper;
import com.colonel.saas.service.BusinessRuleConfigService;
import com.colonel.saas.service.CrawlerTalentInfoService;
import com.colonel.saas.service.talent.TalentEnrichOrchestrator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TalentProfileApplicationServiceTest {

    @Mock
    TalentMapper talentMapper;

    @Mock
    TalentClaimMapper talentClaimMapper;

    @Mock
    TalentEnrichTaskMapper talentEnrichTaskMapper;

    @Mock
    TalentEnrichOrchestrator talentEnrichOrchestrator;

    @Mock
    CrawlerTalentInfoService crawlerTalentInfoService;

    @Mock
    BusinessRuleConfigService businessRuleConfigService;

    private TalentProfileApplicationService service;

    @BeforeEach
    void setUp() {
        service = new TalentProfileApplicationService(
                talentMapper,
                talentClaimMapper,
                talentEnrichTaskMapper,
                talentEnrichOrchestrator,
                crawlerTalentInfoService,
                businessRuleConfigService,
                false);
    }

    @Test
    void updateTagsNormalizesAndPersistsTags() {
        UUID talentId = UUID.randomUUID();
        UUID operatorId = UUID.randomUUID();
        Talent talent = new Talent();
        talent.setId(talentId);

        when(talentMapper.selectById(talentId)).thenReturn(talent);
        when(businessRuleConfigService.getPresetTalentTags()).thenReturn(List.of("美妆", "高转化", "带货"));
        when(talentMapper.updateById(any(Talent.class))).thenReturn(1);

        List<String> result = service.updateTags(talentId, List.of(" 美妆 ", "高转化", "美妆"), operatorId);

        assertThat(result).containsExactly("美妆", "高转化");
        ArgumentCaptor<Talent> captor = ArgumentCaptor.forClass(Talent.class);
        verify(talentMapper).updateById(captor.capture());
        assertThat(captor.getValue().getTags()).containsExactly("美妆", "高转化");
        assertThat(captor.getValue().getTagUpdatedBy()).isEqualTo(operatorId);
    }

    @Test
    void updateTagsThrowsWhenTalentNotFound() {
        UUID talentId = UUID.randomUUID();
        when(talentMapper.selectById(talentId)).thenReturn(null);

        assertThatThrownBy(() -> service.updateTags(talentId, List.of("美妆"), UUID.randomUUID()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("达人不存在");
    }

    @Test
    void createSkipsEnrichmentWhenProfileWasPrefilled() {
        Talent request = new Talent();
        request.setDouyinUid("dy_prefilled");
        request.setDataSource("manual");
        request.setSyncStatus("success");
        request.setTalentLevel("LV2");
        request.setSales30d(68000L);

        when(talentMapper.selectOne(any())).thenReturn(null);
        when(talentMapper.insert(any(Talent.class))).thenReturn(1);
        when(talentMapper.updateById(any(Talent.class))).thenReturn(1);

        Talent result = service.create(request);

        assertThat(result.getLastSyncTime()).isNotNull();
        assertThat(result.getUnsupportedFields()).isEmpty();
        verify(talentEnrichOrchestrator, never()).enrich(any(Talent.class), eq(false));
        verify(talentEnrichTaskMapper, never()).insert(any(TalentEnrichTask.class));
    }

    @Test
    void manualFillUpdatesNonBlankFieldsAndMarksManualSuccess() {
        UUID talentId = UUID.randomUUID();
        Talent talent = new Talent();
        talent.setId(talentId);
        talent.setDeleted(0);
        talent.setDouyinUid("dy_manual");

        Talent request = new Talent();
        request.setNickname(" 手动达人 ");
        request.setContactPhone(" 13800000000 ");
        request.setContactWechat(" wx_manual ");
        request.setIntro(" 重点跟进 ");
        request.setFans(1000L);
        request.setWorksCount(12L);
        request.setTalentLevel("LV2");
        request.setSales30d(52000L);
        request.setUnsupportedFields(List.of("talentLevel", "sales30d"));

        when(talentMapper.selectById(talentId)).thenReturn(talent);
        when(talentMapper.updateById(any(Talent.class))).thenReturn(1);

        Talent result = service.manualFill(talentId, request);

        assertThat(result.getNickname()).isEqualTo("手动达人");
        assertThat(result.getContactPhone()).isEqualTo("13800000000");
        assertThat(result.getContactWechat()).isEqualTo("wx_manual");
        assertThat(result.getIntro()).isEqualTo("重点跟进");
        assertThat(result.getFans()).isEqualTo(1000L);
        assertThat(result.getWorksCount()).isEqualTo(12L);
        assertThat(result.getTalentLevel()).isEqualTo("LV2");
        assertThat(result.getSales30d()).isEqualTo(52000L);
        assertThat(result.getUnsupportedFields()).isEmpty();
        assertThat(result.getDataSource()).isEqualTo("MANUAL");
        assertThat(result.getEnrichStatus()).isEqualTo("SUCCESS");
        assertThat(result.getLastEnrichTime()).isNotNull();
        verify(talentMapper).updateById(talent);
    }

    @Test
    void refreshUpdatesCrawlerFieldsAndMarksTaskSuccessWhenForceCrawlEnabled() {
        TalentProfileApplicationService crawlEnabledService = new TalentProfileApplicationService(
                talentMapper,
                talentClaimMapper,
                talentEnrichTaskMapper,
                talentEnrichOrchestrator,
                crawlerTalentInfoService,
                businessRuleConfigService,
                true);
        UUID talentId = UUID.randomUUID();
        Talent talent = new Talent();
        talent.setId(talentId);
        talent.setDeleted(0);
        talent.setDouyinUid("dy_refresh");

        CrawlerTalentInfo crawlerInfo = new CrawlerTalentInfo();
        crawlerInfo.setTalentId("dy_refresh");
        crawlerInfo.setNickname("crawler-name");
        crawlerInfo.setFansCount(8888L);
        crawlerInfo.setAvatarUrl("https://example.test/avatar.png");
        crawlerInfo.setRegion("zhejiang");

        when(talentMapper.selectById(talentId)).thenReturn(talent);
        when(talentEnrichOrchestrator.enrich(any(Talent.class), eq(true)))
                .thenReturn(new TalentEnrichOrchestrator.OrchestrateResult(true, "TEST", "updated"));
        when(crawlerTalentInfoService.crawlAndSave(any())).thenReturn(1);
        when(crawlerTalentInfoService.findByTalentId("dy_refresh")).thenReturn(crawlerInfo);
        when(talentMapper.updateById(any(Talent.class))).thenReturn(1);

        Talent result = crawlEnabledService.refresh(talentId);

        assertThat(result.getNickname()).isEqualTo("crawler-name");
        assertThat(result.getFans()).isEqualTo(8888L);
        assertThat(result.getAvatarUrl()).isEqualTo("https://example.test/avatar.png");
        assertThat(result.getIpLocation()).isEqualTo("zhejiang");
        assertThat(result.getCrawlStatus()).isEqualTo(1);
        verify(crawlerTalentInfoService).crawlAndSave(List.of("dy_refresh"));

        ArgumentCaptor<TalentEnrichTask> insertCaptor = ArgumentCaptor.forClass(TalentEnrichTask.class);
        verify(talentEnrichTaskMapper).insert(insertCaptor.capture());
        assertThat(insertCaptor.getValue().getTaskStatus()).isEqualTo("RUNNING");

        ArgumentCaptor<TalentEnrichTask> updateCaptor = ArgumentCaptor.forClass(TalentEnrichTask.class);
        verify(talentEnrichTaskMapper).updateById(updateCaptor.capture());
        assertThat(updateCaptor.getValue().getTaskStatus()).isEqualTo("SUCCESS");
    }
}
