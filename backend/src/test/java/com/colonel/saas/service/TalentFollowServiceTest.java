package com.colonel.saas.service;

import com.colonel.saas.domain.talent.application.TalentFollowApplicationService;
import com.colonel.saas.entity.TalentFollowRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TalentFollowServiceTest {

    @Mock
    private TalentFollowApplicationService applicationService;

    @InjectMocks
    private TalentFollowService service;

    @Test
    void createRecordShouldDelegateToApplicationService() {
        UUID talentId = UUID.randomUUID();
        UUID operatorId = UUID.randomUUID();
        LocalDateTime nextFollowTime = LocalDateTime.of(2026, 5, 22, 9, 0);
        TalentFollowRecord record = new TalentFollowRecord();

        when(applicationService.createRecord(
                "A-1", "P-1", talentId, "达人 A", "FOLLOWING", "已沟通寄样", nextFollowTime, operatorId, "渠道同学"
        )).thenReturn(record);

        TalentFollowRecord result = service.createRecord(
                "A-1",
                "P-1",
                talentId,
                "达人 A",
                "FOLLOWING",
                "已沟通寄样",
                nextFollowTime,
                operatorId,
                "渠道同学");

        verify(applicationService).createRecord(
                "A-1", "P-1", talentId, "达人 A", "FOLLOWING", "已沟通寄样", nextFollowTime, operatorId, "渠道同学"
        );
        assertThat(result).isSameAs(record);
    }

    @Test
    void listByProductShouldDelegateToApplicationService() {
        TalentFollowRecord record = new TalentFollowRecord();
        when(applicationService.listByProduct("A-1", "P-1")).thenReturn(List.of(record));

        List<TalentFollowRecord> result = service.listByProduct("A-1", "P-1");

        verify(applicationService).listByProduct("A-1", "P-1");
        assertThat(result).containsExactly(record);
    }
}
