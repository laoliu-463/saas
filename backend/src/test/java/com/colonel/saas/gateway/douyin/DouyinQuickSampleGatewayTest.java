package com.colonel.saas.gateway.douyin;

import com.colonel.saas.gateway.douyin.real.RealDouyinQuickSampleGateway;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DouyinQuickSampleGatewayTest {

    @Test
    void realGateway_shouldReportUnsupportedBySdk() {
        RealDouyinQuickSampleGateway gateway = new RealDouyinQuickSampleGateway();
        assertThat(gateway.isSupported()).isFalse();
        assertThat(gateway.supportStatus()).isEqualTo(DouyinQuickSampleGateway.SupportStatus.UNSUPPORTED_BY_SDK);

        DouyinQuickSampleGateway.QuickSampleApplyResult result = gateway.apply(
                new DouyinQuickSampleGateway.QuickSampleApplyCommand(
                        "rel-1", "10001", "act-1", "talent-1", null, "spec", 1,
                        "name", "phone", "addr", "remark", "user-1"));
        assertThat(result.success()).isFalse();
        assertThat(result.errorCode()).isEqualTo("UNSUPPORTED_BY_SDK");
    }

    @Test
    void mockGateway_shouldNotSupportExternalApply() {
        MockDouyinQuickSampleGateway gateway = new MockDouyinQuickSampleGateway();
        assertThat(gateway.isSupported()).isFalse();
        assertThat(gateway.supportStatus()).isEqualTo(DouyinQuickSampleGateway.SupportStatus.MOCK_ONLY);
    }
}
