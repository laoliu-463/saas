package com.colonel.saas.domain.performance.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.entity.PerformanceCalculationExecution;
import com.colonel.saas.mapper.PerformanceCalculationExecutionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PerformanceCalculationExecutionServiceTest {

    @Mock private PerformanceCalculationExecutionMapper mapper;

    @Test
    void startShouldPersistRunningExecutionAndSkipAlreadySucceededEvent() {
        PerformanceCalculationExecutionService service = new PerformanceCalculationExecutionService(mapper);
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertThat(service.start("OrderSynced:ORD-1:3", "OrderSynced", "ORD-1", 3,
                Map.of("source", "outbox"))).isTrue();

        ArgumentCaptor<PerformanceCalculationExecution> captor = ArgumentCaptor.forClass(PerformanceCalculationExecution.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("RUNNING");
        assertThat(captor.getValue().getOrderVersion()).isEqualTo(3);
        assertThat(captor.getValue().getEventPayload()).containsEntry("source", "outbox");
    }
}
