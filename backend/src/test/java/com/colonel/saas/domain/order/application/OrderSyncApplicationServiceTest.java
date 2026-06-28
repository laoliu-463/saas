package com.colonel.saas.domain.order.application;

import com.colonel.saas.config.AppProperties;
import com.colonel.saas.config.DddRefactorProperties;
import com.colonel.saas.domain.order.application.OrderSyncService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderSyncApplicationServiceTest {

    @Mock
    private OrderSyncService orderSyncService;
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ValueOperations<String, Object> valueOperations;
    @Mock
    private AppProperties appProperties;
    @Mock
    private DddRefactorProperties dddRefactorProperties;
    @Mock
    private DddRefactorProperties.Switch orderApplicationSwitch;
    private OrderSyncApplicationService newService() {
        return new OrderSyncApplicationService(
                orderSyncService, redisTemplate, appProperties, dddRefactorProperties);
    }

    private void stubCheckpoint(long checkpoint) {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(any())).thenReturn(checkpoint);
    }

    @Test
    void historicalCommand_shouldDelegateToSyncByTimeRange() {
        stubCheckpoint(1000L);
        OrderSyncService.SyncResult legacy = legacyResult();
        when(orderSyncService.syncByTimeRange(1000L, 2000L)).thenReturn(legacy);

        OrderSyncResult result = newService().execute(
                OrderSyncCommand.historical(1000L, 2000L, UUID.randomUUID()),
                OrderSyncExecutionContext.manual("OrderController"));

        verify(orderSyncService).syncByTimeRange(1000L, 2000L);
        assertThat(result.getFetched()).isEqualTo(legacy.totalFetched());
        assertThat(result.getInserted()).isEqualTo(legacy.created());
        assertThat(result.getCheckpointBefore()).isEqualTo(1000L);
    }

    @Test
    void dryRun_shouldNotDelegateAndShouldKeepCheckpoint() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(eq("order:sync:last_time"))).thenReturn(5000L);

        OrderSyncResult result = newService().execute(
                OrderSyncCommand.dryRunPreview(OrderSyncTimeType.UPDATE, UUID.randomUUID()),
                OrderSyncExecutionContext.manual("preview"));

        verifyNoInteractions(orderSyncService);
        assertThat(result.getCheckpointBefore()).isEqualTo(5000L);
        assertThat(result.getCheckpointAfter()).isEqualTo(5000L);
        assertThat(result.getErrors()).isNotEmpty();
    }

    @Test
    void scheduledPayRecent_shouldDelegateToPayRecentWindow() {
        stubCheckpoint(0L);
        when(orderSyncService.syncPayRecentWindow()).thenReturn(legacyResult());

        newService().execute(
                OrderSyncCommand.scheduledPayRecent(),
                OrderSyncExecutionContext.scheduled(OrderSyncExecutionContext.TASK_PAY_RECENT));

        verify(orderSyncService).syncPayRecentWindow();
        verify(orderSyncService, never()).syncLatestWindow();
    }

    @Test
    void scheduledSettle_shouldDelegateToSettlementSettleWindow() {
        stubCheckpoint(0L);
        when(orderSyncService.syncSettlementSettleWindow()).thenReturn(legacyResult());

        newService().execute(
                OrderSyncCommand.scheduledSettle(),
                OrderSyncExecutionContext.scheduled(OrderSyncExecutionContext.TASK_SETTLE));

        verify(orderSyncService).syncSettlementSettleWindow();
    }

    @Test
    void isRoutingEnabled_requiresRootAndOrderApplicationSwitches() {
        OrderSyncApplicationService service = newService();
        when(dddRefactorProperties.isEnabled()).thenReturn(false);
        when(dddRefactorProperties.getOrderApplication()).thenReturn(orderApplicationSwitch);
        when(orderApplicationSwitch.isEnabled()).thenReturn(true);
        assertThat(service.isRoutingEnabled()).isFalse();

        when(dddRefactorProperties.isEnabled()).thenReturn(true);
        when(orderApplicationSwitch.isEnabled()).thenReturn(false);
        assertThat(service.isRoutingEnabled()).isFalse();

        when(orderApplicationSwitch.isEnabled()).thenReturn(true);
        assertThat(service.isRoutingEnabled()).isTrue();
    }

    private static OrderSyncService.SyncResult legacyResult() {
        return new OrderSyncService.SyncResult(1000L, 2000L, 2, 10, 3, 4, 5, 1, 0, false);
    }
}
