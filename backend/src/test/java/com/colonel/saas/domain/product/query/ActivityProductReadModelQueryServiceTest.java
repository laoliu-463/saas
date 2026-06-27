package com.colonel.saas.domain.product.query;

import com.colonel.saas.common.enums.ProductBizStatus;
import com.colonel.saas.constant.ProductDisplayStatus;
import com.colonel.saas.domain.product.policy.ProductDisplayPolicy;
import com.colonel.saas.domain.product.policy.ProductOperationDecisionPolicy;
import com.colonel.saas.domain.user.facade.UserDomainFacade;
import com.colonel.saas.entity.ColonelsettlementActivity;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.entity.Merchant;
import com.colonel.saas.entity.ProductOperationLog;
import com.colonel.saas.entity.ProductOperationState;
import com.colonel.saas.entity.ProductSnapshot;
import com.colonel.saas.entity.PromotionLink;
import com.colonel.saas.gateway.douyin.DouyinProductGateway;
import com.colonel.saas.mapper.ColonelsettlementActivityMapper;
import com.colonel.saas.mapper.ColonelsettlementOrderMapper;
import com.colonel.saas.mapper.MerchantMapper;
import com.colonel.saas.mapper.ProductOperationLogMapper;
import com.colonel.saas.mapper.ProductOperationStateMapper;
import com.colonel.saas.mapper.PromotionLinkMapper;
import com.colonel.saas.service.ProductBizStatusService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ActivityProductReadModelQueryServiceTest {

    @Mock private ProductOperationStateMapper operationStateMapper;
    @Mock private ProductOperationLogMapper operationLogMapper;
    @Mock private PromotionLinkMapper promotionLinkMapper;
    @Mock private ColonelsettlementOrderMapper orderMapper;
    @Mock private MerchantMapper merchantMapper;
    @Mock private ColonelsettlementActivityMapper colonelActivityMapper;
    @Mock private UserDomainFacade userDomainFacade;
    @Mock private ProductBizStatusService productBizStatusService;

    private ActivityProductReadModelQueryService service;

    @BeforeEach
    void setUp() {
        service = new ActivityProductReadModelQueryService(
                operationStateMapper,
                operationLogMapper,
                promotionLinkMapper,
                orderMapper,
                merchantMapper,
                colonelActivityMapper,
                userDomainFacade,
                productBizStatusService,
                new ProductDisplayPolicy(),
                new ProductOperationDecisionPolicy());
    }

    @Test
    void buildRemoteListView_shouldAttachRelationIdStatusAndDecisionSummary() {
        String activityId = "12345";
        String productId = "9001";
        UUID assigneeId = UUID.randomUUID();
        ProductOperationState state = state(activityId, productId);
        state.setAssigneeId(assigneeId);
        state.setSelectedToLibrary(true);
        state.setAuditStatus(2);
        state.setDisplayStatus(ProductDisplayStatus.DISPLAYING.name());

        when(operationStateMapper.selectList(any())).thenReturn(List.of(state));
        when(userDomainFacade.loadUserDisplayLabelsByIds(any())).thenReturn(Map.of(assigneeId, "招商负责人"));
        when(operationLogMapper.selectList(any())).thenReturn(List.of(decisionLog(activityId, productId, "A")));
        when(productBizStatusService.readBizStatus(state)).thenReturn(ProductBizStatus.APPROVED);

        Map<String, Object> view = service.buildRemoteListView(new DouyinProductGateway.ActivityProductListResult(
                false,
                Long.parseLong(activityId),
                30001L,
                1L,
                null,
                List.of(item(Long.parseLong(productId), "实时商品"))));

        @SuppressWarnings("unchecked")
        Map<String, Object> row = ((List<Map<String, Object>>) view.get("items")).get(0);
        UUID expectedRelationId = UUID.nameUUIDFromBytes((activityId + ":" + productId).getBytes(StandardCharsets.UTF_8));
        assertThat(row)
                .containsEntry("relationId", expectedRelationId)
                .containsEntry("bizStatus", ProductBizStatus.APPROVED.name())
                .containsEntry("assigneeName", "招商负责人")
                .containsEntry("latestDecisionLevel", "A")
                .containsEntry("latestDecisionLabel", "重点推进");
    }

    @Test
    void buildSnapshotItems_shouldAggregateOrderPromotionMerchantAndActivityReadModel() {
        String activityId = "ACT-1";
        String productId = "9002";
        ProductSnapshot snapshot = snapshot(activityId, productId);
        ProductOperationState state = state(activityId, productId);
        state.setPromoteLink("https://promote.test/9002");

        when(operationStateMapper.selectList(any())).thenReturn(List.of(state));
        when(operationLogMapper.selectList(any())).thenReturn(List.of(decisionLog(activityId, productId, "B")));
        when(orderMapper.selectList(any())).thenReturn(List.of(
                order(activityId, productId, "ATTRIBUTED", 10000L, 1200L, LocalDateTime.parse("2026-06-01T10:00:00")),
                order(activityId, productId, "PENDING", 20000L, 2400L, LocalDateTime.parse("2026-06-02T10:00:00"))));
        when(promotionLinkMapper.selectList(any())).thenReturn(List.of(link(activityId, productId)));
        when(merchantMapper.selectList(any())).thenReturn(List.of(merchant(snapshot.getShopId())));
        when(colonelActivityMapper.selectByActivityId(activityId)).thenReturn(activity(activityId));
        when(productBizStatusService.readBizStatus(state)).thenReturn(ProductBizStatus.LINKED);

        List<Map<String, Object>> items = service.buildSnapshotItems(activityId, List.of(snapshot));

        assertThat(items).hasSize(1);
        assertThat(items.get(0))
                .containsEntry("activityName", "夏季活动")
                .containsEntry("merchantName", "品牌商家")
                .containsEntry("orderCount", 2L)
                .containsEntry("attributedCount", 1L)
                .containsEntry("unattributedCount", 1L)
                .containsEntry("gmv", "300.00")
                .containsEntry("serviceFee", "36.00")
                .containsEntry("promotionLinkCount", 1)
                .containsEntry("promotionLink", "https://promote.test/9002")
                .containsEntry("latestDecisionLevel", "B");
    }

    @Test
    void buildDetailBase_shouldAttachPromotionLinksAndMaterialPack() {
        String activityId = "ACT-2";
        String productId = "9003";
        ProductSnapshot snapshot = snapshot(activityId, productId);
        ProductOperationState state = state(activityId, productId);
        state.setAuditPayload("""
                {"sellingPoints":["卖点A"],"promotionScript":"推广脚本","supportsAds":true,"materialFiles":["https://cdn.test/a.png"]}
                """);
        state.setPromotionScene(1);
        state.setExternalUniqueId("ext-9003");
        state.setLastOperationAt(LocalDateTime.parse("2026-06-03T10:00:00"));

        when(operationLogMapper.selectOne(any())).thenReturn(decisionLog(activityId, productId, "A"));
        when(orderMapper.selectList(any())).thenReturn(List.of(order(activityId, productId, "ATTRIBUTED", 10000L, 1200L, null)));
        when(promotionLinkMapper.selectList(any())).thenReturn(List.of(link(activityId, productId)));
        when(merchantMapper.selectOne(any())).thenReturn(merchant(snapshot.getShopId()));
        when(colonelActivityMapper.selectByActivityId(activityId)).thenReturn(activity(activityId));
        when(productBizStatusService.readBizStatus(state)).thenReturn(ProductBizStatus.APPROVED);

        Map<String, Object> detail = service.buildDetailBase(activityId, productId, snapshot, state);

        assertThat(detail)
                .containsEntry("promotionScene", 1)
                .containsEntry("externalUniqueId", "ext-9003")
                .containsEntry("activityName", "夏季活动")
                .containsEntry("latestDecisionLevel", "A");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> promotionLinks = (List<Map<String, Object>>) detail.get("promotionLinks");
        assertThat(promotionLinks).singleElement().satisfies(link -> {
            assertThat(link).containsEntry("promotionUrl", "https://promote.test/" + productId);
        });
        @SuppressWarnings("unchecked")
        Map<String, Object> pack = (Map<String, Object>) detail.get("promotionMaterialPack");
        assertThat(pack)
                .containsEntry("outreachScript", "推广脚本")
                .containsEntry("supportsAds", true)
                .containsEntry("materialFiles", List.of("https://cdn.test/a.png"));
        assertThat((List<String>) pack.get("sellingPoints")).containsExactly("卖点A");
    }

    private DouyinProductGateway.ActivityProductItem item(long productId, String title) {
        return new DouyinProductGateway.ActivityProductItem(
                productId,
                title,
                "https://img.test/product.jpg",
                5900L,
                "59.00",
                20L,
                1180L,
                25L,
                "25%",
                1,
                "普通佣金",
                "5%",
                10L,
                true,
                true,
                128L,
                7001L,
                "示例店铺",
                "4.9",
                1,
                "推广中",
                "美妆",
                "1000",
                "满减券",
                "2026-04-25 00:00:00",
                "2026-04-30 23:59:59",
                "2026-04-25 00:00:00",
                "2026-04-30 23:59:59",
                "https://detail.test/products/" + productId,
                null,
                Map.of());
    }

    private ProductSnapshot snapshot(String activityId, String productId) {
        ProductSnapshot snapshot = new ProductSnapshot();
        snapshot.setId(UUID.randomUUID());
        snapshot.setActivityId(activityId);
        snapshot.setProductId(productId);
        snapshot.setTitle("测试商品");
        snapshot.setCover("https://img.test/product.jpg");
        snapshot.setPrice(10000L);
        snapshot.setPriceText("100.00");
        snapshot.setShopId(7001L);
        snapshot.setShopName("示例店铺");
        snapshot.setStatus(1);
        snapshot.setStatusText("推广中");
        snapshot.setActivityCosRatio(2000L);
        snapshot.setActivityCosRatioText("20%");
        snapshot.setAdServiceRatio("5%");
        snapshot.setActivityAdCosRatio(500L);
        snapshot.setDetailUrl("https://detail.test/products/" + productId);
        snapshot.setPromotionEndTime("2099-12-31 23:59:59");
        snapshot.setSales(128L);
        return snapshot;
    }

    private ProductOperationState state(String activityId, String productId) {
        ProductOperationState state = new ProductOperationState();
        state.setId(UUID.randomUUID());
        state.setActivityId(activityId);
        state.setProductId(productId);
        state.setBizStatus(ProductBizStatus.PENDING_AUDIT.name());
        state.setSelectedToLibrary(true);
        state.setAuditStatus(2);
        state.setDisplayStatus(ProductDisplayStatus.DISPLAYING.name());
        return state;
    }

    private ProductOperationLog decisionLog(String activityId, String productId, String level) {
        ProductOperationLog log = new ProductOperationLog();
        log.setId(UUID.randomUUID());
        log.setActivityId(activityId);
        log.setProductId(productId);
        log.setOperationType("DECISION");
        log.setOperationPayload("{decisionLevel=" + level + ", decisionLabel=" + ("A".equals(level) ? "重点推进" : "观察推进") + "}");
        log.setOperationRemark("保留推进");
        log.setCreateTime(LocalDateTime.parse("2026-06-01T09:00:00"));
        return log;
    }

    private ColonelsettlementOrder order(
            String activityId,
            String productId,
            String attributionStatus,
            long orderAmount,
            long serviceFee,
            LocalDateTime settleTime) {
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setActivityId(activityId);
        order.setProductId(productId);
        order.setAttributionStatus(attributionStatus);
        order.setOrderAmount(orderAmount);
        order.setSettleColonelCommission(serviceFee);
        order.setSettleTime(settleTime);
        order.setCreateTime(LocalDateTime.parse("2026-05-31T10:00:00"));
        return order;
    }

    private PromotionLink link(String activityId, String productId) {
        PromotionLink link = new PromotionLink();
        link.setId(UUID.randomUUID());
        link.setActivityId(activityId);
        link.setProductId(productId);
        link.setPromotionUrl("https://promote.test/" + productId);
        link.setShortUrl("https://s.test/" + productId);
        link.setLinkStatus("ACTIVE");
        link.setPickSource("CHANNEL");
        link.setCreatedAt(LocalDateTime.parse("2026-06-02T10:00:00"));
        link.setExpireTime(LocalDateTime.parse("2026-07-02T10:00:00"));
        return link;
    }

    private Merchant merchant(Long shopId) {
        Merchant merchant = new Merchant();
        merchant.setMerchantId("merchant-1");
        merchant.setMerchantName("品牌商家");
        merchant.setShopId(shopId);
        merchant.setShopName("品牌旗舰店");
        merchant.setStatus(1);
        return merchant;
    }

    private ColonelsettlementActivity activity(String activityId) {
        ColonelsettlementActivity activity = new ColonelsettlementActivity();
        activity.setActivityId(activityId);
        activity.setName("夏季活动");
        return activity;
    }
}
