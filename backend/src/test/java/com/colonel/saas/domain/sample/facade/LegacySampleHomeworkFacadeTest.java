package com.colonel.saas.domain.sample.facade;

import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.service.SampleLifecycleService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LegacySampleHomeworkFacadeTest {

    @Test
    void completePendingHomeworkByOrder_shouldDelegateToSampleLifecycleService() {
        SampleLifecycleService sampleLifecycleService = mock(SampleLifecycleService.class);
        LegacySampleHomeworkFacade facade = new LegacySampleHomeworkFacade(sampleLifecycleService);
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        when(sampleLifecycleService.completePendingHomeworkByOrder(order)).thenReturn(1);

        int completed = facade.completePendingHomeworkByOrder(order);

        assertThat(completed).isEqualTo(1);
        verify(sampleLifecycleService).completePendingHomeworkByOrder(order);
    }
}
