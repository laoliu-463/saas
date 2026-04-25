package com.colonel.saas.gateway.douyin.real;

import com.colonel.saas.douyin.api.OrderApi;
import com.colonel.saas.gateway.douyin.DouyinOrderGateway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "douyin.mock.enabled", havingValue = "false", matchIfMissing = true)
public class RealDouyinOrderGateway implements DouyinOrderGateway {

    private final OrderApi orderApi;

    public RealDouyinOrderGateway(OrderApi orderApi) {
        this.orderApi = orderApi;
    }

    @Override
    public OrderListResult listSettlement(DouyinOrderQueryRequest request) {
        Map<String, Object> response = orderApi.listSettlement(
                request.startTime(),
                request.endTime(),
                request.count(),
                request.cursor()
        );
        return new OrderListResult(List.of(), false, "0", response);
    }

    @Override
    public OrderListResult listSettlementWindow(String cursor, Integer count) {
        Map<String, Object> response = orderApi.listSettlementWindow(cursor, count);
        return new OrderListResult(List.of(), false, cursor == null ? "0" : cursor, response);
    }

    @Override
    public Map<String, Object> decryptSensitiveData(List<String> orderIds) {
        return orderApi.decryptSensitiveData(orderIds);
    }
}
