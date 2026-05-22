package com.colonel.saas.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;

import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SampleStatusLogServiceTest {

    private static final String INSERT_SQL = """
            INSERT INTO sample_status_log (id, request_id, from_status, to_status, operator_id, operate_time, remark)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

    @Mock
    private JdbcTemplate jdbcTemplate;

    private SampleStatusLogService sampleStatusLogService;

    @BeforeEach
    void setUp() {
        sampleStatusLogService = new SampleStatusLogService(jdbcTemplate);
    }

    @Test
    void log_shouldInsertWithCorrectFields() {
        UUID requestId = UUID.randomUUID();
        UUID operatorId = UUID.randomUUID();
        String remark = "sample shipped";

        sampleStatusLogService.log(requestId, 1, 2, operatorId, remark);

        ArgumentCaptor<UUID> idCaptor = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<UUID> requestCaptor = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<Integer> fromCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> toCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<UUID> operatorCaptor = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<LocalDateTime> timeCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<String> remarkCaptor = ArgumentCaptor.forClass(String.class);

        verify(jdbcTemplate).update(
                eq(INSERT_SQL),
                idCaptor.capture(),
                requestCaptor.capture(),
                fromCaptor.capture(),
                toCaptor.capture(),
                operatorCaptor.capture(),
                timeCaptor.capture(),
                remarkCaptor.capture()
        );

        assertThat(idCaptor.getValue()).isNotNull();
        assertThat(requestCaptor.getValue()).isEqualTo(requestId);
        assertThat(fromCaptor.getValue()).isEqualTo(1);
        assertThat(toCaptor.getValue()).isEqualTo(2);
        assertThat(operatorCaptor.getValue()).isEqualTo(operatorId);
        assertThat(timeCaptor.getValue()).isNotNull();
        assertThat(remarkCaptor.getValue()).isEqualTo(remark);
    }

    @Test
    void log_shouldAllowNullOperatorAndRemark() {
        UUID requestId = UUID.randomUUID();

        sampleStatusLogService.log(requestId, 0, 1, null, null);

        ArgumentCaptor<UUID> requestCaptor = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<UUID> operatorCaptor = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<String> remarkCaptor = ArgumentCaptor.forClass(String.class);

        verify(jdbcTemplate).update(
                eq(INSERT_SQL),
                org.mockito.ArgumentMatchers.any(UUID.class),
                requestCaptor.capture(),
                eq(0),
                eq(1),
                operatorCaptor.capture(),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class),
                remarkCaptor.capture()
        );

        assertThat(requestCaptor.getValue()).isEqualTo(requestId);
        assertThat(operatorCaptor.getValue()).isNull();
        assertThat(remarkCaptor.getValue()).isNull();
    }

    @Test
    void logBatch_shouldSkipEmptyEntries() {
        sampleStatusLogService.logBatch(null);
        sampleStatusLogService.logBatch(List.of());

        verify(jdbcTemplate, never()).batchUpdate(any(), anyList(), anyInt(), any());
    }

    @Test
    void logBatch_shouldBindEachEntry() throws Exception {
        UUID requestId = UUID.randomUUID();
        UUID operatorId = UUID.randomUUID();
        SampleStatusLogService.LogEntry entry =
                new SampleStatusLogService.LogEntry(requestId, 2, 5, operatorId, "signed");

        sampleStatusLogService.logBatch(List.of(entry));

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<ParameterizedPreparedStatementSetter<SampleStatusLogService.LogEntry>> setterCaptor =
                (org.mockito.ArgumentCaptor<ParameterizedPreparedStatementSetter<SampleStatusLogService.LogEntry>>)
                        (org.mockito.ArgumentCaptor<?>) org.mockito.ArgumentCaptor.forClass(ParameterizedPreparedStatementSetter.class);
        verify(jdbcTemplate).batchUpdate(eq(INSERT_SQL), eq(List.of(entry)), eq(1), setterCaptor.capture());

        PreparedStatement ps = mock(PreparedStatement.class);
        setterCaptor.getValue().setValues(ps, entry);

        verify(ps).setObject(eq(1), org.mockito.ArgumentMatchers.any(UUID.class));
        verify(ps).setObject(2, requestId);
        verify(ps).setObject(3, 2);
        verify(ps).setObject(4, 5);
        verify(ps).setObject(5, operatorId);
        verify(ps).setObject(eq(6), org.mockito.ArgumentMatchers.any(LocalDateTime.class));
        verify(ps).setObject(7, "signed");
    }
}
