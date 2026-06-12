package com.colonel.saas.domain.performance.facade;

import com.colonel.saas.dto.performance.PerformanceBatchResponse;
import com.colonel.saas.dto.performance.PerformanceDetailDTO;
import com.colonel.saas.dto.performance.PerformanceListQuery;
import com.colonel.saas.dto.performance.PerformancePageResponse;
import com.colonel.saas.dto.performance.PerformanceSummaryQuery;
import com.colonel.saas.dto.performance.PerformanceSummaryResponse;
import com.colonel.saas.service.performance.PerformanceAccessContext;

import java.util.List;

/**
 * 业绩只读查询门面（DDD-PERF-003）。
 * <p>
 * 控制器及外部服务应通过本接口查询业绩及卡片汇总指标，
 * 避免对业绩查询和汇总服务进行直连依赖。
 * </p>
 */
public interface PerformanceQueryFacade {

    /** 单笔订单业绩查询。 */
    PerformanceDetailDTO getByOrderId(String orderId, PerformanceAccessContext context);

    /** 批量订单业绩查询。 */
    PerformanceBatchResponse batchGet(List<String> orderIds, PerformanceAccessContext context);

    /** 业绩列表分页查询。 */
    PerformancePageResponse list(PerformanceListQuery query, PerformanceAccessContext context);

    /** 业绩指标卡片汇总。 */
    PerformanceSummaryResponse getSummary(PerformanceSummaryQuery query, PerformanceAccessContext context);
}
