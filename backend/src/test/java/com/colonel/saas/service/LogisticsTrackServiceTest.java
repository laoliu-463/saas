package com.colonel.saas.service;

import com.colonel.saas.entity.SampleRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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
}
