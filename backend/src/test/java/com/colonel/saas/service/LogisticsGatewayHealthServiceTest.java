package com.colonel.saas.service;

import com.colonel.saas.config.LogisticsProperties;
import com.colonel.saas.domain.logistics.application.LogisticsGatewayHealthApplicationService;
import com.colonel.saas.dto.logistics.LogisticsGatewayHealthResponse;
import com.colonel.saas.dto.logistics.LogisticsGatewayTestRequest;
import com.colonel.saas.dto.logistics.LogisticsGatewayTestResponse;
import com.colonel.saas.gateway.logistics.query.Kuaidi100LogisticsQueryGateway;
import com.colonel.saas.gateway.logistics.query.KuaidiNiaoLogisticsQueryGateway;
import com.colonel.saas.gateway.logistics.query.MockLogisticsQueryGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * LogisticsGatewayHealthService 委派壳冒烟测试（DDD-LOGISTICS-001 Slice 1）。
 *
 * <p>Service 已是 1-line delegate；本测试仅验证委派路径打通，详细业务逻辑断言
 * 见 {@link LogisticsGatewayHealthApplicationServiceTest}。</p>
 */
@ExtendWith(MockitoExtension.class)
class LogisticsGatewayHealthServiceTest {

    @Mock
    private LogisticsGatewayHealthApplicationService applicationService;

    private LogisticsGatewayHealthService service;

    @BeforeEach
    void setUp() {
        service = new LogisticsGatewayHealthService(
                new LogisticsProperties(),
                new MockLogisticsQueryGateway(),
                null, null, false,
                applicationService);
    }

    @Test
    void diagnoseCurrentProvider_shouldDelegateToApplication() {
        LogisticsGatewayHealthResponse expected = LogisticsGatewayHealthResponse.builder().build();
        when(applicationService.diagnoseCurrentProvider()).thenReturn(expected);

        LogisticsGatewayHealthResponse result = service.diagnoseCurrentProvider();

        assertThat(result).isSameAs(expected);
        verify(applicationService).diagnoseCurrentProvider();
    }

    @Test
    void diagnoseProvider_shouldDelegateToApplication() {
        LogisticsGatewayHealthResponse expected = LogisticsGatewayHealthResponse.builder().build();
        when(applicationService.diagnoseProvider("kuaidiniao")).thenReturn(expected);

        LogisticsGatewayHealthResponse result = service.diagnoseProvider("kuaidiniao");

        assertThat(result).isSameAs(expected);
        verify(applicationService).diagnoseProvider("kuaidiniao");
    }

    @Test
    void testQuery_shouldDelegateToApplication() {
        LogisticsGatewayTestRequest request = new LogisticsGatewayTestRequest();
        request.setProvider("kuaidiniao");
        LogisticsGatewayTestResponse expected = LogisticsGatewayTestResponse.builder().build();
        when(applicationService.testQuery(request)).thenReturn(expected);

        LogisticsGatewayTestResponse result = service.testQuery(request);

        assertThat(result).isSameAs(expected);
        verify(applicationService).testQuery(request);
    }

    @Test
    void maskTrackingNo_shouldRemainAsStaticForwarder() {
        // 静态工具方法仍可访问（兼容旧调用方）
        assertThat(LogisticsGatewayHealthService.maskTrackingNo("SF1234567890"))
                .isEqualTo("SF1***90");
    }
}
