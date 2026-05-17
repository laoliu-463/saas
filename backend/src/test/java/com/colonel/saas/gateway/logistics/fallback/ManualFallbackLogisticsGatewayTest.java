package com.colonel.saas.gateway.logistics.fallback;

import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.gateway.logistics.LogisticsGateway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ManualFallbackLogisticsGatewayTest {

    private final ManualFallbackLogisticsGateway gateway = new ManualFallbackLogisticsGateway();

    @Test
    void queryTrack_returnsNonAdvancingStatusWhenProviderIsNotConfigured() {
        var result = gateway.queryTrack("SF", "SF1234567890");

        assertThat(result.success()).isFalse();
        assertThat(result.companyCode()).isEqualTo("SF");
        assertThat(result.trackingNo()).isEqualTo("SF1234567890");
        assertThat(result.internalStatus()).isEqualTo("NOT_CONFIGURED");
        assertThat(result.signed()).isFalse();
        assertThat(result.traces()).isEmpty();
        assertThat(result.rawResponse()).containsEntry("provider", "manual-fallback");
    }

    @Test
    void queryStatus_returnsManualStatusWhenProviderIsNotConfigured() {
        var result = gateway.queryStatus("SF1234567890");

        assertThat(result.company()).isEqualTo("MANUAL");
        assertThat(result.status()).isEqualTo("NOT_CONFIGURED");
        assertThat(result.message()).contains("物流网关未配置");
        assertThat(result.updateTime()).isNotNull();
    }

    @Test
    void createShipment_requiresManualLogisticsInput() {
        assertThatThrownBy(() -> gateway.createShipment(null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("手动录入物流信息");
    }

    @Test
    void contextRegistersFallbackGatewayWhenNoProviderBeanExists() {
        new ApplicationContextRunner()
                .withUserConfiguration(FallbackScanConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(LogisticsGateway.class);
                    assertThat(context).hasSingleBean(ManualFallbackLogisticsGateway.class);
                });
    }

    @Configuration
    @ComponentScan(basePackageClasses = ManualFallbackLogisticsGatewayConfig.class)
    static class FallbackScanConfig {
    }
}
