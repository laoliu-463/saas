package com.colonel.saas.gateway.douyin;

import java.util.List;
import java.util.Map;

/**
 * 抖店订单 Gateway 接口。
 * <p>
 * 业务层（OrderSyncService / OrderDecryptService）只依赖此接口，
 * 不感知底层是 Test 还是真实 SDK 调用。
 */
public interface DouyinOrderGateway {

    /**
     * 按时间范围拉取结算订单列表（buyin.instituteOrderColonel）。
     */
    OrderListResult listSettlement(DouyinOrderQueryRequest request);

    /**
     * 按当前时间窗口拉取最近订单。
     */
    OrderListResult listSettlementWindow(String cursor, Integer count);

    /**
     * 解密订单敏感数据（order.batchSensitiveDataRequest / order.batchSensitive）。
     */
    Map<String, Object> decryptSensitiveData(List<String> orderIds);

    record DouyinOrderQueryRequest(
            long startTime,
            long endTime,
            int count,
            String cursor
    ) {}

    record DouyinOrderItem(
            String externalOrderId,
            String externalProductId,
            String productId,
            String merchantId,
            String merchantName,
            String talentId,
            String talentName,
            String pickSource,
            Long orderAmount,
            Long serviceFee,
            Integer orderStatus,
            Long createTime,
            Long settleTime,
            Map<String, Object> rawPayload
    ) {}

    record OrderListResult(
            List<DouyinOrderItem> orders,
            boolean hasMore,
            String nextCursor,
            Map<String, Object> rawResponse
    ) {
    }
}
