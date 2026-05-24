package com.colonel.saas.gateway.logistics.query;

/**
 * 统一物流查询网关（V2.0）。业务层通过 {@link LogisticsGatewayRouter} 调用，不直连快递鸟/快递100。
 */
public interface LogisticsQueryGateway {

    LogisticsQueryResult query(String logisticsCompany, String trackingNo);

    boolean isSupported();

    String providerName();
}
