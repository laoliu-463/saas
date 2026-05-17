package com.colonel.saas.gateway.logistics.test;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestLogisticsGatewayTest {

    private final TestLogisticsGateway gateway = new TestLogisticsGateway();

    @Test
    void queryTrack_returnsInTransitScenarioFromTrackingNumber() {
        var result = gateway.queryTrack("SF", "TEST-IN_TRANSIT-001");

        assertThat(result.success()).isTrue();
        assertThat(result.companyCode()).isEqualTo("SF");
        assertThat(result.trackingNo()).isEqualTo("TEST-IN_TRANSIT-001");
        assertThat(result.externalState()).isEqualTo("2");
        assertThat(result.internalStatus()).isEqualTo("IN_TRANSIT");
        assertThat(result.signed()).isFalse();
        assertThat(result.traces()).isNotEmpty();
    }

    @Test
    void queryTrack_returnsSignedScenarioWithoutCompletingSample() {
        var result = gateway.queryTrack("SF", "TEST-SIGNED-001");

        assertThat(result.success()).isTrue();
        assertThat(result.externalState()).isEqualTo("3");
        assertThat(result.internalStatus()).isEqualTo("SIGNED");
        assertThat(result.signed()).isTrue();
        assertThat(result.signedAt()).isNotNull();
        assertThat(result.traces()).extracting("acceptStation")
                .anySatisfy(station -> assertThat((String) station).contains("签收"));
    }

    @Test
    void queryTrack_keepsFailureAsNonAdvancingStatus() {
        var result = gateway.queryTrack("SF", "TEST-FAILED-001");

        assertThat(result.success()).isFalse();
        assertThat(result.externalState()).isNull();
        assertThat(result.internalStatus()).isEqualTo("FAILED");
        assertThat(result.signed()).isFalse();
        assertThat(result.reason()).contains("模拟查询失败");
    }

    @Test
    void queryTrack_returnsExceptionScenarioFromTrackingNumber() {
        var result = gateway.queryTrack("YD", "TEST-EXCEPTION-001");

        assertThat(result.success()).isTrue();
        assertThat(result.companyCode()).isEqualTo("YD");
        assertThat(result.trackingNo()).isEqualTo("TEST-EXCEPTION-001");
        assertThat(result.externalState()).isEqualTo("4");
        assertThat(result.internalStatus()).isEqualTo("EXCEPTION");
        assertThat(result.signed()).isFalse();
        assertThat(result.traces()).isNotEmpty();
    }

    @Test
    void queryTrack_returnsNoTraceScenarioFromTrackingNumber() {
        var result = gateway.queryTrack("ZTO", "TEST-NO_TRACE-001");

        assertThat(result.success()).isTrue();
        assertThat(result.companyCode()).isEqualTo("ZTO");
        assertThat(result.trackingNo()).isEqualTo("TEST-NO_TRACE-001");
        assertThat(result.externalState()).isNull();
        assertThat(result.internalStatus()).isEqualTo("NO_TRACE");
        assertThat(result.signed()).isFalse();
        assertThat(result.traces()).isEmpty();
    }
}
