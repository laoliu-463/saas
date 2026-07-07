package com.colonel.saas.domain.order.application;

import com.colonel.saas.domain.order.application.dto.OrderFilterOptionItem;
import com.colonel.saas.domain.order.application.dto.OrderFilterOptionsQuery;
import com.colonel.saas.domain.order.application.dto.OrderFilterOptionsResult;
import com.colonel.saas.domain.order.application.port.OrderFilterOptionsPort;
import com.colonel.saas.service.AttributionService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

@Service
public class OrderFilterOptionsQueryService {

    private final OrderFilterOptionsPort port;

    public OrderFilterOptionsQueryService(OrderFilterOptionsPort port) {
        this.port = port;
    }

    public OrderFilterOptionsResult getFilterOptions(OrderFilterOptionsQuery query) {
        OrderFilterOptionsQuery safeQuery = query == null
                ? new OrderFilterOptionsQuery(null, null, null, null)
                : query;
        return new OrderFilterOptionsResult(
                safeList(port.listOrderStatusValues(safeQuery)).stream()
                        .map(this::toOrderStatusOption)
                        .filter(Objects::nonNull)
                        .toList(),
                safeList(port.listAttributionStatusValues(safeQuery)).stream()
                        .map(this::toStatusOption)
                        .filter(Objects::nonNull)
                        .toList(),
                safeList(port.listUnattributedReasonValues(safeQuery)).stream()
                        .map(this::toReasonOption)
                        .filter(Objects::nonNull)
                        .toList(),
                normalizeOptions(port.listProductOptions(safeQuery)),
                normalizeOptions(port.listChannelOptions(safeQuery)),
                normalizeOptions(port.listColonelOptions(safeQuery)));
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private List<OrderFilterOptionItem> normalizeOptions(List<OrderFilterOptionItem> options) {
        if (options == null || options.isEmpty()) {
            return List.of();
        }
        return options.stream()
                .filter(Objects::nonNull)
                .map(item -> new OrderFilterOptionItem(
                        item.value(),
                        StringUtils.hasText(item.label()) ? item.label() : item.value()))
                .filter(item -> StringUtils.hasText(item.value()))
                .toList();
    }

    private OrderFilterOptionItem toOrderStatusOption(Object rawValue) {
        if (rawValue == null) {
            return null;
        }
        Integer value;
        if (rawValue instanceof Number number) {
            value = number.intValue();
        } else {
            try {
                value = Integer.parseInt(String.valueOf(rawValue));
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return new OrderFilterOptionItem(String.valueOf(value), orderStatusLabel(value));
    }

    private OrderFilterOptionItem toStatusOption(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return new OrderFilterOptionItem(value, attributionStatusLabel(value));
    }

    private OrderFilterOptionItem toReasonOption(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return new OrderFilterOptionItem(value, unattributedReasonLabel(value));
    }

    private String orderStatusLabel(Integer value) {
        if (value == null) {
            return "";
        }
        return switch (value) {
            case 1 -> "已下单";
            case 2 -> "已发货";
            case 3 -> "已完成";
            case 4 -> "已取消";
            default -> "状态" + value;
        };
    }

    private String attributionStatusLabel(String value) {
        return switch (value) {
            case AttributionService.STATUS_ATTRIBUTED -> "已确认业绩";
            case AttributionService.STATUS_UNATTRIBUTED -> "待排查订单";
            case "PARTIAL" -> "部分归因";
            case "FAILED" -> "同步/归因失败";
            default -> value;
        };
    }

    private String unattributedReasonLabel(String value) {
        return switch (value) {
            case AttributionService.REASON_NO_PICK_SOURCE, "订单未携带推广参数" -> "订单未携带推广参数";
            case AttributionService.REASON_MAPPING_NOT_FOUND, "pick_source 未匹配到有效归因映射" -> "未找到对应推广链接";
            case AttributionService.REASON_COLONEL_MAPPING_NOT_FOUND -> "原生团长订单未找到归因映射";
            case AttributionService.REASON_COLONEL_MAPPING_AMBIGUOUS -> "原生团长订单命中多条归因映射";
            case AttributionService.REASON_TALENT_CLAIM_OWNER_CONFLICT -> "归因负责人和达人认领人不一致";
            case AttributionService.REASON_PRODUCT_NOT_FOUND -> "未匹配到本地商品库";
            case AttributionService.REASON_ACTIVITY_NOT_FOUND -> "商品未关联活动";
            case AttributionService.REASON_CHANNEL_NOT_FOUND -> "未匹配到渠道负责人";
            case AttributionService.REASON_SYNC_FAILED, "订单同步失败" -> "订单同步失败";
            case AttributionService.REASON_ATTRIBUTED -> "已确认业绩";
            default -> value;
        };
    }
}
