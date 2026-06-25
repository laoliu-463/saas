package com.colonel.saas.service;

import com.colonel.saas.common.enums.DataScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Dashboard 新旧路径影子对账服务（DDD-ANALYTICS-002）。
 *
 * <p>当配置开关 {@code ddd.refactor.analytics-shadow.enabled=true} 开启时，
 * 在旧路径返回结果的同时，后台执行新路径（performance_records）聚合查询，
 * 对比 10 项指标并输出 diff 日志。diff 不影响接口响应。
 *
 * <p>对比分预估轨（create_time）和结算轨（settle_time）两组独立执行。
 * 旧路径仅提供结算轨的 orderCount / orderAmount / serviceFee 三项，
 * 其余 7 项为新增指标，仅记录新路径结果用于观测。
 *
 * <p>单位统一：旧路径 serviceFee 以元为单位，新路径以分为单位。
 * 对比时旧值统一乘以 100 换算为分。
 */
@Service
public class DashboardShadowCompareService {

    private static final Logger log = LoggerFactory.getLogger(DashboardShadowCompareService.class);

    private final PerformanceMetricsQueryService performanceMetricsQueryService;
    private final boolean enabled;

    public DashboardShadowCompareService(
            PerformanceMetricsQueryService performanceMetricsQueryService,
            @Value("${ddd.refactor.analytics-shadow.enabled:false}") boolean enabled) {
        this.performanceMetricsQueryService = performanceMetricsQueryService;
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 执行影子对账。
     *
     * <p>当开关关闭时直接返回 null。
     * 当开关开启时，分别对预估轨和结算轨执行新路径聚合，
     * 与旧 Summary 中的重叠指标进行对比，输出 diff 日志。
     *
     * @param oldSummary 旧路径已构建的 Summary（不可变读取）
     * @param startTime  时间范围起始
     * @param endTime    时间范围结束
     * @param userId     用户ID
     * @param deptId     部门ID
     * @param dataScope  数据范围
     * @return 对账结果；开关关闭时返回 null
     */
    public ShadowResult compare(
            DashboardService.Summary oldSummary,
            LocalDateTime startTime,
            LocalDateTime endTime,
            UUID userId,
            UUID deptId,
            DataScope dataScope) {
        if (!enabled) {
            return null;
        }
        try {
            PerformanceMetricsQueryService.PerformanceAggregate estimateAgg =
                    performanceMetricsQueryService.aggregateRange(
                            startTime, endTime, "create_time", userId, deptId, dataScope);
            PerformanceMetricsQueryService.PerformanceAggregate settleAgg =
                    performanceMetricsQueryService.aggregateRange(
                            startTime, endTime, "settle_time", userId, deptId, dataScope);

            long oldOrderCount = nz(oldSummary.getOrderCount());
            long oldOrderAmountCent = nz(oldSummary.getOrderAmount());
            long oldServiceFeeCent = nz(oldSummary.getServiceFee()) * 100L;

            List<MetricDiff> settleDiffs = new ArrayList<>();
            settleDiffs.add(diff("orderCount", oldOrderCount, settleAgg.orderCount()));
            settleDiffs.add(diff("orderAmountCent", oldOrderAmountCent, settleAgg.orderAmountCent()));
            settleDiffs.add(diff("serviceFeeIncomeCent", oldServiceFeeCent, settleAgg.serviceFeeIncomeCent()));
            settleDiffs.add(diff("techServiceFeeCent", null, settleAgg.techServiceFeeCent()));
            settleDiffs.add(diff("serviceFeeExpenseCent", null, settleAgg.serviceFeeExpenseCent()));
            settleDiffs.add(diff("serviceProfitCent", null, settleAgg.serviceProfitCent()));
            settleDiffs.add(diff("recruiterCommissionCent", null, settleAgg.talentCommissionCent()));
            settleDiffs.add(diff("channelCommissionCent", null, settleAgg.channelCommissionCent()));
            settleDiffs.add(diff("grossProfitCent", null, settleAgg.grossProfitCent()));

            TrackComparison settleTrack = new TrackComparison("settle", settleDiffs);
            logTrack(settleTrack);

            List<MetricDiff> estimateDiffs = new ArrayList<>();
            estimateDiffs.add(diff("orderCount", null, estimateAgg.orderCount()));
            estimateDiffs.add(diff("orderAmountCent", null, estimateAgg.orderAmountCent()));
            estimateDiffs.add(diff("serviceFeeIncomeCent", null, estimateAgg.serviceFeeIncomeCent()));
            estimateDiffs.add(diff("techServiceFeeCent", null, estimateAgg.techServiceFeeCent()));
            estimateDiffs.add(diff("serviceFeeExpenseCent", null, estimateAgg.serviceFeeExpenseCent()));
            estimateDiffs.add(diff("serviceProfitCent", null, estimateAgg.serviceProfitCent()));
            estimateDiffs.add(diff("recruiterCommissionCent", null, estimateAgg.talentCommissionCent()));
            estimateDiffs.add(diff("channelCommissionCent", null, estimateAgg.channelCommissionCent()));
            estimateDiffs.add(diff("grossProfitCent", null, estimateAgg.grossProfitCent()));

            TrackComparison estimateTrack = new TrackComparison("estimate", estimateDiffs);
            logTrack(estimateTrack);

            return new ShadowResult(estimateTrack, settleTrack);
        } catch (Exception e) {
            log.warn("[SHADOW-COMPARE] shadow comparison failed: {}", e.getMessage(), e);
            return null;
        }
    }

    private void logTrack(TrackComparison track) {
        for (MetricDiff d : track.metricDiffs()) {
            if (d.oldValue() == null) {
                log.info("[SHADOW-COMPARE] [{}] {} = {} (new-path-only)",
                        track.trackName(), d.metric(), d.newValue());
            } else if (d.diff() == 0) {
                log.info("[SHADOW-COMPARE] [{}] {} PASS old={} new={}",
                        track.trackName(), d.metric(), d.oldValue(), d.newValue());
            } else {
                log.warn("[SHADOW-COMPARE] [{}] {} DIFF old={} new={} diff={}",
                        track.trackName(), d.metric(), d.oldValue(), d.newValue(), d.diff());
            }
        }
    }

    private MetricDiff diff(String metric, Long oldValue, long newValue) {
        long d = oldValue != null ? newValue - oldValue : 0L;
        return new MetricDiff(metric, oldValue, newValue, d);
    }

    private long nz(Long val) {
        return val == null ? 0L : val;
    }

    // ─── Result records ───────────────────────────────────────────────

    /**
     * 影子对账总结果，包含预估轨和结算轨两组对比。
     */
    public record ShadowResult(TrackComparison estimateTrack, TrackComparison settleTrack) {
        /** 结算轨所有可对比指标是否全部一致 */
        public boolean allMatch() {
            return settleTrack.allMatch();
        }
    }

    /**
     * 单轨对账结果。
     *
     * @param trackName   轨名称（"estimate" 或 "settle"）
     * @param metricDiffs 各指标对比详情
     */
    public record TrackComparison(String trackName, List<MetricDiff> metricDiffs) {
        /** 所有有旧值可比的指标是否全部一致 */
        public boolean allMatch() {
            return metricDiffs.stream()
                    .filter(d -> d.oldValue() != null)
                    .allMatch(d -> d.diff() == 0);
        }
    }

    /**
     * 单指标对比详情。
     *
     * @param metric   指标名称
     * @param oldValue 旧路径值（元→分换算后）；null 表示旧路径无此指标
     * @param newValue 新路径值
     * @param diff     差值（newValue - oldValue）；oldValue 为 null 时为 0
     */
    public record MetricDiff(String metric, Long oldValue, long newValue, long diff) {
    }
}
