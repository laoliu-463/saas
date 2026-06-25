package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.common.exception.ForbiddenException;
import com.colonel.saas.constant.ProductDisplayStatus;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.domain.product.port.ProductSampleApplicationPort;
import com.colonel.saas.domain.product.port.QuickSampleApplyCommand;
import com.colonel.saas.domain.product.port.QuickSampleApplyPortResult;
import com.colonel.saas.domain.user.policy.CurrentUserPermissionPolicy;
import com.colonel.saas.dto.product.QuickSampleApplyRequest;
import com.colonel.saas.gateway.douyin.DouyinQuickSampleGateway;
import com.colonel.saas.config.DddRefactorProperties;
import com.colonel.saas.domain.product.facade.ProductDomainFacade;
import com.colonel.saas.entity.Product;
import com.colonel.saas.entity.ProductOperationState;
import com.colonel.saas.entity.ProductSnapshot;
import com.colonel.saas.mapper.ProductMapper;
import com.colonel.saas.mapper.ProductOperationStateMapper;
import com.colonel.saas.mapper.ProductSnapshotMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 商品快速寄样服务测试（DDD-PRODUCT-005 重构后）。
 * <p>
 * 商品域只负责角色校验和商品上下文解析，
 * 寄样创建委托 {@link ProductSampleApplicationPort}。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class QuickSampleApplyTest {

    @Mock private ProductService productService;
    @Mock private ProductMapper productMapper;
    @Mock private ProductSnapshotMapper productSnapshotMapper;
    @Mock private ProductOperationStateMapper productOperationStateMapper;
    @Mock private DouyinQuickSampleGateway douyinQuickSampleGateway;
    @Mock private ProductSampleApplicationPort productSampleApplicationPort;
    @Mock private DddRefactorProperties dddRefactorProperties;
    @Mock private ProductDomainFacade productDomainFacade;

    private ProductQuickSampleService service;

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
        org.mockito.Mockito.lenient().when(dddRefactorProperties.isEnabled()).thenReturn(false);
        org.mockito.Mockito.lenient().when(douyinQuickSampleGateway.isSupported()).thenReturn(false);
        org.mockito.Mockito.lenient().when(douyinQuickSampleGateway.supportStatus())
                .thenReturn(DouyinQuickSampleGateway.SupportStatus.UNSUPPORTED_BY_SDK);
    }

    // --- Helper to build valid product context ---

    private void setupValidProductContext(UUID relationId) {
        Product product = new Product();
        product.setId(relationId);
        product.setProductId("9001");

        ProductSnapshot snapshot = new ProductSnapshot();
        snapshot.setId(relationId);
        snapshot.setActivityId("10001");
        snapshot.setProductId("9001");
        snapshot.setTitle("测试商品");

        ProductOperationState state = new ProductOperationState();
        state.setDisplayStatus(ProductDisplayStatus.DISPLAYING.name());
        state.setSelectedToLibrary(true);

        when(productService.getById(relationId)).thenReturn(product);
        when(productSnapshotMapper.selectById(relationId)).thenReturn(snapshot);
        when(productOperationStateMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(state);
    }

    private QuickSampleApplyPortResult buildSuccessResult(String talentId, UUID sampleId) {
        QuickSampleApplyPortResult result = new QuickSampleApplyPortResult();
        result.setSuccess(true);
        result.setSuccessCount(1);
        result.setFailureCount(0);

        QuickSampleApplyPortResult.TalentResult item = new QuickSampleApplyPortResult.TalentResult();
        item.setTalentId(talentId);
        item.setSuccess(true);
        item.setSampleRequestId(sampleId);
        item.setFallback(true);
        item.setGatewayStatus("UNSUPPORTED_BY_SDK");
        item.setFallbackType("LOCAL_FALLBACK");
        item.setMessage("系统内寄样申请已提交");
        result.getItems().add(item);
        return result;
    }

    private QuickSampleApplyPortResult buildFailureResult(String talentId, String message) {
        QuickSampleApplyPortResult result = new QuickSampleApplyPortResult();
        result.setSuccess(false);
        result.setSuccessCount(0);
        result.setFailureCount(1);

        QuickSampleApplyPortResult.TalentResult item = new QuickSampleApplyPortResult.TalentResult();
        item.setTalentId(talentId);
        item.setSuccess(false);
        item.setMessage(message);
        result.getItems().add(item);
        return result;
    }

    private QuickSampleApplyPortResult buildPartialResult(String t1, String t2) {
        QuickSampleApplyPortResult result = new QuickSampleApplyPortResult();
        result.setSuccess(false);
        result.setSuccessCount(1);
        result.setFailureCount(1);

        QuickSampleApplyPortResult.TalentResult ok = new QuickSampleApplyPortResult.TalentResult();
        ok.setTalentId(t1);
        ok.setSuccess(true);
        ok.setSampleRequestId(UUID.randomUUID());
        ok.setMessage("系统内寄样申请已提交");
        result.getItems().add(ok);

        QuickSampleApplyPortResult.TalentResult fail = new QuickSampleApplyPortResult.TalentResult();
        fail.setTalentId(t2);
        fail.setSuccess(false);
        fail.setMessage("达人不存在");
        result.getItems().add(fail);
        return result;
    }

    // ==================== 角色校验 ====================

    @Test
    void applyQuickSample_shouldRejectNonChannelRole() {
        QuickSampleApplyRequest request = new QuickSampleApplyRequest();
        request.setTalentIds(List.of("talent_001"));

        assertThatThrownBy(() -> service.applyQuickSample(
                UUID.randomUUID(), request, UUID.randomUUID(), UUID.randomUUID(), List.of(RoleCodes.BIZ_STAFF)))
                .isInstanceOf(ForbiddenException.class);
    }

    // ==================== 商品校验 ====================

    @Test
    void applyQuickSample_shouldThrowWhenProductNotFound() {
        UUID relationId = UUID.randomUUID();
        QuickSampleApplyRequest request = new QuickSampleApplyRequest();
        request.setTalentIds(List.of("douyin_talent_001"));

        when(productService.getById(relationId)).thenReturn(null);

        assertThatThrownBy(() -> service.applyQuickSample(
                relationId, request, UUID.randomUUID(), UUID.randomUUID(), List.of(RoleCodes.CHANNEL_STAFF)))
                .isInstanceOf(com.colonel.saas.common.exception.BusinessException.class)
                .hasMessageContaining("商品不存在");
    }

    @Test
    void applyQuickSample_shouldThrowWhenProductNotDisplaying() {
        UUID relationId = UUID.randomUUID();
        Product product = new Product();
        product.setId(relationId);
        product.setProductId("9001");

        ProductSnapshot snapshot = new ProductSnapshot();
        snapshot.setId(relationId);
        snapshot.setActivityId("10001");
        snapshot.setProductId("9001");

        ProductOperationState state = new ProductOperationState();
        state.setDisplayStatus(ProductDisplayStatus.HIDDEN.name());

        when(productService.getById(relationId)).thenReturn(product);
        when(productSnapshotMapper.selectById(relationId)).thenReturn(snapshot);
        when(productOperationStateMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(state);

        QuickSampleApplyRequest request = new QuickSampleApplyRequest();
        request.setTalentIds(List.of("douyin_talent_001"));

        assertThatThrownBy(() -> service.applyQuickSample(
                relationId, request, UUID.randomUUID(), UUID.randomUUID(), List.of(RoleCodes.CHANNEL_STAFF)))
                .isInstanceOf(com.colonel.saas.common.exception.BusinessException.class)
                .hasMessageContaining("仅展示中的商品可发起快速寄样");
    }

    // ==================== 商品域不直接写 sample mapper（DDD-PRODUCT-005 核心断言） ====================

    @Test
    void applyQuickSample_productDomainDoesNotDirectlyWriteSampleMapper() {
        UUID relationId = UUID.randomUUID();
        UUID sampleId = UUID.randomUUID();
        setupValidProductContext(relationId);
        when(productSampleApplicationPort.applyQuickSample(any()))
                .thenReturn(buildSuccessResult("douyin_talent_001", sampleId));

        QuickSampleApplyRequest request = new QuickSampleApplyRequest();
        request.setTalentIds(List.of("douyin_talent_001"));

        var response = service.applyQuickSample(
                relationId, request, UUID.randomUUID(), UUID.randomUUID(), List.of(RoleCodes.CHANNEL_STAFF));

        // 验证：商品域委托端口，不直接操作 sample mapper
        verify(productSampleApplicationPort).applyQuickSample(any(QuickSampleApplyCommand.class));
        assertThat(response.getSuccessCount()).isEqualTo(1);
    }

    // ==================== 单达人快速寄样成功 ====================

    @Test
    void applyQuickSample_singleTalentSuccess() {
        UUID relationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID sampleId = UUID.randomUUID();
        setupValidProductContext(relationId);
        when(productSampleApplicationPort.applyQuickSample(any()))
                .thenReturn(buildSuccessResult("douyin_talent_001", sampleId));

        QuickSampleApplyRequest request = new QuickSampleApplyRequest();
        request.setTalentIds(List.of("douyin_talent_001"));
        request.setQuantity(2);
        request.setSpecification("红色/L");
        request.setRecipientAddress("上海");

        var response = service.applyQuickSample(
                relationId, request, userId, UUID.randomUUID(), List.of(RoleCodes.CHANNEL_STAFF));

        assertThat(response.getSuccessCount()).isEqualTo(1);
        assertThat(response.getFailureCount()).isEqualTo(0);
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getItems()).singleElement()
                .satisfies(item -> {
                    assertThat(item.isSuccess()).isTrue();
                    assertThat(item.getSampleRequestId()).isEqualTo(sampleId);
                });

        // 验证命令字段正确传递
        ArgumentCaptor<QuickSampleApplyCommand> captor =
                ArgumentCaptor.forClass(QuickSampleApplyCommand.class);
        verify(productSampleApplicationPort).applyQuickSample(captor.capture());
        QuickSampleApplyCommand cmd = captor.getValue();
        assertThat(cmd.relationId()).isEqualTo(relationId);
        assertThat(cmd.talentIds()).containsExactly("douyin_talent_001");
        assertThat(cmd.quantity()).isEqualTo(2);
        assertThat(cmd.spec()).isEqualTo("红色/L");
        assertThat(cmd.receiverAddress()).isEqualTo("上海");
        assertThat(cmd.requestSource()).isEqualTo("quick_product_library");
        assertThat(cmd.userId()).isEqualTo(userId);
    }

    // ==================== 多达人快速寄样 ====================

    @Test
    void applyQuickSample_multiTalentPartialFailure() {
        UUID relationId = UUID.randomUUID();
        setupValidProductContext(relationId);
        when(productSampleApplicationPort.applyQuickSample(any()))
                .thenReturn(buildPartialResult("douyin_talent_001", "invalid_talent"));

        QuickSampleApplyRequest request = new QuickSampleApplyRequest();
        request.setTalentIds(List.of("douyin_talent_001", "invalid_talent"));

        var response = service.applyQuickSample(
                relationId, request, UUID.randomUUID(), UUID.randomUUID(), List.of(RoleCodes.CHANNEL_STAFF));

        assertThat(response.getSuccessCount()).isEqualTo(1);
        assertThat(response.getFailureCount()).isEqualTo(1);
        assertThat(response.isSuccess()).isFalse();
    }

    // ==================== 寄样校验失败时错误明细返回前端 ====================

    @Test
    void applyQuickSample_validationFailureReturnsErrorDetail() {
        UUID relationId = UUID.randomUUID();
        setupValidProductContext(relationId);
        when(productSampleApplicationPort.applyQuickSample(any()))
                .thenReturn(buildFailureResult("douyin_talent_001", "达人未满足默认寄样标准，请填写备注说明申请原因"));

        QuickSampleApplyRequest request = new QuickSampleApplyRequest();
        request.setTalentIds(List.of("douyin_talent_001"));

        var response = service.applyQuickSample(
                relationId, request, UUID.randomUUID(), UUID.randomUUID(), List.of(RoleCodes.CHANNEL_STAFF));

        assertThat(response.getFailureCount()).isEqualTo(1);
        assertThat(response.getItems().get(0).getMessage()).contains("达人未满足默认寄样标准");
    }

    @Test
    void applyQuickSample_sevenDaysDuplicateReturnsErrorDetail() {
        UUID relationId = UUID.randomUUID();
        setupValidProductContext(relationId);
        when(productSampleApplicationPort.applyQuickSample(any()))
                .thenReturn(buildFailureResult("douyin_talent_001",
                        "Duplicate sample request is blocked within 7 days"));

        QuickSampleApplyRequest request = new QuickSampleApplyRequest();
        request.setTalentIds(List.of("douyin_talent_001"));

        var response = service.applyQuickSample(
                relationId, request, UUID.randomUUID(), UUID.randomUUID(), List.of(RoleCodes.CHANNEL_STAFF));

        assertThat(response.getFailureCount()).isEqualTo(1);
        assertThat(response.getItems().get(0).getMessage()).contains("Duplicate sample request is blocked");
    }

    // ==================== 商品不存在/不可寄样时错误不变 ====================

    @Test
    void applyQuickSample_productNotInLibraryThrowsSameError() {
        UUID relationId = UUID.randomUUID();
        Product product = new Product();
        product.setId(relationId);
        product.setProductId("9001");

        ProductSnapshot snapshot = new ProductSnapshot();
        snapshot.setId(relationId);
        snapshot.setActivityId("10001");
        snapshot.setProductId("9001");

        ProductOperationState state = new ProductOperationState();
        state.setDisplayStatus(ProductDisplayStatus.DISPLAYING.name());
        state.setSelectedToLibrary(false);

        when(productService.getById(relationId)).thenReturn(product);
        when(productSnapshotMapper.selectById(relationId)).thenReturn(snapshot);
        when(productOperationStateMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(state);

        QuickSampleApplyRequest request = new QuickSampleApplyRequest();
        request.setTalentIds(List.of("douyin_talent_001"));

        assertThatThrownBy(() -> service.applyQuickSample(
                relationId, request, UUID.randomUUID(), UUID.randomUUID(), List.of(RoleCodes.CHANNEL_STAFF)))
                .isInstanceOf(com.colonel.saas.common.exception.BusinessException.class)
                .hasMessageContaining("该商品尚未加入商品库");
    }

    // ==================== 商品物化 ====================

    @Test
    void applyQuickSample_shouldMaterializeProductFromSnapshot() {
        UUID relationId = UUID.randomUUID();
        UUID sampleId = UUID.randomUUID();

        Product legacyProduct = new Product();
        legacyProduct.setId(relationId);
        legacyProduct.setProductId("9001");

        ProductSnapshot snapshot = new ProductSnapshot();
        snapshot.setId(relationId);
        snapshot.setActivityId("10001");
        snapshot.setProductId("9001");
        snapshot.setTitle("快照商品标题");
        snapshot.setPrice(19900L);
        snapshot.setCover("https://example.test/cover.jpg");

        ProductOperationState state = new ProductOperationState();
        state.setDisplayStatus(ProductDisplayStatus.DISPLAYING.name());
        state.setSelectedToLibrary(true);

        when(productService.getById(relationId)).thenReturn(legacyProduct);
        when(productSnapshotMapper.selectById(relationId)).thenReturn(snapshot);
        when(productOperationStateMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(state);
        when(productMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(productMapper.insert(any(Product.class))).thenReturn(1);
        when(productSampleApplicationPort.applyQuickSample(any()))
                .thenReturn(buildSuccessResult("douyin_talent_001", sampleId));

        QuickSampleApplyRequest request = new QuickSampleApplyRequest();
        request.setTalentIds(List.of("douyin_talent_001"));

        var response = service.applyQuickSample(
                relationId, request, UUID.randomUUID(), UUID.randomUUID(), List.of(RoleCodes.CHANNEL_STAFF));

        assertThat(response.getSuccessCount()).isEqualTo(1);

        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productMapper).insert(productCaptor.capture());
        assertThat(productCaptor.getValue().getId()).isEqualTo(relationId);
        assertThat(productCaptor.getValue().getName()).isEqualTo("快照商品标题");
    }

    // ==================== 网关状态透传 ====================

    @Test
    void applyQuickSample_gatewayStatusPassedToResponse() {
        UUID relationId = UUID.randomUUID();
        setupValidProductContext(relationId);
        when(productSampleApplicationPort.applyQuickSample(any()))
                .thenReturn(buildSuccessResult("douyin_talent_001", UUID.randomUUID()));

        QuickSampleApplyRequest request = new QuickSampleApplyRequest();
        request.setTalentIds(List.of("douyin_talent_001"));

        var response = service.applyQuickSample(
                relationId, request, UUID.randomUUID(), UUID.randomUUID(), List.of(RoleCodes.CHANNEL_STAFF));

        assertThat(response.isExternalSupported()).isFalse();
        assertThat(response.getGatewayStatus()).isEqualTo("UNSUPPORTED_BY_SDK");
        assertThat(response.getFallbackType()).isEqualTo("LOCAL_FALLBACK");
        assertThat(response.getMessage()).contains("抖店外部寄样暂未接通");
    }

    // ==================== 管理员角色可通过 ====================

    @Test
    void applyQuickSample_adminRoleCanApply() {
        UUID relationId = UUID.randomUUID();
        setupValidProductContext(relationId);
        when(productSampleApplicationPort.applyQuickSample(any()))
                .thenReturn(buildSuccessResult("douyin_talent_001", UUID.randomUUID()));

        QuickSampleApplyRequest request = new QuickSampleApplyRequest();
        request.setTalentIds(List.of("douyin_talent_001"));

        var response = service.applyQuickSample(
                relationId, request, UUID.randomUUID(), UUID.randomUUID(), List.of("admin"));

        assertThat(response.isSuccess()).isTrue();
    }

    @Test
    void applyQuickSample_shouldAcceptCommaSeparatedChannelRoleCodes() {
        UUID relationId = UUID.randomUUID();
        setupValidProductContext(relationId);
        when(productSampleApplicationPort.applyQuickSample(any()))
                .thenReturn(buildSuccessResult("douyin_talent_001", UUID.randomUUID()));

        QuickSampleApplyRequest request = new QuickSampleApplyRequest();
        request.setTalentIds(List.of("douyin_talent_001"));

        var response = service.applyQuickSample(
                relationId, request, UUID.randomUUID(), UUID.randomUUID(), " BIZ_STAFF, CHANNEL_STAFF ");

        assertThat(response.isSuccess()).isTrue();
    }

    // ==================== 端口未被调用时不应有副作用 ====================

    @Test
    void applyQuickSample_productValidationFails_portNotCalled() {
        UUID relationId = UUID.randomUUID();
        when(productService.getById(relationId)).thenReturn(null);

        QuickSampleApplyRequest request = new QuickSampleApplyRequest();
        request.setTalentIds(List.of("douyin_talent_001"));

        try {
            service.applyQuickSample(
                    relationId, request, UUID.randomUUID(), UUID.randomUUID(), List.of(RoleCodes.CHANNEL_STAFF));
        } catch (Exception ignored) {
        }

        verify(productSampleApplicationPort, never()).applyQuickSample(any());
    }
}
