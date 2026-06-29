package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.entity.ColonelsettlementActivity;
import com.colonel.saas.entity.Product;
import com.colonel.saas.entity.ProductOperationState;
import com.colonel.saas.entity.ProductSnapshot;
import com.colonel.saas.common.handler.UUIDTypeHandler;
import com.colonel.saas.domain.order.facade.OrderReadFacade;
import com.colonel.saas.gateway.douyin.DouyinProductGateway;
import com.colonel.saas.mapper.ColonelsettlementActivityMapper;
import com.colonel.saas.mapper.MerchantMapper;
import com.colonel.saas.mapper.ProductOperationLogMapper;
import com.colonel.saas.mapper.ProductOperationStateMapper;
import com.colonel.saas.mapper.ProductSnapshotMapper;
import com.colonel.saas.mapper.PromotionLinkMapper;
import com.colonel.saas.domain.user.facade.UserDomainFacade;
import com.colonel.saas.domain.product.event.ProductDomainEventPublisher;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 商品库视图 activityName / shopScore 透传测试。
 *
 * <p>背景：旧 {@code ProductService.toLegacyProduct} 漏传 {@code activityName}，且未设
 * {@code shopScore}，导致商品库卡片 hover 抽屉"活动"字段一直为 {@code -}，且没有
 * "店铺评分"字段。本测试覆盖 {@code getSelectedLibraryPage} 链路，
 * 验证虚拟字段在 Product 视图里能正确透传给前端。</p>
 *
 * <p>链路：{@code getSelectedLibraryPage} → {@code collectSelectedLibraryProducts}
 * → {@code loadActivityNameMap}（一次性查活动名）
 * → {@code toLegacyProduct}（注入 activityName + shopScore）</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductServiceLibraryViewTest {

    @Mock private com.colonel.saas.domain.product.application.port.DouyinConvertPort douyinConvertPort;
    @Mock private DouyinProductGateway douyinProductGateway;
    @Mock private ProductSnapshotMapper snapshotMapper;
    @Mock private ProductOperationStateMapper operationStateMapper;
    @Mock private ProductOperationLogMapper operationLogMapper;
    @Mock private PromotionLinkMapper promotionLinkMapper;
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
        initTableInfo(ProductOperationState.class);
        productService = new ProductService(
                douyinConvertPort,
                douyinProductGateway,
                snapshotMapper,
                operationStateMapper,
                operationLogMapper,
                promotionLinkMapper,
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
        when(colonelActivityMapper.selectByActivityId(any())).thenReturn(null);
        when(snapshotMapper.selectSelectedLibraryPage(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), anyLong(), anyLong(), any()))
                .thenReturn(null);
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
    void getSelectedLibraryPage_shouldExposeActivityNameAndShopScore() {
        String activityId = "ACT-LIB-1001";
        String productId = "P-LIB-1001";

        // state 分页：1 条已入选商品
        ProductOperationState state = new ProductOperationState();
        state.setActivityId(activityId);
        state.setProductId(productId);
        state.setSelectedToLibrary(true);
        state.setDisplayStatus("DISPLAYING");
        state.setAuditStatus(2);
        state.setManualDisabled(false);
        Page<ProductOperationState> statePage = new Page<>(1, 50);
        statePage.setRecords(List.of(state));
        statePage.setTotal(1);
        when(operationStateMapper.selectPage(any(), any())).thenReturn(statePage);

        // snapshot：含 rawPayload.shopScore
        ProductSnapshot snapshot = new ProductSnapshot();
        snapshot.setActivityId(activityId);
        snapshot.setProductId(productId);
        snapshot.setTitle("测试商品");
        snapshot.setShopName("测试店铺");
        snapshot.setStatus(1);
        snapshot.setStatusText("推广中");
        snapshot.setSales(0L);
        snapshot.setRawPayload("{\"shopScore\":92}");
        UUID snapshotId = buildSnapshotId(activityId, productId);
        snapshot.setId(snapshotId);
        when(snapshotMapper.selectBatchIds(anyList())).thenReturn(List.of(snapshot));

        // 活动名批量查：返回 1 条
        ColonelsettlementActivity activity = new ColonelsettlementActivity();
        activity.setActivityId(activityId);
        activity.setName("春季招商活动");
        when(colonelActivityMapper.selectNamesByActivityIds(anyList())).thenReturn(List.of(activity));

        IPage<Product> result = productService.getSelectedLibraryPage(
                1, 20, ProductService.SelectedLibraryFilter.empty());

        assertThat(result.getRecords()).hasSize(1);
        Product product = result.getRecords().get(0);
        assertThat(product.getActivityName()).isEqualTo("春季招商活动");
        assertThat(product.getShopScore()).isEqualTo(92);
    }

    @Test
    void getSelectedLibraryPage_total_shouldRemainDisplayingLibraryCount() {
        String activityId = "ACT-DISPLAYING";
        String productId = "P-DISPLAYING";

        ProductOperationState state = new ProductOperationState();
        state.setActivityId(activityId);
        state.setProductId(productId);
        state.setSelectedToLibrary(true);
        state.setDisplayStatus("DISPLAYING");
        state.setAuditStatus(2);
        state.setManualDisabled(false);

        Page<ProductOperationState> statePage = new Page<>(1, 50);
        statePage.setRecords(List.of(state));
        statePage.setTotal(1);
        when(operationStateMapper.selectPage(any(), any())).thenReturn(statePage);

        ProductSnapshot snapshot = new ProductSnapshot();
        snapshot.setActivityId(activityId);
        snapshot.setProductId(productId);
        snapshot.setTitle("展示中商品");
        snapshot.setStatus(1);
        snapshot.setStatusText("推广中");
        snapshot.setSales(0L);
        snapshot.setRawPayload("{\"shopScore\":90}");
        snapshot.setId(buildSnapshotId(activityId, productId));
        when(snapshotMapper.selectBatchIds(anyList())).thenReturn(List.of(snapshot));
        when(colonelActivityMapper.selectNamesByActivityIds(anyList())).thenReturn(List.of());

        IPage<Product> result = productService.getSelectedLibraryPage(
                1, 20, ProductService.SelectedLibraryFilter.empty());

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRecords()).hasSize(1);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<ProductOperationState>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(operationStateMapper).selectPage(any(), wrapperCaptor.capture());
        String sqlSegment = wrapperCaptor.getValue().getSqlSegment();
        assertThat(sqlSegment)
                .contains("selected_to_library")
                .contains("display_status");
    }

    @Test
    void getSelectedLibraryPage_activityNameMapMiss_shouldReturnNull() {
        String activityId = "ACT-LIB-MISS";
        String productId = "P-LIB-MISS";

        ProductOperationState state = new ProductOperationState();
        state.setActivityId(activityId);
        state.setProductId(productId);
        state.setSelectedToLibrary(true);
        state.setDisplayStatus("DISPLAYING");
        Page<ProductOperationState> statePage = new Page<>(1, 50);
        statePage.setRecords(List.of(state));
        statePage.setTotal(1);
        when(operationStateMapper.selectPage(any(), any())).thenReturn(statePage);

        ProductSnapshot snapshot = new ProductSnapshot();
        snapshot.setActivityId(activityId);
        snapshot.setProductId(productId);
        snapshot.setTitle("无活动名商品");
        snapshot.setStatus(1);
        snapshot.setStatusText("推广中");
        snapshot.setSales(0L);
        snapshot.setRawPayload("{\"shopScore\":80}");
        snapshot.setId(buildSnapshotId(activityId, productId));
        when(snapshotMapper.selectBatchIds(anyList())).thenReturn(List.of(snapshot));

        // 活动名查不到（map miss）
        when(colonelActivityMapper.selectNamesByActivityIds(anyList())).thenReturn(List.of());

        IPage<Product> result = productService.getSelectedLibraryPage(
                1, 20, ProductService.SelectedLibraryFilter.empty());

        assertThat(result.getRecords()).hasSize(1);
        Product product = result.getRecords().get(0);
        assertThat(product.getActivityName()).isNull();
        // shopScore 仍然从 rawPayload 解析
        assertThat(product.getShopScore()).isEqualTo(80);
    }

    @Test
    void getSelectedLibraryPage_shopScoreMissingFromRawPayload_shouldReturnNull() {
        String activityId = "ACT-LIB-NO-SCORE";
        String productId = "P-LIB-NO-SCORE";

        ProductOperationState state = new ProductOperationState();
        state.setActivityId(activityId);
        state.setProductId(productId);
        state.setSelectedToLibrary(true);
        state.setDisplayStatus("DISPLAYING");
        Page<ProductOperationState> statePage = new Page<>(1, 50);
        statePage.setRecords(List.of(state));
        statePage.setTotal(1);
        when(operationStateMapper.selectPage(any(), any())).thenReturn(statePage);

        ProductSnapshot snapshot = new ProductSnapshot();
        snapshot.setActivityId(activityId);
        snapshot.setProductId(productId);
        snapshot.setTitle("无评分商品");
        snapshot.setStatus(1);
        snapshot.setStatusText("推广中");
        snapshot.setSales(0L);
        // rawPayload 不含 shopScore
        snapshot.setRawPayload("{\"productId\":1}");
        snapshot.setId(buildSnapshotId(activityId, productId));
        when(snapshotMapper.selectBatchIds(anyList())).thenReturn(List.of(snapshot));

        ColonelsettlementActivity activity = new ColonelsettlementActivity();
        activity.setActivityId(activityId);
        activity.setName("夏季活动");
        when(colonelActivityMapper.selectNamesByActivityIds(anyList())).thenReturn(List.of(activity));

        IPage<Product> result = productService.getSelectedLibraryPage(
                1, 20, ProductService.SelectedLibraryFilter.empty());

        assertThat(result.getRecords()).hasSize(1);
        Product product = result.getRecords().get(0);
        assertThat(product.getActivityName()).isEqualTo("夏季活动");
        assertThat(product.getShopScore()).isNull();
    }

    private static UUID buildSnapshotId(String activityId, String productId) {
        return UUID.nameUUIDFromBytes((activityId + ":" + productId).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
