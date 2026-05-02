package com.colonel.saas.gateway.douyin.real;

import com.colonel.saas.douyin.api.OrderApi;
import com.colonel.saas.gateway.douyin.contract.DouyinContractFixtureProvider;
import com.colonel.saas.gateway.douyin.contract.DouyinUpstreamModeSupport;
import com.colonel.saas.gateway.douyin.DouyinOrderGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@ConditionalOnProperty(name = "douyin.test.enabled", havingValue = "false", matchIfMissing = true)
public class RealDouyinOrderGateway implements DouyinOrderGateway {

    private final OrderApi orderApi;
    private final DouyinUpstreamModeSupport upstreamModeSupport;
    private final DouyinContractFixtureProvider contractFixtureProvider;

    public RealDouyinOrderGateway(
            OrderApi orderApi,
            DouyinUpstreamModeSupport upstreamModeSupport,
            DouyinContractFixtureProvider contractFixtureProvider) {
        this.orderApi = orderApi;
        this.upstreamModeSupport = upstreamModeSupport;
        this.contractFixtureProvider = contractFixtureProvider;
    }

    @Override
    public OrderListResult listSettlement(DouyinOrderQueryRequest request) {
        logGateway();
        if (upstreamModeSupport.isContract()) {
            return contractFixtureProvider.buildOrderListResult(request);
        }
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
        logGateway();
        if (upstreamModeSupport.isContract()) {
            long endTime = System.currentTimeMillis() / 1000;
            long startTime = endTime - 3600;
            return contractFixtureProvider.buildOrderListResult(
                    new DouyinOrderQueryRequest(startTime, endTime, count == null ? 100 : count, cursor)
            );
        }
        Map<String, Object> response = orderApi.listSettlementWindow(cursor, count);
        return new OrderListResult(List.of(), false, cursor == null ? "0" : cursor, response);
    }

    @Override
    public Map<String, Object> decryptSensitiveData(List<String> orderIds) {
        logGateway();
        if (upstreamModeSupport.isContract()) {
            return contractFixtureProvider.buildDecryptSensitiveResponse(orderIds);
        }
        return orderApi.decryptSensitiveData(orderIds);
    }

    private void logGateway() {
        log.info(
                "gateway=RealDouyinOrderGateway, upstreamMode={}, appKey={}, shopId={}, authId={}",
                upstreamModeSupport.value(),
                mask(contractFixtureProvider.appKey()),
                contractFixtureProvider.shopId(),
                contractFixtureProvider.authId()
        );
    }

    private String mask(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.trim();
        if (normalized.length() <= 8) {
            return normalized;
        }
        return normalized.substring(0, 4) + "****" + normalized.substring(normalized.length() - 4);
    }
}
