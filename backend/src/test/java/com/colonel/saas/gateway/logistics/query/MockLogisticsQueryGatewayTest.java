package com.colonel.saas.gateway.logistics.query;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MockLogisticsQueryGatewayTest {

    private final MockLogisticsQueryGateway gateway = new MockLogisticsQueryGateway();

    @Test
    void querySignedPrefix_shouldReturnSigned() {
        LogisticsQueryResult result = gateway.query("SF", "MOCK-SF-001");
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isSigned()).isTrue();
        assertThat(result.getStatusCode()).isEqualTo(LogisticsStatusCode.SIGNED);
    }

    @Test
    void queryInTransitPrefix_shouldStayInTransit() {
        LogisticsQueryResult result = gateway.query("SF", "TRANSIT-001");
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isSigned()).isFalse();
        assertThat(result.getStatusCode()).isEqualTo(LogisticsStatusCode.IN_TRANSIT);
    }

    @Test
    void queryFailedPrefix_shouldReturnError() {
        LogisticsQueryResult result = gateway.query("SF", "FAIL-001");
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getStatusCode()).isEqualTo(LogisticsStatusCode.ERROR);
    }
}
