package com.colonel.saas.service;

import com.colonel.saas.entity.SampleRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LogisticsTrackServiceTest {

    @Mock
    private SampleLogisticsSyncService sampleLogisticsSyncService;

    private LogisticsTrackService service;

    @BeforeEach
    void setUp() {
        service = new LogisticsTrackService(sampleLogisticsSyncService);
    }

    @Test
    @DisplayName("委托 SampleLogisticsSyncService 同步")
    void refreshAndProgress_delegatesToSyncService() {
        SampleRequest sample = new SampleRequest();
        UUID id = UUID.randomUUID();
        sample.setId(id);

        service.refreshAndProgress(sample);

        verify(sampleLogisticsSyncService).syncOne(id);
    }

    @Test
    @DisplayName("sample 为空时不调用")
    void refreshAndProgress_nullSample_skip() {
        service.refreshAndProgress(null);
        verify(sampleLogisticsSyncService, never()).syncOne(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("批量刷新发货中寄样委托 SampleLogisticsSyncService")
    void refreshShippingSamples_delegatesToSyncService() {
        SampleLogisticsSyncService.SyncBatchSummary summary =
                new SampleLogisticsSyncService.SyncBatchSummary(3, 2, 1, 0);
        when(sampleLogisticsSyncService.refreshShippingSamples()).thenReturn(summary);

        SampleLogisticsSyncService.SyncBatchSummary result = service.refreshShippingSamples();

        assertThat(result).isSameAs(summary);
        verify(sampleLogisticsSyncService).refreshShippingSamples();
    }
}
