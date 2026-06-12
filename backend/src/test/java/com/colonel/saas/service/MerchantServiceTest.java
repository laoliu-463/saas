package com.colonel.saas.service;

import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.dto.user.UserOptionResponse;
import com.colonel.saas.entity.Merchant;
import com.colonel.saas.entity.SysUser;
import com.colonel.saas.mapper.MerchantMapper;
import com.colonel.saas.domain.user.facade.UserDomainFacade;
import com.colonel.saas.service.OperationLogService;
import com.colonel.saas.vo.PartnerDetailVO;
import com.colonel.saas.vo.PartnerProductVO;
import com.colonel.saas.vo.PartnerVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MerchantServiceTest {

    @Mock
    private MerchantMapper merchantMapper;
    @Mock
    private OperationLogService operationLogService;
    @Mock
    private UserDomainFacade userDomainFacade;
    @Mock
    private JdbcTemplate jdbcTemplate;

    private MerchantService service;

    @BeforeEach
    void setUp() {
        service = new MerchantService(merchantMapper, operationLogService, userDomainFacade, jdbcTemplate);
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
    void listPartners_shouldReturnPagedMerchantPartnersFromSyncedProductData() {
        LocalDateTime syncTime = LocalDateTime.now();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("partner_id", "1001");
        row.put("partner_name", "清风小店");
        row.put("partner_type", "MERCHANT");
        row.put("shop_id", 1001L);
        row.put("shop_name", "清风小店");
        row.put("product_count", 3L);
        row.put("latest_sync_time", Timestamp.valueOf(syncTime));
        row.put("status", 1);

        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(1L);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(row));

        IPage<PartnerVO> page = service.listPartners("清风", "MERCHANT", 1, 10);

        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getRecords()).hasSize(1);
        PartnerVO partner = page.getRecords().get(0);
        assertThat(partner.getPartnerId()).isEqualTo("1001");
        assertThat(partner.getPartnerName()).isEqualTo("清风小店");
        assertThat(partner.getPartnerType()).isEqualTo("MERCHANT");
        assertThat(partner.getShopId()).isEqualTo(1001L);
        assertThat(partner.getProductCount()).isEqualTo(3L);
        assertThat(partner.getLatestSyncTime()).isEqualTo(syncTime);
    }

    @Test
    void listPartners_shouldReturnPagedColonelPartnersFromSettlementAndMappingData() {
        LocalDateTime syncTime = LocalDateTime.now();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("partner_id", "7351155267604218149");
        row.put("partner_name", "二级团长甲");
        row.put("partner_type", "COLONEL");
        row.put("shop_id", null);
        row.put("shop_name", null);
        row.put("product_count", 2L);
        row.put("latest_sync_time", Timestamp.valueOf(syncTime));
        row.put("status", 1);

        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(1L);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(row));

        IPage<PartnerVO> page = service.listPartners("团长", "COLONEL", 1, 10);

        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getRecords()).hasSize(1);
        PartnerVO partner = page.getRecords().get(0);
        assertThat(partner.getPartnerId()).isEqualTo("7351155267604218149");
        assertThat(partner.getPartnerName()).isEqualTo("二级团长甲");
        assertThat(partner.getPartnerType()).isEqualTo("COLONEL");
        assertThat(partner.getShopId()).isNull();
        assertThat(partner.getProductCount()).isEqualTo(2L);
    }

    @Test
    void listPartners_shouldReturnEmptyPageForUnsupportedPartnerType() {
        IPage<PartnerVO> page = service.listPartners(null, "UNKNOWN", 1, 10);

        assertThat(page.getTotal()).isZero();
        assertThat(page.getRecords()).isEmpty();
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void getPartnerDetail_shouldReturnMerchantPartnerSummary() {
        LocalDateTime syncTime = LocalDateTime.now();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("partner_id", "1001");
        row.put("partner_name", "清风小店");
        row.put("partner_type", "MERCHANT");
        row.put("shop_id", 1001L);
        row.put("shop_name", "清风小店");
        row.put("product_count", 3L);
        row.put("latest_sync_time", Timestamp.valueOf(syncTime));
        row.put("status", 1);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(row));

        PartnerDetailVO detail = service.getPartnerDetail("1001", "MERCHANT");

        assertThat(detail.getPartnerId()).isEqualTo("1001");
        assertThat(detail.getPartnerName()).isEqualTo("清风小店");
        assertThat(detail.getProductCount()).isEqualTo(3L);
        assertThat(detail.getLatestSyncTime()).isEqualTo(syncTime);
    }

    @Test
    void getPartnerDetail_shouldReturnColonelPartnerSummary() {
        LocalDateTime syncTime = LocalDateTime.now();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("partner_id", "7351155267604218149");
        row.put("partner_name", "二级团长甲");
        row.put("partner_type", "COLONEL");
        row.put("shop_id", null);
        row.put("shop_name", null);
        row.put("product_count", 2L);
        row.put("latest_sync_time", Timestamp.valueOf(syncTime));
        row.put("status", 1);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(row));

        PartnerDetailVO detail = service.getPartnerDetail("7351155267604218149", "COLONEL");

        assertThat(detail.getPartnerId()).isEqualTo("7351155267604218149");
        assertThat(detail.getPartnerType()).isEqualTo("COLONEL");
        assertThat(detail.getProductCount()).isEqualTo(2L);
    }

    @Test
    void listPartnerProducts_shouldReturnPagedSnapshotProducts() {
        LocalDateTime syncTime = LocalDateTime.now();
        Map<String, Object> productRow = new LinkedHashMap<>();
        productRow.put("product_id", "P-1001");
        productRow.put("product_name", "夏季爆款水杯");
        productRow.put("activity_id", "A-1001");
        productRow.put("cover", "https://img.example/cup.png");
        productRow.put("price_text", "¥29.90");
        productRow.put("shop_id", 1001L);
        productRow.put("shop_name", "清风小店");
        productRow.put("category_name", "家居");
        productRow.put("sales", 128L);
        productRow.put("status", 1);
        productRow.put("status_text", "推广中");
        productRow.put("sync_time", Timestamp.valueOf(syncTime));
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(1L);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(productRow));

        IPage<PartnerProductVO> page = service.listPartnerProducts("1001", 1, 10);

        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getRecords()).hasSize(1);
        PartnerProductVO product = page.getRecords().get(0);
        assertThat(product.getProductId()).isEqualTo("P-1001");
        assertThat(product.getProductName()).isEqualTo("夏季爆款水杯");
        assertThat(product.getActivityId()).isEqualTo("A-1001");
        assertThat(product.getShopId()).isEqualTo(1001L);
        assertThat(product.getSales()).isEqualTo(128L);
        assertThat(product.getLatestSyncTime()).isEqualTo(syncTime);
    }

    @Test
    void listPartnerProducts_shouldReturnPagedProductsForColonelPartner() {
        LocalDateTime syncTime = LocalDateTime.now();
        Map<String, Object> productRow = new LinkedHashMap<>();
        productRow.put("product_id", "P-2001");
        productRow.put("product_name", "团长活动商品");
        productRow.put("activity_id", "3543332");
        productRow.put("cover", "https://img.example/item.png");
        productRow.put("price_text", "¥19.90");
        productRow.put("shop_id", 2001L);
        productRow.put("shop_name", "示例店");
        productRow.put("category_name", "食品");
        productRow.put("sales", 50L);
        productRow.put("status", 1);
        productRow.put("status_text", "推广中");
        productRow.put("sync_time", Timestamp.valueOf(syncTime));
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(1L);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(productRow));

        IPage<PartnerProductVO> page = service.listPartnerProducts("7351155267604218149", "COLONEL", 1, 10);

        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getRecords()).hasSize(1);
        assertThat(page.getRecords().get(0).getProductId()).isEqualTo("P-2001");
    }

    @Test
    void overrideMerchantAssignment_shouldThrowWhenUserNotFound() {
        UUID userId = UUID.randomUUID();
        when(userDomainFacade.getUserById(userId)).thenReturn(null);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> service.overrideMerchantAssignment("m1", userId, "reason", UUID.randomUUID()))
                .isInstanceOf(com.colonel.saas.common.exception.BusinessException.class)
                .hasMessageContaining("目标负责人不存在");
    }

    @Test
    void overrideMerchantAssignment_shouldThrowWhenUserDeleted() {
        UUID userId = UUID.randomUUID();
        when(userDomainFacade.getUserById(userId)).thenReturn(null);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> service.overrideMerchantAssignment("m1", userId, "reason", UUID.randomUUID()))
                .isInstanceOf(com.colonel.saas.common.exception.BusinessException.class)
                .hasMessageContaining("目标负责人不存在");
    }

    @Test
    void overrideMerchantAssignment_shouldUpdateOwnerAndDeptId() {
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        UserOptionResponse user = new UserOptionResponse(userId, "test", "test", deptId, null, null);
        when(userDomainFacade.getUserById(userId)).thenReturn(user);

        Merchant merchant = new Merchant();
        merchant.setMerchantId("m1");
        when(merchantMapper.selectOne(any())).thenReturn(merchant);
        when(merchantMapper.updateById(any(Merchant.class))).thenReturn(1);

        service.overrideMerchantAssignment("m1", userId, "test reason", UUID.randomUUID());

        assertThat(merchant.getOwnerId()).isEqualTo(userId);
        assertThat(merchant.getOwnerDeptId()).isEqualTo(deptId);
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
