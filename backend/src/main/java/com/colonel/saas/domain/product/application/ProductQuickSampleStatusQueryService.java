package com.colonel.saas.domain.product.application;

import com.colonel.saas.domain.product.application.port.ProductQuickSampleGatewayStatusPort;
import com.colonel.saas.dto.douyin.DouyinQuickSampleStatusResponse;
import org.springframework.stereotype.Service;

/**
 * 商品快速寄样状态查询应用服务。
 */
@Service
public class ProductQuickSampleStatusQueryService {

    private static final String GATEWAY_STATUS_UNSUPPORTED = "UNSUPPORTED_BY_SDK";
    private static final String QUICK_SAMPLE_UNSUPPORTED_MESSAGE = "当前 SDK 未支持 quick_sample_apply";

    private final ProductQuickSampleGatewayStatusPort gatewayStatusPort;

    public ProductQuickSampleStatusQueryService(ProductQuickSampleGatewayStatusPort gatewayStatusPort) {
        this.gatewayStatusPort = gatewayStatusPort;
    }

    public DouyinQuickSampleStatusResponse status() {
        ProductQuickSampleGatewayStatusPort.Status gatewayStatus = gatewayStatusPort.queryStatus();
        String statusName = gatewayStatus.status() == null
                ? GATEWAY_STATUS_UNSUPPORTED
                : gatewayStatus.status();
        return DouyinQuickSampleStatusResponse.builder()
                .supported(gatewayStatus.supported())
                .status(statusName)
                .realConnected(false)
                .message(QUICK_SAMPLE_UNSUPPORTED_MESSAGE)
                .fallbackEnabled(true)
                .build();
    }
}
