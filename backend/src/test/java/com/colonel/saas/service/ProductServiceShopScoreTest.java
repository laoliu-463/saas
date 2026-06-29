package com.colonel.saas.service;

import com.colonel.saas.entity.Merchant;
import com.colonel.saas.entity.ProductOperationState;
import com.colonel.saas.entity.ProductSnapshot;
import com.colonel.saas.domain.order.facade.OrderReadFacade;
import com.colonel.saas.domain.order.facade.PromotionLinkRecordFacade;
import com.colonel.saas.gateway.douyin.DouyinProductGateway;
import com.colonel.saas.mapper.ColonelsettlementActivityMapper;
import com.colonel.saas.mapper.MerchantMapper;
import com.colonel.saas.mapper.ProductOperationStateMapper;
import com.colonel.saas.mapper.ProductOperationLogMapper;
import com.colonel.saas.mapper.ProductSnapshotMapper;
import com.colonel.saas.domain.user.facade.UserDomainFacade;
import com.colonel.saas.domain.product.event.ProductDomainEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 商品库 shopScore 字段透传测试。
 *
 * <p>背景：抖音接口 rawPayload 中带 shopScore（店铺评分），商品快照 rawPayload 完整保存了
 * 该字段，但 ProductService.toActivityProductView 此前未把它放到 view 中，前端拿不到。
 * 本测试覆盖 view.put("shopScore", ...) 的链路，从 rawPayload JSON 解析出整数并透传给前端。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductServiceShopScoreTest {

    @Mock private com.colonel.saas.domain.product.application.port.DouyinConvertPort douyinConvertPort;
    @Mock private DouyinProductGateway douyinProductGateway;
    @Mock private ProductSnapshotMapper snapshotMapper;
    @Mock private ProductOperationStateMapper operationStateMapper;
    @Mock private ProductOperationLogMapper operationLogMapper;
    @Mock private PromotionLinkRecordFacade promotionLinkRecordFacade;
    @Mock private OrderReadFacade orderReadFacade;
    @Mock private MerchantMapper merchantMapper;
    @Mock private UserDomainFacade userDomainFacade;
    @Mock private PickSourceMappingService pickSourceMappingService;
    @Mock private ProductBizStatusService productBizStatusService;
    @Mock private ColonelsettlementActivityMapper colonelActivityMapper;
    @Mock private TalentFollowService talentFollowService;
    @Mock private com.colonel.saas.gateway.douyin.DouyinActivityGateway douyinActivityGateway;
    @Mock private PromotionLinkIdempotencyService promotionLinkIdempotencyService;
    @Mock private com.colonel.saas.domain.config.facade.ConfigDomainFacade configDomainFacade;
    @Mock private ProductDisplayRuleService productDisplayRuleService;
    @Mock private ColonelPartnerSyncService colonelPartnerSyncService;
    @Mock private ProductDomainEventPublisher productDomainEventPublisher;
    private ProductService productService;

    @BeforeEach
    void setUp() {
        productService = new ProductService(
                douyinConvertPort,
                douyinProductGateway,
                snapshotMapper,
                operationStateMapper,
                operationLogMapper,
                promotionLinkRecordFacade,
                orderReadFacade,
                merchantMapper,
                userDomainFacade,
                pickSourceMappingService,
                productBizStatusService,
                colonelActivityMapper,
                talentFollowService,
                douyinActivityGateway,
                promotionLinkIdempotencyService,
                configDomainFacade,
                productDisplayRuleService,
                colonelPartnerSyncService,
                productDomainEventPublisher,
                new com.colonel.saas.domain.product.policy.ProductDisplayPolicy());
        when(productBizStatusService.readBizStatus(any())).thenReturn(null);
        when(talentFollowService.listByProduct(any(), any())).thenReturn(List.of());
    }

    @Test
    void getActivityProductDetail_shouldExposeShopScoreFromRawPayload() {
        String activityId = "ACT-SHOP-SCORE";
        String productId = "P-1001";

        ProductSnapshot snapshot = new ProductSnapshot();
        snapshot.setActivityId(activityId);
        snapshot.setProductId(productId);
        snapshot.setTitle("测试商品");
        snapshot.setShopId(9001L);
        // rawPayload 模拟抖音接口响应，shopScore 字段是 String（抖音 gateway 层未转 int）
        snapshot.setRawPayload("{\"productId\":1001,\"shopScore\":\"90\",\"productStock\":\"50\"}");
        when(snapshotMapper.selectOne(any())).thenReturn(snapshot);
        when(operationStateMapper.selectOne(any())).thenReturn(stateWithSelectedToLibrary(activityId, productId, true));
        when(merchantMapper.selectOne(any())).thenReturn(merchant(9001L, "测试店铺", "品牌方A"));

        Map<String, Object> detail = productService.getActivityProductDetail(activityId, productId);

        assertThat(detail).containsKey("shopScore");
        assertThat(detail.get("shopScore")).isEqualTo(90);
    }

    @Test
    void getActivityProductDetail_shopScoreMissingFromRawPayload_shouldReturnNull() {
        String activityId = "ACT-NO-SHOP-SCORE";
        String productId = "P-2002";

        ProductSnapshot snapshot = new ProductSnapshot();
        snapshot.setActivityId(activityId);
        snapshot.setProductId(productId);
        snapshot.setTitle("无评分商品");
        // rawPayload 中没有 shopScore 字段
        snapshot.setRawPayload("{\"productId\":2002,\"productStock\":\"10\"}");
        when(snapshotMapper.selectOne(any())).thenReturn(snapshot);
        when(operationStateMapper.selectOne(any())).thenReturn(stateWithSelectedToLibrary(activityId, productId, true));

        Map<String, Object> detail = productService.getActivityProductDetail(activityId, productId);

        assertThat(detail).containsKey("shopScore");
        assertThat(detail.get("shopScore")).isNull();
    }

    @Test
    void getActivityProductDetail_rawPayloadCorrupted_shouldReturnNullShopScore() {
        String activityId = "ACT-BAD-PAYLOAD";
        String productId = "P-3003";

        ProductSnapshot snapshot = new ProductSnapshot();
        snapshot.setActivityId(activityId);
        snapshot.setProductId(productId);
        // 损坏的 JSON，parseSnapshotPayload 应容错返回空 Map
        snapshot.setRawPayload("{not-a-valid-json");
        when(snapshotMapper.selectOne(any())).thenReturn(snapshot);
        when(operationStateMapper.selectOne(any())).thenReturn(stateWithSelectedToLibrary(activityId, productId, true));

        Map<String, Object> detail = productService.getActivityProductDetail(activityId, productId);

        assertThat(detail).containsKey("shopScore");
        assertThat(detail.get("shopScore")).isNull();
    }

    @Test
    void getActivityProductDetail_shopScoreAsIntegerRawValue_shouldBeParsed() {
        String activityId = "ACT-INT-SCORE";
        String productId = "P-4004";

        ProductSnapshot snapshot = new ProductSnapshot();
        snapshot.setActivityId(activityId);
        snapshot.setProductId(productId);
        // shopScore 在 rawPayload 里以数字形式存在（不是字符串），parseInteger 走 digits 提取路径
        snapshot.setRawPayload("{\"productId\":4004,\"shopScore\":85,\"productStock\":\"100\"}");
        when(snapshotMapper.selectOne(any())).thenReturn(snapshot);
        when(operationStateMapper.selectOne(any())).thenReturn(stateWithSelectedToLibrary(activityId, productId, true));

        Map<String, Object> detail = productService.getActivityProductDetail(activityId, productId);

        assertThat(detail.get("shopScore")).isEqualTo(85);
    }

    private ProductOperationState stateWithSelectedToLibrary(String activityId, String productId, boolean selected) {
        ProductOperationState state = new ProductOperationState();
        state.setActivityId(activityId);
        state.setProductId(productId);
        state.setSelectedToLibrary(selected);
        state.setBizStatus("APPROVED");
        return state;
    }

    private Merchant merchant(Long shopId, String shopName, String merchantName) {
        Merchant merchant = new Merchant();
        merchant.setShopId(shopId);
        merchant.setShopName(shopName);
        merchant.setMerchantName(merchantName);
        return merchant;
    }
}
