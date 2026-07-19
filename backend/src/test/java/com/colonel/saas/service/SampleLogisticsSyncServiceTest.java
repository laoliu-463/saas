package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.colonel.saas.domain.sample.event.SampleDomainEventPublisher;
import com.colonel.saas.entity.SampleRequest;
import com.colonel.saas.gateway.logistics.query.LogisticsQueryGateway;
import com.colonel.saas.gateway.logistics.query.LogisticsQueryResult;
import com.colonel.saas.gateway.logistics.query.LogisticsStatusCode;
import com.colonel.saas.mapper.SampleLogisticsTraceMapper;
import com.colonel.saas.mapper.SampleRequestMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionOperations;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SampleLogisticsSyncServiceTest {

    @Mock
    private LogisticsQueryGateway logisticsQueryGateway;
    @Mock
    private SampleRequestMapper sampleRequestMapper;
    @Mock
    private SampleLogisticsTraceMapper sampleLogisticsTraceMapper;
    @Mock
    private SampleStatusLogService sampleStatusLogService;
    @Mock
    private SampleDomainEventPublisher sampleDomainEventPublisher;
    @Mock
    private TransactionOperations transactionOperations;

    private SampleLogisticsSyncService service;

    @BeforeEach
    void setUp() {
        service = new SampleLogisticsSyncService(
                logisticsQueryGateway,
                sampleRequestMapper,
                sampleLogisticsTraceMapper,
                sampleStatusLogService,
                sampleDomainEventPublisher,
                transactionOperations);
    }

    @Test
    @DisplayName("发货中物流刷新无候选时返回空汇总")
    void refreshShippingSamples_noCandidates_returnsEmptySummary() {
        when(sampleRequestMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        SampleLogisticsSyncService.SyncBatchSummary summary = service.refreshShippingSamples();

        assertThat(summary.total()).isZero();
        assertThat(summary.success()).isZero();
        assertThat(summary.failed()).isZero();
        assertThat(summary.skipped()).isZero();
        verify(sampleRequestMapper).selectList(any(LambdaQueryWrapper.class));
        verifyNoInteractions(
                logisticsQueryGateway,
                sampleLogisticsTraceMapper,
                sampleStatusLogService,
                sampleDomainEventPublisher,
                transactionOperations);
    }

    @Test
    @DisplayName("快递100限频结果计为跳过且不覆盖已有物流状态")
    void handleLogisticsResult_queryThrottled_preservesExistingStateWithoutPersistence() {
        SampleRequest sample = new SampleRequest();
        sample.setId(UUID.randomUUID());
        sample.setLogisticsStatus(LogisticsStatusCode.IN_TRANSIT.name());
        sample.setLogisticsStatusName("运输中");
        sample.setLogisticsLastError(null);
        sample.setLogisticsRawPayload(Map.of("state", "IN_TRANSIT"));
        LogisticsQueryResult throttled = LogisticsQueryResult.queryFailed(
                "KUAIDI100", "YTO", "YT001", "QUERY_THROTTLED", "至少31分钟后重试");

        LogisticsQueryResult result = service.handleLogisticsResult(sample, throttled);

        assertThat(result).isSameAs(throttled);
        assertThat(sample.getLogisticsStatus()).isEqualTo(LogisticsStatusCode.IN_TRANSIT.name());
        assertThat(sample.getLogisticsStatusName()).isEqualTo("运输中");
        assertThat(sample.getLogisticsLastError()).isNull();
        assertThat(sample.getLogisticsRawPayload()).containsEntry("state", "IN_TRANSIT");
        verify(sampleRequestMapper, never()).updateById(any());
        verifyNoInteractions(sampleLogisticsTraceMapper, sampleStatusLogService, sampleDomainEventPublisher);
    }

    @Test
    @DisplayName("Redis 限频状态不可用时失败关闭且不覆盖已有物流事实")
    void handleLogisticsResult_throttleStateUnavailable_preservesExistingStateWithoutPersistence() {
        SampleRequest sample = new SampleRequest();
        sample.setId(UUID.randomUUID());
        sample.setLogisticsStatus(LogisticsStatusCode.IN_TRANSIT.name());
        sample.setLogisticsStatusName("运输中");
        sample.setLogisticsRawPayload(Map.of("state", "IN_TRANSIT"));
        LogisticsQueryResult unavailable = LogisticsQueryResult.queryFailed(
                "KUAIDI100", "YTO", "YT001", "THROTTLE_STATE_UNAVAILABLE", "Redis unavailable");

        service.handleLogisticsResult(sample, unavailable);

        assertThat(sample.getLogisticsStatus()).isEqualTo(LogisticsStatusCode.IN_TRANSIT.name());
        assertThat(sample.getLogisticsStatusName()).isEqualTo("运输中");
        assertThat(sample.getLogisticsRawPayload()).containsEntry("state", "IN_TRANSIT");
        verify(sampleRequestMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("成功查询显式清除数据库中的历史物流错误")
    void handleLogisticsResult_success_clearsPersistedHistoricalError() {
        SampleRequest sample = new SampleRequest();
        sample.setId(UUID.randomUUID());
        sample.setLogisticsLastError("历史限频错误");
        LogisticsQueryResult success = LogisticsQueryResult.builder()
                .success(true)
                .provider("KUAIDI100")
                .logisticsCompany("YTO")
                .trackingNo("YT001")
                .statusCode(LogisticsStatusCode.UNKNOWN)
                .statusName("UNKNOWN")
                .traces(List.of())
                .rawPayload(Map.of())
                .build();
        when(sampleRequestMapper.updateById(sample)).thenReturn(1);
        when(sampleRequestMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);

        service.handleLogisticsResult(sample, success);

        assertThat(sample.getLogisticsLastError()).isNull();
        verify(sampleRequestMapper).updateById(sample);
        verify(sampleRequestMapper).update(isNull(), any(UpdateWrapper.class));
    }

    @Test
    @DisplayName("批量轮询将 QUERY_THROTTLED 计为跳过而不是成功")
    void syncPendingInTransit_queryThrottled_countsAsSkipped() {
        SampleRequest sample = new SampleRequest();
        sample.setId(UUID.randomUUID());
        sample.setTrackingNo("YT001");
        sample.setShipperCode("YTO");
        LogisticsQueryResult throttled = LogisticsQueryResult.queryFailed(
                "KUAIDI100", "YTO", "YT001", "QUERY_THROTTLED", "至少31分钟后重试");
        when(sampleRequestMapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenReturn(List.of(sample));
        when(logisticsQueryGateway.query(any(com.colonel.saas.gateway.logistics.LogisticsTrackCommand.class)))
                .thenReturn(throttled);
        when(transactionOperations.execute(any())).thenReturn(throttled);

        SampleLogisticsSyncService.SyncBatchSummary summary = service.syncPendingInTransit(10);

        assertThat(summary.total()).isEqualTo(1);
        assertThat(summary.success()).isZero();
        assertThat(summary.failed()).isZero();
        assertThat(summary.skipped()).isEqualTo(1);
    }
}
