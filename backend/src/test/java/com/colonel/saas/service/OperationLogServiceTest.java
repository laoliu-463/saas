package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.entity.OperationLog;
import com.colonel.saas.entity.SysUser;
import com.colonel.saas.mapper.OperationLogMapper;
import com.colonel.saas.domain.user.facade.UserDomainFacade;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperationLogServiceTest {

    @Mock
    private OperationLogMapper operationLogMapper;
    @Mock
    private UserDomainFacade userDomainFacade;
    @Mock
    private JdbcTemplate jdbcTemplate;

    private OperationLogService service;

    @BeforeEach
    void setUp() {
        service = new OperationLogService(operationLogMapper, userDomainFacade, jdbcTemplate, new ObjectMapper());
    }

    @Test
    void record_shouldReturnWithoutDatabaseWorkWhenLogIsNull() {
        service.record(null);

        verifyNoInteractions(operationLogMapper, userDomainFacade, jdbcTemplate);
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
    void record_shouldFillDefaultsAndSerializeJsonColumns() {
        OperationLog log = new OperationLog();
        log.setCreateTime(LocalDateTime.of(2028, 7, 3, 9, 8, 7));
        log.setModule("订单");
        log.setAction("同步");
        log.setRequestParams(Map.of("page", 1));
        log.setRequestBody(Map.of("orderId", "A001"));
        log.setResponseBody(Map.of("ok", true));
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);

        service.record(log);

        verify(jdbcTemplate).update(anyString(), argsCaptor.capture());
        Object[] args = argsCaptor.getValue();
        assertThat(log.getId()).isNotNull();
        assertThat(log.getDeleted()).isZero();
        assertThat(args[0]).isEqualTo(log.getId());
        assertThat(args[3]).isEqualTo("订单");
        assertThat(args[4]).isEqualTo("同步");
        assertThat(args[11]).isEqualTo("{\"page\":1}");
        assertThat(args[12]).isEqualTo("{\"orderId\":\"A001\"}");
        assertThat(args[14]).isEqualTo("{\"ok\":true}");
        assertThat(args[20]).isEqualTo(0);
    }

    @Test
    void recordSystemAction_shouldResolveUsernameAndDelegateToRecord() {
        UUID operatorId = UUID.randomUUID();
        SysUser user = new SysUser();
        user.setUsername("admin");
        when(sysUserMapper.selectById(operatorId)).thenReturn(user);
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);

        service.recordSystemAction(
                operatorId,
                "系统配置",
                "更新配置",
                "PUT",
                "SystemConfig",
                "cfg-1",
                "sample.restrict_days",
                "更新配置项"
        );

        verify(userDomainFacade).selectById(operatorId);
        verify(jdbcTemplate).update(anyString(), argsCaptor.capture());
        Object[] args = argsCaptor.getValue();
        assertThat(args[1]).isEqualTo(operatorId);
        assertThat(args[2]).isEqualTo("admin");
        assertThat(args[3]).isEqualTo("系统配置");
        assertThat(args[4]).isEqualTo("更新配置");
        assertThat(args[5]).isEqualTo("SystemConfig");
        assertThat(args[6]).isEqualTo("cfg-1");
        assertThat(args[7]).isEqualTo("sample.restrict_days");
        assertThat(args[8]).isEqualTo("更新配置项");
        assertThat(args[9]).isEqualTo("PUT");
    }

    @Test
    void findPage_shouldBuildFilteredPageQuery() {
        Page<OperationLog> returned = new Page<>(2, 50);
        when(operationLogMapper.selectPage(any(Page.class), any(QueryWrapper.class))).thenReturn(returned);

        IPage<OperationLog> page = service.findPage(
                " 系统 ",
                " 更新 ",
                " admin ",
                "post",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31),
                2,
                50
        );

        assertThat(page.getCurrent()).isEqualTo(2);
        assertThat(page.getSize()).isEqualTo(50);
        verify(operationLogMapper).selectPage(any(Page.class), any(QueryWrapper.class));
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
            java.sql.ResultSet invalid = org.mockito.Mockito.mock(java.sql.ResultSet.class);
            when(invalid.getString("partition_name")).thenReturn("operation_log_default");
            handler.processRow(invalid);
            java.sql.ResultSet malformed = org.mockito.Mockito.mock(java.sql.ResultSet.class);
            when(malformed.getString("partition_name")).thenReturn("op_log_bad");
            handler.processRow(malformed);
            return null;
        }).when(jdbcTemplate).query(anyString(), any(org.springframework.jdbc.core.RowCallbackHandler.class));

        int dropped = service.cleanupOldPartitions(90);

        assertThat(dropped).isGreaterThanOrEqualTo(0);
        verify(jdbcTemplate, atLeast(1)).execute(anyString());
    }
}
