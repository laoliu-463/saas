package com.colonel.saas.domain.product.port;

/**
 * 商品域寄样委派端口 — 商品域通过此端口发起寄样申请，不直接依赖寄样域 API 包。
 */
public interface ProductSampleApplicationPort {

    QuickSampleApplyPortResult applyQuickSample(QuickSampleApplyCommand command);
}
