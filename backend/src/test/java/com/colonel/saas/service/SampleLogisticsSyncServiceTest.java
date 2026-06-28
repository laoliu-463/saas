package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.domain.sample.event.SampleDomainEventPublisher;
import com.colonel.saas.entity.SampleRequest;
import com.colonel.saas.gateway.logistics.query.LogisticsQueryGateway;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
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
}
