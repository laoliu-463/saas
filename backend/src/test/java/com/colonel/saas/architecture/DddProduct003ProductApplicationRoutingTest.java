package com.colonel.saas.architecture;

import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.config.DddRefactorProperties;
import com.colonel.saas.domain.product.application.ProductQuickSampleApplicationService;
import com.colonel.saas.domain.product.facade.ProductDomainFacade;
import com.colonel.saas.domain.product.facade.dto.ProductSnapshotReadDTO;
import com.colonel.saas.dto.product.QuickSampleApplyRequest;
import com.colonel.saas.dto.product.QuickSampleApplyResponse;
import com.colonel.saas.service.ProductQuickSampleService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DddProduct003ProductApplicationRoutingTest {

    @Mock private ProductQuickSampleService productQuickSampleService;
    @Mock private ProductDomainFacade productDomainFacade;
    @Mock private DddRefactorProperties dddRefactorProperties;
    @Mock private DddRefactorProperties.Switch productFacadeSwitch;

    @InjectMocks
    private ProductQuickSampleApplicationService applicationService;

    private final UUID relationId = UUID.randomUUID();

    @Test
    @DisplayName("开关关闭时 applyQuickSample 直接委派旧服务")
    void applyQuickSample_shouldDelegateLegacyWhenSwitchOff() {
        when(dddRefactorProperties.isEnabled()).thenReturn(false);
        QuickSampleApplyResponse expected = new QuickSampleApplyResponse();
        expected.setSuccess(true);
        when(productQuickSampleService.applyQuickSample(eq(relationId), any(), any(), any(), any()))
                .thenReturn(expected);

        QuickSampleApplyRequest request = new QuickSampleApplyRequest();
        QuickSampleApplyResponse actual = applicationService.applyQuickSample(
                relationId, request, UUID.randomUUID(), UUID.randomUUID(), List.of("channel_staff"));

        assertThat(actual).isSameAs(expected);
        verify(productDomainFacade, never()).findSnapshotById(any());
    }

    @Test
    @DisplayName("开关开启且商品快照不存在时 applyQuickSample 抛 NOT_FOUND")
    void applyQuickSample_shouldRejectMissingProductWhenRoutingEnabled() {
        when(dddRefactorProperties.isEnabled()).thenReturn(true);
        when(dddRefactorProperties.getProductFacade()).thenReturn(productFacadeSwitch);
        when(productFacadeSwitch.isEnabled()).thenReturn(true);
        when(productDomainFacade.findSnapshotById(relationId)).thenReturn(null);

        assertThatThrownBy(() -> applicationService.applyQuickSample(
                relationId, new QuickSampleApplyRequest(), UUID.randomUUID(), UUID.randomUUID(), List.of("channel_staff")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("商品不存在");

        verify(productQuickSampleService, never()).applyQuickSample(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("开关开启且 Facade 存在时 applyQuickSample 继续委派旧服务")
    void applyQuickSample_shouldDelegateAfterFacadeCheckWhenRoutingEnabled() {
        when(dddRefactorProperties.isEnabled()).thenReturn(true);
        when(dddRefactorProperties.getProductFacade()).thenReturn(productFacadeSwitch);
        when(productFacadeSwitch.isEnabled()).thenReturn(true);
        when(productDomainFacade.findSnapshotById(relationId)).thenReturn(snapshot());
        QuickSampleApplyResponse expected = new QuickSampleApplyResponse();
        expected.setSuccess(true);
        when(productQuickSampleService.applyQuickSample(eq(relationId), any(), any(), any(), any()))
                .thenReturn(expected);

        QuickSampleApplyResponse actual = applicationService.applyQuickSample(
                relationId, new QuickSampleApplyRequest(), UUID.randomUUID(), UUID.randomUUID(), List.of("channel_staff"));

        assertThat(actual).isSameAs(expected);
        verify(productDomainFacade).findSnapshotById(relationId);
    }

    @Test
    @DisplayName("开关开启且商品快照存在但无同主键 product 记录时仍可寄样")
    void applyQuickSample_shouldDelegateWhenSnapshotExistsWithoutSamePrimaryKeyProduct() {
        when(dddRefactorProperties.isEnabled()).thenReturn(true);
        when(dddRefactorProperties.getProductFacade()).thenReturn(productFacadeSwitch);
        when(productFacadeSwitch.isEnabled()).thenReturn(true);
        when(productDomainFacade.findSnapshotById(relationId)).thenReturn(snapshot());
        QuickSampleApplyResponse expected = new QuickSampleApplyResponse();
        expected.setSuccess(true);
        when(productQuickSampleService.applyQuickSample(eq(relationId), any(), any(), any(), any()))
                .thenReturn(expected);

        QuickSampleApplyResponse actual = applicationService.applyQuickSample(
                relationId, new QuickSampleApplyRequest(), UUID.randomUUID(), UUID.randomUUID(), List.of("channel_staff"));

        assertThat(actual).isSameAs(expected);
        verify(productDomainFacade, never()).existsById(any());
    }

    private ProductSnapshotReadDTO snapshot() {
        return new ProductSnapshotReadDTO(
                relationId,
                "activity-1",
                "external-product-1",
                "测试商品",
                "https://example.com/cover.jpg",
                1001L,
                "测试店铺",
                9900L,
                "99.00",
                1,
                "https://example.com/product/1");
    }
}
