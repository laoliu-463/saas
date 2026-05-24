package com.colonel.saas.service;

import com.colonel.saas.entity.SampleRequest;
import com.colonel.saas.gateway.logistics.query.LogisticsQueryGateway;
import com.colonel.saas.gateway.logistics.query.LogisticsQueryResult;
import com.colonel.saas.gateway.logistics.query.LogisticsStatusCode;
import com.colonel.saas.gateway.logistics.query.MockLogisticsQueryGateway;
import com.colonel.saas.mapper.SampleLogisticsTraceMapper;
import com.colonel.saas.mapper.SampleRequestMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SampleLogisticsSyncServiceTest {

    @Mock private SampleRequestMapper sampleRequestMapper;
    @Mock private SampleLogisticsTraceMapper sampleLogisticsTraceMapper;
    @Mock private SampleStatusLogService sampleStatusLogService;

    private SampleLogisticsSyncService service;
    private final MockLogisticsQueryGateway mockGateway = new MockLogisticsQueryGateway();

    @BeforeEach
    void setUp() {
        LogisticsQueryGateway router = new LogisticsQueryGateway() {
            @Override
            public LogisticsQueryResult query(String logisticsCompany, String trackingNo) {
                return mockGateway.query(logisticsCompany, trackingNo);
            }

            @Override
            public boolean isSupported() {
                return true;
            }

            @Override
            public String providerName() {
                return "MOCK";
            }
        };
        service = new SampleLogisticsSyncService(
                router, sampleRequestMapper, sampleLogisticsTraceMapper, sampleStatusLogService,
                org.mockito.Mockito.mock(com.colonel.saas.domain.sample.event.SampleDomainEventPublisher.class));
    }

    @Test
    void signedResult_shouldProgressToPendingHomework() {
        UUID id = UUID.randomUUID();
        SampleRequest sample = new SampleRequest();
        sample.setId(id);
        sample.setRequestNo("SM001");
        sample.setStatus(3);
        sample.setTrackingNo("MOCK-SIGNED");
        sample.setShipperCode("SF");
        sample.setUserId(UUID.randomUUID());
        sample.setVersion(0);

        when(sampleRequestMapper.selectById(id)).thenReturn(sample);
        when(sampleRequestMapper.updateById(any())).thenReturn(1);

        LogisticsQueryResult result = service.syncOne(id);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isSigned()).isTrue();
        ArgumentCaptor<SampleRequest> captor = ArgumentCaptor.forClass(SampleRequest.class);
        verify(sampleRequestMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(5);
        verify(sampleStatusLogService).log(any(), any(), any(), any(), any());
    }

    @Test
    void queryFailure_shouldNotChangeMainStatus() {
        UUID id = UUID.randomUUID();
        SampleRequest sample = new SampleRequest();
        sample.setId(id);
        sample.setStatus(3);
        sample.setTrackingNo("FAIL-001");
        sample.setShipperCode("SF");
        sample.setVersion(0);

        when(sampleRequestMapper.selectById(id)).thenReturn(sample);
        when(sampleRequestMapper.updateById(any())).thenReturn(1);

        LogisticsQueryResult result = service.syncOne(id);

        assertThat(result.isSuccess()).isFalse();
        ArgumentCaptor<SampleRequest> captor = ArgumentCaptor.forClass(SampleRequest.class);
        verify(sampleRequestMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(3);
        assertThat(captor.getValue().getLogisticsLastError()).isNotBlank();
        verify(sampleStatusLogService, never()).log(any(), any(), any(), any(), any());
    }

    @Test
    void handleLogisticsResult_signed_shouldUpdateSignedAt() {
        SampleRequest sample = new SampleRequest();
        sample.setId(UUID.randomUUID());
        sample.setStatus(3);
        sample.setTrackingNo("MOCK");
        sample.setShipperCode("SF");
        sample.setUserId(UUID.randomUUID());
        sample.setVersion(0);
        when(sampleRequestMapper.updateById(any())).thenReturn(1);

        LocalDateTime signedAt = LocalDateTime.now().minusHours(1);
        LogisticsQueryResult result = LogisticsQueryResult.builder()
                .success(true)
                .provider("MOCK")
                .trackingNo("MOCK")
                .logisticsCompany("SF")
                .statusCode(LogisticsStatusCode.SIGNED)
                .statusName("已签收")
                .signed(true)
                .signedAt(signedAt)
                .traces(List.of())
                .queriedAt(LocalDateTime.now())
                .build();

        service.handleLogisticsResult(sample, result);

        ArgumentCaptor<SampleRequest> captor = ArgumentCaptor.forClass(SampleRequest.class);
        verify(sampleRequestMapper).updateById(captor.capture());
        assertThat(captor.getValue().getSignedAt()).isEqualTo(signedAt);
    }
}
