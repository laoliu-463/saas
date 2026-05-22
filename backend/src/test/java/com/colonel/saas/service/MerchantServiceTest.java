package com.colonel.saas.service;

import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.entity.Merchant;
import com.colonel.saas.entity.SysUser;
import com.colonel.saas.mapper.MerchantMapper;
import com.colonel.saas.mapper.SysUserMapper;
import com.colonel.saas.service.OperationLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MerchantServiceTest {

    @Mock
    private MerchantMapper merchantMapper;
    @Mock
    private OperationLogService operationLogService;
    @Mock
    private SysUserMapper sysUserMapper;

    private MerchantService service;

    @BeforeEach
    void setUp() {
        service = new MerchantService(merchantMapper, operationLogService, sysUserMapper);
    }

    @Test
    void ensureMerchantFromOrder_shouldUseDefaultTransactionPropagation() throws NoSuchMethodException {
        Method method = MerchantService.class.getMethod("ensureMerchantFromOrder", ColonelsettlementOrder.class);

        Transactional transactional = method.getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRED);
    }

    @Test
    void ensureMerchantFromOrder_shouldSkipWhenMerchantIdBlank() {
        ColonelsettlementOrder order = new OrderBuilder()
                .extraData(Map.of())
                .build();

        service.ensureMerchantFromOrder(order);

        verify(merchantMapper, never()).insert(any(Merchant.class));
    }

    @Test
    void ensureMerchantFromOrder_shouldSkipWhenMerchantAlreadyExists() {
        UUID merchantId = UUID.randomUUID();
        Merchant existing = new Merchant();
        existing.setMerchantId(merchantId.toString());
        when(merchantMapper.selectOne(any())).thenReturn(existing);

        ColonelsettlementOrder order = new OrderBuilder()
                .extraData(Map.of("merchant_id", merchantId.toString()))
                .build();

        service.ensureMerchantFromOrder(order);

        verify(merchantMapper, never()).insert(any(Merchant.class));
    }

    @Test
    void ensureMerchantFromOrder_shouldInsertFromMerchantId() {
        UUID merchantId = UUID.randomUUID();
        when(merchantMapper.selectOne(any())).thenReturn(null);

        ColonelsettlementOrder order = new OrderBuilder()
                .shopId(1L)
                .shopName("Shop One")
                .orderId("order-1")
                .extraData(Map.of("merchant_id", merchantId.toString(), "merchant_name", "Merchant A"))
                .build();

        service.ensureMerchantFromOrder(order);

        ArgumentCaptor<Merchant> captor = ArgumentCaptor.forClass(Merchant.class);
        verify(merchantMapper).insert(captor.capture());
        Merchant saved = captor.getValue();
        assertThat(saved.getMerchantId()).isEqualTo(merchantId.toString());
        assertThat(saved.getMerchantName()).isEqualTo("Merchant A");
        assertThat(saved.getShopId()).isEqualTo(1L);
        assertThat(saved.getShopName()).isEqualTo("Shop One");
        assertThat(saved.getSourceOrderId()).isEqualTo("order-1");
        assertThat(saved.getStatus()).isEqualTo(1);
    }

    @Test
    void ensureMerchantFromOrder_shouldFallbackToShopIdWhenMerchantIdAbsent() {
        UUID merchantId = UUID.randomUUID();
        when(merchantMapper.selectOne(any())).thenReturn(null);

        ColonelsettlementOrder order = new OrderBuilder()
                .shopId(2L)
                .shopName("Shop Two")
                .extraData(Map.of("author_id", merchantId.toString(), "author_name", "Author B"))
                .build();

        service.ensureMerchantFromOrder(order);

        ArgumentCaptor<Merchant> captor = ArgumentCaptor.forClass(Merchant.class);
        verify(merchantMapper).insert(captor.capture());
        Merchant saved = captor.getValue();
        assertThat(saved.getMerchantId()).isEqualTo("2");
        assertThat(saved.getMerchantName()).isEqualTo("Shop Two");
    }

    @Test
    void ensureMerchantFromOrder_shouldInsertFromShopIdWhenExtraDataAbsent() {
        when(merchantMapper.selectOne(any())).thenReturn(null);

        ColonelsettlementOrder order = new OrderBuilder()
                .shopId(3L)
                .shopName("Shop Three")
                .build();

        service.ensureMerchantFromOrder(order);

        ArgumentCaptor<Merchant> captor = ArgumentCaptor.forClass(Merchant.class);
        verify(merchantMapper).insert(captor.capture());
        Merchant saved = captor.getValue();
        assertThat(saved.getMerchantId()).isEqualTo("3");
        assertThat(saved.getMerchantName()).isEqualTo("Shop Three");
    }

    @Test
    void ensureMerchantFromOrder_shouldIgnoreDuplicateKey() {
        when(merchantMapper.selectOne(any())).thenReturn(null);

        ColonelsettlementOrder order = new OrderBuilder()
                .extraData(Map.of("merchant_id", UUID.randomUUID().toString()))
                .build();

        doThrow(new DuplicateKeyException("duplicate")).when(merchantMapper).insert(any(Merchant.class));

        service.ensureMerchantFromOrder(order);

        verify(merchantMapper).insert(any(Merchant.class));
    }

    @Test
    void findOrCreateByChannel_shouldReturnNullWhenChannelIdBlank() {
        ColonelsettlementOrder order = new OrderBuilder().build();

        Merchant result = service.findOrCreateByChannel("", order);

        assertThat(result).isNull();
        verify(merchantMapper, never()).insert(any(Merchant.class));
    }

    @Test
    void findOrCreateByChannel_shouldReturnExistingMerchant() {
        UUID channelId = UUID.randomUUID();
        Merchant existing = new Merchant();
        existing.setMerchantId(channelId.toString());
        when(merchantMapper.selectOne(any())).thenReturn(existing);

        Merchant result = service.findOrCreateByChannel(channelId.toString(), new OrderBuilder().build());

        assertThat(result).isSameAs(existing);
        verify(merchantMapper, never()).insert(any(Merchant.class));
    }

    @Test
    void findOrCreateByChannel_shouldCreateNewWithOrderDetails() {
        UUID channelId = UUID.randomUUID();
        when(merchantMapper.selectOne(any())).thenReturn(null);

        ColonelsettlementOrder order = new OrderBuilder()
                .shopId(99L)
                .shopName("Shop 99")
                .orderId("order-99")
                .extraData(Map.of("key", "value"))
                .build();

        Merchant result = service.findOrCreateByChannel(channelId.toString(), order);

        assertThat(result.getMerchantId()).isEqualTo(channelId.toString());
        assertThat(result.getShopId()).isEqualTo(99L);
        assertThat(result.getShopName()).isEqualTo("Shop 99");
        assertThat(result.getSourceOrderId()).isEqualTo("order-99");
        assertThat(result.getStatus()).isEqualTo(0);
    }

    @Test
    void findOrCreateByChannel_shouldCreateNewWithoutOrderWhenOrderIsNull() {
        UUID channelId = UUID.randomUUID();
        when(merchantMapper.selectOne(any())).thenReturn(null);

        Merchant result = service.findOrCreateByChannel(channelId.toString(), null);

        assertThat(result.getMerchantId()).isEqualTo(channelId.toString());
        assertThat(result.getShopId()).isNull();
        assertThat(result.getShopName()).isNull();
        assertThat(result.getSourceOrderId()).isNull();
        assertThat(result.getExtraData()).isNull();
    }

    @Test
    void findOrCreateByChannel_shouldRecoverFromConcurrentInsert() {
        UUID channelId = UUID.randomUUID();
        Merchant concurrent = new Merchant();
        concurrent.setMerchantId(channelId.toString());
        concurrent.setMerchantName(channelId.toString());
        concurrent.setStatus(0);
        when(merchantMapper.selectOne(any()))
                .thenReturn(null)
                .thenReturn(concurrent);

        ColonelsettlementOrder order = new OrderBuilder().build();

        Merchant result = service.findOrCreateByChannel(channelId.toString(), order);

        assertThat(result.getMerchantId()).isEqualTo(channelId.toString());
        verify(merchantMapper).insert(any(Merchant.class));
    }

    @Test
    void overrideMerchantAssignment_shouldThrowWhenUserNotFound() {
        UUID userId = UUID.randomUUID();
        when(sysUserMapper.selectById(userId)).thenReturn(null);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> service.overrideMerchantAssignment("m1", userId, "reason", UUID.randomUUID()))
                .isInstanceOf(com.colonel.saas.common.exception.BusinessException.class)
                .hasMessageContaining("目标负责人不存在");
    }

    @Test
    void overrideMerchantAssignment_shouldThrowWhenUserDeleted() {
        UUID userId = UUID.randomUUID();
        SysUser deletedUser = new SysUser();
        deletedUser.setDeleted(1);
        when(sysUserMapper.selectById(userId)).thenReturn(deletedUser);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> service.overrideMerchantAssignment("m1", userId, "reason", UUID.randomUUID()))
                .isInstanceOf(com.colonel.saas.common.exception.BusinessException.class)
                .hasMessageContaining("目标负责人不存在");
    }

    @Test
    void overrideMerchantAssignment_shouldUpdateOwnerAndDeptId() {
        UUID userId = UUID.randomUUID();
        SysUser user = new SysUser();
        user.setDeleted(0);
        user.setDeptId(UUID.randomUUID());
        when(sysUserMapper.selectById(userId)).thenReturn(user);

        Merchant merchant = new Merchant();
        merchant.setMerchantId("m1");
        when(merchantMapper.selectOne(any())).thenReturn(merchant);
        when(merchantMapper.updateById(any(Merchant.class))).thenReturn(1);

        service.overrideMerchantAssignment("m1", userId, "test reason", UUID.randomUUID());

        assertThat(merchant.getOwnerId()).isEqualTo(userId);
        assertThat(merchant.getOwnerDeptId()).isEqualTo(user.getDeptId());
        verify(merchantMapper).updateById(merchant);
    }

    private static class OrderBuilder {
        private Long shopId;
        private String shopName;
        private String orderId;
        private Map<String, Object> extraData;

        OrderBuilder shopId(Long shopId) {
            this.shopId = shopId;
            return this;
        }

        OrderBuilder shopName(String shopName) {
            this.shopName = shopName;
            return this;
        }

        OrderBuilder orderId(String orderId) {
            this.orderId = orderId;
            return this;
        }

        OrderBuilder extraData(Map<String, Object> extraData) {
            this.extraData = extraData;
            return this;
        }

        ColonelsettlementOrder build() {
            ColonelsettlementOrder order = new ColonelsettlementOrder();
            order.setShopId(shopId);
            order.setShopName(shopName);
            order.setOrderId(orderId);
            order.setExtraData(extraData);
            return order;
        }
    }
}
