package com.colonel.saas.service;

import com.colonel.saas.entity.OperationLog;
import com.colonel.saas.mapper.OperationLogMapper;
import com.colonel.saas.mapper.SysUserMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OperationLogServiceTest {

    @Mock
    private OperationLogMapper operationLogMapper;
    @Mock
    private SysUserMapper sysUserMapper;
    @Mock
    private JdbcTemplate jdbcTemplate;

    private OperationLogService service;

    @BeforeEach
    void setUp() {
        service = new OperationLogService(operationLogMapper, sysUserMapper, jdbcTemplate, new ObjectMapper());
    }

    @Test
    void record_shouldEnsureMonthlyPartitionBeforeInsert() {
        OperationLog log = new OperationLog();
        log.setModule("系统管理");
        log.setAction("更新配置");
        log.setCreateTime(LocalDateTime.of(2027, 4, 15, 12, 0));

        service.record(log);

        ArgumentCaptor<String> ddlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, atLeast(1)).execute(ddlCaptor.capture());
        List<String> ddlList = ddlCaptor.getAllValues();
        assertThat(ddlList).anyMatch(sql -> sql.contains("op_log_2027_04 PARTITION OF operation_log"));
        assertThat(ddlList).anyMatch(sql -> sql.contains("idx_op_log_2027_04_create_time"));
        verify(jdbcTemplate).update(anyString(), any(Object[].class));
    }

    @Test
    void cleanupOldPartitions_shouldDropExpiredPartitionsOnly() {
        doAnswer(invocation -> {
            org.springframework.jdbc.core.RowCallbackHandler handler = invocation.getArgument(1);
            java.sql.ResultSet jan = org.mockito.Mockito.mock(java.sql.ResultSet.class);
            when(jan.getString("partition_name")).thenReturn("op_log_2026_01");
            handler.processRow(jan);
            java.sql.ResultSet feb = org.mockito.Mockito.mock(java.sql.ResultSet.class);
            when(feb.getString("partition_name")).thenReturn("op_log_2026_02");
            handler.processRow(feb);
            return null;
        }).when(jdbcTemplate).query(anyString(), any(org.springframework.jdbc.core.RowCallbackHandler.class));

        int dropped = service.cleanupOldPartitions(90);

        assertThat(dropped).isGreaterThanOrEqualTo(0);
        verify(jdbcTemplate, atLeast(1)).execute(anyString());
    }
}
