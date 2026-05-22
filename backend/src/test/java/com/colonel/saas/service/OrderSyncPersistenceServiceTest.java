package com.colonel.saas.service;

import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.mapper.ColonelsettlementOrderMapper;
import com.colonel.saas.mapper.OrderSyncDedupClaimMapper;
import com.colonel.saas.mapper.SysUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderSyncPersistenceServiceTest {

    @Mock
    private ColonelsettlementOrderMapper orderMapper;
    @Mock
    private OrderSyncDedupClaimMapper orderSyncDedupClaimMapper;
    @Mock
    private PickSourceMappingService pickSourceMappingService;
    @Mock
    private MerchantService merchantService;
    @Mock
    private SampleLifecycleService sampleLifecycleService;
    @Mock
    private OperationLogService operationLogService;
    @Mock
    private SysUserMapper sysUserMapper;

    private OrderSyncPersistenceService service;

    @BeforeEach
    void setUp() {
        lenient().when(orderMapper.updateSyncedById(any(ColonelsettlementOrder.class))).thenReturn(1);
        service = new OrderSyncPersistenceService(
                orderMapper,
                orderSyncDedupClaimMapper,
                pickSourceMappingService,
                merchantService,
                sampleLifecycleService,
                operationLogService,
                sysUserMapper
        );
    }

    @Test
    void persistOrder_shouldReturnTrueAndTriggerFollowUpsWhenInserted() {
        ColonelsettlementOrder order = makeOrder(UUID.randomUUID());
        when(orderSyncDedupClaimMapper.claim(order.getOrderId(), order.getId())).thenReturn(1);
        when(orderMapper.findByOrderId(order.getOrderId())).thenReturn(null);
        when(orderMapper.insertIgnoreByOrderId(order)).thenReturn(1);

        boolean result = service.persistOrder(order);

        assertThat(result).isTrue();
        verify(pickSourceMappingService).ensureFromOrder(order);
        verify(merchantService).ensureMerchantFromOrder(order);
        verify(sampleLifecycleService).completePendingHomeworkByOrder(order);
    }

    @Test
    void persistOrder_shouldRecordAuditLogsForAttributionFollowUps() {
        UUID channelUserId = UUID.randomUUID();
        ColonelsettlementOrder order = makeOrder(channelUserId);
        order.setUserId(channelUserId);
        order.setProductName("测试商品");
        when(orderSyncDedupClaimMapper.claim(order.getOrderId(), order.getId())).thenReturn(1);
        when(orderMapper.findByOrderId(order.getOrderId())).thenReturn(null);
        when(orderMapper.insertIgnoreByOrderId(order)).thenReturn(1);

        service.persistOrder(order);

        verify(operationLogService).recordSystemAction(
                channelUserId,
                "订单归因",
                "补齐推广映射",
                "POST",
                "order",
                order.getOrderId(),
                "测试商品",
                "订单归因副作用: ensureFromOrder");
        verify(operationLogService).recordSystemAction(
                channelUserId,
                "订单归因",
                "沉淀商家",
                "POST",
                "order",
                order.getOrderId(),
                "测试商品",
                "订单归因副作用: ensureMerchantFromOrder");
        verify(operationLogService).recordSystemAction(
                channelUserId,
                "订单归因",
                "完成寄样作业",
                "POST",
                "order",
                order.getOrderId(),
                "测试商品",
                "订单归因副作用: completePendingHomeworkByOrder");
    }

    @Test
    void persistOrder_shouldReturnFalseWhenInsertIgnored() {
        ColonelsettlementOrder order = makeOrder(UUID.randomUUID());
        ColonelsettlementOrder existing = makeOrder(UUID.randomUUID());
        existing.setId(UUID.randomUUID());
        existing.setCreateTime(java.time.LocalDateTime.now().minusDays(1));
        when(orderSyncDedupClaimMapper.claim(order.getOrderId(), order.getId())).thenReturn(1);
        when(orderMapper.findByOrderId(order.getOrderId())).thenReturn(null, existing);
        when(orderMapper.insertIgnoreByOrderId(order)).thenReturn(0);

        boolean result = service.persistOrder(order);

        assertThat(result).isFalse();
        assertThat(order.getId()).isEqualTo(existing.getId());
        assertThat(order.getCreateTime()).isEqualTo(existing.getCreateTime());
        verify(orderSyncDedupClaimMapper).bindOrderRow(order.getOrderId(), existing.getId());
        verify(orderMapper).updateSyncedById(order);
        verify(pickSourceMappingService).ensureFromOrder(order);
        verify(merchantService).ensureMerchantFromOrder(order);
        verify(sampleLifecycleService).completePendingHomeworkByOrder(order);
    }

    @Test
    void persistOrder_shouldUpdateExistingOrderWhenLegacyRowAlreadyExists() {
        ColonelsettlementOrder order = makeOrder(UUID.randomUUID());
        ColonelsettlementOrder existing = makeOrder(UUID.randomUUID());
        existing.setId(UUID.randomUUID());
        existing.setCreateTime(java.time.LocalDateTime.now().minusHours(2));
        when(orderSyncDedupClaimMapper.claim(order.getOrderId(), order.getId())).thenReturn(1);
        when(orderMapper.findByOrderId(order.getOrderId())).thenReturn(existing);

        boolean result = service.persistOrder(order);

        assertThat(result).isFalse();
        assertThat(order.getId()).isEqualTo(existing.getId());
        verify(orderSyncDedupClaimMapper).bindOrderRow(order.getOrderId(), existing.getId());
        verify(orderMapper).updateSyncedById(order);
        verify(orderMapper, never()).insertIgnoreByOrderId(any());
        verify(pickSourceMappingService).ensureFromOrder(order);
        verify(merchantService).ensureMerchantFromOrder(order);
        verify(sampleLifecycleService).completePendingHomeworkByOrder(order);
    }

    @Test
    void persistOrder_shouldStillEnsureMerchantWhenChannelIdMissing() {
        ColonelsettlementOrder order = makeOrder(null);
        when(orderSyncDedupClaimMapper.claim(order.getOrderId(), order.getId())).thenReturn(1);
        when(orderMapper.findByOrderId(order.getOrderId())).thenReturn(null);
        when(orderMapper.insertIgnoreByOrderId(order)).thenReturn(1);

        boolean result = service.persistOrder(order);

        assertThat(result).isTrue();
        verify(merchantService).ensureMerchantFromOrder(order);
    }

    private ColonelsettlementOrder makeOrder(UUID channelUserId) {
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setId(UUID.randomUUID());
        order.setOrderId("order_" + UUID.randomUUID());
        order.setChannelUserId(channelUserId);
        order.setUserId(channelUserId);
        order.setPickSource("usr_ABC12345_1712000000");
        order.setShopId(1L);
        order.setShopName("Test Shop");
        return order;
    }
}
