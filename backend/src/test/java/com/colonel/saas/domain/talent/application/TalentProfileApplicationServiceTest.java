package com.colonel.saas.domain.talent.application;

import com.colonel.saas.entity.Talent;
import com.colonel.saas.service.TalentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TalentProfileApplicationServiceTest {

    @Mock
    private TalentService talentService;

    @Test
    void updateTagsShouldDelegateWithOperatorForAudit() {
        TalentProfileApplicationService service = new TalentProfileApplicationService(talentService);
        UUID talentId = UUID.randomUUID();
        UUID operatorId = UUID.randomUUID();
        when(talentService.updateTags(talentId, List.of("美妆"), operatorId)).thenReturn(List.of("美妆"));

        List<String> result = service.updateTags(talentId, List.of("美妆"), operatorId);

        assertThat(result).containsExactly("美妆");
        verify(talentService).updateTags(talentId, List.of("美妆"), operatorId);
    }

    @Test
    void updateAndManualFillShouldDelegateToLegacyService() {
        TalentProfileApplicationService service = new TalentProfileApplicationService(talentService);
        UUID talentId = UUID.randomUUID();
        Talent patch = new Talent();
        patch.setNickname("达人A");
        Talent updated = new Talent();
        updated.setId(talentId);
        updated.setNickname("达人A");
        when(talentService.update(talentId, patch)).thenReturn(updated);
        when(talentService.manualFill(talentId, patch)).thenReturn(updated);

        assertThat(service.update(talentId, patch)).isSameAs(updated);
        assertThat(service.manualFill(talentId, patch)).isSameAs(updated);
        verify(talentService).update(talentId, patch);
        verify(talentService).manualFill(talentId, patch);
    }
}
