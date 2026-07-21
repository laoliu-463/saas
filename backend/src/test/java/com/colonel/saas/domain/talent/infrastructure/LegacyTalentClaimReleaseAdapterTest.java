package com.colonel.saas.domain.talent.infrastructure;

import com.colonel.saas.service.TalentService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class LegacyTalentClaimReleaseAdapterTest {

    @Test
    void releaseExpiredClaimsShouldPassTheSameTimeToLegacyService() {
        TalentService talentService = mock(TalentService.class);
        LegacyTalentClaimReleaseAdapter adapter = new LegacyTalentClaimReleaseAdapter(talentService);
        LocalDateTime now = LocalDateTime.of(2026, 7, 12, 2, 15);
        ArgumentCaptor<LocalDateTime> timeCaptor = ArgumentCaptor.forClass(LocalDateTime.class);

        adapter.releaseExpiredClaims(now);

        verify(talentService).releaseExpiredClaims(timeCaptor.capture());
        assertThat(timeCaptor.getValue()).isSameAs(now);
    }
}
