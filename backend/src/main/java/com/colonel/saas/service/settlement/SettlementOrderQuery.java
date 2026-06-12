package com.colonel.saas.service.settlement;

import java.util.List;

public record SettlementOrderQuery(
        String startTime,
        String endTime,
        String timeType,
        Integer size,
        String cursor,
        List<String> orderIds,
        Integer maxPages,
        Integer maxOrders,
        boolean writeEnabled) {

    public List<String> safeOrderIds() {
        return orderIds == null ? List.of() : orderIds;
    }
}
