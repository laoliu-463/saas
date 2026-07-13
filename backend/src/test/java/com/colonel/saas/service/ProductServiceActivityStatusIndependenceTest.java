package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.colonel.saas.common.enums.ProductBizStatus;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.common.handler.UUIDTypeHandler;
import com.colonel.saas.constant.ProductDisplayStatus;
import com.colonel.saas.domain.order.facade.OrderReadFacade;
import com.colonel.saas.domain.order.facade.PromotionLinkRecordFacade;
import com.colonel.saas.domain.product.application.dto.ActivityProductRefreshRequest;
import com.colonel.saas.domain.product.policy.ProductDisplayPolicy;
import com.colonel.saas.douyin.DouyinApiException;
import com.colonel.saas.entity.ColonelsettlementActivity;
import com.colonel.saas.entity.ProductOperationState;
import com.colonel.saas.entity.ProductSnapshot;
import com.colonel.saas.gateway.douyin.DouyinProductGateway;
import com.colonel.saas.mapper.ColonelsettlementActivityMapper;
import com.colonel.saas.mapper.ProductOperationStateMapper;
import com.colonel.saas.mapper.ProductSnapshotMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 活动状态与商品状态独立性测试。
 *
 * <p>活动状态只描述活动自身；商品是否入库、审核通过、展示中，只由商品自身流程和展示规则决定。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductServiceActivityStatusIndependenceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Mock private com.colonel.saas.domain.product.application.port.DouyinConvertPort douyinConvertPort;
    @Mock private DouyinProductGateway douyinProductGateway;
    @Mock private ProductSnapshotMapper snapshotMapper;
    @Mock private ProductOperationStateMapper operationStateMapper;
    @Mock private com.colonel.saas.mapper.ProductOperationLogMapper operationLogMapper;
    @Mock private PromotionLinkRecordFacade promotionLinkRecordFacade;
    @Mock private OrderReadFacade orderReadFacade;
    @Mock private com.colonel.saas.mapper.MerchantMapper merchantMapper;
    @Mock private com.colonel.saas.domain.user.facade.UserDomainFacade userDomainFacade;
    @Mock private PickSourceMappingService pickSourceMappingService;
    @Mock private ProductBizStatusService productBizStatusService;
    @Mock private ColonelsettlementActivityMapper colonelActivityMapper;
    @Mock private TalentFollowService talentFollowService;
    @Mock private com.colonel.saas.gateway.douyin.DouyinActivityGateway douyinActivityGateway;
    @Mock private PromotionLinkIdempotencyService promotionLinkIdempotencyService;
    @Mock private com.colonel.saas.domain.config.facade.ConfigDomainFacade configDomainFacade;
    @Mock private ProductDisplayRuleService productDisplayRuleService;
    @Mock private ColonelPartnerSyncService colonelPartnerSyncService;
    @Mock private com.colonel.saas.domain.product.event.ProductDomainEventPublisher productDomainEventPublisher;
    private ProductDisplayPolicy productDisplayPolicy;
    private ProductService productService;

    @BeforeEach
    void setUp() {
        initTableInfo(ProductSnapshot.class);
        productDisplayPolicy = spy(new ProductDisplayPolicy());
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
                productDisplayPolicy,
                null);
        when(snapshotMapper.upsert(any(ProductSnapshot.class))).thenReturn(1);
        when(operationStateMapper.updateById(any(ProductOperationState.class))).thenReturn(1);
        when(productDisplayRuleService.repairLibraryStateForActivity(any(), eq(false), anyInt()))
                .thenReturn(ProductDisplayRuleService.LibraryRepairResult.empty(null, false));
        when(productDisplayRuleService.repairLibraryStateForActivityProducts(any(), any(), eq(false), anyInt()))
                .thenReturn(ProductDisplayRuleService.LibraryRepairResult.empty(null, false));
    }

    private void initTableInfo(Class<?> entityClass) {
        if (TableInfoHelper.getTableInfo(entityClass) == null) {
            MybatisConfiguration configuration = new MybatisConfiguration();
            configuration.getTypeHandlerRegistry().register(java.util.UUID.class, UUIDTypeHandler.class);
            MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
            TableInfoHelper.initTableInfo(assistant, entityClass);
        }
    }

    @Test
    void applyActivityProductPromotionStatusFilter_defaultOffShouldKeepPublicTerminatedOnly() {
        LambdaQueryWrapper<ProductSnapshot> wrapper = new LambdaQueryWrapper<>();

        ReflectionTestUtils.invokeMethod(
                productService,
                "applyActivityProductPromotionStatusFilter",
                wrapper,
                3);

        verify(productDisplayPolicy, never()).activityProductFilterStatuses(any());
        assertTerminatedStatusFilter(wrapper);
    }

    @Test
    void applyActivityProductPromotionStatusFilter_defaultOffShouldIncludeCanceledWhenNoStatusSelected() {
        LambdaQueryWrapper<ProductSnapshot> wrapper = new LambdaQueryWrapper<>();

        ReflectionTestUtils.invokeMethod(
                productService,
                "applyActivityProductPromotionStatusFilter",
                wrapper,
                null);

        verify(productDisplayPolicy, never()).activityProductFilterStatuses(any());
        assertThat(wrapper.getSqlSegment()).contains("status", "IN");
        assertThat(wrapper.getParamNameValuePairs().values()).containsExactlyInAnyOrder(0, 1, 2, 3, 4, 6);
    }

    @Test
    void applyActivityProductPromotionStatusFilter_dddSwitchOnShouldDelegateToDisplayPolicy() {
        ReflectionTestUtils.setField(productService, "dddRefactorEnabled", true);
        ReflectionTestUtils.setField(productService, "dddProductDisplayPolicyEnabled", true);
        LambdaQueryWrapper<ProductSnapshot> wrapper = new LambdaQueryWrapper<>();

        ReflectionTestUtils.invokeMethod(
                productService,
                "applyActivityProductPromotionStatusFilter",
                wrapper,
                3);

        verify(productDisplayPolicy).activityProductFilterStatuses(3);
        assertTerminatedStatusFilter(wrapper);
    }

    @Test
    void upsertSnapshotsWithStats_promotingProductShouldAutoEnterLibraryAndInheritAssignee() {
        String activityId = "ACT001";
        UUID recruiterId = UUID.randomUUID();
        when(colonelActivityMapper.selectByActivityId(activityId))
                .thenReturn(activity(activityId, 5, "推广中", recruiterId));
        when(operationStateMapper.selectOne(any())).thenReturn(null);
        when(productBizStatusService.initStateIfAbsent(any(), eq(activityId), eq("1"), any(), any(), any()))
                .thenReturn(state(activityId, "1"));

        ProductService.ActivitySnapshotUpsertStats stats =
                productService.upsertSnapshotsWithStats(activityId, List.of(item(1L, "测试商品")));

        assertThat(stats.createdCount()).isEqualTo(1);
        assertThat(stats.libraryEntryCount()).isEqualTo(1);
        ArgumentCaptor<ProductOperationState> captor = ArgumentCaptor.forClass(ProductOperationState.class);
        verify(operationStateMapper).updateById(captor.capture());
        ProductOperationState saved = captor.getValue();
        assertThat(saved.getAssigneeId()).isEqualTo(recruiterId);
        assertThat(saved.getSelectedToLibrary()).isTrue();
        assertThat(saved.getBizStatus()).isEqualTo(ProductBizStatus.APPROVED.name());
        assertThat(saved.getAuditStatus()).isEqualTo(2);
        assertThat(saved.getAuditRemark()).isEqualTo("上游状态为推广中，系统自动入库展示");
        assertThat(saved.getDisplayStatus()).isEqualTo(ProductDisplayStatus.PENDING.name());
        assertThat(saved.getHiddenReason()).isNull();
        verify(productDisplayRuleService, never()).applyForActivityId(any());
    }

    @Test
    void upsertSnapshotsWithStats_promotingProductWithoutActivityAssigneeShouldAutoEnterLibrary() {
        String activityId = "ACT002";
        when(colonelActivityMapper.selectByActivityId(activityId))
                .thenReturn(activity(activityId, 5, "推广中", null));
        when(operationStateMapper.selectOne(any())).thenReturn(null);
        when(productBizStatusService.initStateIfAbsent(any(), eq(activityId), eq("2"), any(), any(), any()))
                .thenReturn(state(activityId, "2"));

        ProductService.ActivitySnapshotUpsertStats stats =
                productService.upsertSnapshotsWithStats(activityId, List.of(item(2L, "测试商品")));

        assertThat(stats.libraryEntryCount()).isEqualTo(1);
        ArgumentCaptor<ProductOperationState> captor = ArgumentCaptor.forClass(ProductOperationState.class);
        verify(operationStateMapper).updateById(captor.capture());
        ProductOperationState saved = captor.getValue();
        assertThat(saved.getAssigneeId()).isNull();
        assertThat(saved.getSelectedToLibrary()).isTrue();
        assertThat(saved.getBizStatus()).isEqualTo(ProductBizStatus.APPROVED.name());
        assertThat(saved.getAuditStatus()).isEqualTo(2);
        verify(productDisplayRuleService, never()).applyForActivityId(any());
    }

    @Test
    void upsertSnapshotsWithStats_shouldDependOnProductPromotingStatusNotActivityStatus() {
        String activityId = "ACT003";
        UUID recruiterId = UUID.randomUUID();
        when(colonelActivityMapper.selectByActivityId(activityId))
                .thenReturn(activity(activityId, 3, "报名中", recruiterId));
        when(operationStateMapper.selectOne(any())).thenReturn(null);
        when(productBizStatusService.initStateIfAbsent(any(), eq(activityId), eq("3"), any(), any(), any()))
                .thenReturn(state(activityId, "3"));

        productService.upsertSnapshotsWithStats(activityId, List.of(item(3L, "测试商品")));

        ArgumentCaptor<ProductOperationState> captor = ArgumentCaptor.forClass(ProductOperationState.class);
        verify(operationStateMapper).updateById(captor.capture());
        ProductOperationState saved = captor.getValue();
        assertThat(saved.getAssigneeId()).isEqualTo(recruiterId);
        assertThat(saved.getSelectedToLibrary()).isTrue();
        assertThat(saved.getBizStatus()).isEqualTo(ProductBizStatus.APPROVED.name());
        assertThat(saved.getAuditStatus()).isEqualTo(2);
        assertThat(saved.getDisplayStatus()).isEqualTo(ProductDisplayStatus.PENDING.name());
        verify(productDisplayRuleService, never()).applyForActivityId(any());
    }

    @Test
    void upsertSnapshotsWithStats_nonPromotingProductShouldHideAndLeaveLibrary() {
        String activityId = "ACT005";
        ProductOperationState existing = state(activityId, "5");
        existing.setSelectedToLibrary(true);
        existing.setDisplayStatus(ProductDisplayStatus.DISPLAYING.name());
        existing.setBizStatus(ProductBizStatus.APPROVED.name());
        existing.setAuditStatus(2);

        when(colonelActivityMapper.selectByActivityId(activityId))
                .thenReturn(activity(activityId, 5, "推广中", null));
        when(operationStateMapper.selectOne(any())).thenReturn(existing);
        when(productBizStatusService.initStateIfAbsent(any(), eq(activityId), eq("5"), any(), any(), any()))
                .thenReturn(existing);

        ProductService.ActivitySnapshotUpsertStats stats =
                productService.upsertSnapshotsWithStats(activityId, List.of(item(5L, "测试商品", 2, "申请未通过")));

        assertThat(stats.libraryEntryCount()).isEqualTo(0);
        ArgumentCaptor<ProductOperationState> captor = ArgumentCaptor.forClass(ProductOperationState.class);
        verify(operationStateMapper).updateById(captor.capture());
        ProductOperationState saved = captor.getValue();
        assertThat(saved.getSelectedToLibrary()).isFalse();
        assertThat(saved.getDisplayStatus()).isEqualTo(ProductDisplayStatus.HIDDEN.name());
        assertThat(saved.getHiddenReason()).isEqualTo("UPSTREAM_NOT_PROMOTING");
    }

    @Test
    void upsertSnapshotsWithStats_upstreamPromotingShouldOverrideHistoricalLocalRejected() {
        String activityId = "ACT006";
        ProductOperationState rejected = state(activityId, "6");
        rejected.setBizStatus(ProductBizStatus.REJECTED.name());
        rejected.setAuditStatus(3);
        rejected.setSelectedToLibrary(false);

        when(colonelActivityMapper.selectByActivityId(activityId))
                .thenReturn(activity(activityId, 5, "推广中", null));
        when(operationStateMapper.selectOne(any())).thenReturn(rejected);
        when(productBizStatusService.initStateIfAbsent(any(), eq(activityId), eq("6"), any(), any(), any()))
                .thenReturn(rejected);

        ProductService.ActivitySnapshotUpsertStats stats =
                productService.upsertSnapshotsWithStats(activityId, List.of(item(6L, "测试商品")));

        assertThat(stats.libraryEntryCount()).isEqualTo(1);
        ArgumentCaptor<ProductOperationState> captor = ArgumentCaptor.forClass(ProductOperationState.class);
        verify(operationStateMapper).updateById(captor.capture());
        ProductOperationState saved = captor.getValue();
        assertThat(saved.getBizStatus()).isEqualTo(ProductBizStatus.APPROVED.name());
        assertThat(saved.getAuditStatus()).isEqualTo(2);
        assertThat(saved.getSelectedToLibrary()).isTrue();
        assertThat(saved.getDisplayStatus()).isEqualTo(ProductDisplayStatus.PENDING.name());
        assertThat(saved.getHiddenReason()).isNull();
        assertThat(saved.getAuditRemark()).isEqualTo("上游状态为推广中，系统自动入库展示");
    }

    @Test
    void upsertSnapshotsWithStats_manualPausedPromotingProductShouldStayPausedButEnterLibrary() {
        String activityId = "ACT009";
        ProductOperationState paused = state(activityId, "9");
        paused.setManualDisabled(true);
        paused.setSelectedToLibrary(false);
        paused.setDisplayStatus(ProductDisplayStatus.PENDING.name());
        paused.setHiddenReason(null);

        when(colonelActivityMapper.selectByActivityId(activityId))
                .thenReturn(activity(activityId, 5, "推广中", null));
        when(operationStateMapper.selectOne(any())).thenReturn(paused);
        when(productBizStatusService.initStateIfAbsent(any(), eq(activityId), eq("9"), any(), any(), any()))
                .thenReturn(paused);

        ProductService.ActivitySnapshotUpsertStats stats =
                productService.upsertSnapshotsWithStats(activityId, List.of(item(9L, "暂停商品")));

        assertThat(stats.libraryEntryCount()).isEqualTo(1);
        ArgumentCaptor<ProductOperationState> captor = ArgumentCaptor.forClass(ProductOperationState.class);
        verify(operationStateMapper).updateById(captor.capture());
        ProductOperationState saved = captor.getValue();
        assertThat(saved.getSelectedToLibrary()).isTrue();
        assertThat(saved.getAuditStatus()).isEqualTo(2);
        assertThat(saved.getBizStatus()).isEqualTo(ProductBizStatus.APPROVED.name());
        assertThat(saved.getManualDisabled()).isTrue();
        assertThat(saved.getDisplayStatus()).isEqualTo(ProductDisplayStatus.HIDDEN.name());
        assertThat(saved.getHiddenReason()).isEqualTo("LOCAL_PAUSED");
    }

    @Test
    void buildActivityProductListView_shouldExposeCanonicalStatusFieldsForPromotingPausedRow() {
        String activityId = "12345";
        ProductOperationState paused = state(activityId, "10");
        paused.setManualDisabled(true);
        paused.setSelectedToLibrary(true);
        paused.setAuditStatus(2);
        paused.setBizStatus(ProductBizStatus.APPROVED.name());
        paused.setDisplayStatus(ProductDisplayStatus.HIDDEN.name());
        paused.setHiddenReason("LOCAL_PAUSED");

        when(operationStateMapper.selectList(any())).thenReturn(List.of(paused), List.of());
        when(productBizStatusService.readBizStatus(paused)).thenReturn(ProductBizStatus.APPROVED);

        Map<String, Object> view = productService.buildActivityProductListView(
                new DouyinProductGateway.ActivityProductListResult(
                        false,
                        Long.parseLong(activityId),
                        30001L,
                        1L,
                        null,
                        List.of(item(10L, "暂停商品"))));

        @SuppressWarnings("unchecked")
        Map<String, Object> row = ((List<Map<String, Object>>) view.get("items")).get(0);
        assertThat(row)
                .containsEntry("officialStatus", "PROMOTING")
                .containsEntry("reviewStatus", "APPROVED")
                .containsEntry("publishStatus", "PAUSED")
                .containsEntry("manualDisabled", true)
                .containsEntry("selectedToLibrary", true)
                .containsEntry("displayStatus", ProductDisplayStatus.HIDDEN.name())
                .containsEntry("hiddenReason", "LOCAL_PAUSED");
    }

    @Test
    void buildActivityProductListView_unknownUpstreamStatusShouldNotDefaultToPromoting() {
        String activityId = "12345";
        when(operationStateMapper.selectList(any())).thenReturn(List.of(), List.of());

        Map<String, Object> view = productService.buildActivityProductListView(
                new DouyinProductGateway.ActivityProductListResult(
                        false,
                        Long.parseLong(activityId),
                        30001L,
                        1L,
                        null,
                        List.of(item(11L, "未知状态商品", 9, ""))));

        @SuppressWarnings("unchecked")
        Map<String, Object> row = ((List<Map<String, Object>>) view.get("items")).get(0);
        assertThat(row)
                .containsEntry("officialStatus", "PENDING_REVIEW")
                .containsEntry("reviewStatus", "PENDING")
                .containsEntry("publishStatus", "UNPUBLISHED")
                .containsEntry("selectedToLibrary", false);
    }

    @Test
    void buildActivityProductListViewFromDb_shouldExposeFullActivityStatusCounts() {
        String activityId = "3916506";
        when(snapshotMapper.selectCount(any())).thenReturn(1274L);
        when(snapshotMapper.selectPageSorted(
                eq(activityId),
                isNull(),
                isNull(),
                eq("NONE"),
                isNull(),
                isNull(),
                isNull(),
                eq(20L),
                eq(0L),
                any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(snapshotMapper.selectActivityStatusCounts(activityId)).thenReturn(Map.of(
                "total", 1274L,
                "pendingReview", 10L,
                "promoting", 726L,
                "rejected", 486L,
                "terminated", 46L,
                "canceled", 4L,
                "expired", 6L));

        Map<String, Object> view = productService.buildActivityProductListViewFromDb(
                activityId, 20, null, null, null, null, null, null, null);

        assertThat(view.get("total")).isEqualTo(1274L);
        assertThat(view.get("statusCounts")).isEqualTo(Map.of(
                "total", 1274L,
                "pendingReview", 10L,
                "promoting", 726L,
                "rejected", 486L,
                "terminated", 46L,
                "canceled", 4L,
                "expired", 6L));
    }

    @Test
    void buildActivityProductListView_realtimeRowsShouldExposeSnapshotRelationId() {
        String activityId = "12345";
        long productId = 12L;
        when(operationStateMapper.selectList(any())).thenReturn(List.of(), List.of());

        Map<String, Object> view = productService.buildActivityProductListView(
                new DouyinProductGateway.ActivityProductListResult(
                        false,
                        Long.parseLong(activityId),
                        30001L,
                        1L,
                        null,
                        List.of(item(productId, "实时商品"))));

        @SuppressWarnings("unchecked")
        Map<String, Object> row = ((List<Map<String, Object>>) view.get("items")).get(0);
        UUID expectedRelationId = UUID.nameUUIDFromBytes((activityId + ":" + productId).getBytes(StandardCharsets.UTF_8));
        assertThat(row)
                .containsEntry("productId", productId)
                .containsEntry("relationId", expectedRelationId);
    }

    @Test
    void auditProduct_rejectShouldHideAndRemoveFromLibrary() {
        String activityId = "ACT004";
        String productId = "4";
        ProductSnapshot snapshot = snapshot(activityId, productId);
        ProductOperationState state = state(activityId, productId);
        state.setSelectedToLibrary(true);
        state.setDisplayStatus(ProductDisplayStatus.DISPLAYING.name());

        when(snapshotMapper.selectOne(any())).thenReturn(snapshot);
        when(operationStateMapper.selectOne(any())).thenReturn(state);
        when(productBizStatusService.readBizStatus(state)).thenReturn(ProductBizStatus.PENDING_AUDIT);
        when(productBizStatusService.changeStatus(
                eq(state),
                eq(ProductBizStatus.REJECTED),
                eq("AUDIT"),
                any(),
                any(),
                any(),
                eq("审核拒绝"),
                any(ProductBizStatusService.StatusMutation.class)))
                .thenAnswer(invocation -> {
                    ProductBizStatusService.StatusMutation mutation = invocation.getArgument(7);
                    mutation.apply(state);
                    state.setBizStatus(ProductBizStatus.REJECTED.name());
                    return state;
                });

        Map<String, Object> detail = productService.auditProduct(activityId, productId, false, "不符合商品库要求", null, null);

        assertThat(state.getSelectedToLibrary()).isFalse();
        assertThat(state.getDisplayStatus()).isEqualTo(ProductDisplayStatus.HIDDEN.name());
        assertThat(state.getAuditStatus()).isEqualTo(3);
        assertThat(state.getAuditRemark()).isEqualTo("不符合商品库要求");
        assertThat(detail.get("selectedToLibrary")).isEqualTo(false);
        assertThat(detail.get("displayStatus")).isEqualTo(ProductDisplayStatus.HIDDEN.name());
        verify(productDisplayRuleService, never()).applyForProductId(productId);
    }

    @Test
    void auditProduct_approveShouldRejectRecentSameProductAlreadyInLibrary() {
        String activityId = "ACT005";
        String productId = "5";
        ProductSnapshot snapshot = snapshot(activityId, productId);
        ProductOperationState current = state(activityId, productId);
        ProductOperationState existing = state("ACT_EXISTING", productId);
        existing.setAuditStatus(2);
        existing.setSelectedToLibrary(true);
        existing.setSelectedAt(LocalDateTime.now().minusMonths(2));
        existing.setDisplayStatus(ProductDisplayStatus.DISPLAYING.name());

        when(snapshotMapper.selectOne(any())).thenReturn(snapshot);
        when(operationStateMapper.selectOne(any())).thenReturn(current);
        when(operationStateMapper.selectList(any())).thenReturn(List.of(existing));
        when(productBizStatusService.readBizStatus(current)).thenReturn(ProductBizStatus.PENDING_AUDIT);

        assertThatThrownBy(() -> productService.auditProduct(
                activityId,
                productId,
                true,
                "素材完整",
                validAuditSupplement(),
                null,
                null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("近三个月")
                .hasMessageContaining(productId);
        assertThat(current.getSelectedToLibrary()).isNull();
        verify(productDisplayRuleService, never()).applyForProductId(productId);
    }

    @Test
    void auditProduct_approveShouldPersistNormalizedAuditPayload() throws Exception {
        String activityId = "ACT006";
        String productId = "6";
        UUID operatorId = UUID.randomUUID();
        UUID operatorDeptId = UUID.randomUUID();
        ProductSnapshot snapshot = snapshot(activityId, productId);
        ProductOperationState state = state(activityId, productId);
        Map<String, Object> supplement = new LinkedHashMap<>();
        supplement.put("exclusivePriceRemark", " 直播间专属价 129 元 ");
        supplement.put("shippingInfo", "48 小时内发货");
        supplement.put("sellingPoints", List.of("高转化卖点", " "));
        supplement.put("promotionScript", "达人可直接口播");
        supplement.put("supportsAds", true);
        supplement.put("adsRule", "投流 1:0.5");
        supplement.put("rewardRemark", "达标后额外奖励");
        supplement.put("participationRequirements", "粉丝画像匹配");
        supplement.put("campaignTimeRemark", "活动期内有效");
        supplement.put("materialFiles", List.of("https://material.test/card.png", " "));
        supplement.put("goodsTags", List.of("食品", "夏季"));
        supplement.put("productTags", List.of("主推"));
        supplement.put("sampleThresholdSales", 30000L);
        supplement.put("sampleThresholdLevel", 2);
        supplement.put("sampleThresholdRemark", "需真人出镜");

        when(snapshotMapper.selectOne(any())).thenReturn(snapshot);
        when(operationStateMapper.selectOne(any())).thenReturn(state);
        when(operationStateMapper.selectList(any())).thenReturn(List.of());
        when(productBizStatusService.readBizStatus(state)).thenReturn(ProductBizStatus.PENDING_AUDIT);
        when(productBizStatusService.changeStatus(
                eq(state),
                eq(ProductBizStatus.APPROVED),
                eq("AUDIT"),
                eq(operatorId),
                eq(operatorDeptId),
                any(),
                eq("审核通过，已加入商品库"),
                any(ProductBizStatusService.StatusMutation.class)))
                .thenAnswer(invocation -> {
                    ProductBizStatusService.StatusMutation mutation = invocation.getArgument(7);
                    mutation.apply(state);
                    state.setBizStatus(ProductBizStatus.APPROVED.name());
                    return state;
                });

        productService.auditProduct(activityId, productId, true, "素材完整", supplement, operatorId, operatorDeptId);

        assertThat(state.getSelectedToLibrary()).isTrue();
        assertThat(state.getAuditStatus()).isEqualTo(2);
        assertThat(state.getAuditRemark()).isNull();
        assertThat(state.getAuditPayload()).isNotBlank();
        Map<String, Object> persisted = OBJECT_MAPPER.readValue(
                state.getAuditPayload(),
                new TypeReference<Map<String, Object>>() {});
        assertThat(persisted)
                .containsEntry("exclusivePriceRemark", "直播间专属价 129 元")
                .containsEntry("shippingInfo", "48 小时内发货")
                .containsEntry("promotionScript", "达人可直接口播")
                .containsEntry("supportsAds", true)
                .containsEntry("adsRule", "投流 1:0.5")
                .containsEntry("rewardRemark", "达标后额外奖励")
                .containsEntry("participationRequirements", "粉丝画像匹配")
                .containsEntry("campaignTimeRemark", "活动期内有效")
                .containsEntry("sampleThresholdRemark", "需真人出镜");
        assertThat(persisted.get("sellingPoints")).asList().containsExactly("高转化卖点");
        assertThat(persisted.get("materialFiles")).asList().containsExactly("https://material.test/card.png");
        assertThat(persisted.get("goodsTags")).asList().containsExactly("食品", "夏季");
        assertThat(persisted.get("productTags")).asList().containsExactly("主推");
        assertThat(((Number) persisted.get("sampleThresholdSales")).longValue()).isEqualTo(30000L);
        assertThat(((Number) persisted.get("sampleThresholdLevel")).longValue()).isEqualTo(2L);
        verify(productDisplayRuleService).applyForProductId(productId);
    }

    @Test
    void upsertSnapshots_shouldPreserveLocalOperationStateFieldsWhenSnapshotRefreshes() {
        String activityId = "ACT007";
        String productId = "7";
        UUID assigneeId = UUID.randomUUID();
        UUID selectedBy = UUID.randomUUID();
        UUID pinnedBy = UUID.randomUUID();
        LocalDateTime selectedAt = LocalDateTime.of(2026, 6, 1, 10, 0);
        LocalDateTime firstDisplayedAt = LocalDateTime.of(2026, 6, 1, 10, 5);
        LocalDateTime lastDisplayedAt = LocalDateTime.of(2026, 6, 1, 11, 0);
        LocalDateTime pinnedAt = LocalDateTime.of(2026, 6, 1, 11, 5);
        LocalDateTime pinnedUntil = LocalDateTime.of(2026, 6, 2, 11, 5);

        ProductOperationState existing = state(activityId, productId);
        existing.setAuditStatus(2);
        existing.setAuditRemark("人工审核通过");
        existing.setAuditPayload("{\"operator\":\"local\"}");
        existing.setAssigneeId(assigneeId);
        existing.setSelectedToLibrary(true);
        existing.setSelectedAt(selectedAt);
        existing.setSelectedBy(selectedBy);
        existing.setDisplayStatus(ProductDisplayStatus.DISPLAYING.name());
        existing.setHiddenReason(null);
        existing.setFirstDisplayedAt(firstDisplayedAt);
        existing.setLastDisplayedAt(lastDisplayedAt);
        existing.setBizStatus(ProductBizStatus.APPROVED.name());
        existing.setPromoteLink("https://promo.test/link");
        existing.setShortLink("https://s.test/a");
        existing.setPinnedAt(pinnedAt);
        existing.setPinnedUntil(pinnedUntil);
        existing.setPinnedBy(pinnedBy);

        when(colonelActivityMapper.selectByActivityId(activityId))
                .thenReturn(activity(activityId, 5, "推广中", UUID.randomUUID()));
        when(operationStateMapper.selectOne(any())).thenReturn(existing);
        when(productBizStatusService.initStateIfAbsent(any(), eq(activityId), eq(productId), any(), any(), any()))
                .thenReturn(existing);

        productService.upsertSnapshots(activityId, List.of(item(7L, "上游标题已更新")));

        assertThat(existing.getAuditStatus()).isEqualTo(2);
        assertThat(existing.getAuditRemark()).isEqualTo("人工审核通过");
        assertThat(existing.getAuditPayload()).isEqualTo("{\"operator\":\"local\"}");
        assertThat(existing.getAssigneeId()).isEqualTo(assigneeId);
        assertThat(existing.getSelectedToLibrary()).isTrue();
        assertThat(existing.getSelectedAt()).isEqualTo(selectedAt);
        assertThat(existing.getSelectedBy()).isEqualTo(selectedBy);
        assertThat(existing.getDisplayStatus()).isEqualTo(ProductDisplayStatus.DISPLAYING.name());
        assertThat(existing.getHiddenReason()).isNull();
        assertThat(existing.getFirstDisplayedAt()).isEqualTo(firstDisplayedAt);
        assertThat(existing.getLastDisplayedAt()).isEqualTo(lastDisplayedAt);
        assertThat(existing.getBizStatus()).isEqualTo(ProductBizStatus.APPROVED.name());
        assertThat(existing.getPromoteLink()).isEqualTo("https://promo.test/link");
        assertThat(existing.getShortLink()).isEqualTo("https://s.test/a");
        assertThat(existing.getPinnedAt()).isEqualTo(pinnedAt);
        assertThat(existing.getPinnedUntil()).isEqualTo(pinnedUntil);
        assertThat(existing.getPinnedBy()).isEqualTo(pinnedBy);
        verify(operationStateMapper, never()).updateById(any(ProductOperationState.class));
    }

    @Test
    void refreshActivitySnapshots_shouldAcceptApplicationRefreshRequestAndReuseRefreshFlow() {
        String activityId = "ACT009";
        List<DouyinProductGateway.ActivityProductQueryRequest> requests = new ArrayList<>();
        when(douyinProductGateway.queryActivityProducts(any())).thenAnswer(invocation -> {
            DouyinProductGateway.ActivityProductQueryRequest request = invocation.getArgument(0);
            requests.add(request);
            return new DouyinProductGateway.ActivityProductListResult(
                    false,
                    9L,
                    30001L,
                    1L,
                    null,
                    List.of(item(9L, "DTO刷新商品")));
        });
        when(operationStateMapper.selectOne(any())).thenReturn(null);
        when(productBizStatusService.initStateIfAbsent(any(), eq(activityId), eq("9"), any(), any(), any()))
                .thenReturn(state(activityId, "9"));
        when(productDisplayRuleService.repairLibraryStateForActivity(activityId, false, 10000))
                .thenReturn(new ProductDisplayRuleService.LibraryRepairResult(
                        activityId, false, 1, 1, 1, 1, 0, 0, 0, 0, List.of()));

        ProductService.ActivityProductRefreshResult result = productService.refreshActivitySnapshots(
                new ActivityProductRefreshRequest(
                        "app-1", activityId, 4L, 2L, 19, "合作", 0,
                        "商品", 1, 1L, "cursor-1", 3L));

        assertThat(result.syncedProductCount()).isEqualTo(1);
        assertThat(requests).hasSize(1);
        DouyinProductGateway.ActivityProductQueryRequest request = requests.get(0);
        assertThat(request.appId()).isEqualTo("app-1");
        assertThat(request.activityId()).isEqualTo(activityId);
        assertThat(request.searchType()).isEqualTo(4L);
        assertThat(request.sortType()).isEqualTo(2L);
        assertThat(request.count()).isEqualTo(19);
        assertThat(request.cooperationInfo()).isEqualTo("合作");
        assertThat(request.cooperationType()).isEqualTo(0);
        assertThat(request.productInfo()).isEqualTo("商品");
        assertThat(request.status()).isEqualTo(1);
        assertThat(request.retrieveMode()).isEqualTo(1L);
        assertThat(request.cursor()).isEqualTo("cursor-1");
        assertThat(request.page()).isNull();
    }

    @Test
    void refreshActivitySnapshots_shouldRepairLibraryStateBeforeApplyingDisplayRule() {
        String activityId = "ACT008";
        when(douyinProductGateway.queryActivityProducts(any()))
                .thenReturn(new DouyinProductGateway.ActivityProductListResult(
                        false,
                        Long.parseLong(activityId.replace("ACT", "")),
                        30001L,
                        1L,
                        null,
                        List.of(item(8L, "刷新商品"))));
        when(operationStateMapper.selectOne(any())).thenReturn(null);
        when(productBizStatusService.initStateIfAbsent(any(), eq(activityId), eq("8"), any(), any(), any()))
                .thenReturn(state(activityId, "8"));
        when(productDisplayRuleService.repairLibraryStateForActivity(activityId, false, 10000))
                .thenReturn(new ProductDisplayRuleService.LibraryRepairResult(
                        activityId, false, 1, 1, 1, 1, 0, 0, 0, 0, List.of()));

        ProductService.ActivityProductRefreshResult result = productService.refreshActivitySnapshots(
                new DouyinProductGateway.ActivityProductQueryRequest(
                        null, activityId, 4L, 1L, 20, null, null, null, null, 1L, null, null));

        assertThat(result.syncedProductCount()).isEqualTo(1);
        InOrder inOrder = inOrder(productDisplayRuleService);
        inOrder.verify(productDisplayRuleService).repairLibraryStateForActivity(activityId, false, 10000);
        inOrder.verify(productDisplayRuleService).applyForActivityId(activityId);
    }

    @Test
    void refreshActivitySnapshots_whenPageRefreshEnabled_shouldPublishPageStateBeforeFullReconcile() {
        String activityId = "ACT016";
        ReflectionTestUtils.setField(productService, "pageLibraryRefreshEnabled", true);
        when(douyinProductGateway.queryActivityProducts(any()))
                .thenReturn(new DouyinProductGateway.ActivityProductListResult(
                        false,
                        16L,
                        30001L,
                        1L,
                        null,
                        List.of(item(16L, "分页即时刷新商品"))));
        when(operationStateMapper.selectOne(any())).thenReturn(null);
        when(productBizStatusService.initStateIfAbsent(any(), eq(activityId), eq("16"), any(), any(), any()))
                .thenReturn(state(activityId, "16"));
        when(productDisplayRuleService.repairLibraryStateForActivityProducts(
                eq(activityId), any(), eq(false), eq(1)))
                .thenReturn(ProductDisplayRuleService.LibraryRepairResult.empty(activityId, false));
        when(productDisplayRuleService.repairLibraryStateForActivity(activityId, false, 10000))
                .thenReturn(ProductDisplayRuleService.LibraryRepairResult.empty(activityId, false));

        ProductService.ActivityProductRefreshResult result = productService.refreshActivitySnapshots(
                new DouyinProductGateway.ActivityProductQueryRequest(
                        null, activityId, 4L, 1L, 20, null, null, null, null, 1L, null, null));

        assertThat(result.syncedProductCount()).isEqualTo(1);
        InOrder inOrder = inOrder(productDisplayRuleService);
        inOrder.verify(productDisplayRuleService).repairLibraryStateForActivityProducts(
                eq(activityId), argThat(ids -> ids.contains("16")), eq(false), eq(1));
        inOrder.verify(productDisplayRuleService).applyForProductIds(argThat(ids -> ids.contains("16")));
        inOrder.verify(productDisplayRuleService).repairLibraryStateForActivity(activityId, false, 10000);
        inOrder.verify(productDisplayRuleService).applyForActivityId(activityId);
    }

    @Test
    void refreshActivitySnapshots_shouldMarkMissingSnapshotsDeletedForCompletedStatusRefresh() {
        String activityId = "ACT010";
        when(douyinProductGateway.queryActivityProducts(any()))
                .thenReturn(new DouyinProductGateway.ActivityProductListResult(
                        false,
                        10L,
                        30001L,
                        1L,
                        null,
                        List.of(item(8L, "当前终止商品", 3, "合作已终止"))));
        when(operationStateMapper.selectOne(any())).thenReturn(null);
        when(productBizStatusService.initStateIfAbsent(any(), eq(activityId), eq("8"), any(), any(), any()))
                .thenReturn(state(activityId, "8"));
        when(snapshotMapper.update(isNull(), any())).thenReturn(2);

        ProductService.ActivityProductRefreshResult result = productService.refreshActivitySnapshots(
                new DouyinProductGateway.ActivityProductQueryRequest(
                        null, activityId, 4L, 1L, 20, null, null, null, 3, 1L, null, null));

        assertThat(result.syncedProductCount()).isEqualTo(1);
        verify(snapshotMapper).update(isNull(), argThat(wrapper -> {
            String sql = wrapper.getSqlSegment();
            return sql.contains("activity_id")
                    && sql.contains("deleted")
                    && sql.contains("status")
                    && sql.contains("product_id NOT IN");
        }));
    }

    @Test
    void refreshActivitySnapshotsByStatusPartitions_shouldQueryEverySupportedStatusAndReconcileWholeActivity() {
        String activityId = "ACT011";
        List<DouyinProductGateway.ActivityProductQueryRequest> requests = new ArrayList<>();
        when(douyinProductGateway.queryActivityProducts(any())).thenAnswer(invocation -> {
            DouyinProductGateway.ActivityProductQueryRequest request = invocation.getArgument(0);
            requests.add(request);
            int status = request.status() == null ? -1 : request.status();
            long productId = 10_000L + status;
            return new DouyinProductGateway.ActivityProductListResult(
                    false,
                    11L,
                    30001L,
                    1L,
                    null,
                    List.of(item(productId, "状态" + status + "商品", status, statusText(status))));
        });
        when(operationStateMapper.selectOne(any())).thenReturn(null);
        when(productBizStatusService.initStateIfAbsent(any(), eq(activityId), any(), any(), any(), any()))
                .thenAnswer(invocation -> state(activityId, invocation.getArgument(2)));
        when(snapshotMapper.update(isNull(), any())).thenReturn(0);

        ProductService.ActivityProductRefreshResult result = productService.refreshActivitySnapshotsByStatusPartitions(
                new DouyinProductGateway.ActivityProductQueryRequest(
                        null, activityId, 4L, 1L, 20, null, null, null, null, 1L, null, null),
                100,
                100,
                300L,
                3,
                null);

        assertThat(result.complete()).isTrue();
        assertThat(result.distinctProductIds()).isEqualTo(6);
        assertThat(requests).hasSize(6);
        assertThat(requests)
                .extracting(DouyinProductGateway.ActivityProductQueryRequest::status)
                .contains(0, 1, 2, 3, 4, 6);
        verify(snapshotMapper).update(isNull(), argThat(wrapper -> {
            String sql = wrapper.getSqlSegment();
            return sql.contains("activity_id")
                    && sql.contains("deleted")
                    && sql.contains("product_id NOT IN")
                    && !sql.contains("status =");
        }));
        verify(productDisplayRuleService).repairLibraryStateForActivity(activityId, false, 10000);
        verify(productDisplayRuleService).applyForActivityId(activityId);
    }

    @Test
    void refreshActivitySnapshotsByStatusPartitions_shouldParallelizeBoundedPageModeForPriorityStatuses() {
        String activityId = "ACT017";
        List<DouyinProductGateway.ActivityProductQueryRequest> requests =
                new java.util.concurrent.CopyOnWriteArrayList<>();
        when(douyinProductGateway.queryActivityProducts(any())).thenAnswer(invocation -> {
            DouyinProductGateway.ActivityProductQueryRequest request = invocation.getArgument(0);
            requests.add(request);
            int status = request.status();
            long page = request.page() == null ? 1L : request.page();
            long base = status == 0 ? 10_000L : 20_000L;
            long offset = (page - 1L) * 2L;
            return new DouyinProductGateway.ActivityProductListResult(
                    false,
                    17L,
                    30001L,
                    4L,
                    null,
                    List.of(
                            item(base + offset + 1L, "分页商品" + status + "-" + page + "-1", status, statusText(status)),
                            item(base + offset + 2L, "分页商品" + status + "-" + page + "-2", status, statusText(status))));
        });
        when(operationStateMapper.selectOne(any())).thenReturn(null);
        when(productBizStatusService.initStateIfAbsent(any(), eq(activityId), any(), any(), any(), any()))
                .thenAnswer(invocation -> state(activityId, invocation.getArgument(2)));
        when(snapshotMapper.update(isNull(), any())).thenReturn(0);

        ProductService.ActivityProductRefreshResult result = productService.refreshActivitySnapshotsByStatusPartitions(
                new DouyinProductGateway.ActivityProductQueryRequest(
                        null, activityId, 4L, 1L, 2, null, null, null, null, 1L, null, null),
                List.of(0, 1),
                100,
                100,
                300L,
                2,
                null);

        assertThat(result.complete()).isTrue();
        assertThat(result.fetchedRows()).isEqualTo(8);
        assertThat(result.distinctProductIds()).isEqualTo(8);
        assertThat(requests).hasSize(4);
        assertThat(requests).allMatch(request -> Long.valueOf(0L).equals(request.retrieveMode())
                && request.cursor() == null
                && request.page() != null);
        assertThat(requests).extracting(DouyinProductGateway.ActivityProductQueryRequest::page)
                .containsExactlyInAnyOrder(1L, 1L, 2L, 2L);
        verify(productDisplayRuleService).repairLibraryStateForActivity(activityId, false, 10000);
        verify(productDisplayRuleService).applyForActivityId(activityId);
    }

    @Test
    void refreshActivitySnapshotsByStatusPartitions_shouldFallbackToSerialWhenUpstreamRejectsStatusFilter() {
        String activityId = "ACT012";
        List<DouyinProductGateway.ActivityProductQueryRequest> requests = new ArrayList<>();
        when(douyinProductGateway.queryActivityProducts(any())).thenAnswer(invocation -> {
            DouyinProductGateway.ActivityProductQueryRequest request = invocation.getArgument(0);
            requests.add(request);
            if (Integer.valueOf(4).equals(request.status())) {
                throw new DouyinApiException(
                        50002,
                        "参数校验失败",
                        "isv.business-failed:257",
                        "log-test",
                        "alliance.colonelActivityProduct");
            }
            int status = request.status() == null ? 1 : request.status();
            return new DouyinProductGateway.ActivityProductListResult(
                    false,
                    12L,
                    30001L,
                    1L,
                    null,
                    List.of(item(20_000L + status, "回退商品", status, statusText(status))));
        });
        when(operationStateMapper.selectOne(any())).thenReturn(null);
        when(productBizStatusService.initStateIfAbsent(any(), eq(activityId), any(), any(), any(), any()))
                .thenAnswer(invocation -> state(activityId, invocation.getArgument(2)));
        when(snapshotMapper.update(isNull(), any())).thenReturn(0);

        ProductService.ActivityProductRefreshResult result = productService.refreshActivitySnapshotsByStatusPartitions(
                new DouyinProductGateway.ActivityProductQueryRequest(
                        null, activityId, 4L, 1L, 20, null, null, null, null, 1L, null, null),
                100,
                100,
                300L,
                3,
                null);

        assertThat(result.complete()).isTrue();
        assertThat(result.distinctProductIds()).isEqualTo(1);
        assertThat(requests)
                .extracting(DouyinProductGateway.ActivityProductQueryRequest::status)
                .contains(0, 1, 2, 3, 4, null);
        verify(snapshotMapper).update(isNull(), argThat(wrapper -> !wrapper.getSqlSegment().contains("status =")));
    }

    private DouyinProductGateway.ActivityProductItem item(long productId, String title) {
        return item(productId, title, 1, "推广中");
    }

    private String statusText(int status) {
        return switch (status) {
            case 0 -> "待审核";
            case 1 -> "推广中";
            case 2 -> "申请未通过";
            case 3 -> "合作已终止";
            case 4 -> "合作前取消";
            case 6 -> "合作已到期";
            default -> "未知";
        };
    }

    private void assertTerminatedStatusFilter(LambdaQueryWrapper<ProductSnapshot> wrapper) {
        assertThat(wrapper.getSqlSegment())
                .contains("status")
                .doesNotContain("IN");
    }

    private DouyinProductGateway.ActivityProductItem item(long productId, String title, int status, String statusText) {
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
                status,
                statusText,
                "美妆",
                "1000",
                "满减券",
                "2026-04-25 00:00:00",
                "2026-04-30 23:59:59",
                "2026-04-25 00:00:00",
                "2026-04-30 23:59:59",
                "https://detail.test/products/" + productId,
                null,
                Map.of()
        );
    }

    private ProductOperationState state(String activityId, String productId) {
        ProductOperationState state = new ProductOperationState();
        state.setId(UUID.randomUUID());
        state.setActivityId(activityId);
        state.setProductId(productId);
        state.setBizStatus(ProductBizStatus.PENDING_AUDIT.name());
        return state;
    }

    private ProductSnapshot snapshot(String activityId, String productId) {
        ProductSnapshot snapshot = new ProductSnapshot();
        snapshot.setId(UUID.randomUUID());
        snapshot.setActivityId(activityId);
        snapshot.setProductId(productId);
        snapshot.setTitle("测试商品");
        snapshot.setPrice(5900L);
        snapshot.setPriceText("59.00");
        snapshot.setStatus(0);
        snapshot.setStatusText("待审核");
        snapshot.setActivityCosRatio(20L);
        snapshot.setActivityCosRatioText("20%");
        snapshot.setDetailUrl("https://detail.test/products/" + productId);
        snapshot.setPromotionEndTime("2099-12-31 23:59:59");
        return snapshot;
    }

    private Map<String, Object> validAuditSupplement() {
        return Map.of(
                "exclusivePriceRemark", "直播间专属价 129 元",
                "shippingInfo", "48 小时内发货",
                "sellingPoints", List.of("高转化卖点"),
                "promotionScript", "达人可直接口播",
                "supportsAds", true,
                "rewardRemark", "达标后额外奖励",
                "participationRequirements", "粉丝画像匹配",
                "campaignTimeRemark", "活动期内有效",
                "materialFiles", List.of("https://material.test/card.png")
        );
    }

    private ColonelsettlementActivity activity(String activityId, int statusCode, String statusText, UUID recruiterId) {
        ColonelsettlementActivity activity = new ColonelsettlementActivity();
        activity.setActivityId(activityId);
        activity.setActivityStatusCode(statusCode);
        activity.setActivityStatusText(statusText);
        activity.setRecruiterUserId(recruiterId);
        return activity;
    }

    @Test
    void refreshActivitySnapshotsByStatusPartitions_shouldReconcileCompletedPartitionEvenIfTotalIncomplete() {
        String activityId = "ACT015";
        List<DouyinProductGateway.ActivityProductQueryRequest> requests = new ArrayList<>();
        when(douyinProductGateway.queryActivityProducts(any())).thenAnswer(invocation -> {
            DouyinProductGateway.ActivityProductQueryRequest request = invocation.getArgument(0);
            requests.add(request);
            if (Integer.valueOf(0).equals(request.status())) {
                return new DouyinProductGateway.ActivityProductListResult(
                        false,
                        2L,
                        30001L,
                        1L,
                        null,
                        List.of(item(10001L, "待审核商品A", 0, "待审核"), item(10002L, "待审核商品B", 0, "待审核")));
            }
            if ("next-cursor-1".equals(request.cursor())) {
                return new DouyinProductGateway.ActivityProductListResult(
                        true,
                        50000L,
                        30001L,
                        1L,
                        "next-cursor-2",
                        List.of(item(20003L, "推广中商品C", 1, "推广中"), item(20004L, "推广中商品D", 1, "推广中")));
            }
            return new DouyinProductGateway.ActivityProductListResult(
                    true,
                    50000L,
                    30001L,
                    1L,
                    "next-cursor-1",
                    List.of(item(20001L, "推广中商品A", 1, "推广中"), item(20002L, "推广中商品B", 1, "推广中")));
        });
        when(operationStateMapper.selectOne(any())).thenReturn(null);
        when(productBizStatusService.initStateIfAbsent(any(), eq(activityId), any(), any(), any(), any()))
                .thenAnswer(invocation -> state(activityId, invocation.getArgument(2)));
        when(snapshotMapper.update(isNull(), any())).thenReturn(1);

        ProductService.ActivityProductRefreshResult result = productService.refreshActivitySnapshotsByStatusPartitions(
                new DouyinProductGateway.ActivityProductQueryRequest(
                        null, activityId, 4L, 1L, 2, null, null, null, null, 1L, null, null),
                List.of(0, 1),
                100,
                3,
                300L,
                2,
                null);

        assertThat(result.complete()).isFalse();
        verify(productDisplayRuleService).repairLibraryStateForActivityProducts(
                eq(activityId), argThat(ids -> ids.size() == 3
                        && ids.stream().allMatch(id -> List.of("10001", "10002", "20001", "20002", "20003").contains(id))), eq(false), eq(10000));
        verify(productDisplayRuleService, never()).repairLibraryStateForActivity(eq(activityId), eq(false), anyInt());
        verify(productDisplayRuleService).applyForProductIds(
                argThat(ids -> ids.size() == 3
                        && ids.stream().allMatch(id -> List.of("10001", "10002", "20001", "20002", "20003").contains(id))));
        verify(productDisplayRuleService, never()).applyForActivityId(activityId);
        verify(snapshotMapper).update(isNull(), argThat(wrapper -> {
            String sql = wrapper.getSqlSegment();
            return sql.contains("activity_id")
                    && sql.contains("deleted")
                    && sql.contains("status")
                    && sql.contains("product_id NOT IN");
        }));
    }

    @Test
    void verifyStatusPartitionReconcileForActivity3223881() {
        String activityId = "3223881";
        when(douyinProductGateway.queryActivityProducts(any())).thenAnswer(invocation -> {
            DouyinProductGateway.ActivityProductQueryRequest request = invocation.getArgument(0);
            if (Integer.valueOf(0).equals(request.status())) {
                return new DouyinProductGateway.ActivityProductListResult(
                        false,
                        4L,
                        30001L,
                        1L,
                        null,
                        List.of(
                                item(1001L, "待审核商品1", 0, "待审核"),
                                item(1002L, "待审核商品2", 0, "待审核"),
                                item(1003L, "待审核商品3", 0, "待审核"),
                                item(1004L, "待审核商品4", 0, "待审核")
                        ));
            }
            if ("next-cursor-1".equals(request.cursor())) {
                return new DouyinProductGateway.ActivityProductListResult(
                        true,
                        50000L,
                        30001L,
                        1L,
                        "next-cursor-2",
                        List.of(item(2003L, "推广中商品C", 1, "推广中"), item(2004L, "推广中商品D", 1, "推广中")));
            }
            return new DouyinProductGateway.ActivityProductListResult(
                    true,
                    50000L,
                    30001L,
                    1L,
                    "next-cursor-1",
                    List.of(item(2001L, "推广中商品A", 1, "推广中"), item(2002L, "推广中商品B", 1, "推广中")));
        });
        when(operationStateMapper.selectOne(any())).thenReturn(null);
        when(productBizStatusService.initStateIfAbsent(any(), eq(activityId), any(), any(), any(), any()))
                .thenAnswer(invocation -> state(activityId, invocation.getArgument(2)));
        when(snapshotMapper.update(isNull(), any())).thenReturn(6);

        ProductService.ActivityProductRefreshResult result = productService.refreshActivitySnapshotsByStatusPartitions(
                new DouyinProductGateway.ActivityProductQueryRequest(
                        null, activityId, 4L, 1L, 2, null, null, null, null, 1L, null, null),
                List.of(0, 1),
                100,
                6,
                300L,
                2,
                null);

        assertThat(result.complete()).isFalse();
        assertThat(result.distinctProductIds()).isEqualTo(6);

        verify(snapshotMapper).update(isNull(), argThat(wrapper -> {



            String sql = wrapper.getSqlSegment();
            return sql.contains("activity_id")
                    && sql.contains("deleted")
                    && sql.contains("status")
                    && sql.contains("product_id NOT IN");
        }));
    }
}
