package com.colonel.saas.service;

import com.colonel.saas.domain.product.event.ProductDomainEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class ProductServiceColonelBuyinIdTest {

    private static final String LONG_BUYIN_ID = "46128341673481000";

    @Mock
    private com.colonel.saas.gateway.douyin.DouyinPromotionGateway douyinPromotionGateway;
    @Mock
    private com.colonel.saas.gateway.douyin.DouyinProductGateway douyinProductGateway;
    @Mock
    private com.colonel.saas.mapper.ProductSnapshotMapper snapshotMapper;
    @Mock
    private com.colonel.saas.mapper.ProductOperationStateMapper operationStateMapper;
    @Mock
    private com.colonel.saas.mapper.ProductOperationLogMapper operationLogMapper;
    @Mock
    private com.colonel.saas.mapper.PromotionLinkMapper promotionLinkMapper;
    @Mock
    private com.colonel.saas.mapper.ColonelsettlementOrderMapper orderMapper;
    @Mock
    private com.colonel.saas.mapper.MerchantMapper merchantMapper;
    @Mock
    private com.colonel.saas.mapper.SysUserMapper sysUserMapper;
    @Mock
    private PickSourceMappingService pickSourceMappingService;
    @Mock
    private ProductBizStatusService productBizStatusService;
    @Mock
    private com.colonel.saas.mapper.ColonelsettlementActivityMapper colonelActivityMapper;
    @Mock
    private TalentFollowService talentFollowService;
    @Mock
    private com.colonel.saas.gateway.douyin.DouyinActivityGateway douyinActivityGateway;
    @Mock
    private com.colonel.saas.domain.config.facade.ConfigDomainFacade configDomainFacade;
    @Mock
    private ProductDisplayRuleService productDisplayRuleService;
    @Mock
    private ColonelPartnerSyncService colonelPartnerSyncService;
    @Mock
    private ProductDomainEventPublisher productDomainEventPublisher;

    private ProductService productService;

    @BeforeEach
    void setUp() {
        productService = new ProductService(
                douyinPromotionGateway,
                douyinProductGateway,
                snapshotMapper,
                operationStateMapper,
                operationLogMapper,
                promotionLinkMapper,
                orderMapper,
                merchantMapper,
                sysUserMapper,
                pickSourceMappingService,
                productBizStatusService,
                colonelActivityMapper,
                talentFollowService,
                douyinActivityGateway,
                new PromotionLinkIdempotencyService(new com.fasterxml.jackson.databind.ObjectMapper()),
                configDomainFacade,
                productDisplayRuleService,
                colonelPartnerSyncService,
                productDomainEventPublisher,
                new com.colonel.saas.domain.product.policy.ProductDisplayPolicy()
        );
    }

    @Test
    void extractColonelBuyinIdFromText_shouldCaptureFullLongId() {
        String raw = "detail_url=https://haohuo.jinritemai.com/views/product/item2?id=1"
                + "&origin_colonel_buyin_id=" + LONG_BUYIN_ID + "&activity_id=100";

        String buyinId = invokeExtractColonelBuyinIdFromText(raw);

        assertThat(buyinId).isEqualTo(LONG_BUYIN_ID);
    }

    @Test
    void resolveColonelBuyinIdFromPayload_shouldPreserveNumericBuyinWithoutScientificNotation() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("origin_colonel_buyin_id", 46128341673481000L);

        String buyinId = invokeResolveColonelBuyinIdFromPayload(toJson(payload));

        assertThat(buyinId).isEqualTo(LONG_BUYIN_ID);
    }

    private String invokeExtractColonelBuyinIdFromText(String raw) {
        return (String) ReflectionTestUtils.invokeMethod(productService, "extractColonelBuyinIdFromText", raw);
    }

    private String invokeResolveColonelBuyinIdFromPayload(String raw) {
        return (String) ReflectionTestUtils.invokeMethod(productService, "resolveColonelBuyinIdFromPayload", raw);
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(payload);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
