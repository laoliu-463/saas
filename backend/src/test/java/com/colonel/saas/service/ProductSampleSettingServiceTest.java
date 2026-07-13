package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.entity.ProductOperationLog;
import com.colonel.saas.entity.ProductOperationState;
import com.colonel.saas.entity.ProductSnapshot;
import com.colonel.saas.mapper.ProductOperationLogMapper;
import com.colonel.saas.mapper.ProductOperationStateMapper;
import com.colonel.saas.mapper.ProductSnapshotMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductSampleSettingServiceTest {

    @Mock
    private ProductSnapshotMapper snapshotMapper;
    @Mock
    private ProductOperationStateMapper operationStateMapper;
    @Mock
    private ProductOperationLogMapper operationLogMapper;

    private ProductSampleSettingService service;

    @BeforeEach
    void setUp() {
        service = new ProductSampleSettingService(
                snapshotMapper,
                operationStateMapper,
                operationLogMapper,
                new ObjectMapper());
    }

    @Test
    void update_shouldMergeSampleSettingIntoExistingAuditPayload() throws Exception {
        UUID relationId = UUID.randomUUID();
        ProductSnapshot snapshot = snapshot(relationId);
        ProductOperationState state = new ProductOperationState();
        state.setId(UUID.randomUUID());
        state.setActivityId(snapshot.getActivityId());
        state.setProductId(snapshot.getProductId());
        state.setVersion(3);
        state.setBizStatus("APPROVED");
        state.setAuditPayload("{\"promotionScript\":\"保留原审核信息\",\"freeSample\":false}");

        when(snapshotMapper.selectById(relationId)).thenReturn(snapshot);
        when(operationStateMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(state);
        when(operationStateMapper.updateById(state)).thenReturn(1);

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("supportFreeSample", true);
        request.put("hasSampleThreshold", true);
        request.put("minWindowSales30d", 100L);
        request.put("minSales30d", 50000L);
        request.put("minFans", 1000L);
        request.put("minTalentLevel", 1L);
        request.put("sampleBoxCount", 4L);
        request.put("sampleQuantity", 1L);

        Map<String, Object> result = service.update(
                relationId,
                request,
                UUID.randomUUID(),
                UUID.randomUUID());

        assertThat(result)
                .containsEntry("promotionScript", "保留原审核信息")
                .containsEntry("supportFreeSample", true)
                .containsEntry("freeSample", true)
                .containsEntry("sampleType", "FREE")
                .containsEntry("sampleThresholdSales", 50000L)
                .containsEntry("sampleThresholdLevel", 1L)
                .containsEntry("sampleBoxCount", 4L)
                .containsEntry("sampleQuantity", 1L);
        assertThat(result.get("hasSampleThreshold")).isEqualTo(true);

        verify(operationStateMapper).updateById(state);
        ArgumentCaptor<ProductOperationLog> logCaptor = ArgumentCaptor.forClass(ProductOperationLog.class);
        verify(operationLogMapper).insert(logCaptor.capture());
        assertThat(logCaptor.getValue().getOperationType()).isEqualTo("SAMPLE_SETTING");
        assertThat(logCaptor.getValue().getBeforeStatus()).isEqualTo("APPROVED");
    }

    @Test
    void update_shouldRejectNegativeThreshold() {
        UUID relationId = UUID.randomUUID();
        when(snapshotMapper.selectById(relationId)).thenReturn(snapshot(relationId));

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("supportFreeSample", true);
        request.put("minSales30d", -1);

        assertThatThrownBy(() -> service.update(relationId, request, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("近30天销售额");
    }

    @Test
    void update_shouldRemoveThresholdValuesWhenThresholdSwitchIsOff() throws Exception {
        UUID relationId = UUID.randomUUID();
        ProductSnapshot snapshot = snapshot(relationId);
        ProductOperationState state = new ProductOperationState();
        state.setId(UUID.randomUUID());
        state.setActivityId(snapshot.getActivityId());
        state.setProductId(snapshot.getProductId());
        state.setVersion(1);
        state.setAuditPayload("{\"hasSampleThreshold\":true,\"sampleThresholdSales\":50000}");

        when(snapshotMapper.selectById(relationId)).thenReturn(snapshot);
        when(operationStateMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(state);
        when(operationStateMapper.updateById(state)).thenReturn(1);

        Map<String, Object> request = Map.of(
                "supportFreeSample", false,
                "hasSampleThreshold", false,
                "sampleBoxCount", 4,
                "sampleQuantity", 1);

        Map<String, Object> result = service.update(relationId, request, null, null);

        assertThat(result)
                .containsEntry("supportFreeSample", false)
                .containsEntry("sampleType", "PAID")
                .containsEntry("hasSampleThreshold", false)
                .doesNotContainKeys("minWindowSales30d", "minSales30d", "minFans", "minTalentLevel",
                        "sampleThresholdSales", "sampleThresholdLevel");
    }

    private ProductSnapshot snapshot(UUID relationId) {
        ProductSnapshot snapshot = new ProductSnapshot();
        snapshot.setId(relationId);
        snapshot.setActivityId("activity-1");
        snapshot.setProductId("product-1");
        return snapshot;
    }
}
