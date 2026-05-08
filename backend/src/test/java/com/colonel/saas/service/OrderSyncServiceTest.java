package com.colonel.saas.service;

import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.entity.SysUser;
import com.colonel.saas.gateway.douyin.DouyinOrderGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderSyncServiceTest {

    @Mock
    private DouyinOrderGateway douyinOrderGateway;
    @Mock
    private OrderSyncPersistenceService persistenceService;
    @Mock
    private AttributionService attributionService;
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ValueOperations<String, Object> valueOperations;

    private OrderSyncService service;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(persistenceService.persistOrder(any())).thenReturn(true);
        service = new OrderSyncService(douyinOrderGateway, persistenceService, attributionService, redisTemplate, false);
    }

    @Test
    void syncByTimeRange_shouldReturnLockedWhenLockNotAcquired() {
        when(valueOperations.setIfAbsent(eq("order:sync:lock"), eq("1"), any(Duration.class))).thenReturn(false);

        OrderSyncService.SyncResult result = service.syncByTimeRange(100L, 200L);

        assertThat(result.locked()).isTrue();
        verify(douyinOrderGateway, never()).listSettlement(any(DouyinOrderGateway.DouyinOrderQueryRequest.class));
    }

    @Test
    void syncByTimeRange_shouldPersistLastSyncTimeAndReleaseLock() {
        when(valueOperations.setIfAbsent(eq("order:sync:lock"), eq("1"), any(Duration.class))).thenReturn(true);
        when(douyinOrderGateway.listSettlement(any(DouyinOrderGateway.DouyinOrderQueryRequest.class)))
                .thenReturn(new DouyinOrderGateway.OrderListResult(List.of(), false, "0", Map.of()));

        OrderSyncService.SyncResult result = service.syncByTimeRange(100L, 200L);

        assertThat(result.locked()).isFalse();
        assertThat(result.inserted()).isZero();
        verify(valueOperations).set("order:sync:last_time", "200");
        verify(redisTemplate).delete("order:sync:lock");
    }

    @Test
    void syncLatestWindow_shouldUseLastSyncWithOverlap() {
        when(valueOperations.get("order:sync:last_time")).thenReturn(12345L);
        when(valueOperations.setIfAbsent(eq("order:sync:lock"), eq("1"), any(Duration.class))).thenReturn(true);
        when(douyinOrderGateway.listSettlement(any(DouyinOrderGateway.DouyinOrderQueryRequest.class)))
                .thenReturn(new DouyinOrderGateway.OrderListResult(List.of(), false, "0", Map.of()));

        service.syncLatestWindow();

        ArgumentCaptor<DouyinOrderGateway.DouyinOrderQueryRequest> captor =
                ArgumentCaptor.forClass(DouyinOrderGateway.DouyinOrderQueryRequest.class);
        verify(douyinOrderGateway).listSettlement(captor.capture());
        assertThat(captor.getValue().startTime()).isEqualTo(12285L);
        assertThat(captor.getValue().count()).isEqualTo(100);
        assertThat(captor.getValue().cursor()).isEqualTo("0");
    }

    @Test
    void syncByTimeRange_shouldPersistAttributedOrderAndFillUserNames() {
        UUID channelUserId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        UUID colonelUserId = UUID.randomUUID();
        when(valueOperations.setIfAbsent(eq("order:sync:lock"), eq("1"), any(Duration.class))).thenReturn(true);
        when(douyinOrderGateway.listSettlement(any(DouyinOrderGateway.DouyinOrderQueryRequest.class)))
                .thenReturn(new DouyinOrderGateway.OrderListResult(
                        List.of(new DouyinOrderGateway.DouyinOrderItem(
                                "order-1",
                                "ext-product-1",
                                "product-1",
                                "shop_1001",
                                "Test Shop",
                                "talent-1",
                                "Talent Name",
                                "pick-source-1",
                                12345L,
                                888L,
                                1,
                                1710000000L,
                                1710003600L,
                                Map.of("product_name", "Product A")
                        )),
                        false,
                        "1",
                        Map.of()
                ));
        when(attributionService.resolveAttribution(any(), any()))
                .thenReturn(AttributionService.AttributionResult.attributed(
                        channelUserId,
                        deptId,
                        channelUserId,
                        null,
                        "talent-1",
                        "activity-1",
                        colonelUserId,
                        AttributionService.REASON_ATTRIBUTED
                ));
        when(persistenceService.getUser(channelUserId)).thenReturn(user("Channel A"));
        when(persistenceService.getUser(colonelUserId)).thenReturn(user("Colonel A"));

        OrderSyncService.SyncResult result = service.syncByTimeRange(100L, 200L);

        assertThat(result.inserted()).isEqualTo(1);
        assertThat(result.attributed()).isEqualTo(1);
        ArgumentCaptor<ColonelsettlementOrder> captor = ArgumentCaptor.forClass(ColonelsettlementOrder.class);
        verify(persistenceService).persistOrder(captor.capture());
        ColonelsettlementOrder saved = captor.getValue();
        assertThat(saved.getOrderId()).isEqualTo("order-1");
        assertThat(saved.getProductName()).isEqualTo("Product A");
        assertThat(saved.getAttributionStatus()).isEqualTo(AttributionService.STATUS_ATTRIBUTED);
        assertThat(saved.getChannelUserName()).isEqualTo("Channel A");
        assertThat(saved.getColonelUserName()).isEqualTo("Colonel A");
        assertThat(saved.getActivityId()).isEqualTo("activity-1");
    }

    @Test
    void syncByTimeRange_shouldSkipBlankOrderId() {
        when(valueOperations.setIfAbsent(eq("order:sync:lock"), eq("1"), any(Duration.class))).thenReturn(true);
        when(douyinOrderGateway.listSettlement(any(DouyinOrderGateway.DouyinOrderQueryRequest.class)))
                .thenReturn(new DouyinOrderGateway.OrderListResult(
                        List.of(new DouyinOrderGateway.DouyinOrderItem(
                                "  ",
                                "ext-product-1",
                                "product-1",
                                "1001",
                                "Test Shop",
                                null,
                                null,
                                "pick-source-1",
                                12345L,
                                888L,
                                1,
                                1710000000L,
                                null,
                                Map.of("product_name", "Product A")
                        )),
                        false,
                        "1",
                        Map.of()
                ));

        OrderSyncService.SyncResult result = service.syncByTimeRange(100L, 200L);

        assertThat(result.inserted()).isZero();
        verify(attributionService, never()).resolveAttribution(any(), any());
        verify(persistenceService, never()).persistOrder(any());
    }

    @Test
    void syncByTimeRange_shouldCountFailuresWhenAttributionThrows() {
        when(valueOperations.setIfAbsent(eq("order:sync:lock"), eq("1"), any(Duration.class))).thenReturn(true);
        when(douyinOrderGateway.listSettlement(any(DouyinOrderGateway.DouyinOrderQueryRequest.class)))
                .thenReturn(new DouyinOrderGateway.OrderListResult(
                        List.of(new DouyinOrderGateway.DouyinOrderItem(
                                "order-1",
                                "ext-product-1",
                                "product-1",
                                "1001",
                                "Test Shop",
                                null,
                                null,
                                "pick-source-1",
                                12345L,
                                888L,
                                1,
                                1710000000L,
                                null,
                                Map.of("product_name", "Product A")
                        )),
                        false,
                        "1",
                        Map.of()
                ));
        when(attributionService.resolveAttribution(any(), any())).thenThrow(new BusinessException("boom"));

        OrderSyncService.SyncResult result = service.syncByTimeRange(100L, 200L);

        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.inserted()).isZero();
        verify(persistenceService, never()).persistOrder(any());
    }

    @Test
    void syncByTimeRange_shouldValidateTimeRange() {
        assertThatThrownBy(() -> service.syncByTimeRange(0L, 200L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Invalid sync time range");
        assertThatThrownBy(() -> service.syncByTimeRange(300L, 200L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Invalid sync time range");
    }

    @Test
    void mockModeShouldFallbackWhenRedisUnavailable() {
        service = new OrderSyncService(douyinOrderGateway, persistenceService, attributionService, redisTemplate, true);
        when(valueOperations.setIfAbsent(eq("order:sync:lock"), eq("1"), any(Duration.class)))
                .thenThrow(new RedisConnectionFailureException("redis down"));
        when(douyinOrderGateway.listSettlement(any(DouyinOrderGateway.DouyinOrderQueryRequest.class)))
                .thenReturn(new DouyinOrderGateway.OrderListResult(List.of(), false, "0", Map.of()));

        OrderSyncService.SyncResult result = service.syncByTimeRange(100L, 200L);

        assertThat(result.locked()).isFalse();
        assertThat(service.getLastSyncTime()).isNotNull();
    }

    private SysUser user(String name) {
        SysUser user = new SysUser();
        user.setRealName(name);
        return user;
    }
}
