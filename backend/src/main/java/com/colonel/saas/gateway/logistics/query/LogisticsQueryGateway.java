package com.colonel.saas.gateway.logistics.query;

import com.colonel.saas.gateway.logistics.LogisticsTrackCommand;

/**
 * 统一物流查询网关（V2.0）。业务层通过 {@link LogisticsGatewayRouter} 调用，不直连快递鸟/快递100。
 */
public interface LogisticsQueryGateway {

    LogisticsQueryResult query(String logisticsCompany, String trackingNo);

    default LogisticsQueryResult query(LogisticsTrackCommand command) {
        if (command == null) {
            return query(null, null);
        }
        return query(command.getCompanyCode(), command.getTrackingNo());
    }

    boolean isSupported();

    String providerName();
}
