package com.colonel.saas.architecture;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.config.DddRefactorProperties;
import com.colonel.saas.domain.product.facade.ProductDomainFacade;
import com.colonel.saas.domain.product.facade.dto.ProductReadDTO;
import com.colonel.saas.domain.product.facade.dto.ProductSnapshotReadDTO;
import com.colonel.saas.domain.product.port.ProductSampleApplicationPort;
import com.colonel.saas.domain.product.port.QuickSampleApplyPortResult;
import com.colonel.saas.domain.user.policy.CurrentUserPermissionPolicy;
import com.colonel.saas.dto.product.QuickSampleApplyRequest;
import com.colonel.saas.entity.Product;
import com.colonel.saas.entity.ProductOperationState;
import com.colonel.saas.constant.ProductDisplayStatus;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.gateway.douyin.DouyinQuickSampleGateway;
import com.colonel.saas.mapper.ProductMapper;
import com.colonel.saas.mapper.ProductOperationStateMapper;
import com.colonel.saas.mapper.ProductSnapshotMapper;
import com.colonel.saas.service.ProductQuickSampleService;
import com.colonel.saas.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DddProduct003ProductRoutingTest {

    @Mock private ProductService productService;
    @Mock private ProductMapper productMapper;
    @Mock private ProductSnapshotMapper productSnapshotMapper;
    @Mock private ProductOperationStateMapper productOperationStateMapper;
    @Mock private DouyinQuickSampleGateway douyinQuickSampleGateway;
    @Mock private ProductSampleApplicationPort productSampleApplicationPort;
    @Mock private DddRefactorProperties dddRefactorProperties;
    @Mock private DddRefactorProperties.Switch productFacadeSwitch;
    @Mock private ProductDomainFacade productDomainFacade;

    private ProductQuickSampleService service;
    private final UUID relationId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ProductQuickSampleService(
                productService,
                productMapper,
                productSnapshotMapper,
                productOperationStateMapper,
                douyinQuickSampleGateway,
                productSampleApplicationPort,
                false,
                dddRefactorProperties,
                productDomainFacade,
                new CurrentUserPermissionPolicy()
        );
        lenient().when(douyinQuickSampleGateway.isSupported()).thenReturn(false);
        lenient().when(douyinQuickSampleGateway.supportStatus())
                .thenReturn(DouyinQuickSampleGateway.SupportStatus.UNSUPPORTED_BY_SDK);
    }

    @Test
    @DisplayName("开关关闭时，快速寄样从 snapshot mapper 直接查询快照")
    void shouldQueryFromMapperWhenSwitchOff() {
        when(dddRefactorProperties.isEnabled()).thenReturn(false);

        Product product = new Product();
        product.setId(relationId);
        product.setProductId("9001");
        when(productService.getById(relationId)).thenReturn(product);

        // 如果走 mapper，它会查询 productSnapshotMapper
        assertThatThrownBy(() -> service.applyQuickSample(
                relationId, new QuickSampleApplyRequest(), UUID.randomUUID(), UUID.randomUUID(), List.of(RoleCodes.CHANNEL_STAFF)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("商品快照不存在");

        verify(productSnapshotMapper).selectById(relationId);
        verify(productDomainFacade, never()).findSnapshotById(any());
    }

    @Test
    @DisplayName("开关开启时，快速寄样从 ProductDomainFacade 查询快照且正常完成")
    void shouldQueryFromFacadeWhenSwitchOn() {
        when(dddRefactorProperties.isEnabled()).thenReturn(true);
        when(dddRefactorProperties.getProductFacade()).thenReturn(productFacadeSwitch);
        when(productFacadeSwitch.isEnabled()).thenReturn(true);

        Product product = new Product();
        product.setId(relationId);
        product.setProductId("9001");
        when(productService.getById(relationId)).thenReturn(product);

        ProductSnapshotReadDTO snapshotRead = new ProductSnapshotReadDTO(
                relationId, "10001", "9001", "测试商品", "cover", 123L, "shop", 9900L, null, 1, "url"
        );
        when(productDomainFacade.findSnapshotById(relationId)).thenReturn(snapshotRead);

        ProductOperationState state = new ProductOperationState();
        state.setDisplayStatus(ProductDisplayStatus.DISPLAYING.name());
        state.setSelectedToLibrary(true);
        when(productOperationStateMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(state);

        // findPersistedProduct
        ProductReadDTO productRead = new ProductReadDTO(relationId, "9001", "outer", "测试商品", "cover", 9900L, null, null, null, null);
        when(productDomainFacade.findProductByExternalId("9001")).thenReturn(productRead);

        QuickSampleApplyPortResult result = new QuickSampleApplyPortResult();
        result.setSuccess(true);
        result.setSuccessCount(1);
        when(productSampleApplicationPort.applyQuickSample(any())).thenReturn(result);

        QuickSampleApplyRequest request = new QuickSampleApplyRequest();
        request.setTalentIds(List.of("talent_1"));

        var response = service.applyQuickSample(
                relationId, request, UUID.randomUUID(), UUID.randomUUID(), List.of(RoleCodes.CHANNEL_STAFF));

        assertThat(response.isSuccess()).isTrue();
        verify(productDomainFacade).findSnapshotById(relationId);
        verify(productDomainFacade).findProductByExternalId("9001");
        verify(productSnapshotMapper, never()).selectById(any());
    }

    @Test
    @DisplayName("快速寄样角色编码匹配委托用户域权限策略")
    void productQuickSample_shouldDelegateRoleCodeMatchingToUserPolicy() throws Exception {
        String source = readSource("com/colonel/saas/service/ProductQuickSampleService.java");

        assertThat(source)
                .doesNotContain("private boolean hasAnyRole")
                .doesNotContain("roleCodes.toString()")
                .doesNotContain("roleCodes instanceof Collection")
                .contains("CurrentUserPermissionPolicy")
                .contains("currentUserPermissionPolicy.hasAnyRole");
    }

    @Test
    @DisplayName("选品分页角色编码匹配委托用户域权限策略")
    void productPickPage_shouldDelegateRoleCodeMatchingToUserPolicy() throws Exception {
        String source = readSource("com/colonel/saas/controller/ProductController.java");

        assertThat(source)
                .doesNotContain("roleCodes.stream()")
                .doesNotContain(".map(String::toLowerCase)")
                .doesNotContain("normalized.contains(RoleCodes.ADMIN)")
                .contains("CurrentUserPermissionPolicy")
                .contains("currentUserPermissionPolicy.hasAnyRole(roleCodes, RoleCodes.BIZ_STAFF)")
                .contains("currentUserPermissionPolicy.hasAnyRole(roleCodes, RoleCodes.ADMIN, RoleCodes.BIZ_LEADER)");
    }

    @Test
    @DisplayName("商品审核状态和日志语义委托商品域策略")
    void productAuditDecision_shouldDelegateAuditStatusAndLogPayloadToPolicy() throws Exception {
        String source = readSource("com/colonel/saas/service/ProductService.java");
        String readModelSource = readSource(
                "com/colonel/saas/service/ActivityProductReadModelQueryService.java");

        assertThat(source)
                .contains("ProductAuditDecisionPolicy")
                .contains("ProductOperationDecisionPolicy")
                .contains("productAuditDecisionPolicy.resolve")
                .contains("productOperationDecisionPolicy.libraryEntry")
                .contains("productOperationDecisionPolicy.bindActivity")
                .contains("productOperationDecisionPolicy.assignProduct")
                .contains("productOperationDecisionPolicy.assignAuditOwner")
                .contains("productOperationDecisionPolicy.progressDecision")
                .doesNotContain("审核通过前请补充：")
                .doesNotContain("审核通过并加入商品库")
                .doesNotContain("商品已分配给审核负责人")
                .doesNotContain("商品已分配给招商组长")
                .doesNotContain("商品推进判断已更新")
                .doesNotContain("private String normalizeDecisionLevel")
                .doesNotContain("private String decisionLabel");
        assertThat(readModelSource)
                .contains("ProductOperationDecisionPolicy")
                .contains("productOperationDecisionPolicy.decisionLabel");
    }

    @Test
    @DisplayName("商品快照基础读取委托商品域 query 服务")
    void productSnapshotReads_shouldDelegateToProductQueryService() throws Exception {
        String source = readSource("com/colonel/saas/service/ProductService.java");

        assertThat(source)
                .contains("ProductSnapshotQueryService")
                .contains("productSnapshotQueryService.pageLatest")
                .contains("productSnapshotQueryService.requireById")
                .contains("productSnapshotQueryService.requireActivityProduct")
                .contains("productSnapshotQueryService.findActivityProduct");
    }

    @Test
    @DisplayName("活动商品列表和详情读模型委托商品域 query 服务")
    void activityProductReadModels_shouldDelegateToProductQueryService() throws Exception {
        String source = readSource("com/colonel/saas/service/ProductService.java");

        assertThat(source)
                .contains("ActivityProductReadModelQueryService")
                .contains("activityProductReadModelQueryService.buildRemoteListView")
                .contains("activityProductReadModelQueryService.buildSnapshotItems")
                .contains("activityProductReadModelQueryService.buildDetailBase")
                .doesNotContain("private Map<String, DecisionSummary> buildDecisionSummaryMap")
                .doesNotContain("private DecisionSummary findDecisionSummary")
                .doesNotContain("private Map<String, OrderSummary> buildOrderSummaryMap")
                .doesNotContain("private OrderSummary findOrderSummary")
                .doesNotContain("private Map<String, PromotionSummary> buildPromotionSummaryMap")
                .doesNotContain("private PromotionSummary findPromotionSummary");
    }

    @Test
    @DisplayName("商品 backfill/repair 组件拆分保持服务只负责编排")
    void productBackfillAndRepair_shouldDelegateToDedicatedComponents() throws Exception {
        String backfillSource = readSource("com/colonel/saas/service/ProductActivityBackfillService.java");
        String displayRuleSource = readSource("com/colonel/saas/service/ProductDisplayRuleService.java");

        assertThat(backfillSource)
                .contains("ProductBackfillJobMetadata")
                .contains("backfillJobMetadata.started")
                .contains("backfillJobMetadata.progress")
                .contains("backfillJobMetadata.finished");

        assertThat(displayRuleSource)
                .contains("ProductLibraryRepairPolicy")
                .contains("productLibraryRepairPolicy.decide")
                .contains("productLibraryRepairPolicy.apply")
                .doesNotContain("private record LibraryRepairDecision");
    }

    @Test
    @DisplayName("商品 backfill 管理 API 统一经过商品域应用入口")
    void productBackfillAdmin_shouldRouteThroughProductApplicationBoundary() throws Exception {
        String controllerSource = readSource("com/colonel/saas/controller/ProductSyncAdminController.java");
        String applicationSource = readSource(
                "com/colonel/saas/domain/product/application/ProductActivityBackfillApplicationService.java");

        assertThat(controllerSource)
                .contains("ProductActivityBackfillApplicationService")
                .contains("BackfillCommand")
                .doesNotContain("ProductActivityBackfillService")
                .doesNotContain("ProductSyncDryRunProbeService");
        assertThat(applicationSource)
                .contains("ProductActivityBackfillService")
                .contains("ProductSyncDryRunProbeService.ActivityDryRunResult")
                .contains("toLegacyRequest");
    }

    @Test
    @DisplayName("商品转链 Port、mapping 和事件边界唯一收口")
    void productPromotion_shouldUseSingleApplicationPortAndCompletedEvent() throws Exception {
        String productServiceSource = readSource("com/colonel/saas/service/ProductService.java");
        String copyPromotionSource = readSource("com/colonel/saas/domain/product/application/CopyPromotionApplicationService.java");
        String eventPublisherSource = readSource("com/colonel/saas/domain/product/event/ProductDomainEventPublisher.java");
        Path duplicatePort = Path.of("src/main/java/com/colonel/saas/domain/product/port/DouyinConvertPort.java");
        if (!Files.exists(duplicatePort)) {
            duplicatePort = Path.of("backend/src/main/java/com/colonel/saas/domain/product/port/DouyinConvertPort.java");
        }

        assertThat(Files.exists(duplicatePort)).isFalse();
        assertThat(productServiceSource)
                .contains("domain.product.application.port.DouyinConvertPort")
                .contains("publishPromotionLinkCompleted")
                .contains("UUID mappingId;")
                .contains("mappingId = pickSourceMappingService.saveOrUpdate");
        assertThat(copyPromotionSource)
                .doesNotContain("domain.product.port.DouyinConvertPort")
                .doesNotContain("private final DouyinConvertPort");
        assertThat(eventPublisherSource)
                .contains("ProductPromotionLinkCompletedEvent")
                .contains("PRODUCT_PROMOTION_LINK_COMPLETED");
    }

    private String readSource(String relativePath) throws Exception {
        Path sourcePath = Path.of("src/main/java", relativePath);
        if (!Files.exists(sourcePath)) {
            sourcePath = Path.of("backend/src/main/java", relativePath);
        }
        return Files.readString(sourcePath);
    }
}
