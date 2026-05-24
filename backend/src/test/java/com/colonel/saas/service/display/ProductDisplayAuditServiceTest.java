package com.colonel.saas.service.display;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.entity.ProductDisplayAuditLog;
import com.colonel.saas.mapper.ProductDisplayAuditLogMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductDisplayAuditServiceTest {

    @Mock
    private ProductDisplayAuditLogMapper auditLogMapper;

    private ProductDisplayAuditService service;

    @BeforeEach
    void setUp() {
        service = new ProductDisplayAuditService(auditLogMapper, new ObjectMapper());
    }

    @Test
    void writeAudit_shouldPersistSerializedAuditLog() {
        UUID oldRelationId = UUID.randomUUID();
        UUID newRelationId = UUID.randomUUID();
        UUID candidateId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        service.writeAudit(
                "P100",
                oldRelationId,
                newRelationId,
                List.of(candidateId),
                "FORCE_SHOW",
                "优先展示",
                "替换隐藏",
                7,
                DisplayRuleOperatorContext.admin(adminId),
                Map.of("reason", "manual"));

        ArgumentCaptor<ProductDisplayAuditLog> captor = ArgumentCaptor.forClass(ProductDisplayAuditLog.class);
        verify(auditLogMapper).insert(captor.capture());
        ProductDisplayAuditLog log = captor.getValue();
        assertThat(log.getId()).isNotNull();
        assertThat(log.getProductId()).isEqualTo("P100");
        assertThat(log.getOldRelationId()).isEqualTo(oldRelationId);
        assertThat(log.getNewRelationId()).isEqualTo(newRelationId);
        assertThat(log.getCandidateRelationIds()).contains(candidateId.toString());
        assertThat(log.getActionType()).isEqualTo("FORCE_SHOW");
        assertThat(log.getSelectedReason()).isEqualTo("优先展示");
        assertThat(log.getHiddenReason()).isEqualTo("替换隐藏");
        assertThat(log.getRuleVersion()).isEqualTo(7);
        assertThat(log.getOperatorType()).isEqualTo(DisplayRuleOperatorContext.TYPE_ADMIN);
        assertThat(log.getOperatorId()).isEqualTo(adminId.toString());
        assertThat(log.getDetailJson()).contains("\"reason\":\"manual\"");
        assertThat(log.getCreatedAt()).isNotNull();
    }

    @Test
    void writeAudit_shouldKeepNullableFieldsAndFallbackWhenJsonSerializationFails() throws Exception {
        ObjectMapper objectMapper = org.mockito.Mockito.mock(ObjectMapper.class);
        when(objectMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("boom") {
        });
        ProductDisplayAuditService failingService = new ProductDisplayAuditService(auditLogMapper, objectMapper);

        failingService.writeAudit(
                "P200",
                null,
                null,
                List.of(UUID.randomUUID()),
                "AUTO_HIDE",
                null,
                null,
                3,
                DisplayRuleOperatorContext.system(),
                Map.of("reason", "system"));

        ArgumentCaptor<ProductDisplayAuditLog> captor = ArgumentCaptor.forClass(ProductDisplayAuditLog.class);
        verify(auditLogMapper).insert(captor.capture());
        ProductDisplayAuditLog log = captor.getValue();
        assertThat(log.getOldRelationId()).isNull();
        assertThat(log.getNewRelationId()).isNull();
        assertThat(log.getCandidateRelationIds()).isEqualTo("[]");
        assertThat(log.getDetailJson()).isEqualTo("[]");
        assertThat(log.getOperatorType()).isEqualTo(DisplayRuleOperatorContext.TYPE_SYSTEM);
        assertThat(log.getOperatorId()).isNull();
    }

    @Test
    void writeAudit_shouldLeaveDetailJsonNullWhenDetailMissing() {
        service.writeAudit(
                "P300",
                null,
                null,
                List.of(),
                "AUTO_SHOW",
                null,
                null,
                1,
                DisplayRuleOperatorContext.job(),
                null);

        ArgumentCaptor<ProductDisplayAuditLog> captor = ArgumentCaptor.forClass(ProductDisplayAuditLog.class);
        verify(auditLogMapper).insert(captor.capture());
        assertThat(captor.getValue().getCandidateRelationIds()).isEqualTo("[]");
        assertThat(captor.getValue().getDetailJson()).isNull();
        assertThat(captor.getValue().getOperatorId()).isNull();
    }

    @Test
    void pageAuditLogs_shouldNormalizePageAndFilterTrimmedProductId() {
        when(auditLogMapper.selectPage(any(Page.class), any())).thenAnswer(invocation -> invocation.getArgument(0));

        Page<ProductDisplayAuditLog> result = service.pageAuditLogs(" P100 ", 0, 0);

        assertThat(result.getCurrent()).isEqualTo(1);
        assertThat(result.getSize()).isEqualTo(1);
        verify(auditLogMapper).selectPage(any(Page.class), any());
    }

    @Test
    void pageAuditLogs_shouldOmitProductFilterWhenBlank() {
        when(auditLogMapper.selectPage(any(Page.class), any())).thenAnswer(invocation -> invocation.getArgument(0));

        Page<ProductDisplayAuditLog> result = service.pageAuditLogs(" ", 2, 20);

        assertThat(result.getCurrent()).isEqualTo(2);
        assertThat(result.getSize()).isEqualTo(20);
        verify(auditLogMapper).selectPage(any(Page.class), any());
    }
}
