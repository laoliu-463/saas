package com.colonel.saas.domain.order.policy;

import com.colonel.saas.common.enums.DataScope;

import java.time.LocalDate;
import java.util.Collection;
import java.util.UUID;

/** 数据平台订单与指标缓存键策略。 */
public final class OrderDataCacheKeyPolicy {

    private final OrderDataScopePolicy orderDataScopePolicy;

    public OrderDataCacheKeyPolicy(OrderDataScopePolicy orderDataScopePolicy) {
        this.orderDataScopePolicy = orderDataScopePolicy;
    }

    public String orderSummaryKey(
            String prefix,
            String timeColumn,
            LocalDate startDate, LocalDate endDate,
            String orderId, String status, UUID talentId, String merchantId,
            String productId, String productName, String shopName,
            String talentName, String colonelName, String channelName,
            String colonelActivityId, String recruitType,
            UUID userId, UUID deptId, DataScope dataScope, Collection<String> roleCodes) {
        return prefix + join(
                timeColumn,
                startDate, endDate,
                orderId, status, talentId, merchantId,
                productId, productName, shopName,
                talentName, colonelName, channelName,
                colonelActivityId, recruitType,
                userId, deptId, dataScope == null ? "NO_SCOPE" : dataScope,
                orderDataScopePolicy.roleCodesCacheKey(roleCodes));
    }

    public String metricsKey(
            String timeColumn,
            UUID userId,
            UUID deptId,
            DataScope dataScope,
            Collection<String> roleCodes) {
        String roleKey = orderDataScopePolicy.roleCodesCacheKey(roleCodes);
        if (dataScope == DataScope.PERSONAL) {
            return join(timeColumn, DataScope.PERSONAL, userId, roleKey);
        }
        if (dataScope == DataScope.DEPT) {
            return join(timeColumn, DataScope.DEPT, deptId, roleKey);
        }
        if (dataScope == DataScope.ALL) {
            return join(timeColumn, DataScope.ALL, roleKey);
        }
        return join(timeColumn, "NO_SCOPE", roleKey);
    }

    private String join(Object... values) {
        StringBuilder builder = new StringBuilder();
        for (Object value : values) {
            if (builder.length() > 0) {
                builder.append('|');
            }
            builder.append(value == null ? "" : value);
        }
        return builder.toString();
    }
}
