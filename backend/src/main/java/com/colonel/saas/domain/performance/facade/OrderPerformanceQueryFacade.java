package com.colonel.saas.domain.performance.facade;

import com.colonel.saas.dto.performance.OrderPerformanceBatchResponse;
import com.colonel.saas.dto.performance.OrderPerformanceDTO;
import com.colonel.saas.dto.performance.PerformanceListQuery;
import com.colonel.saas.dto.performance.PerformanceSummaryQuery;
import com.colonel.saas.dto.performance.PerformanceSummaryResponse;
import com.colonel.saas.domain.performance.policy.PerformanceAccessContext;

import java.util.List;

/**
 * 订单列表 / 详情 / 数据平台业绩补全门面（DDD-PERF-004）。
 *
 * <p>调用方通过本接口拿到订单业绩聚合数据，订单域不直接读
 * performance_records。</p>
 *
 * <p>设计目标：</p>
 * <ul>
 *   <li>单笔 / 批量查询通过 {@code orderIds} 走批量接口，避免 N+1</li>
 *   <li>{@link OrderPerformanceDTO} 是 BFF 需要的最小字段集，不暴露
 *       performance_records 表结构</li>
 *   <li>权限过滤仍走 UserDomainFacade，由调用方传入
 *       {@link PerformanceAccessContext}</li>
 * </ul>
 */
public interface OrderPerformanceQueryFacade {

    /** 单笔订单业绩补全；订单无业绩记录时返回空对象（isValid=false）。 */
    OrderPerformanceDTO getOrderPerformance(String orderId, PerformanceAccessContext context);

    /** 批量订单业绩补全；空入参返回空 items，不抛异常。 */
    OrderPerformanceBatchResponse batchGetOrderPerformance(List<String> orderIds,
                                                           PerformanceAccessContext context);

    /** 业绩列表（按 OrderPerformanceDTO 维度），用于订单详情扩展视图。 */
    List<OrderPerformanceDTO> listPerformance(PerformanceListQuery query,
                                              PerformanceAccessContext context);

    /** 业绩汇总（Dashboard 沿用既有 PerformanceSummaryResponse 形态）。 */
    PerformanceSummaryResponse getPerformanceSummary(PerformanceSummaryQuery query,
                                                     PerformanceAccessContext context);

    /** 业绩导出，返回 OrderPerformanceDTO 列表；调用方需自行限制行数。 */
    List<OrderPerformanceDTO> exportPerformance(PerformanceListQuery query,
                                                PerformanceAccessContext context);
}