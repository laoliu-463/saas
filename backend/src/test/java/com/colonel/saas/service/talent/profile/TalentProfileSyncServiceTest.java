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
}
