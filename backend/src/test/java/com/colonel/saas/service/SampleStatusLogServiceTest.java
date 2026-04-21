package com.colonel.saas.service;

import com.colonel.saas.entity.SampleStatusLog;
import com.colonel.saas.mapper.SampleStatusLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SampleStatusLogServiceTest {

    @Mock
    private SampleStatusLogMapper sampleStatusLogMapper;

    private SampleStatusLogService sampleStatusLogService;

    @BeforeEach
    void setUp() {
        sampleStatusLogService = new SampleStatusLogService(sampleStatusLogMapper);
    }

    @Test
    void log_shouldInsertWithCorrectFields() {
        UUID requestId = UUID.randomUUID();
        UUID operatorId = UUID.randomUUID();
        String remark = "样品已发出";

        sampleStatusLogService.log(requestId, 1, 2, operatorId, remark);

        ArgumentCaptor<SampleStatusLog> captor = ArgumentCaptor.forClass(SampleStatusLog.class);
        verify(sampleStatusLogMapper).insert(captor.capture());
        SampleStatusLog saved = captor.getValue();

        assertThat(saved.getRequestId()).isEqualTo(requestId);
        assertThat(saved.getFromStatus()).isEqualTo(1);
        assertThat(saved.getToStatus()).isEqualTo(2);
        assertThat(saved.getOperatorId()).isEqualTo(operatorId);
        assertThat(saved.getRemark()).isEqualTo(remark);
        assertThat(saved.getOperateTime()).isNotNull();
    }

    @Test
    void log_nullRemarkAndOperator_shouldNotThrow() {
        sampleStatusLogService.log(UUID.randomUUID(), 0, 1, null, null);

        ArgumentCaptor<SampleStatusLog> captor = ArgumentCaptor.forClass(SampleStatusLog.class);
        verify(sampleStatusLogMapper).insert(captor.capture());
        assertThat(captor.getValue().getRemark()).isNull();
        assertThat(captor.getValue().getOperatorId()).isNull();
    }
}
