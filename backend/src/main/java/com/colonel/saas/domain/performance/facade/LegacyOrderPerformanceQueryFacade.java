package com.colonel.saas.domain.performance.facade;

import com.colonel.saas.dto.performance.OrderPerformanceBatchResponse;
import com.colonel.saas.dto.performance.OrderPerformanceDTO;
import com.colonel.saas.dto.performance.PerformanceDetailDTO;
import com.colonel.saas.dto.performance.PerformanceListItemDTO;
import com.colonel.saas.dto.performance.PerformanceListQuery;
import com.colonel.saas.dto.performance.PerformancePageResponse;
import com.colonel.saas.dto.performance.PerformanceSummaryQuery;
import com.colonel.saas.dto.performance.PerformanceSummaryResponse;
import com.colonel.saas.service.PerformanceQueryService;
import com.colonel.saas.service.PerformanceSummaryService;
import com.colonel.saas.domain.performance.policy.PerformanceAccessContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * {@link OrderPerformanceQueryFacade} 遗留实现（DDD-PERF-004）。
 *
 * <p>委派 {@link PerformanceQueryService} 与 {@link PerformanceSummaryService}
 * 执行实际查询，并把 {@link PerformanceDetailDTO} / {@link PerformanceBatchResponse}
 * 转换为 BFF 友好的 {@link OrderPerformanceDTO}，避免订单域直接依赖
 * performance_records Mapper。</p>
 */
@Service
public class LegacyOrderPerformanceQueryFacade implements OrderPerformanceQueryFacade {

    private static final Logger log = LoggerFactory.getLogger(LegacyOrderPerformanceQueryFacade.class);

    private final PerformanceQueryService performanceQueryService;
    private final PerformanceSummaryService performanceSummaryService;

    public LegacyOrderPerformanceQueryFacade(
            PerformanceQueryService performanceQueryService,
            PerformanceSummaryService performanceSummaryService) {
        this.performanceQueryService = performanceQueryService;
        this.performanceSummaryService = performanceSummaryService;
    }

    @Override
    public OrderPerformanceDTO getOrderPerformance(String orderId, PerformanceAccessContext context) {
        if (orderId == null || orderId.isEmpty()) {
            return emptyOrderPerformance(null);
        }
        try {
            PerformanceDetailDTO detail = performanceQueryService.getPerformance(orderId, context);
            return toOrderPerformance(detail);
        } catch (RuntimeException ex) {
            log.warn("getOrderPerformance failed orderId={}", orderId, ex);
            return emptyOrderPerformance(orderId);
        }
    }

    @Override
    public OrderPerformanceBatchResponse batchGetOrderPerformance(List<String> orderIds,
                                                                  PerformanceAccessContext context) {
        OrderPerformanceBatchResponse response = new OrderPerformanceBatchResponse();
        if (orderIds == null || orderIds.isEmpty()) {
            response.setItems(Collections.emptyList());
            return response;
        }
        try {
            response.setItems(performanceQueryService.batchGetOrderPerformance(orderIds, context));
            return response;
        } catch (RuntimeException ex) {
            log.warn("batchGetOrderPerformance failed size={}", orderIds.size(), ex);
            response.setItems(Collections.emptyList());
            return response;
        }
    }

    @Override
    public List<OrderPerformanceDTO> listPerformance(PerformanceListQuery query,
                                                     PerformanceAccessContext context) {
        if (query == null) {
            return Collections.emptyList();
        }
        try {
            PerformancePageResponse page = performanceQueryService.list(query, context);
            if (page == null || page.getItems() == null) {
                return Collections.emptyList();
            }
            List<OrderPerformanceDTO> items = new ArrayList<>(page.getItems().size());
            for (PerformanceListItemDTO item : page.getItems()) {
                items.add(toOrderPerformance(item));
            }
            return items;
        } catch (RuntimeException ex) {
            log.warn("listPerformance failed", ex);
            return Collections.emptyList();
        }
    }

    @Override
    public PerformanceSummaryResponse getPerformanceSummary(PerformanceSummaryQuery query,
                                                           PerformanceAccessContext context) {
        try {
            return performanceSummaryService.getSummary(query, context);
        } catch (RuntimeException ex) {
            log.warn("getPerformanceSummary failed", ex);
            return new PerformanceSummaryResponse();
        }
    }

    @Override
    public List<OrderPerformanceDTO> exportPerformance(PerformanceListQuery query,
                                                       PerformanceAccessContext context) {
        if (query == null) {
            return Collections.emptyList();
        }
        try {
            List<PerformanceListItemDTO> rows = performanceQueryService.listForExport(query, context);
            if (rows == null) {
                return Collections.emptyList();
            }
            List<OrderPerformanceDTO> items = new ArrayList<>(rows.size());
            for (PerformanceListItemDTO row : rows) {
                items.add(toOrderPerformance(row));
            }
            return items;
        } catch (RuntimeException ex) {
            log.warn("exportPerformance failed", ex);
            return Collections.emptyList();
        }
    }

    static OrderPerformanceDTO toOrderPerformance(PerformanceDetailDTO detail) {
        OrderPerformanceDTO dto = new OrderPerformanceDTO();
        if (detail == null) {
            return dto;
        }
        dto.setOrderId(detail.getOrderId());
        dto.setFinalChannelId(detail.getFinalChannelId());
        dto.setFinalChannelName(detail.getFinalChannelName());
        dto.setFinalRecruiterId(detail.getFinalRecruiterId());
        dto.setFinalRecruiterName(detail.getFinalRecruiterName());
        dto.setChannelAttributionType(detail.getChannelAttributionType());
        dto.setRecruiterAttributionType(detail.getRecruiterAttributionType());
        dto.setEstimateServiceProfit(detail.getEstimateServiceProfit());
        dto.setEffectiveServiceProfit(detail.getEffectiveServiceProfit());
        dto.setEstimateServiceFeeExpense(detail.getEstimateServiceFeeExpense());
        dto.setEffectiveServiceFeeExpense(detail.getEffectiveServiceFeeExpense());
        dto.setEstimateRecruiterCommission(detail.getEstimateRecruiterCommission());
        dto.setEffectiveRecruiterCommission(detail.getEffectiveRecruiterCommission());
        dto.setEstimateChannelCommission(detail.getEstimateChannelCommission());
        dto.setEffectiveChannelCommission(detail.getEffectiveChannelCommission());
        dto.setEstimateGrossProfit(detail.getEstimateGrossProfit());
        dto.setEffectiveGrossProfit(detail.getEffectiveGrossProfit());
        dto.setIsValid(detail.getValid());
        dto.setIsReversed(detail.getReversed());
        return dto;
    }

    static OrderPerformanceDTO toOrderPerformance(PerformanceListItemDTO item) {
        OrderPerformanceDTO dto = new OrderPerformanceDTO();
        if (item == null) {
            return dto;
        }
        dto.setOrderId(item.getOrderId());
        dto.setFinalChannelId(item.getFinalChannelId());
        dto.setFinalChannelName(item.getFinalChannelName());
        dto.setFinalRecruiterId(item.getFinalRecruiterId());
        dto.setFinalRecruiterName(item.getFinalRecruiterName());
        dto.setEstimateServiceProfit(item.getEstimateServiceProfit());
        dto.setEffectiveServiceProfit(item.getEffectiveServiceProfit());
        dto.setEstimateRecruiterCommission(item.getEstimateRecruiterCommission());
        dto.setEffectiveRecruiterCommission(item.getEffectiveRecruiterCommission());
        dto.setEstimateChannelCommission(item.getEstimateChannelCommission());
        dto.setEffectiveChannelCommission(item.getEffectiveChannelCommission());
        dto.setEstimateGrossProfit(item.getEstimateGrossProfit());
        dto.setEffectiveGrossProfit(item.getEffectiveGrossProfit());
        return dto;
    }

    static OrderPerformanceDTO emptyOrderPerformance(String orderId) {
        OrderPerformanceDTO dto = new OrderPerformanceDTO();
        dto.setOrderId(orderId);
        dto.setIsValid(Boolean.FALSE);
        dto.setIsReversed(Boolean.FALSE);
        return dto;
    }
}
