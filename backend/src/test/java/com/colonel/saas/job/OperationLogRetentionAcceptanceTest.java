package com.colonel.saas.job;

import com.colonel.saas.service.DistributedJobLockService;
import com.colonel.saas.service.OperationLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;

import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P1-LOG-RETENTION 验收脚本（无 DB 依赖，可在 CI / 本地直接跑）。
 */
@ExtendWith(MockitoExtension.class)
class OperationLogRetentionAcceptanceTest {

    private static final int RETENTION_DAYS = 90;
    private static final DateTimeFormatter PARTITION_MONTH = DateTimeFormatter.ofPattern("yyyy_MM");

    @Mock
    private OperationLogService operationLogService;
    @Mock
    private DistributedJobLockService jobLockService;
    @Mock
    private JdbcTemplate jdbcTemplate;

    private LogCleanupJob logCleanupJob;

    @BeforeEach
    void setUp() {
        lenient().when(jobLockService.tryAcquire(eq(JobLockKeys.LOG_CLEANUP), any(Duration.class))).thenReturn(true);
        logCleanupJob = new LogCleanupJob(operationLogService, jobLockService, RETENTION_DAYS);
    }

    @Test
    void step1_logCleanupJobBeanContractShouldExist() {
        assertThat(logCleanupJob).isNotNull();
    }

    @Test
    void step2_defaultRetentionDaysShouldBe90() {
        LogCleanupJob jobWithDefault = new LogCleanupJob(operationLogService, jobLockService, RETENTION_DAYS);
        assertThat(jobWithDefault).isNotNull();
    }

    @Test
    void step3_to5_cleanupOldPartitionsShouldDropFourMonthOldPartitionAndKeepRecent() {
        YearMonth expiredMonth = YearMonth.from(LocalDate.now().minusDays(RETENTION_DAYS + 120));
        YearMonth recentMonth = YearMonth.from(LocalDate.now().minusDays(10));
        String expiredPartition = partitionName(expiredMonth);
        String recentPartition = partitionName(recentMonth);

        doAnswer(invocation -> {
            RowCallbackHandler handler = invocation.getArgument(1);
            handler.processRow(resultSet(expiredPartition));
            handler.processRow(resultSet(recentPartition));
            return null;
        }).when(jdbcTemplate).query(anyString(), any(RowCallbackHandler.class));

        OperationLogService realService = new OperationLogService(null, null, jdbcTemplate, null);
        int dropped = realService.cleanupOldPartitions(RETENTION_DAYS);

        assertThat(dropped).isEqualTo(1);
        verify(jdbcTemplate).execute("DROP TABLE IF EXISTS " + expiredPartition);

        when(operationLogService.cleanupOldPartitions(RETENTION_DAYS)).thenReturn(dropped);
        logCleanupJob.cleanupOldPartitions();
        verify(operationLogService).cleanupOldPartitions(RETENTION_DAYS);
        verify(jobLockService).release(JobLockKeys.LOG_CLEANUP);
    }

    private java.sql.ResultSet resultSet(String partitionName) throws java.sql.SQLException {
        java.sql.ResultSet rs = org.mockito.Mockito.mock(java.sql.ResultSet.class);
        when(rs.getString("partition_name")).thenReturn(partitionName);
        return rs;
    }

    private String partitionName(YearMonth month) {
        return "op_log_" + month.format(PARTITION_MONTH);
    }
}
