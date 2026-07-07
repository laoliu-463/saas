package com.colonel.saas.domain.product.application;

import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.common.exception.UpstreamErrorCode;
import com.colonel.saas.common.result.ResultCode;
import com.colonel.saas.domain.config.facade.ConfigDomainFacade;
import com.colonel.saas.domain.product.application.dto.PromotionLinkCopyResult;
import com.colonel.saas.domain.product.application.port.CopyPromotionSupportPort;
import com.colonel.saas.entity.ProductOperationState;
import com.colonel.saas.entity.ProductSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CopyPromotionApplicationServiceTest {

    @Mock
    private CopyPromotionSupportPort copyPromotionSupportPort;
    @Mock
    private ConfigDomainFacade configDomainFacade;

    private CopyPromotionApplicationService applicationService;

    @BeforeEach
    void setUp() {
        applicationService = new CopyPromotionApplicationService(
                copyPromotionSupportPort,
                configDomainFacade);
    }

    @Test
    void copyPromotion_shouldRenderFallbackTextWhenRealWriteDisabled() {
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();

        ProductSnapshot snapshot = new ProductSnapshot();
        snapshot.setProductId("P-1");
        snapshot.setTitle("测试商品");
        snapshot.setShopName("测试店铺");
        ProductOperationState state = new ProductOperationState();
        var context = new CopyPromotionSupportPort.Context(snapshot, state);
        when(copyPromotionSupportPort.prepareCopyPromotionContext(any(), any(), any())).thenReturn(context);

        PromotionLinkCopyResult result = applicationService.copyPromotion(
                "ACT-1",
                "P-1",
                userId,
                deptId,
                "ext-1",
                4,
                true,
                "PRODUCT_LIBRARY",
                "talent-1",
                "idem-1",
                false,
                false);

        assertThat(result.copyText()).isNotNull();
        assertThat(result.realPromotionWriteEnabled()).isFalse();
    }

    @Test
    void copyPromotion_shouldDelegatePromotionLinkAndExposePickSourceWhenRealWriteAllowed() {
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();

        ProductSnapshot snapshot = new ProductSnapshot();
        snapshot.setProductId("P-1");
        snapshot.setTitle("测试商品");
        snapshot.setShopName("测试店铺");
        ProductOperationState state = new ProductOperationState();
        var context = new CopyPromotionSupportPort.Context(snapshot, state);
        when(copyPromotionSupportPort.prepareCopyPromotionContext(any(), any(), any())).thenReturn(context);
        when(copyPromotionSupportPort.generatePromotionLinkForCopy(
                eq("ACT-1"),
                eq("P-1"),
                eq(userId),
                eq(deptId),
                eq("ext-1"),
                eq(4),
                eq(true),
                eq("PRODUCT_LIBRARY"),
                eq("talent-1"),
                eq("idem-1")))
                .thenReturn(new CopyPromotionSupportPort.GeneratedPromotionLink(
                        "https://short.example/p1",
                        "https://promote.example/p1",
                        "PS-TRACE-1"));

        PromotionLinkCopyResult result = applicationService.copyPromotion(
                "ACT-1",
                "P-1",
                userId,
                deptId,
                "ext-1",
                4,
                true,
                "PRODUCT_LIBRARY",
                "talent-1",
                "idem-1",
                true,
                true);

        assertThat(result.promotionLinkGenerated()).isTrue();
        assertThat(result.promotionLink()).isEqualTo("https://short.example/p1");
        assertThat(result.pickSource()).isEqualTo("PS-TRACE-1");
        assertThat(result.fallbackReason()).isNull();
        verify(copyPromotionSupportPort).generatePromotionLinkForCopy(
                "ACT-1",
                "P-1",
                userId,
                deptId,
                "ext-1",
                4,
                true,
                "PRODUCT_LIBRARY",
                "talent-1",
                "idem-1");
    }

    @Test
    void copyPromotion_shouldPropagateStateInvalidErrorCodeWhenProductStateRejectsPromotion() {
        when(copyPromotionSupportPort.prepareCopyPromotionContext(any(), any(), any()))
                .thenThrow(BusinessException.stateInvalid("当前状态不允许执行PROMOTION_LINK，当前状态：PENDING"));

        assertThatThrownBy(() -> applicationService.copyPromotion(
                "ACT-1",
                "P-1",
                UUID.randomUUID(),
                UUID.randomUUID(),
                "ext-1",
                4,
                true,
                "PRODUCT_LIBRARY",
                "talent-1",
                "idem-1",
                true,
                true))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> {
                    BusinessException business = (BusinessException) error;
                    assertThat(business.getCode()).isEqualTo(ResultCode.STATE_INVALID.getCode());
                    assertThat(business.getErrorCode()).isNull();
                    assertThat(business.getMessage()).contains("PROMOTION_LINK");
                });
    }

    @Test
    void copyPromotion_shouldPropagateIdempotencyInProgressErrorCodeWhenDuplicateRequestIsRunning() {
        ProductSnapshot snapshot = new ProductSnapshot();
        snapshot.setProductId("P-1");
        ProductOperationState state = new ProductOperationState();
        when(copyPromotionSupportPort.prepareCopyPromotionContext(any(), any(), any()))
                .thenReturn(new CopyPromotionSupportPort.Context(snapshot, state));
        when(copyPromotionSupportPort.generatePromotionLinkForCopy(
                any(), any(), any(), any(), any(), any(), anyBoolean(), any(), any(), any()))
                .thenThrow(BusinessException.idempotencyInProgress("相同 Idempotency-Key 请求正在处理中，请稍后重试"));

        assertThatThrownBy(() -> applicationService.copyPromotion(
                "ACT-1",
                "P-1",
                UUID.randomUUID(),
                UUID.randomUUID(),
                "ext-1",
                4,
                true,
                "PRODUCT_LIBRARY",
                "talent-1",
                "idem-1",
                true,
                true))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> {
                    BusinessException business = (BusinessException) error;
                    assertThat(business.getCode()).isEqualTo(ResultCode.IDEMPOTENCY_IN_PROGRESS.getCode());
                    assertThat(business.getErrorCode()).isNull();
                    assertThat(business.getMessage()).contains("Idempotency-Key");
                });
    }

    @Test
    void copyPromotion_shouldPropagateUpstreamErrorCodeWhenPromotionGatewayFails() {
        ProductSnapshot snapshot = new ProductSnapshot();
        snapshot.setProductId("P-1");
        ProductOperationState state = new ProductOperationState();
        when(copyPromotionSupportPort.prepareCopyPromotionContext(any(), any(), any()))
                .thenReturn(new CopyPromotionSupportPort.Context(snapshot, state));
        when(copyPromotionSupportPort.generatePromotionLinkForCopy(
                any(), any(), any(), any(), any(), any(), anyBoolean(), any(), any(), any()))
                .thenThrow(BusinessException.upstream(
                        UpstreamErrorCode.DOUYIN_TOKEN_INVALID,
                        "抖音 token 失效，请重新授权"));

        assertThatThrownBy(() -> applicationService.copyPromotion(
                "ACT-1",
                "P-1",
                UUID.randomUUID(),
                UUID.randomUUID(),
                "ext-1",
                4,
                true,
                "PRODUCT_LIBRARY",
                "talent-1",
                "idem-1",
                true,
                true))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> {
                    BusinessException business = (BusinessException) error;
                    assertThat(business.getCode()).isEqualTo(ResultCode.EXTERNAL_SERVICE.getCode());
                    assertThat(business.getErrorCode()).isEqualTo(UpstreamErrorCode.DOUYIN_TOKEN_INVALID.name());
                    assertThat(business.getMessage()).contains("重新授权");
                });
    }

    @Test
    void copyPromotionApplicationService_shouldNotDependOnLegacyProductServiceOrGateway() throws Exception {
        Path sourcePath = Path.of("src/main/java/com/colonel/saas/domain/product/application/CopyPromotionApplicationService.java");
        if (!Files.exists(sourcePath)) {
            sourcePath = Path.of("backend/src/main/java/com/colonel/saas/domain/product/application/CopyPromotionApplicationService.java");
        }
        String source = Files.readString(sourcePath);

        assertThat(source)
                .doesNotContain("com.colonel.saas.service.ProductService")
                .doesNotContain("com.colonel.saas.gateway.douyin.DouyinPromotionGateway")
                .doesNotContain("@Lazy");
    }

    @Test
    void legacyProductService_shouldNotExposeCopyPromotionEntryPoint() throws Exception {
        Path sourcePath = Path.of("src/main/java/com/colonel/saas/service/ProductService.java");
        if (!Files.exists(sourcePath)) {
            sourcePath = Path.of("backend/src/main/java/com/colonel/saas/service/ProductService.java");
        }
        String source = Files.readString(sourcePath);

        assertThat(source)
                .doesNotContain("generatePromotionLinkCopy(")
                .doesNotContain("CopyPromotionApplicationService");
    }
}
