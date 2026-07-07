package com.colonel.saas.architecture;

import com.colonel.saas.domain.product.application.ProductQuickSampleStatusQueryService;
import com.colonel.saas.domain.product.application.port.ProductQuickSampleGatewayStatusPort;
import com.colonel.saas.dto.douyin.DouyinQuickSampleStatusResponse;
import com.colonel.saas.service.ProductQuickSampleService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DddProductQuickSampleStatusQueryServiceTest {

    @Test
    void status_shouldKeepLegacyDiagnosticResponseFields() {
        ProductQuickSampleStatusQueryService service = new ProductQuickSampleStatusQueryService(
                () -> new ProductQuickSampleGatewayStatusPort.Status(true, "MOCK_ONLY"));

        DouyinQuickSampleStatusResponse response = service.status();

        assertThat(response.isSupported()).isTrue();
        assertThat(response.getStatus()).isEqualTo("MOCK_ONLY");
        assertThat(response.isRealConnected()).isFalse();
        assertThat(response.getMessage()).isEqualTo("当前 SDK 未支持 quick_sample_apply");
        assertThat(response.isFallbackEnabled()).isTrue();
    }

    @Test
    void status_shouldFallbackToLegacyUnsupportedStatusWhenGatewayStatusIsNull() {
        ProductQuickSampleStatusQueryService service = new ProductQuickSampleStatusQueryService(
                () -> new ProductQuickSampleGatewayStatusPort.Status(false, null));

        DouyinQuickSampleStatusResponse response = service.status();

        assertThat(response.isSupported()).isFalse();
        assertThat(response.getStatus()).isEqualTo(ProductQuickSampleService.GATEWAY_STATUS_UNSUPPORTED);
    }
}
