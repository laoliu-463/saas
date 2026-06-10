package com.colonel.saas.domain.product.facade;

import com.colonel.saas.constant.ProductDisplayStatus;
import com.colonel.saas.domain.product.facade.dto.ActivityProductForSampleDTO;
import com.colonel.saas.domain.product.facade.dto.PartnerBriefDTO;
import com.colonel.saas.domain.product.facade.dto.ProductOwnerDTO;
import com.colonel.saas.entity.ColonelPartner;
import com.colonel.saas.entity.ColonelsettlementActivity;
import com.colonel.saas.entity.Product;
import com.colonel.saas.entity.ProductOperationState;
import com.colonel.saas.entity.ProductSnapshot;
import com.colonel.saas.mapper.ColonelPartnerMapper;
import com.colonel.saas.mapper.ColonelsettlementActivityMapper;
import com.colonel.saas.mapper.ProductOperationStateMapper;
import com.colonel.saas.mapper.ProductSnapshotMapper;
import com.colonel.saas.service.ColonelPartnerSyncService;
import com.colonel.saas.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DDD-PRODUCT-001：ProductDomainFacade 只读门面契约测试。
 */
@ExtendWith(MockitoExtension.class)
class LegacyProductDomainFacadeTest {

    @Mock private ProductService productService;
    @Mock private ProductSnapshotMapper productSnapshotMapper;
    @Mock private ProductOperationStateMapper productOperationStateMapper;
    @Mock private ColonelsettlementActivityMapper colonelActivityMapper;
    @Mock private ColonelPartnerMapper colonelPartnerMapper;
    @Mock private ColonelPartnerSyncService colonelPartnerSyncService;

    private ProductDomainFacade facade;

    @BeforeEach
    void setUp() {
        facade = new LegacyProductDomainFacade(
                productService,
                productSnapshotMapper,
                productOperationStateMapper,
                colonelActivityMapper,
                colonelPartnerMapper,
                colonelPartnerSyncService);
    }

    @Test
    void getActivityProductForSample_shouldReturnSnapshotByRelationId() {
        UUID relationId = relationId("ACT-1", "P-1");
        Product legacyProduct = legacyProduct(relationId, "P-1", "养生茶");
        ProductSnapshot snapshot = snapshot(relationId, "ACT-1", "P-1", "养生茶");
        ProductOperationState state = displayingState("ACT-1", "P-1");

        when(productService.getById(relationId)).thenReturn(legacyProduct);
        when(productSnapshotMapper.selectById(relationId)).thenReturn(snapshot);
        when(productOperationStateMapper.selectOne(any())).thenReturn(state);

        ActivityProductForSampleDTO result = facade.getActivityProductForSample(relationId);

        assertThat(result.relationId()).isEqualTo(relationId);
        assertThat(result.activityId()).isEqualTo("ACT-1");
        assertThat(result.productId()).isEqualTo("P-1");
        assertThat(result.title()).isEqualTo("养生茶");
        assertThat(result.price()).isEqualTo(12900L);
        assertThat(result.shopName()).isEqualTo("样例店铺");
        assertThat(result.displayStatus()).isEqualTo(ProductDisplayStatus.DISPLAYING.name());
        assertThat(result.visibleForSample()).isTrue();
    }

    @Test
    void getRecruiterForActivityProduct_shouldUseActivityProductOwnerFirst() {
        UUID assigneeId = UUID.randomUUID();
        ProductSnapshot snapshot = snapshot(relationId("ACT-1", "P-1"), "ACT-1", "P-1", "养生茶");
        ProductOperationState state = displayingState("ACT-1", "P-1");
        state.setAssigneeId(assigneeId);

        when(productSnapshotMapper.selectOne(any())).thenReturn(snapshot);
        when(productOperationStateMapper.selectOne(any())).thenReturn(state);

        ProductOwnerDTO owner = facade.getRecruiterForActivityProduct("ACT-1", "P-1");

        assertThat(owner.relationId()).isEqualTo(snapshot.getId());
        assertThat(owner.ownerUserId()).isEqualTo(assigneeId);
        assertThat(owner.ownerSource()).isEqualTo("PRODUCT_ASSIGNEE");
        verify(colonelActivityMapper, never()).selectByActivityId(any());
    }

    @Test
    void getRecruiterForActivityProduct_shouldFallbackToActivityDefaultRecruiter() {
        UUID recruiterId = UUID.randomUUID();
        UUID recruiterDeptId = UUID.randomUUID();
        ProductSnapshot snapshot = snapshot(relationId("ACT-2", "P-2"), "ACT-2", "P-2", "护肤品");
        ProductOperationState state = displayingState("ACT-2", "P-2");
        state.setAssigneeId(null);
        ColonelsettlementActivity activity = new ColonelsettlementActivity();
        activity.setActivityId("ACT-2");
        activity.setRecruiterUserId(recruiterId);
        activity.setRecruiterDeptId(recruiterDeptId);

        when(productSnapshotMapper.selectOne(any())).thenReturn(snapshot);
        when(productOperationStateMapper.selectOne(any())).thenReturn(state);
        when(colonelActivityMapper.selectByActivityId("ACT-2")).thenReturn(activity);

        ProductOwnerDTO owner = facade.getRecruiterForActivityProduct("ACT-2", "P-2");

        assertThat(owner.ownerUserId()).isEqualTo(recruiterId);
        assertThat(owner.ownerDeptId()).isEqualTo(recruiterDeptId);
        assertThat(owner.ownerSource()).isEqualTo("ACTIVITY_RECRUITER");
    }

    @Test
    void listPartners_shouldDelegateLegacyPartnerQuery() {
        ColonelPartner partner = new ColonelPartner();
        partner.setId(UUID.randomUUID());
        partner.setColonelBuyinId("1888888888888888888");
        partner.setColonelName("王团长");
        partner.setContactName("王经理");
        partner.setContactPhone("13800000000");
        partner.setSource("BUYIN");

        when(colonelPartnerSyncService.listByNameKeyword("王", 200)).thenReturn(List.of(partner));

        List<PartnerBriefDTO> result = facade.listPartners("王");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).partnerId()).isEqualTo(partner.getId().toString());
        assertThat(result.get(0).colonelBuyinId()).isEqualTo("1888888888888888888");
        assertThat(result.get(0).partnerName()).isEqualTo("王团长");
        verify(colonelPartnerSyncService).listByNameKeyword(eq("王"), eq(200));
    }

    @Test
    void checkProductVisibleForSample_shouldMatchLegacyLibraryVisibilityRules() {
        UUID visibleRelationId = relationId("ACT-1", "P-1");
        ProductSnapshot visibleSnapshot = snapshot(visibleRelationId, "ACT-1", "P-1", "养生茶");
        ProductOperationState visibleState = displayingState("ACT-1", "P-1");
        when(productService.getById(visibleRelationId)).thenReturn(legacyProduct(visibleRelationId, "P-1", "养生茶"));
        when(productSnapshotMapper.selectById(visibleRelationId)).thenReturn(visibleSnapshot);
        when(productOperationStateMapper.selectOne(any())).thenReturn(visibleState);

        assertThat(facade.checkProductVisibleForSample(visibleRelationId)).isTrue();

        UUID pausedRelationId = relationId("ACT-1", "P-2");
        ProductSnapshot pausedSnapshot = snapshot(pausedRelationId, "ACT-1", "P-2", "暂停商品");
        ProductOperationState pausedState = displayingState("ACT-1", "P-2");
        pausedState.setManualDisabled(true);
        when(productService.getById(pausedRelationId)).thenReturn(legacyProduct(pausedRelationId, "P-2", "暂停商品"));
        when(productSnapshotMapper.selectById(pausedRelationId)).thenReturn(pausedSnapshot);
        when(productOperationStateMapper.selectOne(any())).thenReturn(pausedState);

        assertThat(facade.checkProductVisibleForSample(pausedRelationId)).isFalse();
    }

    private static UUID relationId(String activityId, String productId) {
        return UUID.nameUUIDFromBytes((activityId + ":" + productId).getBytes(StandardCharsets.UTF_8));
    }

    private static Product legacyProduct(UUID id, String productId, String name) {
        Product product = new Product();
        product.setId(id);
        product.setProductId(productId);
        product.setName(name);
        return product;
    }

    private static ProductSnapshot snapshot(UUID id, String activityId, String productId, String title) {
        ProductSnapshot snapshot = new ProductSnapshot();
        snapshot.setId(id);
        snapshot.setActivityId(activityId);
        snapshot.setProductId(productId);
        snapshot.setTitle(title);
        snapshot.setCover("https://img.example/" + productId + ".jpg");
        snapshot.setPrice(12900L);
        snapshot.setPriceText("129.00");
        snapshot.setShopId(10001L);
        snapshot.setShopName("样例店铺");
        snapshot.setStatus(1);
        snapshot.setStatusText("推广中");
        snapshot.setCategoryName("食品饮料");
        snapshot.setDetailUrl("https://product.example/" + productId);
        snapshot.setPromotionStartTime("2026-06-01 00:00:00");
        snapshot.setPromotionEndTime("2026-06-30 23:59:59");
        snapshot.setActivityCosRatio(2500L);
        snapshot.setActivityCosRatioText("25%");
        snapshot.setSyncTime(LocalDateTime.now());
        return snapshot;
    }

    private static ProductOperationState displayingState(String activityId, String productId) {
        ProductOperationState state = new ProductOperationState();
        state.setId(relationId(activityId, productId));
        state.setActivityId(activityId);
        state.setProductId(productId);
        state.setSelectedToLibrary(true);
        state.setDisplayStatus(ProductDisplayStatus.DISPLAYING.name());
        state.setManualDisabled(false);
        state.setAuditStatus(2);
        return state;
    }
}
