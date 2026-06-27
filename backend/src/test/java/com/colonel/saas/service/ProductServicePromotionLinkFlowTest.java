package com.colonel.saas.service;

import com.colonel.saas.common.enums.ProductBizStatus;
import com.colonel.saas.domain.config.facade.ConfigDomainFacade;
import com.colonel.saas.domain.product.application.CopyPromotionApplicationService;
import com.colonel.saas.domain.product.application.port.DouyinConvertPort;
import com.colonel.saas.domain.product.event.ProductDomainEventPublisher;
import com.colonel.saas.domain.product.policy.ProductDisplayPolicy;
import com.colonel.saas.domain.user.facade.UserDomainFacade;
import com.colonel.saas.entity.ProductOperationState;
import com.colonel.saas.entity.ProductSnapshot;
import com.colonel.saas.gateway.douyin.DouyinActivityGateway;
import com.colonel.saas.gateway.douyin.DouyinProductGateway;
import com.colonel.saas.gateway.douyin.DouyinPromotionGateway;
import com.colonel.saas.mapper.ColonelsettlementActivityMapper;
import com.colonel.saas.mapper.ColonelsettlementOrderMapper;
import com.colonel.saas.mapper.MerchantMapper;
import com.colonel.saas.mapper.ProductOperationLogMapper;
import com.colonel.saas.mapper.ProductOperationStateMapper;
import com.colonel.saas.mapper.ProductSnapshotMapper;
import com.colonel.saas.mapper.PromotionLinkMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServicePromotionLinkFlowTest {

    @Mock private DouyinConvertPort douyinConvertPort;
    @Mock private DouyinProductGateway douyinProductGateway;
    @Mock private ProductSnapshotMapper snapshotMapper;
    @Mock private ProductOperationStateMapper operationStateMapper;
    @Mock private ProductOperationLogMapper operationLogMapper;
    @Mock private PromotionLinkMapper promotionLinkMapper;
    @Mock private ColonelsettlementOrderMapper orderMapper;
    @Mock private MerchantMapper merchantMapper;
    @Mock private UserDomainFacade userDomainFacade;
    @Mock private PickSourceMappingService pickSourceMappingService;
    @Mock private ProductBizStatusService productBizStatusService;
    @Mock private ColonelsettlementActivityMapper colonelActivityMapper;
    @Mock private TalentFollowService talentFollowService;
    @Mock private DouyinActivityGateway douyinActivityGateway;
    @Mock private PromotionLinkIdempotencyService promotionLinkIdempotencyService;
    @Mock private ConfigDomainFacade configDomainFacade;
    @Mock private ProductDisplayRuleService productDisplayRuleService;
    @Mock private ColonelPartnerSyncService colonelPartnerSyncService;
    @Mock private ProductDomainEventPublisher productDomainEventPublisher;
    @Mock private CopyPromotionApplicationService copyPromotionApplicationService;

    private ProductService productService;

    @BeforeEach
    void setUp() {
        productService = new ProductService(
                douyinConvertPort,
                douyinProductGateway,
                snapshotMapper,
                operationStateMapper,
                operationLogMapper,
                promotionLinkMapper,
                orderMapper,
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
                new ProductDisplayPolicy(),
                copyPromotionApplicationService);
    }

    @Test
    void generatePromotionLinkInternal_shouldPersistMappingAndPublishCompletedEventWithMappingId() {
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        UUID mappingId = UUID.randomUUID();
        ProductSnapshot snapshot = snapshot();
        ProductOperationState state = operationState();
        when(snapshotMapper.selectOne(any())).thenReturn(snapshot);
        when(operationStateMapper.selectOne(any())).thenReturn(state);
        when(productBizStatusService.readBizStatus(state)).thenReturn(ProductBizStatus.APPROVED);
        when(userDomainFacade.loadUserChannelCodesByIds(any())).thenReturn(Map.of(userId, "channel-a"));
        when(userDomainFacade.getUserName(userId)).thenReturn("渠道甲");
        when(douyinConvertPort.convert(any())).thenReturn(new DouyinConvertPort.ConvertResult(
                "PS-1",
                "pick-extra",
                "SID-1",
                "https://short",
                "https://promote",
                "uuid-seed"));
        when(pickSourceMappingService.saveOrUpdate(
                eq(userId),
                eq("渠道甲"),
                eq(deptId),
                eq("talent-1"),
                isNull(),
                eq("SID-1"),
                isNull(),
                eq("PS-1"),
                eq("P-1"),
                eq("ACT-1"),
                eq("https://detail"),
                eq("https://promote"),
                any(UUID.class),
                eq("PRODUCT_LIBRARY"),
                eq("pick-extra")))
                .thenReturn(mappingId);

        DouyinPromotionGateway.PromotionLinkResult result = productService.generatePromotionLinkInternal(
                "ACT-1",
                "P-1",
                userId,
                deptId,
                "ext-1",
                4,
                true,
                "PRODUCT_LIBRARY",
                "talent-1");

        assertThat(result.pickSource()).isEqualTo("PS-1");
        ArgumentCaptor<DouyinConvertPort.ConvertCommand> commandCaptor =
                ArgumentCaptor.forClass(DouyinConvertPort.ConvertCommand.class);
        verify(douyinConvertPort).convert(commandCaptor.capture());
        assertThat(commandCaptor.getValue().context().pickExtra()).isEqualTo("channel_channel_a");
        verify(pickSourceMappingService).saveOrUpdate(
                eq(userId),
                eq("渠道甲"),
                eq(deptId),
                eq("talent-1"),
                isNull(),
                eq("SID-1"),
                isNull(),
                eq("PS-1"),
                eq("P-1"),
                eq("ACT-1"),
                eq("https://detail"),
                eq("https://promote"),
                any(UUID.class),
                eq("PRODUCT_LIBRARY"),
                eq("pick-extra"));
        verify(productDomainEventPublisher).publishPromotionLinkCompleted(
                eq("ACT-1"),
                eq("P-1"),
                any(UUID.class),
                eq(mappingId),
                eq(userId),
                eq("talent-1"),
                eq("PS-1"),
                eq("pick-extra"),
                eq("https://promote"),
                eq("https://short"),
                eq("PRODUCT_LIBRARY"));
    }

    @Test
    void generatePromotionLink_shouldReturnCompletedIdempotencyResultWithoutSideEffects() {
        UUID userId = UUID.randomUUID();
        DouyinPromotionGateway.PromotionLinkResult completed =
                new DouyinPromotionGateway.PromotionLinkResult(
                        "PS-OLD",
                        "extra-old",
                        "SID-OLD",
                        "https://short-old",
                        "https://promote-old",
                        "seed-old");
        when(promotionLinkIdempotencyService.buildScopeKey(userId, "ACT-1", "P-1", "idem-1"))
                .thenReturn("scope-1");
        when(promotionLinkIdempotencyService.findCompleted("scope-1"))
                .thenReturn(Optional.of(completed));

        DouyinPromotionGateway.PromotionLinkResult result = productService.generatePromotionLink(
                "ACT-1",
                "P-1",
                userId,
                UUID.randomUUID(),
                "ext-1",
                4,
                true,
                "PRODUCT_LIBRARY",
                "talent-1",
                "idem-1");

        assertThat(result).isEqualTo(completed);
        verify(promotionLinkIdempotencyService, never()).tryAcquireInFlight(anyString());
        verifyNoInteractions(douyinConvertPort, pickSourceMappingService, productDomainEventPublisher);
    }

    private ProductSnapshot snapshot() {
        ProductSnapshot snapshot = new ProductSnapshot();
        snapshot.setActivityId("ACT-1");
        snapshot.setProductId("P-1");
        snapshot.setDetailUrl("https://detail");
        return snapshot;
    }

    private ProductOperationState operationState() {
        ProductOperationState state = new ProductOperationState();
        state.setActivityId("ACT-1");
        state.setProductId("P-1");
        state.setSelectedToLibrary(true);
        state.setBizStatus(ProductBizStatus.APPROVED.name());
        return state;
    }
}
