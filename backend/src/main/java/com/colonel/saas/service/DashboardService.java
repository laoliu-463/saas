package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.mapper.ColonelsettlementOrderMapper;
import lombok.Data;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DashboardService {

    private final ColonelsettlementOrderMapper orderMapper;

    public DashboardService(ColonelsettlementOrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    public Summary getSummary(LocalDateTime startTime, LocalDateTime endTime, UUID userId, UUID deptId, DataScope dataScope) {
        // 总计
        QueryWrapper<ColonelsettlementOrder> totalWrapper = new QueryWrapper<ColonelsettlementOrder>()
                .select("count(*) as orderCount", "sum(order_amount) as orderAmount", "sum(settle_colonel_commission) as serviceFee");
        applyRange(totalWrapper, startTime, endTime);
        applyScope(totalWrapper, userId, deptId, dataScope);
        Map<String, Object> totalMap = orderMapper.selectMaps(totalWrapper).stream()
                .findFirst()
                .orElse(Map.of());

        // 已归因/未归因
        QueryWrapper<ColonelsettlementOrder> attributedWrapper = new QueryWrapper<ColonelsettlementOrder>()
                .eq("attribution_status", AttributionService.STATUS_ATTRIBUTED);
        applyRange(attributedWrapper, startTime, endTime);
        applyScope(attributedWrapper, userId, deptId, dataScope);
        Long attributedCount = orderMapper.selectCount(attributedWrapper);
        QueryWrapper<ColonelsettlementOrder> unattributedWrapper = new QueryWrapper<ColonelsettlementOrder>()
                .eq("attribution_status", AttributionService.STATUS_UNATTRIBUTED);
        applyRange(unattributedWrapper, startTime, endTime);
        applyScope(unattributedWrapper, userId, deptId, dataScope);
        Long unattributedCount = orderMapper.selectCount(unattributedWrapper);

        // 渠道业绩
        QueryWrapper<ColonelsettlementOrder> channelWrapper = new QueryWrapper<ColonelsettlementOrder>()
                .select("channel_user_id as channelUserId", "channel_user_name as channelUserName", "count(*) as orderCount", "sum(order_amount) as orderAmount", "sum(settle_colonel_commission) as serviceFee")
                .isNotNull("channel_user_id")
                .groupBy("channel_user_id", "channel_user_name");
        applyRange(channelWrapper, startTime, endTime);
        applyScope(channelWrapper, userId, deptId, dataScope);
        List<PerformanceItem> channelPerformance = orderMapper.selectMaps(channelWrapper).stream()
                .map(this::toPerformanceItem)
                .sorted(Comparator.comparingLong(PerformanceItem::getOrderCount).reversed())
                .limit(10)
                .toList();

        // 招商业绩
        QueryWrapper<ColonelsettlementOrder> colonelWrapper = new QueryWrapper<ColonelsettlementOrder>()
                .select("colonel_user_id as colonelUserId", "colonel_user_name as colonelUserName", "count(*) as orderCount", "sum(order_amount) as orderAmount", "sum(settle_colonel_commission) as serviceFee")
                .isNotNull("colonel_user_id")
                .groupBy("colonel_user_id", "colonel_user_name");
        applyRange(colonelWrapper, startTime, endTime);
        applyScope(colonelWrapper, userId, deptId, dataScope);
        List<PerformanceItem> colonelPerformance = orderMapper.selectMaps(colonelWrapper).stream()
                .map(this::toPerformanceItem)
                .sorted(Comparator.comparingLong(PerformanceItem::getOrderCount).reversed())
                .limit(10)
                .toList();

        QueryWrapper<ColonelsettlementOrder> reasonWrapper = new QueryWrapper<ColonelsettlementOrder>()
                .select("attribution_remark as reason", "count(*) as count")
                .eq("attribution_status", AttributionService.STATUS_UNATTRIBUTED)
                .isNotNull("attribution_remark")
                .groupBy("attribution_remark");
        applyRange(reasonWrapper, startTime, endTime);
        applyScope(reasonWrapper, userId, deptId, dataScope);
        List<ReasonCountItem> unattributedReasons = orderMapper.selectMaps(reasonWrapper).stream()
                .map(this::toReasonCountItem)
                .sorted(Comparator.comparingLong(ReasonCountItem::getCount).reversed())
                .toList();

        Summary summary = new Summary();
        summary.setOrderCount(asLong(totalMap.get("ordercount")));
        summary.setOrderAmount(asLong(totalMap.get("orderamount")));
        summary.setServiceFee(asLong(totalMap.get("servicefee")));
        summary.setAttributedOrderCount(attributedCount);
        summary.setUnattributedOrderCount(unattributedCount);
        summary.setAttributionRate(summary.getOrderCount() == null || summary.getOrderCount() == 0
                ? 0D
                : attributedCount.doubleValue() / summary.getOrderCount().doubleValue());
        summary.setChannelPerformance(channelPerformance);
        summary.setColonelPerformance(colonelPerformance);
        summary.setUnattributedReasons(unattributedReasons);
        return summary;
    }

    public Summary getSummary() {
        return getSummary(null, null, null, null, null);
    }

    private PerformanceItem toPerformanceItem(Map<String, Object> map) {
        PerformanceItem item = new PerformanceItem();
        item.setChannelUserId(asString(map.get("channeluserid")));
        item.setChannelUserName(asString(map.get("channelusername")));
        item.setColonelUserId(asString(map.get("coloneluserid")));
        item.setColonelUserName(asString(map.get("colonelusername")));
        item.setOrderCount(asLong(map.get("ordercount")));
        item.setOrderAmount(asLong(map.get("orderamount")));
        item.setServiceFee(asLong(map.get("servicefee")));
        return item;
    }

    private ReasonCountItem toReasonCountItem(Map<String, Object> map) {
        ReasonCountItem item = new ReasonCountItem();
        item.setReason(asString(map.get("reason")));
        item.setCount(asLong(map.get("count")));
        return item;
    }

    private void applyRange(QueryWrapper<ColonelsettlementOrder> wrapper, LocalDateTime startTime, LocalDateTime endTime) {
        if (wrapper == null) {
            return;
        }
        if (startTime != null) {
            wrapper.ge("settle_time", startTime);
        }
        if (endTime != null) {
            wrapper.le("settle_time", endTime);
        }
    }

    private void applyScope(QueryWrapper<ColonelsettlementOrder> wrapper, UUID userId, UUID deptId, DataScope dataScope) {
        if (wrapper == null || dataScope == null) {
            return;
        }
        switch (dataScope) {
            case PERSONAL -> { if (userId != null) { wrapper.eq("user_id", userId); } }
            case DEPT -> { if (deptId != null) { wrapper.eq("dept_id", deptId); } }
            case ALL -> { /* no filter */ }
        }
    }

    private long asLong(Object val) {
        if (val == null) return 0L;
        if (val instanceof Number n) return n.longValue();
        return 0L;
    }

    private String asString(Object val) {
        return val == null ? null : String.valueOf(val);
    }

    @Data
    public static class Summary {
        private Long orderCount;
        private Long orderAmount;
        private Long serviceFee;
        private Long attributedOrderCount;
        private Long unattributedOrderCount;
        private Double attributionRate;
        private List<PerformanceItem> channelPerformance;
        private List<PerformanceItem> colonelPerformance;
        private List<ReasonCountItem> unattributedReasons;
    }

    @Data
    public static class PerformanceItem {
        private String channelUserId;
        private String channelUserName;
        private String colonelUserId;
        private String colonelUserName;
        private Long orderCount;
        private Long orderAmount;
        private Long serviceFee;
    }

    @Data
    public static class ReasonCountItem {
        private String reason;
        private Long count;
    }
}
