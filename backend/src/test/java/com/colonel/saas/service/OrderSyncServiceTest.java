package com.colonel.saas.service;

import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.gateway.douyin.DouyinOrderGateway;
import com.colonel.saas.entity.ColonelsettlementOrder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
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

    private OrderSyncService orderSyncService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(persistenceService.persistOrder(any())).thenReturn(true);
        orderSyncService = new OrderSyncService(douyinOrderGateway, persistenceService, attributionService, redisTemplate);
    }

    // --- Original tests ---

    @Test
    void syncByTimeRange_shouldReturnLockedWhenLockNotAcquired() {
        when(valueOperations.setIfAbsent(eq("order:sync:lock"), eq("1"), any(Duration.class))).thenReturn(false);

        OrderSyncService.SyncResult result = orderSyncService.syncByTimeRange(100L, 200L);

        assertThat(result.locked()).isTrue();
        verify(douyinOrderGateway, never()).listSettlement(anyLong(), anyLong(), anyInt(), anyString());
    }

    @Test
    void syncByTimeRange_shouldPersistLastSyncPoint() {
        when(valueOperations.setIfAbsent(eq("order:sync:lock"), eq("1"), any(Duration.class))).thenReturn(true);
        when(douyinOrderGateway.listSettlement(anyLong(), anyLong(), anyInt(), anyString()))
                .thenReturn(new DouyinOrderGateway.OrderListResult(Map.of("data", Map.of("order_list", List.of(), "has_more", false, "cursor", 0L))));

        OrderSyncService.SyncResult result = orderSyncService.syncByTimeRange(100L, 200L);

        assertThat(result.locked()).isFalse();
        assertThat(result.inserted()).isZero();
        verify(valueOperations).set("order:sync:last_time", "200");
        verify(redisTemplate).delete("order:sync:lock");
    }

    @Test
    void syncLatestWindow_shouldUseLastSyncWithOverlap() {
        long lastSyncTime = 12345L;
        when(valueOperations.get("order:sync:last_time")).thenReturn(lastSyncTime);
        when(valueOperations.setIfAbsent(eq("order:sync:lock"), eq("1"), any(Duration.class))).thenReturn(true);
        when(douyinOrderGateway.listSettlement(anyLong(), anyLong(), anyInt(), anyString()))
                .thenReturn(new DouyinOrderGateway.OrderListResult(Map.of("data", Map.of("order_list", List.of(), "has_more", false, "cursor", 0L))));

        orderSyncService.syncLatestWindow();

        ArgumentCaptor<Long> startCaptor = ArgumentCaptor.forClass(Long.class);
        verify(douyinOrderGateway).listSettlement(startCaptor.capture(), anyLong(), eq(100), eq("0"));
        assertThat(startCaptor.getValue()).isEqualTo(lastSyncTime - 60);
    }

    // --- getData() tests ---

    @Test
    void syncByTimeRange_getData_returnsEmptyMapWhenResponseIsNull() {
        when(valueOperations.setIfAbsent(eq("order:sync:lock"), eq("1"), any(Duration.class))).thenReturn(true);
        when(douyinOrderGateway.listSettlement(anyLong(), anyLong(), anyInt(), anyString())).thenReturn(new DouyinOrderGateway.OrderListResult(null));

        OrderSyncService.SyncResult result = orderSyncService.syncByTimeRange(100L, 200L);

        assertThat(result.inserted()).isZero();
        assertThat(result.pages()).isEqualTo(1);
    }

    @Test
    void syncByTimeRange_getData_returnsEmptyMapWhenDataIsNotMap() {
        when(valueOperations.setIfAbsent(eq("order:sync:lock"), eq("1"), any(Duration.class))).thenReturn(true);
        when(douyinOrderGateway.listSettlement(anyLong(), anyLong(), anyInt(), anyString()))
                .thenReturn(new DouyinOrderGateway.OrderListResult(Map.of("data", "not a map")));

        OrderSyncService.SyncResult result = orderSyncService.syncByTimeRange(100L, 200L);

        assertThat(result.pages()).isEqualTo(1);
    }

    @Test
    void syncByTimeRange_getData_convertsMapWithMixedTypes() {
        when(valueOperations.setIfAbsent(eq("order:sync:lock"), eq("1"), any(Duration.class))).thenReturn(true);
        when(douyinOrderGateway.listSettlement(anyLong(), anyLong(), anyInt(), anyString()))
                .thenReturn(new DouyinOrderGateway.OrderListResult(Map.of("data", Map.of(
                        "order_list", List.of(),
                        "has_more", false,
                        "cursor", 100,
                        "extra_key", 42
                ))));

        OrderSyncService.SyncResult result = orderSyncService.syncByTimeRange(100L, 200L);

        assertThat(result.pages()).isEqualTo(1);
        verify(douyinOrderGateway).listSettlement(100L, 200L, 100, "0");
    }

    // --- getOrderList() tests ---

    @Test
    void syncByTimeRange_getOrderList_returnsEmptyWhenNoOrderKey() {
        when(valueOperations.setIfAbsent(eq("order:sync:lock"), eq("1"), any(Duration.class))).thenReturn(true);
        when(douyinOrderGateway.listSettlement(anyLong(), anyLong(), anyInt(), anyString()))
                .thenReturn(new DouyinOrderGateway.OrderListResult(Map.of("data", Map.of("has_more", false, "cursor", 0L))));

        OrderSyncService.SyncResult result = orderSyncService.syncByTimeRange(100L, 200L);

        assertThat(result.inserted()).isZero();
        assertThat(result.pages()).isEqualTo(1);
    }

    @Test
    void syncByTimeRange_getOrderList_convertsOrdersWithNonMapItems() {
        when(valueOperations.setIfAbsent(eq("order:sync:lock"), eq("1"), any(Duration.class))).thenReturn(true);
        when(douyinOrderGateway.listSettlement(anyLong(), anyLong(), anyInt(), anyString()))
                .thenReturn(new DouyinOrderGateway.OrderListResult(Map.of("data", Map.of(
                        "orders", List.of(
                                Map.of("order_id", "oid_1", "product_id", "pid_1", "create_time", 1710000000000L),
                                "not a map item",
                                Map.of("order_id", "oid_2", "product_id", "pid_2", "create_time", 1710000001000L)
                        ),
                        "has_more", false,
                        "cursor", 0L
                ))));
        when(attributionService.resolveAttribution(any(), any()))
                .thenReturn(new AttributionService.AttributionResult(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "talent"));

        OrderSyncService.SyncResult result = orderSyncService.syncByTimeRange(100L, 200L);

        assertThat(result.pages()).isEqualTo(1);
        assertThat(result.inserted()).isEqualTo(2);
    }

    @Test
    void syncByTimeRange_getOrderList_supportsDatasFieldAndStringCreateTime() {
        when(valueOperations.setIfAbsent(eq("order:sync:lock"), eq("1"), any(Duration.class))).thenReturn(true);
        when(douyinOrderGateway.listSettlement(anyLong(), anyLong(), anyInt(), anyString()))
                .thenReturn(new DouyinOrderGateway.OrderListResult(Map.of("data", Map.of(
                        "datas", List.of(
                                Map.of("order_id", "oid_datas_1", "product_id", "pid_1", "create_time", "2026-04-22 20:30:00")
                        ),
                        "has_more", false,
                        "cursor", 0L
                ))));
        when(attributionService.resolveAttribution(any(), any()))
                .thenReturn(new AttributionService.AttributionResult(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "talent"));

        OrderSyncService.SyncResult result = orderSyncService.syncByTimeRange(100L, 200L);

        assertThat(result.inserted()).isEqualTo(1);
    }

    @Test
    void syncByTimeRange_supportsMoreAndNextCursorPagination() {
        when(valueOperations.setIfAbsent(eq("order:sync:lock"), eq("1"), any(Duration.class))).thenReturn(true);
        when(douyinOrderGateway.listSettlement(anyLong(), anyLong(), anyInt(), anyString()))
                .thenReturn(new DouyinOrderGateway.OrderListResult(Map.of("data", Map.of(
                        "order_list", List.of(
                                Map.of("order_id", "oid_page_1", "product_id", "pid_1", "create_time", 1710000000000L)
                        ),
                        "more", true,
                        "next_cursor", 1L
                ))))
                .thenReturn(new DouyinOrderGateway.OrderListResult(Map.of("data", Map.of(
                        "order_list", List.of(
                                Map.of("order_id", "oid_page_2", "product_id", "pid_2", "create_time", 1710000001000L)
                        ),
                        "more", false,
                        "next_cursor", 2L
                ))));
        when(attributionService.resolveAttribution(any(), any()))
                .thenReturn(new AttributionService.AttributionResult(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "talent"));

        OrderSyncService.SyncResult result = orderSyncService.syncByTimeRange(100L, 200L);

        assertThat(result.pages()).isEqualTo(2);
        verify(douyinOrderGateway).listSettlement(100L, 200L, 100, "0");
        verify(douyinOrderGateway).listSettlement(100L, 200L, 100, "1");
    }

    // --- mapOrder() tests ---

    @Test
    void syncByTimeRange_mapOrder_skipsBlankOrderId() {
        when(valueOperations.setIfAbsent(eq("order:sync:lock"), eq("1"), any(Duration.class))).thenReturn(true);
        when(douyinOrderGateway.listSettlement(anyLong(), anyLong(), anyInt(), anyString()))
                .thenReturn(new DouyinOrderGateway.OrderListResult(Map.of("data", Map.of(
                        "order_list", List.of(
                                Map.of("order_id", "   ", "product_id", "pid", "create_time", 1710000000000L)
                        ),
                        "has_more", false,
                        "cursor", 0L
                ))));
        when(attributionService.resolveAttribution(any(), any()))
                .thenReturn(new AttributionService.AttributionResult(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "talent"));

        OrderSyncService.SyncResult result = orderSyncService.syncByTimeRange(100L, 200L);

        assertThat(result.inserted()).isZero();
        assertThat(result.skipped()).isZero();
    }

    @Test
    void syncByTimeRange_mapOrder_truncatesPickSourceOver128Chars() {
        when(valueOperations.setIfAbsent(eq("order:sync:lock"), eq("1"), any(Duration.class))).thenReturn(true);
        String longPickSource = "x".repeat(200);
        ArgumentCaptor<ColonelsettlementOrder> orderCaptor = ArgumentCaptor.forClass(ColonelsettlementOrder.class);
        when(douyinOrderGateway.listSettlement(anyLong(), anyLong(), anyInt(), anyString()))
                .thenReturn(new DouyinOrderGateway.OrderListResult(Map.of("data", Map.of(
                        "order_list", List.of(
                                Map.of("order_id", "oid_trunc", "product_id", "pid", "pick_source", longPickSource, "create_time", 1710000000000L)
                        ),
                        "has_more", false,
                        "cursor", 0L
                ))));
        when(attributionService.resolveAttribution(any(), any()))
                .thenReturn(new AttributionService.AttributionResult(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "talent"));

        OrderSyncService.SyncResult result = orderSyncService.syncByTimeRange(100L, 200L);

        assertThat(result.inserted()).isEqualTo(1);
        verify(persistenceService).persistOrder(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getPickSource()).hasSize(128);
    }

    @Test
    void syncByTimeRange_mapOrder_throwsOnNullCreateTime() {
        when(valueOperations.setIfAbsent(eq("order:sync:lock"), eq("1"), any(Duration.class))).thenReturn(true);
        when(douyinOrderGateway.listSettlement(anyLong(), anyLong(), anyInt(), anyString()))
                .thenReturn(new DouyinOrderGateway.OrderListResult(Map.of("data", Map.of(
                        "order_list", List.of(
                                Map.of("order_id", "oid_bad", "product_id", "pid")
                        ),
                        "has_more", false,
                        "cursor", 0L
                ))));
        when(attributionService.resolveAttribution(any(), any()))
                .thenReturn(new AttributionService.AttributionResult(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "talent"));

        OrderSyncService.SyncResult result = orderSyncService.syncByTimeRange(100L, 200L);

        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.inserted()).isZero();
    }

    @Test
    void syncByTimeRange_mapOrder_throwsOnIllegalCreateTime() {
        when(valueOperations.setIfAbsent(eq("order:sync:lock"), eq("1"), any(Duration.class))).thenReturn(true);
        when(douyinOrderGateway.listSettlement(anyLong(), anyLong(), anyInt(), anyString()))
                .thenReturn(new DouyinOrderGateway.OrderListResult(Map.of("data", Map.of(
                        "order_list", List.of(
                                Map.of("order_id", "oid_illegal", "product_id", "pid", "create_time", "not a number")
                        ),
                        "has_more", false,
                        "cursor", 0L
                ))));
        when(attributionService.resolveAttribution(any(), any()))
                .thenReturn(new AttributionService.AttributionResult(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "talent"));

        OrderSyncService.SyncResult result = orderSyncService.syncByTimeRange(100L, 200L);

        assertThat(result.skipped()).isEqualTo(1);
    }

    // --- asLong / asBoolean utility tests ---

    @Test
    void syncByTimeRange_mapOrder_asLongUsesDefaultForNonNumeric() {
        when(valueOperations.setIfAbsent(eq("order:sync:lock"), eq("1"), any(Duration.class))).thenReturn(true);
        when(douyinOrderGateway.listSettlement(anyLong(), anyLong(), anyInt(), anyString()))
                .thenReturn(new DouyinOrderGateway.OrderListResult(Map.of("data", Map.of(
                        "order_list", List.of(
                                Map.of("order_id", "oid", "product_id", "pid", "order_amount", "not_a_number", "create_time", 1710000000000L)
                        ),
                        "has_more", false,
                        "cursor", 0L
                ))));
        when(attributionService.resolveAttribution(any(), any()))
                .thenReturn(new AttributionService.AttributionResult(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "talent"));

        OrderSyncService.SyncResult result = orderSyncService.syncByTimeRange(100L, 200L);

        assertThat(result.inserted()).isEqualTo(1);
        ArgumentCaptor<ColonelsettlementOrder> captor = ArgumentCaptor.forClass(ColonelsettlementOrder.class);
        verify(persistenceService).persistOrder(captor.capture());
        assertThat(captor.getValue().getOrderAmount()).isZero();
    }

    @Test
    void syncByTimeRange_mapOrder_asLongUsesNumberDirectly() {
        when(valueOperations.setIfAbsent(eq("order:sync:lock"), eq("1"), any(Duration.class))).thenReturn(true);
        when(douyinOrderGateway.listSettlement(anyLong(), anyLong(), anyInt(), anyString()))
                .thenReturn(new DouyinOrderGateway.OrderListResult(Map.of("data", Map.of(
                        "order_list", List.of(
                                Map.of("order_id", "oid", "product_id", "pid", "order_amount", 5000, "create_time", 1710000000000L)
                        ),
                        "has_more", false,
                        "cursor", 0L
                ))));
        when(attributionService.resolveAttribution(any(), any()))
                .thenReturn(new AttributionService.AttributionResult(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "talent"));

        OrderSyncService.SyncResult result = orderSyncService.syncByTimeRange(100L, 200L);

        assertThat(result.inserted()).isEqualTo(1);
        ArgumentCaptor<ColonelsettlementOrder> captor = ArgumentCaptor.forClass(ColonelsettlementOrder.class);
        verify(persistenceService).persistOrder(captor.capture());
        assertThat(captor.getValue().getOrderAmount()).isEqualTo(5000L);
    }

    @Test
    void syncByTimeRange_asBooleanFromNumber() {
        when(valueOperations.setIfAbsent(eq("order:sync:lock"), eq("1"), any(Duration.class))).thenReturn(true);
        when(douyinOrderGateway.listSettlement(anyLong(), anyLong(), anyInt(), anyString()))
                .thenReturn(new DouyinOrderGateway.OrderListResult(Map.of("data", Map.of(
                        "order_list", List.of(
                                Map.of("order_id", "oid", "product_id", "pid", "create_time", 1710000000000L)
                        ),
                        "has_more", 1,
                        "cursor", 100L
                ))));
        when(attributionService.resolveAttribution(any(), any()))
                .thenReturn(new AttributionService.AttributionResult(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "talent"));

        OrderSyncService.SyncResult result = orderSyncService.syncByTimeRange(100L, 200L);

        assertThat(result.pages()).isGreaterThanOrEqualTo(1);
    }

    // --- syncRange loop tests ---

    @Test
    void syncByTimeRange_catchesBusinessExceptionAndSkipsOrder() {
        when(valueOperations.setIfAbsent(eq("order:sync:lock"), eq("1"), any(Duration.class))).thenReturn(true);
        when(douyinOrderGateway.listSettlement(anyLong(), anyLong(), anyInt(), anyString()))
                .thenReturn(new DouyinOrderGateway.OrderListResult(Map.of("data", Map.of(
                        "order_list", List.of(
                                Map.of("order_id", "oid_good", "product_id", "pid", "create_time", 1710000000000L),
                                Map.of("order_id", "oid_bad", "product_id", "pid", "create_time", 1710000001000L)
                        ),
                        "has_more", false,
                        "cursor", 0L
                ))));
        when(attributionService.resolveAttribution(any(), any()))
                .thenReturn(new AttributionService.AttributionResult(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "talent"))
                .thenThrow(new BusinessException("归因失败"));

        OrderSyncService.SyncResult result = orderSyncService.syncByTimeRange(100L, 200L);

        assertThat(result.inserted()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(1);
    }

    @Test
    void syncByTimeRange_breaksLoopOnEmptyOrderList() {
        when(valueOperations.setIfAbsent(eq("order:sync:lock"), eq("1"), any(Duration.class))).thenReturn(true);
        when(douyinOrderGateway.listSettlement(anyLong(), anyLong(), anyInt(), anyString()))
                .thenReturn(new DouyinOrderGateway.OrderListResult(Map.of("data", Map.of(
                        "order_list", List.of(),
                        "has_more", true,
                        "cursor", 0L
                ))));

        OrderSyncService.SyncResult result = orderSyncService.syncByTimeRange(100L, 200L);

        assertThat(result.pages()).isEqualTo(1);
        verify(douyinOrderGateway, never()).listSettlement(anyLong(), anyLong(), anyInt(), eq("100"));
    }

    // --- syncByTimeRange parameter validation ---

    @Test
    void syncByTimeRange_throwsWhenStartTimeInvalid() {
        assertThatThrownBy(() -> orderSyncService.syncByTimeRange(0L, 200L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Invalid sync time range");
    }

    @Test
    void syncByTimeRange_throwsWhenEndTimeInvalid() {
        assertThatThrownBy(() -> orderSyncService.syncByTimeRange(100L, 0L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Invalid sync time range");
    }

    @Test
    void syncByTimeRange_throwsWhenStartGteEnd() {
        assertThatThrownBy(() -> orderSyncService.syncByTimeRange(300L, 200L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Invalid sync time range");
    }

    // --- firstNonBlank utility tests ---

    @Test
    void syncByTimeRange_mapOrder_firstNonBlank_usesSecondField() {
        when(valueOperations.setIfAbsent(eq("order:sync:lock"), eq("1"), any(Duration.class))).thenReturn(true);
        when(douyinOrderGateway.listSettlement(anyLong(), anyLong(), anyInt(), anyString()))
                .thenReturn(new DouyinOrderGateway.OrderListResult(Map.of("data", Map.of(
                        "order_list", List.of(
                                Map.of("order_id", "oid", "product_id", "pid", "title", "Real Title", "create_time", 1710000000000L)
                        ),
                        "has_more", false,
                        "cursor", 0L
                ))));
        when(attributionService.resolveAttribution(any(), any()))
                .thenReturn(new AttributionService.AttributionResult(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "talent"));

        OrderSyncService.SyncResult result = orderSyncService.syncByTimeRange(100L, 200L);

        assertThat(result.inserted()).isEqualTo(1);
        ArgumentCaptor<ColonelsettlementOrder> captor = ArgumentCaptor.forClass(ColonelsettlementOrder.class);
        verify(persistenceService).persistOrder(captor.capture());
        assertThat(captor.getValue().getProductName()).isEqualTo("Real Title");
    }
}