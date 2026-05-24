package com.colonel.saas.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.common.enums.ProductBizStatus;
import com.colonel.saas.constant.ProductDisplayStatus;
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
import com.colonel.saas.mapper.SysUserMapper;
import com.colonel.saas.domain.product.event.ProductDomainEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceFilterTest {

    @Mock private DouyinPromotionGateway douyinPromotionGateway;
    @Mock private DouyinProductGateway douyinProductGateway;
    @Mock private ProductSnapshotMapper snapshotMapper;
    @Mock private ProductOperationStateMapper operationStateMapper;
    @Mock private ProductOperationLogMapper operationLogMapper;
    @Mock private PromotionLinkMapper promotionLinkMapper;
    @Mock private ColonelsettlementOrderMapper orderMapper;
    @Mock private MerchantMapper merchantMapper;
    @Mock private SysUserMapper sysUserMapper;
    @Mock private PickSourceMappingService pickSourceMappingService;
    @Mock private ProductBizStatusService productBizStatusService;
    @Mock private ColonelsettlementActivityMapper colonelActivityMapper;
    @Mock private TalentFollowService talentFollowService;
    @Mock private DouyinActivityGateway douyinActivityGateway;
    @Mock private BusinessRuleConfigService businessRuleConfigService;
    @Mock private ProductDisplayRuleService productDisplayRuleService;
    @Mock private ColonelPartnerSyncService colonelPartnerSyncService;
    @Mock private ProductDomainEventPublisher productDomainEventPublisher;

    private ProductService service;

    @BeforeEach
    void setUp() {
        service = new ProductService(
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
                businessRuleConfigService,
                productDisplayRuleService,
                colonelPartnerSyncService,
                productDomainEventPublisher
        );
        lenient().when(productBizStatusService.readBizStatus(any())).thenReturn(ProductBizStatus.APPROVED);
    }

    @Test
    void getSelectedLibraryPage_shouldFilterByColonelNameAndPublishedAndCheckboxTags() {
        ProductOperationState matchedState = state("10001", "9001");
        matchedState.setPromoteLink("https://promo.example");
        matchedState.setAuditPayload("{\"productTags\":[\"手卡\",\"专属价\"],\"materialDownload\":true}");

        ProductOperationState ignoredState = state("10002", "9002");
        Page<ProductOperationState> statePage = new Page<>(1, 200, 2);
        statePage.setRecords(List.of(matchedState, ignoredState));

        ProductSnapshot matched = snapshot("10001", "9001", "玩具乐器", 9900L);
        ProductSnapshot ignored = snapshot("10002", "9002", "美妆", 8800L);

        when(operationStateMapper.selectPage(any(Page.class), any())).thenReturn(statePage);
        when(snapshotMapper.selectBatchIds(any())).thenReturn(List.of(matched, ignored));
        when(colonelPartnerSyncService.resolveProductIdsByColonelName("张团长")).thenReturn(Set.of("9001"));

        var result = service.getSelectedLibraryPage(1, 10, filter()
                .categories("玩具乐器")
                .colonelName("张团长")
                .published("1")
                .materialDownload("1")
                .handCard("1")
                .build());

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRecords()).singleElement().extracting("productId").isEqualTo("9001");
    }

    @Test
    void getSelectedLibraryPage_shouldFilterByCommissionRange() {
        ProductOperationState state1 = state("10001", "9001");
        ProductOperationState state2 = state("10002", "9002");
        Page<ProductOperationState> statePage = new Page<>(1, 200, 2);
        statePage.setRecords(List.of(state1, state2));

        ProductSnapshot snap1 = snapshot("10001", "9001", "玩具乐器", 9900L);
        snap1.setActivityCosRatio(1500L);
        ProductSnapshot snap2 = snapshot("10002", "9002", "美妆", 8800L);
        snap2.setActivityCosRatio(3500L);

        when(operationStateMapper.selectPage(any(Page.class), any())).thenReturn(statePage);
        when(snapshotMapper.selectBatchIds(any())).thenReturn(List.of(snap1, snap2));

        var result = service.getSelectedLibraryPage(1, 10, filter()
                .commissionMin("2000")
                .commissionMax("4000")
                .build());

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRecords()).singleElement().extracting("productId").isEqualTo("9002");
    }

    @Test
    void getSelectedLibraryPage_shouldFilterByLivePriceRange() {
        ProductOperationState state1 = state("10001", "9001");
        ProductOperationState state2 = state("10002", "9002");
        Page<ProductOperationState> statePage = new Page<>(1, 200, 2);
        statePage.setRecords(List.of(state1, state2));

        ProductSnapshot snap1 = snapshot("10001", "9001", "玩具乐器", 5000L);
        ProductSnapshot snap2 = snapshot("10002", "9002", "美妆", 15000L);

        when(operationStateMapper.selectPage(any(Page.class), any())).thenReturn(statePage);
        when(snapshotMapper.selectBatchIds(any())).thenReturn(List.of(snap1, snap2));

        var result = service.getSelectedLibraryPage(1, 10, filter()
                .livePriceMin("3000")
                .livePriceMax("10000")
                .build());

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRecords()).singleElement().extracting("productId").isEqualTo("9001");
    }

    @Test
    void getSelectedLibraryPage_shouldFilterByActivityId() {
        ProductSnapshot snap1 = snapshot("ACT001", "9001", "玩具乐器", 9900L);
        ProductSnapshot snap2 = snapshot("ACT002", "9002", "美妆", 8800L);

        ProductOperationState state1 = state("ACT001", "9001");
        ProductOperationState state2 = state("ACT002", "9002");
        Page<ProductOperationState> statePage = new Page<>(1, 200, 2);
        statePage.setRecords(List.of(state1, state2));

        when(operationStateMapper.selectPage(any(Page.class), any())).thenReturn(statePage);
        when(snapshotMapper.selectBatchIds(any())).thenReturn(List.of(snap1, snap2));

        var result = service.getSelectedLibraryPage(1, 10, filter().activityId("ACT001").build());

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRecords()).singleElement().extracting("productId").isEqualTo("9001");
    }

    @Test
    void getSelectedLibraryPage_shouldFilterFreeSampleListedAndSupplementCheckboxes() {
        ProductOperationState matchedState = state("10001", "9001");
        matchedState.setAuditPayload("""
                {
                  "sampleType":"FREE",
                  "materialDownloadAvailable":true,
                  "dedupeSelection":true,
                  "notInProductPool":true,
                  "handCardAvailable":true,
                  "doubleCommission":true
                }
                """);
        ProductOperationState ignoredState = state("10002", "9002");
        ignoredState.setAuditPayload("{\"sampleType\":\"PAID\"}");
        Page<ProductOperationState> statePage = new Page<>(1, 200, 2);
        statePage.setRecords(List.of(matchedState, ignoredState));

        ProductSnapshot matched = snapshot("10001", "9001", "玩具乐器", 9900L);
        matched.setStatus(1);
        ProductSnapshot ignored = snapshot("10002", "9002", "美妆", 8800L);
        ignored.setStatus(0);

        when(operationStateMapper.selectPage(any(Page.class), any())).thenReturn(statePage);
        when(snapshotMapper.selectBatchIds(any())).thenReturn(List.of(matched, ignored));

        var result = service.getSelectedLibraryPage(1, 10, filter()
                .listed("1")
                .freeSample("1")
                .materialDownload("1")
                .handCard("1")
                .doubleCommission("1")
                .notInLibrary("1")
                .dedup("1")
                .build());

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRecords()).singleElement().extracting("productId").isEqualTo("9001");
    }

    @Test
    void getSelectedLibraryPage_shouldFilterByAssigneeId() {
        UUID recruiterId = UUID.randomUUID();
        ProductOperationState matched = state("10001", "9001");
        matched.setAssigneeId(recruiterId);
        ProductOperationState ignored = state("10002", "9002");
        Page<ProductOperationState> statePage = new Page<>(1, 200, 2);
        statePage.setRecords(List.of(matched, ignored));

        ProductSnapshot snap1 = snapshot("10001", "9001", "玩具乐器", 9900L);
        ProductSnapshot snap2 = snapshot("10002", "9002", "美妆", 8800L);

        when(operationStateMapper.selectPage(any(Page.class), any())).thenReturn(statePage);
        when(snapshotMapper.selectBatchIds(any())).thenReturn(List.of(snap1, snap2));

        var result = service.getSelectedLibraryPage(1, 10, filter().assigneeId(recruiterId.toString()).build());

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRecords()).singleElement().extracting("productId").isEqualTo("9001");
    }

    @Test
    void listLibraryCategories_shouldReturnDistinctSortedNames() {
        when(snapshotMapper.listDisplayingLibraryCategoryNames()).thenReturn(List.of("美妆", "食品饮料", "美妆"));

        assertThat(service.listLibraryCategories()).containsExactly("美妆", "食品饮料");
    }

    private static FilterBuilder filter() {
        return new FilterBuilder();
    }

    private static final class FilterBuilder {
        private String keyword;
        private Integer status;
        private String shopKeyword;
        private String categoryName;
        private String categories;
        private String activityId;
        private String assigneeId;
        private String serviceFee;
        private String supportsAds;
        private String salesRange;
        private String promotionLink;
        private String allianceStatus;
        private String commission;
        private String hasSample;
        private String assignee;
        private String systemTag;
        private String decision;
        private String partnerId;
        private String partnerType;
        private String sortBy;
        private String goodsTags;
        private String productTags;
        private String colonelName;
        private String published;
        private String cooperationType;
        private String livePriceMin;
        private String livePriceMax;
        private String commissionMin;
        private String commissionMax;
        private String sampleSalesMin;
        private String sampleSalesMax;
        private String materialDownload;
        private String exclusivePrice;
        private String productChain;
        private String handCard;
        private String doubleCommission;
        private String notInLibrary;
        private String dedup;
        private String recruitActivityId;
        private String recruitActivityName;
        private String listed;
        private String freeSample;

        FilterBuilder categories(String value) { this.categories = value; return this; }
        FilterBuilder activityId(String value) { this.activityId = value; return this; }
        FilterBuilder assigneeId(String value) { this.assigneeId = value; return this; }
        FilterBuilder colonelName(String value) { this.colonelName = value; return this; }
        FilterBuilder published(String value) { this.published = value; return this; }
        FilterBuilder listed(String value) { this.listed = value; return this; }
        FilterBuilder freeSample(String value) { this.freeSample = value; return this; }
        FilterBuilder livePriceMin(String value) { this.livePriceMin = value; return this; }
        FilterBuilder livePriceMax(String value) { this.livePriceMax = value; return this; }
        FilterBuilder commissionMin(String value) { this.commissionMin = value; return this; }
        FilterBuilder commissionMax(String value) { this.commissionMax = value; return this; }
        FilterBuilder materialDownload(String value) { this.materialDownload = value; return this; }
        FilterBuilder handCard(String value) { this.handCard = value; return this; }
        FilterBuilder doubleCommission(String value) { this.doubleCommission = value; return this; }
        FilterBuilder notInLibrary(String value) { this.notInLibrary = value; return this; }
        FilterBuilder dedup(String value) { this.dedup = value; return this; }

        ProductService.SelectedLibraryFilter build() {
            return new ProductService.SelectedLibraryFilter(
                    keyword, status, shopKeyword, categoryName, categories, activityId, assigneeId,
                    serviceFee, supportsAds, salesRange, promotionLink, allianceStatus, commission, hasSample,
                    assignee, systemTag, decision, partnerId, partnerType, sortBy, goodsTags, productTags,
                    colonelName, published, cooperationType, livePriceMin, livePriceMax, commissionMin, commissionMax,
                    sampleSalesMin, sampleSalesMax, materialDownload, exclusivePrice, productChain, handCard,
                    doubleCommission, notInLibrary, dedup, recruitActivityId, recruitActivityName, listed, freeSample);
        }
    }

    private ProductOperationState state(String activityId, String productId) {
        ProductOperationState state = new ProductOperationState();
        state.setId(UUID.randomUUID());
        state.setActivityId(activityId);
        state.setProductId(productId);
        state.setSelectedToLibrary(true);
        state.setDisplayStatus(ProductDisplayStatus.DISPLAYING.name());
        state.setSelectedAt(LocalDateTime.now());
        state.setBizStatus(ProductBizStatus.APPROVED.name());
        return state;
    }

    private ProductSnapshot snapshot(String activityId, String productId, String category, long priceCent) {
        ProductSnapshot snapshot = new ProductSnapshot();
        snapshot.setId(UUID.nameUUIDFromBytes((activityId + ":" + productId).getBytes(StandardCharsets.UTF_8)));
        snapshot.setActivityId(activityId);
        snapshot.setProductId(productId);
        snapshot.setCategoryName(category);
        snapshot.setPrice(priceCent);
        snapshot.setPriceText("¥99.00");
        snapshot.setStatus(1);
        snapshot.setStatusText("推广中");
        snapshot.setSales(35000L);
        snapshot.setActivityCosRatio(2500L);
        snapshot.setSyncTime(LocalDateTime.now());
        return snapshot;
    }
}
