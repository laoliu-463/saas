package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.mapper.ColonelsettlementOrderMapper;
import lombok.Data;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    private final ColonelsettlementOrderMapper orderMapper;

    public DashboardService(ColonelsettlementOrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    public Summary getSummary() {
        // 总计
        QueryWrapper<ColonelsettlementOrder> totalWrapper = new QueryWrapper<ColonelsettlementOrder>()
                .select("count(*) as orderCount", "sum(order_amount) as orderAmount", "sum(settle_colonel_commission) as serviceFee");
        Map<String, Object> totalMap = orderMapper.selectMaps(totalWrapper).get(0);

        // 已归因/未归因
        Long attributedCount = orderMapper.selectCount(new QueryWrapper<ColonelsettlementOrder>().eq("attribution_status", "ATTRIBUTED"));
        Long unattributedCount = orderMapper.selectCount(new QueryWrapper<ColonelsettlementOrder>().eq("attribution_status", "UNATTRIBUTED"));

        // 渠道业绩
        QueryWrapper<ColonelsettlementOrder> channelWrapper = new QueryWrapper<ColonelsettlementOrder>()
                .select("channel_user_id as channelUserId", "channel_user_name as channelUserName", "count(*) as orderCount", "sum(order_amount) as orderAmount", "sum(settle_colonel_commission) as serviceFee")
                .isNotNull("channel_user_id")
                .groupBy("channel_user_id", "channel_user_name");
        List<PerformanceItem> channelPerformance = orderMapper.selectMaps(channelWrapper).stream().map(this::toPerformanceItem).toList();

        // 招商业绩
        QueryWrapper<ColonelsettlementOrder> colonelWrapper = new QueryWrapper<ColonelsettlementOrder>()
                .select("colonel_user_id as colonelUserId", "colonel_user_name as colonelUserName", "count(*) as orderCount", "sum(order_amount) as orderAmount", "sum(settle_colonel_commission) as serviceFee")
                .isNotNull("colonel_user_id")
                .groupBy("colonel_user_id", "colonel_user_name");
        List<PerformanceItem> colonelPerformance = orderMapper.selectMaps(colonelWrapper).stream().map(this::toPerformanceItem).toList();

        Summary summary = new Summary();
        summary.setOrderCount(asLong(totalMap.get("ordercount")));
        summary.setOrderAmount(asLong(totalMap.get("orderamount")));
        summary.setServiceFee(asLong(totalMap.get("servicefee")));
        summary.setAttributedOrderCount(attributedCount);
        summary.setUnattributedOrderCount(unattributedCount);
        summary.setChannelPerformance(channelPerformance);
        summary.setColonelPerformance(colonelPerformance);
        return summary;
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
        private List<PerformanceItem> channelPerformance;
        private List<PerformanceItem> colonelPerformance;
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
}
