package com.colonel.saas.domain.performance.application;

import com.colonel.saas.domain.performance.policy.PerformanceAttributionPolicy;
import com.colonel.saas.entity.ColonelsettlementOrder;

/**
 * 业绩域最终归属解析端口。
 *
 * <p>订单实体只提供默认归属事实；本端口负责将默认事实交给业绩域规则，
 * 返回最终渠道/招商归属。应用服务不直接读取订单域的最终归属字段。</p>
 */
@FunctionalInterface
public interface PerformanceAttributionResolver {

    /**
     * 根据订单事实解析业绩最终归属。
     *
     * @param order 订单事实
     * @return 最终归属结果；实现不得返回 {@code null}
     */
    PerformanceAttributionPolicy.AttributionResult resolve(ColonelsettlementOrder order);
}
