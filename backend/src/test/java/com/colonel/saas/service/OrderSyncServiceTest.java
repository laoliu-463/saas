package com.colonel.saas.service;

import com.colonel.saas.config.AppProperties;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.entity.SysUser;
import com.colonel.saas.gateway.douyin.DouyinOrderGateway;
import com.colonel.saas.job.JobLockKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P0-ORDER-001 OrderSyncService 单元测试，聚焦：
 * <ol>
 *   <li>PAY_RECENT 近窗口与默认增量窗口使用<strong>独立</strong>锁与 Redis 水位 key。</li>
 *   <li>两条路径都通过抖音 update 通道拉单，<strong>不存在</strong> pay 通道。</li>
 *   <li>PAY_RECENT 在锁冲突时返回 {@code locked=true} 且不持久化水位。</li>
 *   <li>PAY_RECENT 完成后写入独立 Redis key {@code order:sync:pay_recent_last_time}。</li>
 *   <li>正常完成时不覆盖增量同步的水位 {@code order:sync:last_time}。</li>
 * </ol>
 */
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
    @Mock
    private DistributedJobLockService jobLockService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private AppProperties appProperties;
    private OrderSyncService service;

    @BeforeEach
    void setUp() {
        appProperties = new AppProperties();
        appProperties.getTest().setEnabled(false);

        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        // Default: empty gateway response so syncItems exits immediately.
        lenient().when(douyinOrderGateway.listSettlement(any(DouyinOrderGateway.DouyinOrderQueryRequest.class)))
                .thenReturn(new DouyinOrderGateway.OrderListResult(List.of(), false, "0", Map.of()));
        lenient().when(douyinOrderGateway.listInstituteOrders(any(DouyinOrderGateway.DouyinOrderQueryRequest.class)))
                .thenReturn(new DouyinOrderGateway.OrderListResult(List.of(), false, "0", Map.of()));
        lenient().when(persistenceService.loadUsersByIds(any())).thenReturn(Map.<java.util.UUID, SysUser>of());

        service = new OrderSyncService(
                douyinOrderGateway,
                persistenceService,
                attributionService,
                redisTemplate,
                jobLockService,
                appProperties
        );
    }

    @Test
    void syncPayRecentWindow_shouldUseIndependentLockKey() {
        when(jobLockService.tryAcquireStrict(eq(JobLockKeys.ORDER_SYNC_PAY_RECENT), any(Duration.class)))
                .thenReturn(true);

        service.syncPayRecentWindow();

        // PAY_RECENT path acquires ORDER_SYNC_PAY_RECENT, never ORDER_SYNC
        verify(jobLockService).tryAcquireStrict(eq(JobLockKeys.ORDER_SYNC_PAY_RECENT), any(Duration.class));
        verify(jobLockService, never()).tryAcquireStrict(eq(JobLockKeys.ORDER_SYNC), any(Duration.class));
        verify(jobLockService).release(JobLockKeys.ORDER_SYNC_PAY_RECENT);
        verify(jobLockService, never()).release(JobLockKeys.ORDER_SYNC);
    }

    @Test
    void syncPayRecentWindow_shouldReturnLockedWithoutPersistingWaterlineWhenLockBusy() {
        when(jobLockService.tryAcquireStrict(eq(JobLockKeys.ORDER_SYNC_PAY_RECENT), any(Duration.class)))
                .thenReturn(false);

        OrderSyncService.SyncResult result = service.syncPayRecentWindow();

        assertThat(result.locked()).isTrue();
        // No gateway call when lock is busy
        verify(douyinOrderGateway, never()).listSettlement(any(DouyinOrderGateway.DouyinOrderQueryRequest.class));
        // No Redis write when lock is busy
        verify(valueOperations, never()).set(anyString(), any());
    }

    @Test
    void syncPayRecentWindow_shouldPersistWaterlineToPayRecentKeyOnly() {
        when(jobLockService.tryAcquireStrict(eq(JobLockKeys.ORDER_SYNC_PAY_RECENT), any(Duration.class)))
                .thenReturn(true);

        service.syncPayRecentWindow();

        // PAY_RECENT persistence only touches the pay_recent_last_time key, never the default last_time key
        verify(valueOperations).set(eq("order:sync:pay_recent_last_time"), any());
        verify(valueOperations, never()).set(eq("order:sync:last_time"), any());
    }

    @Test
    void syncPayRecentWindow_shouldUseSixHourWindow() {
        when(jobLockService.tryAcquireStrict(eq(JobLockKeys.ORDER_SYNC_PAY_RECENT), any(Duration.class)))
                .thenReturn(true);

        service.syncPayRecentWindow();

        ArgumentCaptor<DouyinOrderGateway.DouyinOrderQueryRequest> captor =
                ArgumentCaptor.forClass(DouyinOrderGateway.DouyinOrderQueryRequest.class);
        verify(douyinOrderGateway).listSettlement(captor.capture());
        DouyinOrderGateway.DouyinOrderQueryRequest request = captor.getValue();
        long windowSeconds = request.endTime() - request.startTime();
        // PAY_RECENT window = 6 hours = 21600 seconds; allow ±60s drift for clock jitter
        assertThat(windowSeconds).isBetween(6L * 60L * 60L - 60L, 6L * 60L * 60L + 60L);
    }

    @Test
    void syncByTimeRange_shouldUseDefaultIncrementalLockAndKey() {
        when(jobLockService.tryAcquireStrict(eq(JobLockKeys.ORDER_SYNC), any(Duration.class)))
                .thenReturn(true);

        long now = java.time.Instant.now().getEpochSecond();
        service.syncByTimeRange(now - 600, now);

        // Default incremental path uses ORDER_SYNC, never PAY_RECENT
        verify(jobLockService).tryAcquireStrict(eq(JobLockKeys.ORDER_SYNC), any(Duration.class));
        verify(jobLockService, never()).tryAcquireStrict(eq(JobLockKeys.ORDER_SYNC_PAY_RECENT), any(Duration.class));
        // Default incremental writes to order:sync:last_time, never pay_recent key
        verify(valueOperations).set(eq("order:sync:last_time"), any());
        verify(valueOperations, never()).set(eq("order:sync:pay_recent_last_time"), any());
    }

    @Test
    void syncPayRecentAndIncremental_shouldNotShareWaterline() {
        // PAY_RECENT runs first
        when(jobLockService.tryAcquireStrict(eq(JobLockKeys.ORDER_SYNC_PAY_RECENT), any(Duration.class)))
                .thenReturn(true);
        service.syncPayRecentWindow();

        // Then incremental runs in same process
        when(jobLockService.tryAcquireStrict(eq(JobLockKeys.ORDER_SYNC), any(Duration.class)))
                .thenReturn(true);
        long now = java.time.Instant.now().getEpochSecond();
        service.syncByTimeRange(now - 600, now);

        // Each path writes exactly its own key, never the other
        verify(valueOperations, times(1)).set(eq("order:sync:pay_recent_last_time"), any());
        verify(valueOperations, times(1)).set(eq("order:sync:last_time"), any());
    }

    @Test
    void syncInstituteOrdersRecentWindow_shouldUseIndependentLockKey() {
        when(jobLockService.tryAcquireStrict(eq(JobLockKeys.ORDER_SYNC_INSTITUTE), any(Duration.class)))
                .thenReturn(true);

        service.syncInstituteOrdersRecentWindow();

        // INSTITUTE path acquires ORDER_SYNC_INSTITUTE, never ORDER_SYNC or PAY_RECENT
        verify(jobLockService).tryAcquireStrict(eq(JobLockKeys.ORDER_SYNC_INSTITUTE), any(Duration.class));
        verify(jobLockService, never()).tryAcquireStrict(eq(JobLockKeys.ORDER_SYNC), any(Duration.class));
        verify(jobLockService, never()).tryAcquireStrict(eq(JobLockKeys.ORDER_SYNC_PAY_RECENT), any(Duration.class));
        verify(jobLockService).release(JobLockKeys.ORDER_SYNC_INSTITUTE);
    }

    @Test
    void syncInstituteOrdersRecentWindow_shouldReturnLockedWhenBusy() {
        when(jobLockService.tryAcquireStrict(eq(JobLockKeys.ORDER_SYNC_INSTITUTE), any(Duration.class)))
                .thenReturn(false);

        OrderSyncService.SyncResult result = service.syncInstituteOrdersRecentWindow();

        assertThat(result.locked()).isTrue();
        verify(douyinOrderGateway, never()).listInstituteOrders(any(DouyinOrderGateway.DouyinOrderQueryRequest.class));
        verify(valueOperations, never()).set(eq("order:sync:institute_recent_last_time"), any());
    }

    @Test
    void syncInstituteOrdersRecentWindow_shouldPersistWaterlineToInstituteKeyOnly() {
        when(jobLockService.tryAcquireStrict(eq(JobLockKeys.ORDER_SYNC_INSTITUTE), any(Duration.class)))
                .thenReturn(true);

        service.syncInstituteOrdersRecentWindow();

        verify(valueOperations).set(eq("order:sync:institute_recent_last_time"), any());
        verify(valueOperations, never()).set(eq("order:sync:last_time"), any());
        verify(valueOperations, never()).set(eq("order:sync:pay_recent_last_time"), any());
    }

    @Test
    void syncInstituteOrdersRecentWindow_shouldUseTwentyFourHourWindow() {
        when(jobLockService.tryAcquireStrict(eq(JobLockKeys.ORDER_SYNC_INSTITUTE), any(Duration.class)))
                .thenReturn(true);

        service.syncInstituteOrdersRecentWindow();

        ArgumentCaptor<DouyinOrderGateway.DouyinOrderQueryRequest> captor =
                ArgumentCaptor.forClass(DouyinOrderGateway.DouyinOrderQueryRequest.class);
        verify(douyinOrderGateway).listInstituteOrders(captor.capture());
        DouyinOrderGateway.DouyinOrderQueryRequest request = captor.getValue();
        long windowSeconds = request.endTime() - request.startTime();
        assertThat(windowSeconds).isBetween(24L * 60L * 60L - 60L, 24L * 60L * 60L + 60L);
    }

    @Test
    void syncInstituteOrdersRecentWindow_shouldCallListInstituteOrdersNotListSettlement() {
        when(jobLockService.tryAcquireStrict(eq(JobLockKeys.ORDER_SYNC_INSTITUTE), any(Duration.class)))
                .thenReturn(true);

        service.syncInstituteOrdersRecentWindow();

        // INSTITUTE uses listInstituteOrders (6468), never listSettlement (2704)
        verify(douyinOrderGateway).listInstituteOrders(any(DouyinOrderGateway.DouyinOrderQueryRequest.class));
        verify(douyinOrderGateway, never()).listSettlement(any(DouyinOrderGateway.DouyinOrderQueryRequest.class));
    }

    @Test
    void readInstituteLastSyncTime_shouldReturnZeroWhenRedisEmpty() {
        when(valueOperations.get("order:sync:institute_recent_last_time")).thenReturn(null);

        long result = service.readInstituteLastSyncTime();

        assertThat(result).isEqualTo(0L);
    }

    @Test
    void readInstituteLastSyncTime_shouldParseStoredValue() {
        when(valueOperations.get("order:sync:institute_recent_last_time")).thenReturn("1780480000");

        long result = service.readInstituteLastSyncTime();

        assertThat(result).isEqualTo(1780480000L);
    }

    @Test
    void syncInstituteOrdersRecentWindow_shouldPersistFactAndEstimateTrackOnly() {
        when(jobLockService.tryAcquireStrict(eq(JobLockKeys.ORDER_SYNC_INSTITUTE), any(Duration.class)))
                .thenReturn(true);
        when(douyinOrderGateway.listInstituteOrders(any(DouyinOrderGateway.DouyinOrderQueryRequest.class)))
                .thenReturn(new DouyinOrderGateway.OrderListResult(List.of(instituteOrderItem()), false, "0", Map.of()));
        when(attributionService.resolveAttribution(any(ColonelsettlementOrder.class), any()))
                .thenReturn(AttributionService.AttributionResult.unattributed(
                        null,
                        null,
                        "3859423",
                        null,
                        AttributionService.REASON_NO_PICK_SOURCE
                ));
        when(persistenceService.persistOrder(any(ColonelsettlementOrder.class))).thenReturn(true);

        OrderSyncService.SyncResult result = service.syncInstituteOrdersRecentWindow();

        assertThat(result.created()).isEqualTo(1);
        ArgumentCaptor<ColonelsettlementOrder> orderCaptor = ArgumentCaptor.forClass(ColonelsettlementOrder.class);
        verify(persistenceService).persistOrder(orderCaptor.capture());
        ColonelsettlementOrder order = orderCaptor.getValue();
        assertThat(order.getOrderId()).isEqualTo("6953395247297468025");
        assertThat(order.getOrderAmount()).isEqualTo(2550L);
        assertThat(order.getActualAmount()).isEqualTo(2550L);
        assertThat(order.getEstimateServiceFee()).isEqualTo(55L);
        assertThat(order.getEstimateTechServiceFee()).isEqualTo(7L);
        // INSTITUTE writes fact/estimate only; settlement/effective track is left for 2704.
        assertThat(order.getSettleAmount()).isNull();
        assertThat(order.getEffectiveServiceFee()).isNull();
        assertThat(order.getEffectiveTechServiceFee()).isNull();
        assertThat(order.getSettleTime()).isNull();
        assertThat(order.getPayTime()).isNotNull();
        assertThat(order.getOrderCreateTime()).isNotNull();
        assertThat(order.getSyncSource()).isEqualTo(OrderSyncPersistenceService.SYNC_SOURCE_INSTITUTE);
    }

    private DouyinOrderGateway.DouyinOrderItem instituteOrderItem() {
        Map<String, Object> rawPayload = new LinkedHashMap<>();
        rawPayload.put("order_id", "6953395247297468025");
        rawPayload.put("product_id", "3810562766247428542");
        rawPayload.put("product_name", "天淇巧仁派对巧克力坚果冰淇淋");
        rawPayload.put("shop_id", "56591058");
        rawPayload.put("shop_name", "冰戈的冷饮店");
        rawPayload.put("author_buyin_id", "7137334329718292775");
        rawPayload.put("author_account", "小张张张");
        rawPayload.put("pay_goods_amount", 2550L);
        rawPayload.put("pay_success_time", "2026-06-03 17:50:34");
        rawPayload.put("create_time", "2026-06-03 17:49:01");
        rawPayload.put("flow_point", "PAY_SUCC");
        rawPayload.put("colonel_order_info", Map.of(
                "activity_id", "3859423",
                "estimated_commission", 55L,
                "real_commission", 0L,
                "tech_service_fee", 7L
        ));
        return new DouyinOrderGateway.DouyinOrderItem(
                "6953395247297468025",
                "3810562766247428542",
                "3810562766247428542",
                "56591058",
                "冰戈的冷饮店",
                "7137334329718292775",
                "小张张张",
                null,
                2550L,
                55L,
                1,
                1780480234L,
                null,
                rawPayload
        );
    }
}
