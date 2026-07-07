package com.colonel.saas.domain.product.application.port;

/**
 * 商品快速寄样 Gateway 状态端口。
 *
 * <p>隔离 Controller 与 legacy Douyin Gateway 的直接依赖，仅暴露诊断状态所需字段。</p>
 */
public interface ProductQuickSampleGatewayStatusPort {

    Status queryStatus();

    record Status(
            boolean supported,
            String status) {
    }
}
