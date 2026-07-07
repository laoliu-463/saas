package com.colonel.saas.domain.order.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.config.DddRefactorProperties;
import com.colonel.saas.domain.order.application.dto.OrderFilterOptionItem;
import com.colonel.saas.domain.order.application.dto.OrderFilterOptionsQuery;
import com.colonel.saas.domain.order.application.port.OrderFilterOptionsPort;
import com.colonel.saas.domain.user.policy.DataScopeResolver;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.mapper.ColonelsettlementOrderMapper;
import com.colonel.saas.service.AttributionService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class OrderFilterOptionsMapperAdapter implements OrderFilterOptionsPort {

    private final ColonelsettlementOrderMapper orderMapper;
    private final DataScopeResolver dataScopeResolver;
    private final DddRefactorProperties dddRefactorProperties;

    public OrderFilterOptionsMapperAdapter(
            ColonelsettlementOrderMapper orderMapper,
            DataScopeResolver dataScopeResolver,
            DddRefactorProperties dddRefactorProperties) {
        this.orderMapper = orderMapper;
        this.dataScopeResolver = dataScopeResolver;
        this.dddRefactorProperties = dddRefactorProperties;
    }

    @Override
    public List<Object> listOrderStatusValues(OrderFilterOptionsQuery query) {
        QueryWrapper<ColonelsettlementOrder> wrapper = new QueryWrapper<ColonelsettlementOrder>()
                .select("distinct order_status as value")
                .isNotNull("order_status")
                .orderByAsc("order_status")
                .last("limit 20");
        applyQueryDataScope(wrapper, query);
        return selectMaps(wrapper).stream()
                .map(row -> readValue(row, "value"))
                .toList();
    }

    @Override
    public List<String> listAttributionStatusValues(OrderFilterOptionsQuery query) {
        QueryWrapper<ColonelsettlementOrder> wrapper = new QueryWrapper<ColonelsettlementOrder>()
                .select("distinct attribution_status as value")
                .isNotNull("attribution_status")
                .orderByAsc("attribution_status")
                .last("limit 20");
        applyQueryDataScope(wrapper, query);
        return selectMaps(wrapper).stream()
                .map(row -> asText(readValue(row, "value")))
                .toList();
    }

    @Override
    public List<String> listUnattributedReasonValues(OrderFilterOptionsQuery query) {
        QueryWrapper<ColonelsettlementOrder> wrapper = new QueryWrapper<ColonelsettlementOrder>()
                .select("distinct attribution_remark as value")
                .eq("attribution_status", AttributionService.STATUS_UNATTRIBUTED)
                .isNotNull("attribution_remark")
                .orderByAsc("attribution_remark")
                .last("limit 50");
        applyQueryDataScope(wrapper, query);
        return selectMaps(wrapper).stream()
                .map(row -> asText(readValue(row, "value")))
                .toList();
    }

    @Override
    public List<OrderFilterOptionItem> listProductOptions(OrderFilterOptionsQuery query) {
        QueryWrapper<ColonelsettlementOrder> wrapper = new QueryWrapper<ColonelsettlementOrder>()
                .select("distinct product_id as value", "product_name as label")
                .isNotNull("product_id")
                .and(query.hasKeyword(), nested -> nested
                        .like("product_name", query.keyword())
                        .or()
                        .like("product_id", query.keyword()))
                .last("limit 50");
        applyQueryDataScope(wrapper, query);
        return toOptions(selectMaps(wrapper));
    }

    @Override
    public List<OrderFilterOptionItem> listChannelOptions(OrderFilterOptionsQuery query) {
        QueryWrapper<ColonelsettlementOrder> wrapper = new QueryWrapper<ColonelsettlementOrder>()
                .select("distinct channel_user_name as value", "channel_user_name as label")
                .isNotNull("channel_user_name")
                .and(query.hasKeyword(), nested -> nested.like("channel_user_name", query.keyword()))
                .last("limit 50");
        applyQueryDataScope(wrapper, query);
        return toOptions(selectMaps(wrapper));
    }

    @Override
    public List<OrderFilterOptionItem> listColonelOptions(OrderFilterOptionsQuery query) {
        QueryWrapper<ColonelsettlementOrder> wrapper = new QueryWrapper<ColonelsettlementOrder>()
                .select("distinct colonel_user_name as value", "colonel_user_name as label")
                .isNotNull("colonel_user_name")
                .and(query.hasKeyword(), nested -> nested.like("colonel_user_name", query.keyword()))
                .last("limit 50");
        applyQueryDataScope(wrapper, query);
        return toOptions(selectMaps(wrapper));
    }

    private List<Map<String, Object>> selectMaps(QueryWrapper<ColonelsettlementOrder> wrapper) {
        List<Map<String, Object>> rows = orderMapper.selectMaps(wrapper);
        return rows == null ? List.of() : rows;
    }

    private List<OrderFilterOptionItem> toOptions(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream()
                .map(row -> new OrderFilterOptionItem(
                        asText(readValue(row, "value")),
                        readValue(row, "label") == null ? asText(readValue(row, "value")) : String.valueOf(readValue(row, "label"))))
                .toList();
    }

    private Object readValue(Map<String, Object> row, String key) {
        if (row == null || row.isEmpty() || key == null) {
            return null;
        }
        if (row.containsKey(key)) {
            return row.get(key);
        }
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (key.equalsIgnoreCase(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private void applyQueryDataScope(
            QueryWrapper<ColonelsettlementOrder> wrapper,
            OrderFilterOptionsQuery query) {
        if (wrapper == null || query == null || query.dataScope() == null) {
            return;
        }
        if (!dddRefactorProperties.getDataScopePolicy().isEnabled()) {
            applyLegacyDataScope(wrapper, query.userId(), query.deptId(), query.dataScope());
            return;
        }
        dataScopeResolver.applyTo(wrapper, query.userId(), query.deptId(), query.dataScope(), "user_id", "dept_id");
    }

    private void applyLegacyDataScope(
            QueryWrapper<ColonelsettlementOrder> wrapper,
            UUID userId,
            UUID deptId,
            DataScope dataScope) {
        switch (dataScope) {
            case PERSONAL -> {
                if (userId != null) {
                    wrapper.eq("user_id", userId);
                }
            }
            case DEPT -> {
                if (deptId != null) {
                    wrapper.eq("dept_id", deptId);
                }
            }
            case ALL -> {
                // no filter
            }
        }
    }

    private String asText(Object raw) {
        return raw == null ? null : String.valueOf(raw);
    }
}
