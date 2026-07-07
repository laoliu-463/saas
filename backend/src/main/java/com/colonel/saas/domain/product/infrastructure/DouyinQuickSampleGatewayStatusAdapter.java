package com.colonel.saas.domain.product.infrastructure;

import com.colonel.saas.domain.product.application.port.ProductQuickSampleGatewayStatusPort;
import com.colonel.saas.gateway.douyin.DouyinQuickSampleGateway;
import org.springframework.stereotype.Component;

/**
 * 商品快速寄样状态端口的 Douyin Gateway 适配器。
 */
@Component
public class DouyinQuickSampleGatewayStatusAdapter implements ProductQuickSampleGatewayStatusPort {

    private final DouyinQuickSampleGateway douyinQuickSampleGateway;

    public DouyinQuickSampleGatewayStatusAdapter(DouyinQuickSampleGateway douyinQuickSampleGateway) {
        this.douyinQuickSampleGateway = douyinQuickSampleGateway;
    }

    @Override
    public Status queryStatus() {
        DouyinQuickSampleGateway.SupportStatus supportStatus = douyinQuickSampleGateway.supportStatus();
        return new Status(
                douyinQuickSampleGateway.isSupported(),
                supportStatus == null ? null : supportStatus.name());
    }
}
