package com.colonel.saas.domain.talent.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.entity.TalentFollowRecord;
import com.colonel.saas.mapper.TalentFollowRecordMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
    private TalentFollowRecordMapper talentFollowRecordMapper;

    @Test
    void createRecordShouldPersistFollowRecord() {
        TalentFollowApplicationService service = new TalentFollowApplicationService(talentFollowRecordMapper);
        UUID talentId = UUID.randomUUID();
        UUID operatorId = UUID.randomUUID();
        LocalDateTime nextFollowTime = LocalDateTime.of(2026, 6, 27, 16, 30);

        TalentFollowRecord created = service.createRecord(
                "A-1", "P-1", talentId, "达人A", "FOLLOWING", "已沟通", nextFollowTime, operatorId, "渠道A");

        ArgumentCaptor<TalentFollowRecord> recordCaptor = ArgumentCaptor.forClass(TalentFollowRecord.class);
        verify(talentFollowRecordMapper).insert(recordCaptor.capture());
        assertThat(created).isSameAs(recordCaptor.getValue());
        assertThat(created.getActivityId()).isEqualTo("A-1");
        assertThat(created.getProductId()).isEqualTo("P-1");
        assertThat(created.getTalentId()).isEqualTo(talentId);
        assertThat(created.getTalentName()).isEqualTo("达人A");
        assertThat(created.getFollowStatus()).isEqualTo("FOLLOWING");
        assertThat(created.getContent()).isEqualTo("已沟通");
        assertThat(created.getNextFollowTime()).isEqualTo(nextFollowTime);
        assertThat(created.getOperatorId()).isEqualTo(operatorId);
        assertThat(created.getOperatorName()).isEqualTo("渠道A");
    }

    @Test
    void listByProductShouldQueryByActivityAndProduct() {
        TalentFollowApplicationService service = new TalentFollowApplicationService(talentFollowRecordMapper);
        TalentFollowRecord record = new TalentFollowRecord();
        when(talentFollowRecordMapper.selectList(org.mockito.ArgumentMatchers.<LambdaQueryWrapper<TalentFollowRecord>>any()))
                .thenReturn(List.of(record));

        List<TalentFollowRecord> records = service.listByProduct("A-1", "P-1");

        assertThat(records).containsExactly(record);
        verify(talentFollowRecordMapper).selectList(
                org.mockito.ArgumentMatchers.<LambdaQueryWrapper<TalentFollowRecord>>any());
    }
}
