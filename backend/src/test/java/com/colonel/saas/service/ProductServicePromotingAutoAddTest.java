package com.colonel.saas.service;

import com.colonel.saas.common.enums.ProductBizStatus;
import com.colonel.saas.constant.ProductDisplayStatus;
import com.colonel.saas.entity.ColonelsettlementActivity;
import com.colonel.saas.entity.ProductOperationState;
import com.colonel.saas.gateway.douyin.DouyinProductGateway;
import com.colonel.saas.mapper.ColonelsettlementActivityMapper;
import com.colonel.saas.mapper.ProductOperationStateMapper;
import com.colonel.saas.mapper.ProductSnapshotMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.*;

/**
 * 推广中活动商品自动入库测试。
 *
 * 业务规则：招商被分配的活动是推广中 → 该活动下所有商品全部自动展示到商品库
 *
 * 核心实现位于 ActivityPromotionSupport.shouldForceLibraryDisplay():
 * - 条件：活动推广中 && 招商已分配（recruiterUserId 非空）
 * - 结果：触发 applyAssignedPromotingLibraryState() 自动入库并展示
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductServicePromotingAutoAddTest {

    @Mock private com.colonel.saas.gateway.douyin.DouyinPromotionGateway douyinPromotionGateway;
    @Mock private DouyinProductGateway douyinProductGateway;
    @Mock private ProductSnapshotMapper snapshotMapper;
    @Mock private ProductOperationStateMapper operationStateMapper;
    @Mock private com.colonel.saas.mapper.ProductOperationLogMapper operationLogMapper;
    @Mock private com.colonel.saas.mapper.PromotionLinkMapper promotionLinkMapper;
    @Mock private com.colonel.saas.mapper.ColonelsettlementOrderMapper orderMapper;
    @Mock private com.colonel.saas.mapper.MerchantMapper merchantMapper;
    @Mock private com.colonel.saas.mapper.SysUserMapper sysUserMapper;
    @Mock private PickSourceMappingService pickSourceMappingService;
    @Mock private ProductBizStatusService productBizStatusService;
    @Mock private ColonelsettlementActivityMapper colonelActivityMapper;
    @Mock private TalentFollowService talentFollowService;
    @Mock private com.colonel.saas.gateway.douyin.DouyinActivityGateway douyinActivityGateway;
    @Mock private PromotionLinkIdempotencyService promotionLinkIdempotencyService;
    @Mock private BusinessRuleConfigService businessRuleConfigService;
    @Mock private ProductDisplayRuleService productDisplayRuleService;
    @Mock private ColonelPartnerSyncService colonelPartnerSyncService;
    @Mock private com.colonel.saas.domain.product.event.ProductDomainEventPublisher productDomainEventPublisher;

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
                promotionLinkIdempotencyService,
                businessRuleConfigService,
                productDisplayRuleService,
                colonelPartnerSyncService,
                productDomainEventPublisher);
    }

    /**
     * 创建完整的 ActivityProductItem（匹配 DouyinProductGateway.ActivityProductItem record）
     */
    private DouyinProductGateway.ActivityProductItem createItem(long productId, String title) {
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
                Map.of()
        );
    }

    /**
     * 创建 ColonelsettlementActivity
     */
    private ColonelsettlementActivity createActivity(String activityId, int statusCode, String statusText, UUID recruiterId) {
        ColonelsettlementActivity activity = new ColonelsettlementActivity();
        activity.setActivityId(activityId);
        activity.setActivityStatusCode(statusCode);
        activity.setActivityStatusText(statusText);
        activity.setRecruiterUserId(recruiterId);
        return activity;
    }

    /**
     * 场景：推广中(statusCode=5) + 招商已分配(recruiterId != null)
     * → 商品应自动入库并展示
     */
    @Test
    void upsertSnapshotsWithStats_promotingWithRecruiter_shouldAutoAddToLibrary() {
        String activityId = "ACT001";
        UUID recruiterId = UUID.randomUUID();

        // 准备推广中活动 + 招商已分配
        ColonelsettlementActivity activity = createActivity(activityId, 5, "推广中", recruiterId);
        when(colonelActivityMapper.selectByActivityId(activityId)).thenReturn(activity);

        // 准备商品同步数据
        DouyinProductGateway.ActivityProductItem item = createItem(1L, "测试商品");
        List<DouyinProductGateway.ActivityProductItem> items = List.of(item);

        // mock: 运营状态不存在（需要新建）
        when(operationStateMapper.selectOne(any())).thenReturn(null);
        when(productBizStatusService.initStateIfAbsent(any(), eq(activityId), eq("1"), any(), any(), any()))
                .thenAnswer(invocation -> {
                    ProductOperationState existing = invocation.getArgument(0);
                    if (existing == null) {
                        ProductOperationState newState = new ProductOperationState();
                        newState.setId(UUID.randomUUID());
                        newState.setActivityId(activityId);
                        newState.setProductId("1");
                        newState.setBizStatus(ProductBizStatus.PENDING_AUDIT.name());
                        return newState;
                    }
                    return existing;
                });

        when(productBizStatusService.readBizStatus(any())).thenReturn(ProductBizStatus.PENDING_AUDIT);
        when(operationStateMapper.updateById(any())).thenReturn(1);

        // 执行
        ProductService.ActivitySnapshotUpsertStats stats = productService.upsertSnapshotsWithStats(activityId, items);

        // 验证：商品应被自动入库并展示
        ArgumentCaptor<ProductOperationState> stateCaptor = ArgumentCaptor.forClass(ProductOperationState.class);
        verify(operationStateMapper, atLeastOnce()).updateById(stateCaptor.capture());

        List<ProductOperationState> savedStates = stateCaptor.getAllValues();
        // 至少有一次更新：assignee 赋值
        assertThat(savedStates).isNotEmpty();

        // 验证招商组长被正确分配
        boolean hasAssigneeAssigned = savedStates.stream()
                .anyMatch(s -> recruiterId.equals(s.getAssigneeId()));
        assertThat(hasAssigneeAssigned).isTrue();

        // 验证 selectedToLibrary = true（通过 autoAddToLibrary）
        boolean hasSelectedToLibrary = savedStates.stream()
                .anyMatch(s -> Boolean.TRUE.equals(s.getSelectedToLibrary()));
        assertThat(hasSelectedToLibrary).isTrue();

        // 验证 displayStatus = DISPLAYING
        boolean hasDisplaying = savedStates.stream()
                .anyMatch(s -> ProductDisplayStatus.DISPLAYING.name().equals(s.getDisplayStatus()));
        assertThat(hasDisplaying).isTrue();
    }

    /**
     * 场景：推广中(statusCode=5) + 招商未分配(recruiterId = null)
     * → 商品不应自动入库（forceLibrary = false，因为 recruiterUserId 为空）
     */
    @Test
    void upsertSnapshotsWithStats_promotingWithoutRecruiter_shouldNotAutoAdd() {
        String activityId = "ACT002";

        // 准备推广中活动 + 招商未分配
        ColonelsettlementActivity activity = createActivity(activityId, 5, "推广中", null);
        when(colonelActivityMapper.selectByActivityId(activityId)).thenReturn(activity);

        // 准备商品同步数据
        DouyinProductGateway.ActivityProductItem item = createItem(2L, "测试商品");
        List<DouyinProductGateway.ActivityProductItem> items = List.of(item);

        // mock: 运营状态不存在，且不会分配给任何人（因为 recruiterId 为 null）
        when(operationStateMapper.selectOne(any())).thenReturn(null);
        // initStateIfAbsent 会被调用（创建新的运营状态记录）
        when(productBizStatusService.initStateIfAbsent(any(), eq(activityId), eq("2"), any(), any(), any()))
                .thenAnswer(invocation -> {
                    ProductOperationState existing = invocation.getArgument(0);
                    if (existing == null) {
                        ProductOperationState newState = new ProductOperationState();
                        newState.setId(UUID.randomUUID());
                        newState.setActivityId(activityId);
                        newState.setProductId("2");
                        newState.setBizStatus(ProductBizStatus.PENDING_AUDIT.name());
                        return newState;
                    }
                    return existing;
                });

        // 执行
        productService.upsertSnapshotsWithStats(activityId, items);

        // 验证：因为 recruiterId 为空，forceLibrary = false
        // - 不会触发展示规则引擎（关键断言）
        verify(productDisplayRuleService, never()).applyForActivityId(any());

        // - 不会调用 updateById 来设置 assignee（因为 activityRecruiterId 为 null）
        verify(operationStateMapper, never()).updateById(any());
    }

    /**
     * 场景：非推广中(statusCode=3) + 招商已分配(recruiterId != null)
     * → 商品不应自动入库
     */
    @Test
    void upsertSnapshotsWithStats_nonPromotingWithRecruiter_shouldNotAutoAdd() {
        String activityId = "ACT003";
        UUID recruiterId = UUID.randomUUID();

        // 准备非推广中活动（报名中）+ 招商已分配
        ColonelsettlementActivity activity = createActivity(activityId, 3, "报名中", recruiterId);
        when(colonelActivityMapper.selectByActivityId(activityId)).thenReturn(activity);

        // 准备商品同步数据
        DouyinProductGateway.ActivityProductItem item = createItem(3L, "测试商品");
        List<DouyinProductGateway.ActivityProductItem> items = List.of(item);

        // mock: 运营状态不存在
        when(operationStateMapper.selectOne(any())).thenReturn(null);
        when(productBizStatusService.initStateIfAbsent(any(), eq(activityId), eq("3"), any(), any(), any()))
                .thenAnswer(invocation -> {
                    ProductOperationState existing = invocation.getArgument(0);
                    if (existing == null) {
                        ProductOperationState newState = new ProductOperationState();
                        newState.setId(UUID.randomUUID());
                        newState.setActivityId(activityId);
                        newState.setProductId("3");
                        newState.setBizStatus(ProductBizStatus.PENDING_AUDIT.name());
                        return newState;
                    }
                    return existing;
                });

        when(operationStateMapper.updateById(any())).thenReturn(1);

        // 执行
        productService.upsertSnapshotsWithStats(activityId, items);

        // 验证：selectedToLibrary 不应为 true（forceLibrary = false）
        ArgumentCaptor<ProductOperationState> stateCaptor = ArgumentCaptor.forClass(ProductOperationState.class);
        verify(operationStateMapper, atLeastOnce()).updateById(stateCaptor.capture());

        List<ProductOperationState> savedStates = stateCaptor.getAllValues();
        assertThat(savedStates).noneMatch(s -> Boolean.TRUE.equals(s.getSelectedToLibrary()));
    }

    /**
     * 场景：推广中 + 招商已分配 → 应触发展示规则引擎
     */
    @Test
    void upsertSnapshotsWithStats_promotingWithRecruiter_shouldTriggerDisplayRule() {
        String activityId = "ACT004";
        UUID recruiterId = UUID.randomUUID();

        // 准备推广中活动 + 招商已分配
        ColonelsettlementActivity activity = createActivity(activityId, 5, "推广中", recruiterId);
        when(colonelActivityMapper.selectByActivityId(activityId)).thenReturn(activity);

        // 准备商品同步数据
        DouyinProductGateway.ActivityProductItem item = createItem(4L, "测试商品");
        List<DouyinProductGateway.ActivityProductItem> items = List.of(item);

        when(operationStateMapper.selectOne(any())).thenReturn(null);
        when(productBizStatusService.initStateIfAbsent(any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    ProductOperationState state = new ProductOperationState();
                    state.setId(UUID.randomUUID());
                    state.setBizStatus(ProductBizStatus.PENDING_AUDIT.name());
                    return state;
                });
        when(productBizStatusService.readBizStatus(any())).thenReturn(ProductBizStatus.PENDING_AUDIT);
        when(operationStateMapper.updateById(any())).thenReturn(1);

        // 执行
        productService.upsertSnapshotsWithStats(activityId, items);

        // 验证：应触发展示规则引擎（因为 forceLibrary = true）
        verify(productDisplayRuleService).applyForActivityId(activityId);
    }

    /**
     * 场景：推广中 + 招商已分配 → 多个商品应全部入库
     */
    @Test
    void upsertSnapshotsWithStats_promotingWithRecruiter_multipleProducts_shouldAllAddToLibrary() {
        String activityId = "ACT005";
        UUID recruiterId = UUID.randomUUID();

        // 准备推广中活动 + 招商已分配
        ColonelsettlementActivity activity = createActivity(activityId, 5, "推广中", recruiterId);
        when(colonelActivityMapper.selectByActivityId(activityId)).thenReturn(activity);

        // 准备多个商品
        List<DouyinProductGateway.ActivityProductItem> items = List.of(
                createItem(5L, "商品A"),
                createItem(6L, "商品B"),
                createItem(7L, "商品C")
        );

        when(operationStateMapper.selectOne(any())).thenReturn(null);
        when(productBizStatusService.initStateIfAbsent(any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    ProductOperationState state = new ProductOperationState();
                    state.setId(UUID.randomUUID());
                    state.setBizStatus(ProductBizStatus.PENDING_AUDIT.name());
                    return state;
                });
        when(productBizStatusService.readBizStatus(any())).thenReturn(ProductBizStatus.PENDING_AUDIT);
        when(operationStateMapper.updateById(any())).thenReturn(1);

        // 执行
        ProductService.ActivitySnapshotUpsertStats stats = productService.upsertSnapshotsWithStats(activityId, items);

        // 验证：3个商品全部处理
        assertThat(stats.createdCount() + stats.updatedCount()).isEqualTo(3);

        // 验证：所有商品 selectedToLibrary = true
        ArgumentCaptor<ProductOperationState> stateCaptor = ArgumentCaptor.forClass(ProductOperationState.class);
        verify(operationStateMapper, atLeast(3)).updateById(stateCaptor.capture());

        List<ProductOperationState> savedStates = stateCaptor.getAllValues();
        // 所有状态都应有 selectedToLibrary=true
        assertThat(savedStates).allMatch(s -> Boolean.TRUE.equals(s.getSelectedToLibrary()));
    }

    /**
     * 场景：推广中 + 招商未分配 → 不应触发展示规则引擎
     */
    @Test
    void upsertSnapshotsWithStats_promotingWithoutRecruiter_shouldNotTriggerDisplayRule() {
        String activityId = "ACT006";

        // 准备推广中活动 + 招商未分配
        ColonelsettlementActivity activity = createActivity(activityId, 5, "推广中", null);
        when(colonelActivityMapper.selectByActivityId(activityId)).thenReturn(activity);

        // 准备商品同步数据
        DouyinProductGateway.ActivityProductItem item = createItem(6L, "测试商品");
        List<DouyinProductGateway.ActivityProductItem> items = List.of(item);

        when(operationStateMapper.selectOne(any())).thenReturn(null);
        when(productBizStatusService.initStateIfAbsent(any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    ProductOperationState state = new ProductOperationState();
                    state.setId(UUID.randomUUID());
                    state.setBizStatus(ProductBizStatus.PENDING_AUDIT.name());
                    return state;
                });
        when(operationStateMapper.updateById(any())).thenReturn(1);

        // 执行
        productService.upsertSnapshotsWithStats(activityId, items);

        // 验证：不应触发展示规则引擎（forceLibrary = false）
        verify(productDisplayRuleService, never()).applyForActivityId(any());
    }
}
