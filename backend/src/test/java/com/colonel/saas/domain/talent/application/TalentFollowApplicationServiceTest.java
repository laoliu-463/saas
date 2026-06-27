package com.colonel.saas.domain.talent.application;

import com.colonel.saas.entity.TalentFollowRecord;
import com.colonel.saas.service.TalentFollowService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TalentFollowApplicationServiceTest {

    @Mock
    private TalentFollowService talentFollowService;

    @Test
    void createRecordAndListByProductShouldDelegateToLegacyService() {
        TalentFollowApplicationService service = new TalentFollowApplicationService(talentFollowService);
        UUID talentId = UUID.randomUUID();
        UUID operatorId = UUID.randomUUID();
        LocalDateTime nextFollowTime = LocalDateTime.of(2026, 6, 27, 16, 30);
        TalentFollowRecord record = new TalentFollowRecord();
        record.setId(UUID.randomUUID());
        when(talentFollowService.createRecord(
                "A-1", "P-1", talentId, "达人A", "FOLLOWING", "已沟通", nextFollowTime, operatorId, "渠道A"))
                .thenReturn(record);
        when(talentFollowService.listByProduct("A-1", "P-1")).thenReturn(List.of(record));

        TalentFollowRecord created = service.createRecord(
                "A-1", "P-1", talentId, "达人A", "FOLLOWING", "已沟通", nextFollowTime, operatorId, "渠道A");
        List<TalentFollowRecord> records = service.listByProduct("A-1", "P-1");

        assertThat(created).isSameAs(record);
        assertThat(records).containsExactly(record);
        verify(talentFollowService).createRecord(
                "A-1", "P-1", talentId, "达人A", "FOLLOWING", "已沟通", nextFollowTime, operatorId, "渠道A");
        verify(talentFollowService).listByProduct("A-1", "P-1");
    }
}
