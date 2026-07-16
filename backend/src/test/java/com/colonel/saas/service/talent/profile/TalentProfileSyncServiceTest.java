package com.colonel.saas.service.talent.profile;

import com.colonel.saas.dto.talent.ResolveTalentProfileResponse;
import com.colonel.saas.entity.Talent;
import com.colonel.saas.mapper.TalentMapper;
import com.colonel.saas.mapper.TalentProfileSyncLogMapper;
import com.colonel.saas.service.TalentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TalentProfileSyncServiceTest {

    @Mock
    private TalentProfileProvider provider;
    @Mock
    private TalentMapper talentMapper;
    @Mock
    private TalentProfileSyncLogMapper syncLogMapper;
    @Mock
    private TalentService talentService;

    private TalentProfileSyncService service;

    @BeforeEach
    void setUp() {
        when(provider.providerCode()).thenReturn("test-provider");
        service = new TalentProfileSyncService(List.of(provider), talentMapper, syncLogMapper, talentService);
    }

    @Test
    void successfulRefreshShouldKeepExistingMetricsWhenProviderDoesNotReturnThem() {
        UUID talentId = UUID.randomUUID();
        Talent existing = new Talent();
        existing.setId(talentId);
        existing.setDouyinUid("talent-uid");
        existing.setTalentLevel("LV2");
        existing.setSales30d(68000L);
        existing.setUnsupportedFields(List.of("talentLevel", "sales30d"));

        when(talentMapper.selectOne(any())).thenReturn(existing);
        when(talentMapper.updateById(existing)).thenReturn(1);
        when(provider.supports(any())).thenReturn(true);
        when(provider.fetch(any())).thenReturn(TalentProfileResult.builder()
                .success(true)
                .syncStatus(TalentProfileResult.STATUS_PARTIAL_SUCCESS)
                .nickname("更新后的昵称")
                .unsupportedFields(TalentProfileResult.DEFAULT_UNSUPPORTED)
                .build());

        ResolveTalentProfileResponse response = service.resolveProfile("talent-uid", true, false, null);

        assertThat(response.isSuccess()).isTrue();
        assertThat(existing.getTalentLevel()).isEqualTo("LV2");
        assertThat(existing.getSales30d()).isEqualTo(68000L);
        assertThat(existing.getUnsupportedFields()).isEmpty();
    }
}
