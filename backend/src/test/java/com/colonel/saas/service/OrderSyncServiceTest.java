package com.colonel.saas.service;

import com.colonel.saas.common.time.AppZone;
import com.colonel.saas.config.AppProperties;
import com.colonel.saas.config.DddRefactorProperties;
import com.colonel.saas.domain.order.application.OrderAmountMappingRouter;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.gateway.douyin.DouyinOrderGateway;
import com.colonel.saas.job.JobLockKeys;
import com.colonel.saas.service.settlement.SettlementOrderGateway;
import com.colonel.saas.service.settlement.SettlementOrderPage;
import com.colonel.saas.service.settlement.SettlementOrderQuery;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Mock
    private DouyinOrderGateway douyinOrderGateway;
    @Mock
    private SettlementOrderGateway instituteSettlementGateway;
    @Mock
    private SettlementOrderGateway multiSettlementFallbackGateway;
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
    private OrderAmountMappingRouter orderAmountMappingRouter;
    private OrderSyncService service;

    @BeforeEach
    void setUp() {
        appProperties = new AppProperties();
        appProperties.getTest().setEnabled(false);
        orderAmountMappingRouter = new OrderAmountMappingRouter(new DddRefactorProperties());

        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        // Default: empty gateway response so syncItems exits immediately.
        lenient().when(douyinOrderGateway.listSettlement(any(DouyinOrderGateway.DouyinOrderQueryRequest.class)))
                .thenReturn(new DouyinOrderGateway.OrderListResult(List.of(), false, "0", Map.of()));
        lenient().when(douyinOrderGateway.listInstituteOrders(any(DouyinOrderGateway.DouyinOrderQueryRequest.class)))
                .thenReturn(new DouyinOrderGateway.OrderListResult(List.of(), false, "0", Map.of()));
        lenient().when(instituteSettlementGateway.fetch(any()))
                .thenReturn(new SettlementOrderPage(List.of(), "0", false,
                        com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode(),
                        "buyin.instituteOrderColonel", OrderSyncPersistenceService.SYNC_SOURCE_INSTITUTE_SETTLEMENT));
        lenient().when(multiSettlementFallbackGateway.fetch(any()))
                .thenReturn(new SettlementOrderPage(List.of(), "0", false,
                        com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode(),
                        "buyin.colonelMultiSettlementOrders", OrderSyncPersistenceService.SYNC_SOURCE_SETTLEMENT));
        lenient().when(persistenceService.loadUserNamesByIds(any())).thenReturn(Map.<java.util.UUID, String>of());

        service = new OrderSyncService(
                douyinOrderGateway,
                instituteSettlementGateway,
                multiSettlementFallbackGateway,
                persistenceService,
                attributionService,
                redisTemplate,
                jobLockService,
                appProperties,
                orderAmountMappingRouter
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

        ArgumentCaptor<SettlementOrderQuery> captor = ArgumentCaptor.forClass(SettlementOrderQuery.class);
        verify(instituteSettlementGateway).fetch(captor.capture());
        verify(douyinOrderGateway, never()).listSettlement(any(DouyinOrderGateway.DouyinOrderQueryRequest.class));
        SettlementOrderQuery request = captor.getValue();
        long windowSeconds = toEpochSecond(request.endTime()) - toEpochSecond(request.startTime());
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
    void syncInstituteOrdersRecentWindow_shouldUseFullWindowWhenNoWaterline() {
        when(jobLockService.tryAcquireStrict(eq(JobLockKeys.ORDER_SYNC_INSTITUTE), any(Duration.class)))
                .thenReturn(true);
        when(valueOperations.get("order:sync:institute_recent_last_time")).thenReturn(null);

        service.syncInstituteOrdersRecentWindow();

        ArgumentCaptor<DouyinOrderGateway.DouyinOrderQueryRequest> captor =
                ArgumentCaptor.forClass(DouyinOrderGateway.DouyinOrderQueryRequest.class);
        verify(douyinOrderGateway).listInstituteOrders(captor.capture());
        DouyinOrderGateway.DouyinOrderQueryRequest request = captor.getValue();
        long windowSeconds = request.endTime() - request.startTime();
        assertThat(windowSeconds).isBetween(24L * 60L * 60L - 60L, 24L * 60L * 60L + 60L);
    }

    @Test
    void syncInstituteOrdersRecentWindow_shouldUseIncrementalWindowWhenWaterlineExists() {
        when(jobLockService.tryAcquireStrict(eq(JobLockKeys.ORDER_SYNC_INSTITUTE), any(Duration.class)))
                .thenReturn(true);
        long endTime = java.time.Instant.now().getEpochSecond() - 30L;
        when(valueOperations.get("order:sync:institute_recent_last_time"))
                .thenReturn(String.valueOf(endTime - 180L));

        service.syncInstituteOrdersRecentWindow();

        ArgumentCaptor<DouyinOrderGateway.DouyinOrderQueryRequest> captor =
                ArgumentCaptor.forClass(DouyinOrderGateway.DouyinOrderQueryRequest.class);
        verify(douyinOrderGateway).listInstituteOrders(captor.capture());
        DouyinOrderGateway.DouyinOrderQueryRequest request = captor.getValue();
        long windowSeconds = request.endTime() - request.startTime();
        assertThat(windowSeconds).isLessThan(15L * 60L);
    }

    @Test
    void syncInstituteFullBackfillWindow_shouldUseTwentyFourHourWindow() {
        when(jobLockService.tryAcquireStrict(eq(JobLockKeys.ORDER_SYNC_INSTITUTE), any(Duration.class)))
                .thenReturn(true);

        service.syncInstituteFullBackfillWindow();

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
        // PAY_SUCC 待结算样本：6468 只写事实/预估轨，结算轨保持 null（见 SETTLE 样本测试）。
        assertThat(order.getSettleAmount()).isNull();
        assertThat(order.getEffectiveServiceFee()).isNull();
        assertThat(order.getEffectiveTechServiceFee()).isNull();
        assertThat(order.getSettleTime()).isNull();
        assertThat(order.getPayTime()).isNotNull();
        assertThat(order.getOrderCreateTime()).isNotNull();
        assertThat(order.getSyncSource()).isEqualTo(OrderSyncPersistenceService.SYNC_SOURCE_INSTITUTE);
    }

    @Test
    void syncInstituteOrdersRecentWindow_shouldContinueWhenDataCursorExistsDespiteHasMoreFalse() {
        when(jobLockService.tryAcquireStrict(eq(JobLockKeys.ORDER_SYNC_INSTITUTE), any(Duration.class)))
                .thenReturn(true);
        when(douyinOrderGateway.listInstituteOrders(any(DouyinOrderGateway.DouyinOrderQueryRequest.class)))
                .thenReturn(
                        pageWithRawCursor(List.of(instituteOrderItem("ORDER-1")), false, null, Map.of("data", Map.of("cursor", "cursor-2"))),
                        pageWithRawCursor(List.of(instituteOrderItem("ORDER-2")), false, null, Map.of("data", Map.of("cursor", "0")))
                );
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

        assertThat(result.pages()).isEqualTo(2);
        assertThat(result.totalFetched()).isEqualTo(2);
        assertThat(result.uniqueOrders()).isEqualTo(2);
        assertThat(result.created()).isEqualTo(2);
        assertThat(result.stopReason()).isEqualTo("NO_NEXT_CURSOR");

        ArgumentCaptor<DouyinOrderGateway.DouyinOrderQueryRequest> requestCaptor =
                ArgumentCaptor.forClass(DouyinOrderGateway.DouyinOrderQueryRequest.class);
        verify(douyinOrderGateway, times(2)).listInstituteOrders(requestCaptor.capture());
        assertThat(requestCaptor.getAllValues().get(0).cursor()).isEqualTo("0");
        assertThat(requestCaptor.getAllValues().get(1).cursor()).isEqualTo("cursor-2");
    }

    @Test
    void syncInstituteOrdersRecentWindow_shouldContinueWhenHasMoreFalseButNextCursorExists() {
        when(jobLockService.tryAcquireStrict(eq(JobLockKeys.ORDER_SYNC_INSTITUTE), any(Duration.class)))
                .thenReturn(true);
        when(douyinOrderGateway.listInstituteOrders(any(DouyinOrderGateway.DouyinOrderQueryRequest.class)))
                .thenReturn(
                        pageWithRawCursor(List.of(instituteOrderItem("ORDER-1")), false, "cursor-2", Map.of()),
                        pageWithRawCursor(List.of(instituteOrderItem("ORDER-2")), false, "0", Map.of())
                );
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

        assertThat(result.pages()).isEqualTo(2);
        assertThat(result.totalFetched()).isEqualTo(2);
        assertThat(result.created()).isEqualTo(2);
        assertThat(result.stopReason()).isEqualTo("NO_NEXT_CURSOR");
    }

    @Test
    void syncInstituteOrdersRecentWindow_shouldStopWhenCursorRepeats() {
        when(jobLockService.tryAcquireStrict(eq(JobLockKeys.ORDER_SYNC_INSTITUTE), any(Duration.class)))
                .thenReturn(true);
        when(douyinOrderGateway.listInstituteOrders(any(DouyinOrderGateway.DouyinOrderQueryRequest.class)))
                .thenReturn(
                        pageWithRawCursor(List.of(instituteOrderItem("ORDER-1")), false, "cursor-2", Map.of()),
                        pageWithRawCursor(List.of(instituteOrderItem("ORDER-2")), false, "cursor-2", Map.of())
                );
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

        assertThat(result.pages()).isEqualTo(2);
        assertThat(result.stopReason()).isEqualTo("DUPLICATE_CURSOR");
        verify(douyinOrderGateway, times(2)).listInstituteOrders(any(DouyinOrderGateway.DouyinOrderQueryRequest.class));
    }

    @Test
    void syncInstituteOrdersRecentWindow_shouldStopWhenMaxPagesReached() {
        ReflectionTestUtils.setField(service, "maxPages", 2);
        when(jobLockService.tryAcquireStrict(eq(JobLockKeys.ORDER_SYNC_INSTITUTE), any(Duration.class)))
                .thenReturn(true);
        AtomicInteger counter = new AtomicInteger();
        when(douyinOrderGateway.listInstituteOrders(any(DouyinOrderGateway.DouyinOrderQueryRequest.class)))
                .thenAnswer(invocation -> {
                    int pageNo = counter.incrementAndGet();
                    return pageWithRawCursor(
                            List.of(instituteOrderItem("ORDER-" + pageNo)),
                            false,
                            "cursor-" + (pageNo + 1),
                            Map.of()
                    );
                });
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

        assertThat(result.pages()).isEqualTo(2);
        assertThat(result.stopReason()).isEqualTo("MAX_PAGES");
        verify(douyinOrderGateway, times(2)).listInstituteOrders(any(DouyinOrderGateway.DouyinOrderQueryRequest.class));
    }

    @Test
    void syncInstituteOrdersHotRecent_shouldUseIndependentHotLockKey() {
        when(jobLockService.tryAcquireStrict(eq(JobLockKeys.ORDER_SYNC_INSTITUTE_HOT), any(Duration.class)))
                .thenReturn(true);

        service.syncInstituteOrdersHotRecent();

        verify(jobLockService).tryAcquireStrict(eq(JobLockKeys.ORDER_SYNC_INSTITUTE_HOT), any(Duration.class));
        verify(jobLockService, never()).tryAcquireStrict(eq(JobLockKeys.ORDER_SYNC_INSTITUTE), any(Duration.class));
        verify(jobLockService).release(JobLockKeys.ORDER_SYNC_INSTITUTE_HOT);
    }

    @Test
    void syncInstituteOrdersHotRecent_shouldReturnLockedWithoutPersistingWhenBusy() {
        when(jobLockService.tryAcquireStrict(eq(JobLockKeys.ORDER_SYNC_INSTITUTE_HOT), any(Duration.class)))
                .thenReturn(false);

        OrderSyncService.SyncResult result = service.syncInstituteOrdersHotRecent();

        assertThat(result.locked()).isTrue();
        verify(douyinOrderGateway, never()).listInstituteOrders(any(DouyinOrderGateway.DouyinOrderQueryRequest.class));
        verify(valueOperations, never()).set(eq("order:sync:institute_hot_last_time"), any());
    }

    @Test
    void syncInstituteOrdersHotRecent_shouldPersistHotWaterlineOnlyOnSuccess() {
        when(jobLockService.tryAcquireStrict(eq(JobLockKeys.ORDER_SYNC_INSTITUTE_HOT), any(Duration.class)))
                .thenReturn(true);

        service.syncInstituteOrdersHotRecent();

        verify(valueOperations).set(eq("order:sync:institute_hot_last_time"), any());
        verify(valueOperations, never()).set(eq("order:sync:institute_recent_last_time"), any());
    }

    @Test
    void syncInstituteOrdersHotRecent_shouldNotPersistWaterlineOnFailure() {
        when(jobLockService.tryAcquireStrict(eq(JobLockKeys.ORDER_SYNC_INSTITUTE_HOT), any(Duration.class)))
                .thenReturn(true);
        when(douyinOrderGateway.listInstituteOrders(any(DouyinOrderGateway.DouyinOrderQueryRequest.class)))
                .thenThrow(new RuntimeException("upstream failed"));

        assertThatThrownBy(() -> service.syncInstituteOrdersHotRecent())
                .isInstanceOf(RuntimeException.class)
                .hasMessage("upstream failed");

        verify(valueOperations, never()).set(eq("order:sync:institute_hot_last_time"), any());
    }

    @Test
    void resolveInstituteHotStartTime_shouldUseWindowWhenNoWaterline() {
        long endTime = java.time.Instant.now().getEpochSecond() - 30L;
        when(valueOperations.get("order:sync:institute_hot_last_time")).thenReturn(null);

        long startTime = service.resolveInstituteHotStartTime(endTime);

        long windowSeconds = endTime - startTime;
        assertThat(windowSeconds).isBetween(300L - 5L, 300L + 5L);
    }

    @Test
    void resolveInstituteHotStartTime_shouldUseOverlapWhenWaterlineExists() {
        long endTime = java.time.Instant.now().getEpochSecond() - 30L;
        long lastHot = endTime - 60L;
        when(valueOperations.get("order:sync:institute_hot_last_time"))
                .thenReturn(String.valueOf(lastHot));

        long startTime = service.resolveInstituteHotStartTime(endTime);

        assertThat(startTime).isEqualTo(lastHot - 120L);
    }

    @Test
    void syncInstituteOrdersHotRecent_shouldUseHotLagNotGlobalLag() {
        ReflectionTestUtils.setField(service, "lagSeconds", 60L);
        when(jobLockService.tryAcquireStrict(eq(JobLockKeys.ORDER_SYNC_INSTITUTE_HOT), any(Duration.class)))
                .thenReturn(true);

        service.syncInstituteOrdersHotRecent();

        ArgumentCaptor<DouyinOrderGateway.DouyinOrderQueryRequest> captor =
                ArgumentCaptor.forClass(DouyinOrderGateway.DouyinOrderQueryRequest.class);
        verify(douyinOrderGateway).listInstituteOrders(captor.capture());
        long now = java.time.Instant.now().getEpochSecond();
        long endLag = now - captor.getValue().endTime();
        assertThat(endLag).isBetween(25L, 35L);
    }

    @Test
    void syncInstituteOrdersHotRecent_shouldStopWhenHotMaxPagesReached() {
        ReflectionTestUtils.setField(service, "instituteHotMaxPages", 2);
        when(jobLockService.tryAcquireStrict(eq(JobLockKeys.ORDER_SYNC_INSTITUTE_HOT), any(Duration.class)))
                .thenReturn(true);
        AtomicInteger counter = new AtomicInteger();
        when(douyinOrderGateway.listInstituteOrders(any(DouyinOrderGateway.DouyinOrderQueryRequest.class)))
                .thenAnswer(invocation -> {
                    int pageNo = counter.incrementAndGet();
                    return pageWithRawCursor(
                            List.of(instituteOrderItem("HOT-ORDER-" + pageNo)),
                            false,
                            "cursor-" + (pageNo + 1),
                            Map.of()
                    );
                });
        when(attributionService.resolveAttribution(any(ColonelsettlementOrder.class), any()))
                .thenReturn(AttributionService.AttributionResult.unattributed(
                        null,
                        null,
                        "3859423",
                        null,
                        AttributionService.REASON_NO_PICK_SOURCE
                ));
        when(persistenceService.persistOrder(any(ColonelsettlementOrder.class))).thenReturn(true);

        OrderSyncService.SyncResult result = service.syncInstituteOrdersHotRecent();

        assertThat(result.pages()).isEqualTo(2);
        assertThat(result.stopReason()).isEqualTo("MAX_PAGES");
        verify(douyinOrderGateway, times(2)).listInstituteOrders(any(DouyinOrderGateway.DouyinOrderQueryRequest.class));
    }

    @Test
    void syncInstituteOrdersRecentWindow_shouldUpsertDuplicateOrderOnlyOnceAcrossPages() {
        when(jobLockService.tryAcquireStrict(eq(JobLockKeys.ORDER_SYNC_INSTITUTE), any(Duration.class)))
                .thenReturn(true);
        when(douyinOrderGateway.listInstituteOrders(any(DouyinOrderGateway.DouyinOrderQueryRequest.class)))
                .thenReturn(
                        pageWithRawCursor(List.of(instituteOrderItem("ORDER-DUP")), false, "cursor-2", Map.of()),
                        pageWithRawCursor(List.of(instituteOrderItem("ORDER-DUP")), false, "0", Map.of())
                );
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

        assertThat(result.pages()).isEqualTo(2);
        assertThat(result.totalFetched()).isEqualTo(2);
        assertThat(result.uniqueOrders()).isEqualTo(1);
        assertThat(result.created()).isEqualTo(1);
        verify(persistenceService, times(1)).persistOrder(any(ColonelsettlementOrder.class));
    }

    private DouyinOrderGateway.DouyinOrderItem instituteOrderItem() {
        return instituteOrderItem("6953395247297468025");
    }

    private DouyinOrderGateway.OrderListResult pageWithRawCursor(
            List<DouyinOrderGateway.DouyinOrderItem> orders,
            boolean hasMore,
            String nextCursor,
            Map<String, Object> rawResponse) {
        return new DouyinOrderGateway.OrderListResult(orders, hasMore, nextCursor, rawResponse);
    }

    private SettlementOrderPage settlementPage(
            List<DouyinOrderGateway.DouyinOrderItem> orders,
            String nextCursor,
            Map<String, Object> rawResponse) {
        List<JsonNode> nodes = orders.stream()
                .map(DouyinOrderGateway.DouyinOrderItem::rawPayload)
                .map(payload -> (JsonNode) OBJECT_MAPPER.valueToTree(payload))
                .toList();
        return new SettlementOrderPage(
                nodes,
                nextCursor,
                false,
                OBJECT_MAPPER.valueToTree(rawResponse),
                "buyin.instituteOrderColonel",
                OrderSyncPersistenceService.SYNC_SOURCE_INSTITUTE_SETTLEMENT);
    }

    private long toEpochSecond(String dateTime) {
        return AppZone.toEpochSecond(LocalDateTime.parse(dateTime, DATE_TIME_FORMATTER));
    }

    @Test
    void syncPayRecentWindow_shouldPassConfiguredInstituteSettlementTimeType() {
        when(jobLockService.tryAcquireStrict(eq(JobLockKeys.ORDER_SYNC_PAY_RECENT), any(Duration.class)))
                .thenReturn(true);

        service.syncPayRecentWindow();

        ArgumentCaptor<SettlementOrderQuery> captor = ArgumentCaptor.forClass(SettlementOrderQuery.class);
        verify(instituteSettlementGateway).fetch(captor.capture());
        verify(douyinOrderGateway, never()).listSettlement(any(DouyinOrderGateway.DouyinOrderQueryRequest.class));
        assertThat(captor.getValue().timeType()).isEqualTo("settle");
    }

    @Test
    void syncSettlementSettleWindow_shouldUseIndependentLockAndCheckpointKey() {
        when(jobLockService.tryAcquireStrict(eq(JobLockKeys.ORDER_SYNC_SETTLE), any(Duration.class)))
                .thenReturn(true);

        service.syncSettlementSettleWindow();

        verify(jobLockService).tryAcquireStrict(eq(JobLockKeys.ORDER_SYNC_SETTLE), any(Duration.class));
        verify(jobLockService, never()).tryAcquireStrict(eq(JobLockKeys.ORDER_SYNC), any(Duration.class));
        verify(valueOperations, never()).set(eq("order:sync:settle_last_time"), any());
    }

    @Test
    void syncSettlementSettleWindow_shouldPassSettleTimeType() {
        when(jobLockService.tryAcquireStrict(eq(JobLockKeys.ORDER_SYNC_SETTLE), any(Duration.class)))
                .thenReturn(true);

        service.syncSettlementSettleWindow();

        ArgumentCaptor<SettlementOrderQuery> captor = ArgumentCaptor.forClass(SettlementOrderQuery.class);
        verify(instituteSettlementGateway).fetch(captor.capture());
        verify(multiSettlementFallbackGateway, never()).fetch(any());
        verify(douyinOrderGateway, never()).listSettlement(any(DouyinOrderGateway.DouyinOrderQueryRequest.class));
        assertThat(captor.getValue().timeType()).isEqualTo("settle");
    }

    @Test
    void syncSettlementSettleWindow_shouldUse2704OnlyWhenConfiguredAsFallbackSource() {
        ReflectionTestUtils.setField(service, "settlementSource", "colonelMultiSettlementOrders");
        when(jobLockService.tryAcquireStrict(eq(JobLockKeys.ORDER_SYNC_SETTLE), any(Duration.class)))
                .thenReturn(true);

        service.syncSettlementSettleWindow();

        verify(multiSettlementFallbackGateway).fetch(any(SettlementOrderQuery.class));
        verify(instituteSettlementGateway, never()).fetch(any());
    }

    @Test
    void syncInstituteOrdersRecentWindow_shouldWriteSettlementWhen6468Settled() {
        when(jobLockService.tryAcquireStrict(eq(JobLockKeys.ORDER_SYNC_INSTITUTE), any(Duration.class)))
                .thenReturn(true);
        when(douyinOrderGateway.listInstituteOrders(any(DouyinOrderGateway.DouyinOrderQueryRequest.class)))
                .thenReturn(new DouyinOrderGateway.OrderListResult(
                        List.of(instituteSettledOrderItem()), false, "0", Map.of()));
        when(attributionService.resolveAttribution(any(ColonelsettlementOrder.class), any()))
                .thenReturn(AttributionService.AttributionResult.unattributed(
                        null, null, "3859423", null, AttributionService.REASON_NO_PICK_SOURCE));
        when(persistenceService.persistOrder(any(ColonelsettlementOrder.class))).thenReturn(true);

        OrderSyncService.SyncResult result = service.syncInstituteOrdersRecentWindow();

        assertThat(result.created()).isEqualTo(1);
        ArgumentCaptor<ColonelsettlementOrder> orderCaptor = ArgumentCaptor.forClass(ColonelsettlementOrder.class);
        verify(persistenceService).persistOrder(orderCaptor.capture());
        ColonelsettlementOrder order = orderCaptor.getValue();
        assertThat(order.getOrderAmount()).isEqualTo(1680L);
        assertThat(order.getSettleAmount()).isEqualTo(1680L);
        assertThat(order.getEffectiveServiceFee()).isEqualTo(30L);
        assertThat(order.getSettleTime()).isNotNull();
        assertThat(order.getSyncSource()).isEqualTo(OrderSyncPersistenceService.SYNC_SOURCE_INSTITUTE);
    }

    @Test
    void syncSettlementSettleWindow_shouldNotPersistOrdersWhenUpstreamEmpty() {
        when(jobLockService.tryAcquireStrict(eq(JobLockKeys.ORDER_SYNC_SETTLE), any(Duration.class)))
                .thenReturn(true);

        service.syncSettlementSettleWindow();

        verify(persistenceService, never()).persistOrder(any());
    }

    @Test
    void syncSettlementSettleWindow_shouldPersistInstituteSettlementFields() {
        when(jobLockService.tryAcquireStrict(eq(JobLockKeys.ORDER_SYNC_SETTLE), any(Duration.class)))
                .thenReturn(true);
        DouyinOrderGateway.DouyinOrderItem item = settlementOrderItemWithPhase("SETTLE_PHASE_1", "phase-99");
        when(instituteSettlementGateway.fetch(any(SettlementOrderQuery.class)))
                .thenReturn(settlementPage(List.of(item), "0", Map.of("log_id", "log-settle-1603")));
        when(attributionService.resolveAttribution(any(ColonelsettlementOrder.class), any()))
                .thenReturn(AttributionService.AttributionResult.unattributed(
                        null, null, "3859423", null, AttributionService.REASON_NO_PICK_SOURCE));
        when(persistenceService.persistOrder(any())).thenReturn(true);

        service.syncSettlementSettleWindow();

        ArgumentCaptor<ColonelsettlementOrder> captor = ArgumentCaptor.forClass(ColonelsettlementOrder.class);
        verify(persistenceService).persistOrder(captor.capture());
        ColonelsettlementOrder order = captor.getValue();
        assertThat(order.getPhaseId()).isEqualTo("phase-99");
        assertThat(order.getSettleAmount()).isEqualTo(2480L);
        assertThat(order.getEffectiveServiceFee()).isEqualTo(50L);
        assertThat(order.getEffectiveTechServiceFee()).isEqualTo(6L);
        assertThat(order.getFlowPoint()).isEqualTo("SETTLE");
        assertThat(order.getSyncSource()).isEqualTo(OrderSyncPersistenceService.SYNC_SOURCE_INSTITUTE_SETTLEMENT);
    }

    @Test
    void syncSettlementSettleWindow_shouldNotAdvanceCheckpointWhenUpstreamEmpty() {
        when(jobLockService.tryAcquireStrict(eq(JobLockKeys.ORDER_SYNC_SETTLE), any(Duration.class)))
                .thenReturn(true);

        service.syncSettlementSettleWindow();

        verify(valueOperations, never()).set(eq("order:sync:settle_last_time"), any());
    }

    @Test
    void syncSettlementSettleWindow_shouldAdvanceCheckpointWhenOrdersFetched() {
        when(jobLockService.tryAcquireStrict(eq(JobLockKeys.ORDER_SYNC_SETTLE), any(Duration.class)))
                .thenReturn(true);
        DouyinOrderGateway.DouyinOrderItem item = instituteOrderItem("SETTLE_ORDER_1");
        when(instituteSettlementGateway.fetch(any(SettlementOrderQuery.class)))
                .thenReturn(settlementPage(List.of(item), "0", Map.of("log_id", "log-settle-1")));
        when(attributionService.resolveAttribution(any(ColonelsettlementOrder.class), any()))
                .thenReturn(AttributionService.AttributionResult.unattributed(
                        null, null, "3859423", null, AttributionService.REASON_NO_PICK_SOURCE));
        when(persistenceService.persistOrder(any())).thenReturn(true);

        service.syncSettlementSettleWindow();

        verify(valueOperations).set(eq("order:sync:settle_last_time"), any());
    }

    @Test
    void syncSettlementSettleWindow_shouldNotAdvanceCheckpointWhenGatewayFails() {
        when(jobLockService.tryAcquireStrict(eq(JobLockKeys.ORDER_SYNC_SETTLE), any(Duration.class)))
                .thenReturn(true);
        when(instituteSettlementGateway.fetch(any(SettlementOrderQuery.class)))
                .thenThrow(new RuntimeException("upstream down"));

        assertThatThrownBy(() -> service.syncSettlementSettleWindow())
                .isInstanceOf(RuntimeException.class);

        verify(valueOperations, never()).set(eq("order:sync:settle_last_time"), any());
    }

    @Test
    void syncByOrderIds_shouldUseInstituteSettlementGatewayAndNotFallbackTo2704ByDefault() {
        when(jobLockService.tryAcquireStrict(eq(JobLockKeys.ORDER_SYNC), any(Duration.class)))
                .thenReturn(true);

        service.syncByOrderIds(List.of("ORDER-1603-1"));

        ArgumentCaptor<SettlementOrderQuery> captor = ArgumentCaptor.forClass(SettlementOrderQuery.class);
        verify(instituteSettlementGateway).fetch(captor.capture());
        verify(multiSettlementFallbackGateway, never()).fetch(any());
        verify(douyinOrderGateway, never()).listSettlementByOrderIds(any());
        assertThat(captor.getValue().orderIds()).containsExactly("ORDER-1603-1");
        assertThat(captor.getValue().timeType()).isEqualTo("settle");
    }

    @Test
    void shouldAdvanceSettleCheckpoint_requiresFetchedOrders() {
        OrderSyncService.SyncResult empty = new OrderSyncService.SyncResult(
                1L, 2L, 0, 0, 0, 0, 0, 0, 0, false, 0, "EMPTY_PAGE");
        OrderSyncService.SyncResult withData = new OrderSyncService.SyncResult(
                1L, 2L, 1, 1, 1, 0, 0, 0, 0, false, 1, "NO_NEXT_CURSOR");

        assertThat(service.shouldAdvanceSettleCheckpoint(empty)).isFalse();
        assertThat(service.shouldAdvanceSettleCheckpoint(withData)).isTrue();
    }

    private DouyinOrderGateway.DouyinOrderItem instituteSettledOrderItem() {
        Map<String, Object> rawPayload = new LinkedHashMap<>();
        rawPayload.put("order_id", "SETTLE_ORDER_6468");
        rawPayload.put("product_id", "3810562766247428542");
        rawPayload.put("pay_goods_amount", 1680L);
        rawPayload.put("settled_goods_amount", 1680L);
        rawPayload.put("pay_success_time", "2026-06-10 11:00:00");
        rawPayload.put("settle_time", "2026-06-10 12:00:00");
        rawPayload.put("flow_point", "SETTLE");
        rawPayload.put("colonel_order_info", Map.of(
                "estimated_commission", 34L,
                "real_commission", 30L,
                "tech_service_fee", 3L,
                "settled_tech_service_fee", 2L
        ));
        return new DouyinOrderGateway.DouyinOrderItem(
                "SETTLE_ORDER_6468",
                "3810562766247428542",
                "3810562766247428542",
                "56591058",
                "冰戈的冷饮店",
                null,
                null,
                null,
                1680L,
                34L,
                2,
                1780480000L,
                1780483600L,
                rawPayload
        );
    }

    private DouyinOrderGateway.DouyinOrderItem settlementOrderItemWithPhase(String orderId, String phaseId) {
        Map<String, Object> rawPayload = new LinkedHashMap<>();
        rawPayload.put("order_id", orderId);
        rawPayload.put("phase_id", phaseId);
        rawPayload.put("pay_goods_amount", 2550L);
        rawPayload.put("settled_goods_amount", 2480L);
        rawPayload.put("settle_time", "2026-06-05 10:00:00");
        rawPayload.put("flow_point", "SETTLE");
        rawPayload.put("colonel_order_info", Map.of(
                "estimated_commission", 55L,
                "real_commission", 50L,
                "settled_tech_service_fee", 6L
        ));
        return new DouyinOrderGateway.DouyinOrderItem(
                orderId,
                "3810562766247428542",
                "3810562766247428542",
                "56591058",
                "冰戈的冷饮店",
                null,
                null,
                null,
                2550L,
                55L,
                2,
                1780480000L,
                1780483600L,
                rawPayload
        );
    }

    private DouyinOrderGateway.DouyinOrderItem instituteOrderItem(String orderId) {
        Map<String, Object> rawPayload = new LinkedHashMap<>();
        rawPayload.put("order_id", orderId);
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
                orderId,
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
