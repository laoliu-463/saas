package com.colonel.saas.domain.sample.application;

import com.colonel.saas.entity.SampleRequest;
import com.colonel.saas.service.SampleLogisticsSyncService;
import com.colonel.saas.service.SampleLogisticsSyncService.SyncBatchSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * LogisticsTrackApplicationService 单元测试（DDD-LOGISTICS-001 Slice 2）。
 *
 * <p>原 LogisticsTrackServiceTest 中针对 refreshAndProgress / refreshShippingSamples
 * 的业务断言已迁移到 Application；Service 委派壳为 1-line delegate，单独测试由
 * 集成覆盖。</p>
 */
@ExtendWith(MockitoExtension.class)
class LogisticsTrackApplicationServiceTest {

    @Mock
    private SampleLogisticsSyncService sampleLogisticsSyncService;

    private LogisticsTrackApplicationService applicationService;

    @BeforeEach
    void setUp() {
        applicationService = new LogisticsTrackApplicationService(sampleLogisticsSyncService);
    }

    @Test
    void refreshAndProgress_shouldDelegateToSyncService() {
        SampleRequest sample = new SampleRequest();
        UUID id = UUID.randomUUID();
        sample.setId(id);

        applicationService.refreshAndProgress(sample);

        verify(sampleLogisticsSyncService).syncOne(id);
    }

    @Test
    void refreshAndProgress_shouldSkipWhenSampleNull() {
        applicationService.refreshAndProgress(null);
        verify(sampleLogisticsSyncService, never()).syncOne(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void refreshAndProgress_shouldSkipWhenSampleIdNull() {
        SampleRequest sample = new SampleRequest();
        applicationService.refreshAndProgress(sample);
        verify(sampleLogisticsSyncService, never()).syncOne(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void refreshShippingSamples_shouldDelegateToSyncService() {
        SyncBatchSummary summary = new SyncBatchSummary(3, 2, 1, 0);
        when(sampleLogisticsSyncService.refreshShippingSamples()).thenReturn(summary);

        SyncBatchSummary result = applicationService.refreshShippingSamples();

        assertThat(result).isSameAs(summary);
        verify(sampleLogisticsSyncService).refreshShippingSamples();
    }
}
