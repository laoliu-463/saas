package com.colonel.saas.service.talent.profile;

import com.colonel.saas.dto.talent.ResolveTalentProfileResponse;
import com.colonel.saas.entity.Talent;
import com.colonel.saas.mapper.TalentMapper;
import com.colonel.saas.mapper.TalentProfileSyncLogMapper;
import com.colonel.saas.service.TalentService;
import com.colonel.saas.service.talent.profile.provider.ManualTalentProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TalentProfileSyncServiceTest {

    @Mock
    private TalentMapper talentMapper;
    @Mock
    private TalentProfileSyncLogMapper syncLogMapper;
    @Mock
    private TalentService talentService;

    private TalentProfileSyncService service;

    @BeforeEach
    void setUp() {
        service = new TalentProfileSyncService(
                List.of(new ManualTalentProvider()),
                talentMapper,
                syncLogMapper,
                talentService);
    }

    @Test
    void resolveProfile_shouldMarkManualDataSource() {
        ResolveTalentProfileResponse response = service.resolveProfile(
                "test_account",
                false,
                true,
                Map.of(
                        "nickname", "手动达人",
                        "fansCount", 12000,
                        "douyinAccount", "test_account"
                ));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getProvider()).isEqualTo("manual");
        assertThat(response.getDataSource()).isEqualTo("manual");
        assertThat(response.getProfile().getNickname()).isEqualTo("手动达人");
        assertThat(response.getProfile().getSecUid()).isNull();
        assertThat(response.getUnsupportedFields()).contains("talentLevel", "sales30d");
        verify(syncLogMapper).insert(any());
    }

    @Test
    void syncExistingProfile_shouldNotOverwritePriorSuccessOnFailure() {
        UUID talentId = UUID.randomUUID();
        Talent talent = new Talent();
        talent.setId(talentId);
        talent.setDouyinUid("uid_ok");
        talent.setNickname("旧昵称");
        talent.setSyncStatus("success");
        talent.setDataSource("public_web");
        talent.setFans(99_000L);
        when(talentService.getById(talentId)).thenReturn(talent);

        ResolveTalentProfileResponse response = service.syncExistingProfile(talentId, true);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getProfile().getNickname()).isEqualTo("旧昵称");
        assertThat(response.getProfile().getFansCount()).isEqualTo(99_000L);
    }

    @Test
    void syncExistingProfile_shouldApplySuccessfulProviderResult() {
        UUID talentId = UUID.randomUUID();
        Talent talent = new Talent();
        talent.setId(talentId);
        talent.setDouyinNo("account_a");
        when(talentService.getById(talentId)).thenReturn(talent);

        TalentProfileProvider provider = new StubProvider(true, TalentProfileResult.builder()
                .success(true)
                .providerCode("stub")
                .syncStatus(TalentProfileResult.STATUS_SUCCESS)
                .douyinAccount("account_a")
                .talentUid("uid_a")
                .secUid("sec_a")
                .nickname("达人A")
                .avatarUrl("https://img.example/a.png")
                .fansCount(123L)
                .likeCount(456L)
                .followingCount(7L)
                .worksCount(8L)
                .ipLocation("上海")
                .talentLevel("LV2")
                .sales30d(900L)
                .fetchedFields(List.of("nickname"))
                .unsupportedFields(List.of())
                .rawPayload(Map.of("ok", true))
                .build());
        TalentProfileSyncService customService = new TalentProfileSyncService(
                List.of(provider),
                talentMapper,
                syncLogMapper,
                talentService);

        ResolveTalentProfileResponse response = customService.syncExistingProfile(talentId, true);

        ArgumentCaptor<Talent> captor = ArgumentCaptor.forClass(Talent.class);
        verify(talentMapper).updateById(captor.capture());
        Talent saved = captor.getValue();
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getProvider()).isEqualTo("stub");
        assertThat(saved.getDataSource()).isEqualTo("stub");
        assertThat(saved.getDouyinAccount()).isEqualTo("account_a");
        assertThat(saved.getTalentUid()).isEqualTo("uid_a");
        assertThat(saved.getDouyinUid()).isEqualTo("uid_a");
        assertThat(saved.getSecUid()).isEqualTo("sec_a");
        assertThat(saved.getNickname()).isEqualTo("达人A");
        assertThat(saved.getFans()).isEqualTo(123L);
        assertThat(saved.getLikesCount()).isEqualTo(456L);
        assertThat(saved.getFollowingCount()).isEqualTo(7L);
        assertThat(saved.getWorksCount()).isEqualTo(8L);
        assertThat(saved.getIpLocation()).isEqualTo("上海");
        assertThat(saved.getTalentLevel()).isEqualTo("LV2");
        assertThat(saved.getSales30d()).isEqualTo(900L);
        assertThat(saved.getSyncErrorCode()).isNull();
        assertThat(saved.getRawPayload()).containsEntry("ok", true);
        verify(syncLogMapper).insert(any());
    }

    @Test
    void resolveProfile_shouldReturnNoProviderFailureWhenUnsupported() {
        TalentProfileProvider provider = new StubProvider(false, null);
        TalentProfileSyncService customService = new TalentProfileSyncService(
                List.of(provider),
                talentMapper,
                syncLogMapper,
                talentService);

        ResolveTalentProfileResponse response = customService.resolveProfile("plain_account", false, false, null);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getProvider()).isNull();
        assertThat(response.getSyncErrorCode()).isEqualTo("NO_PROVIDER");
        assertThat(response.getSyncErrorMessage()).contains("no profile provider");
        assertThat(response.getDataSource()).isNull();
        assertThat(response.getProfile().getNickname()).isNull();
        verify(syncLogMapper).insert(any());
    }

    @Test
    void applyManualProfile_shouldPersistManualSource() {
        UUID talentId = UUID.randomUUID();
        Talent talent = new Talent();
        talent.setId(talentId);
        talent.setDouyinUid("uid_manual");
        when(talentService.getById(talentId)).thenReturn(talent);

        ResolveTalentProfileResponse response = service.applyManualProfile(
                talentId,
                Map.of("nickname", "补录昵称", "fansCount", 8000));

        ArgumentCaptor<Talent> captor = ArgumentCaptor.forClass(Talent.class);
        verify(talentMapper).updateById(captor.capture());
        Talent saved = captor.getValue();
        assertThat(response.isSuccess()).isTrue();
        assertThat(saved.getDataSource()).isEqualTo("manual");
        assertThat(saved.getNickname()).isEqualTo("补录昵称");
        assertThat(saved.getFans()).isEqualTo(8000L);
    }

    @Test
    void syncExistingProfile_shouldFallbackToCrawlerAfterApiFailure() {
        UUID talentId = UUID.randomUUID();
        Talent talent = new Talent();
        talent.setId(talentId);
        talent.setDouyinNo("account_fb");
        when(talentService.getById(talentId)).thenReturn(talent);

        TalentProfileProvider apiProvider = new StubProvider(true, TalentProfileResult.builder()
                .success(false)
                .providerCode("API")
                .syncStatus(TalentProfileResult.STATUS_FAILED)
                .errorCode("UNSUPPORTED")
                .errorMessage("no official profile api")
                .unsupportedFields(TalentProfileResult.DEFAULT_UNSUPPORTED)
                .build());
        TalentProfileProvider crawlerProvider = new StubProvider(true, TalentProfileResult.builder()
                .success(true)
                .providerCode("CRAWLER")
                .syncStatus(TalentProfileResult.STATUS_SUCCESS)
                .nickname("爬虫昵称")
                .fansCount(1000L)
                .fetchedFields(List.of("nickname"))
                .unsupportedFields(List.of())
                .build());
        TalentProfileSyncService customService = new TalentProfileSyncService(
                List.of(apiProvider, crawlerProvider),
                talentMapper,
                syncLogMapper,
                talentService);

        ResolveTalentProfileResponse response = customService.syncExistingProfile(talentId, true);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getProvider()).isEqualTo("CRAWLER");
        assertThat(response.getProfile().getNickname()).isEqualTo("爬虫昵称");
    }

    @Test
    void applyManualProfile_shouldPersistFailureWhenPayloadIsEmpty() {
        UUID talentId = UUID.randomUUID();
        Talent talent = new Talent();
        talent.setId(talentId);
        talent.setDouyinUid("uid_manual");
        when(talentService.getById(talentId)).thenReturn(talent);

        ResolveTalentProfileResponse response = service.applyManualProfile(talentId, Map.of());

        ArgumentCaptor<Talent> captor = ArgumentCaptor.forClass(Talent.class);
        verify(talentMapper).updateById(captor.capture());
        Talent saved = captor.getValue();
        assertThat(response.isSuccess()).isFalse();
        assertThat(saved.getSyncStatus()).isEqualTo(TalentProfileResult.STATUS_FAILED);
        assertThat(saved.getSyncErrorCode()).isEqualTo("NO_PROVIDER");
        assertThat(saved.getUnsupportedFields()).contains("talentLevel", "sales30d");
    }

    private record StubProvider(boolean supports, TalentProfileResult result) implements TalentProfileProvider {
        @Override
        public String providerCode() {
            return "stub";
        }

        @Override
        public int order() {
            return 1;
        }

        @Override
        public boolean supports(TalentProfileQuery query) {
            return supports;
        }

        @Override
        public TalentProfileResult fetch(TalentProfileQuery query) {
            return result;
        }
    }
}
