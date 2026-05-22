package com.colonel.saas.service;

import com.colonel.saas.entity.TalentFollowRecord;
import com.colonel.saas.mapper.TalentFollowRecordMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TalentFollowServiceTest {

    private final TalentFollowRecordMapper mapper = mock(TalentFollowRecordMapper.class);
    private final TalentFollowService service = new TalentFollowService(mapper);

    @Test
    void createRecordShouldPopulateAndPersistFollowRecord() {
        UUID talentId = UUID.randomUUID();
        UUID operatorId = UUID.randomUUID();
        LocalDateTime nextFollowTime = LocalDateTime.of(2026, 5, 22, 9, 0);
        ArgumentCaptor<TalentFollowRecord> captor = ArgumentCaptor.forClass(TalentFollowRecord.class);

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

        verify(mapper).insert(captor.capture());
        assertThat(result).isSameAs(captor.getValue());
        assertThat(result.getActivityId()).isEqualTo("A-1");
        assertThat(result.getProductId()).isEqualTo("P-1");
        assertThat(result.getTalentId()).isEqualTo(talentId);
        assertThat(result.getFollowStatus()).isEqualTo("FOLLOWING");
        assertThat(result.getContent()).isEqualTo("已沟通寄样");
        assertThat(result.getNextFollowTime()).isEqualTo(nextFollowTime);
        assertThat(result.getOperatorId()).isEqualTo(operatorId);
        assertThat(result.getOperatorName()).isEqualTo("渠道同学");
    }

    @Test
    void listByProductShouldReturnMapperResults() {
        TalentFollowRecord record = new TalentFollowRecord();
        record.setActivityId("A-1");
        record.setProductId("P-1");
        when(mapper.selectList(any())).thenReturn(List.of(record));

        List<TalentFollowRecord> result = service.listByProduct("A-1", "P-1");

        assertThat(result).containsExactly(record);
        verify(mapper).selectList(any());
    }
}
