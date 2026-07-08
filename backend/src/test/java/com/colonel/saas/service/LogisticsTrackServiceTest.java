package com.colonel.saas.service;

import com.colonel.saas.domain.sample.application.LogisticsTrackApplicationService;
import com.colonel.saas.entity.SampleRequest;
import com.colonel.saas.service.SampleLogisticsSyncService.SyncBatchSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * LogisticsTrackService 委派壳冒烟测试（DDD-LOGISTICS-001 Slice 2）。
 *
 * <p>Service 已是 1-line delegate；本测试仅验证委派路径打通，详细业务逻辑断言
 * 见 {@link LogisticsTrackApplicationServiceTest}。</p>
 */
@ExtendWith(MockitoExtension.class)
class LogisticsTrackServiceTest {

    @Mock
    private LogisticsTrackApplicationService applicationService;

    private LogisticsTrackService service;

    @BeforeEach
    void setUp() {
        service = new LogisticsTrackService(applicationService);
    }

    @Test
    void refreshAndProgress_shouldDelegateToApplication() {
        SampleRequest sample = new SampleRequest();
        UUID id = UUID.randomUUID();
        sample.setId(id);

        service.refreshAndProgress(sample);

        verify(applicationService).refreshAndProgress(sample);
    }

    @Test
    void refreshShippingSamples_shouldDelegateToApplication() {
        SyncBatchSummary summary = new SyncBatchSummary(3, 2, 1, 0);
        when(applicationService.refreshShippingSamples()).thenReturn(summary);

        SyncBatchSummary result = service.refreshShippingSamples();

        assertThat(result).isSameAs(summary);
        verify(applicationService).refreshShippingSamples();
    }
}
