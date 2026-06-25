package com.colonel.saas.service.settlement;

public interface SettlementOrderGateway {

    SettlementOrderPage fetch(SettlementOrderQuery query);
}
