package com.colonel.saas.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.common.base.BaseController;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.dto.order.OrderDetailResponse;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.mapper.ColonelsettlementOrderMapper;
import com.colonel.saas.service.AttributionService;
import com.colonel.saas.service.OrderQueryService;
import com.colonel.saas.service.OrderSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Tag(name = "订单管理")
@RestController
@RequestMapping("/orders")
public class OrderController extends BaseController {

    private final OrderSyncService orderSyncService;
    private final ColonelsettlementOrderMapper orderMapper;
    private final OrderQueryService orderQueryService;

    public OrderController(
            OrderSyncService orderSyncService,
            ColonelsettlementOrderMapper orderMapper,
            OrderQueryService orderQueryService) {
        this.orderSyncService = orderSyncService;
        this.orderMapper = orderMapper;
        this.orderQueryService = orderQueryService;
    }

    @Operation(summary = "手动同步订单")
    @PostMapping("/sync")
    public ApiResult<OrderSyncService.SyncResult> syncOrders(@RequestBody SyncRequest request) {
        long start = parseDateTime(request.getStartTime());
        long end = parseDateTime(request.getEndTime());
        return ok(orderSyncService.syncByTimeRange(start, end));
    }

    @Operation(summary = "获取订单列表")
    @GetMapping
    public ApiResult<IPage<ColonelsettlementOrder>> getOrders(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) String attributionStatus,
            @RequestParam(required = false) String unattributedReason,
            @RequestParam(required = false) String productId,
            @RequestParam(required = false) String channelKeyword,
            @RequestParam(required = false) String colonelKeyword,
            @RequestParam(required = false) Integer orderStatus,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        Page<ColonelsettlementOrder> query = new Page<>(page, pageSize);
        LambdaQueryWrapper<ColonelsettlementOrder> wrapper = buildWrapper(
                attributionStatus,
                unattributedReason,
                productId,
                channelKeyword,
                colonelKeyword,
                orderStatus,
                startTime,
                endTime
        )
                .orderByDesc(ColonelsettlementOrder::getUpdateTime)
                .orderByDesc(ColonelsettlementOrder::getCreateTime);
        IPage<ColonelsettlementOrder> result = orderMapper.selectPage(query, wrapper);
        result.getRecords().forEach(this::normalizeOrderRow);
        return ok(result);
    }

    @Operation(summary = "获取未归因订单")
    @GetMapping("/unattributed")
    public ApiResult<IPage<ColonelsettlementOrder>> getUnattributedOrders(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) String unattributedReason,
            @RequestParam(required = false) String productId,
            @RequestParam(required = false) String channelKeyword,
            @RequestParam(required = false) String colonelKeyword,
            @RequestParam(required = false) Integer orderStatus,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        return getOrders(page, pageSize, AttributionService.STATUS_UNATTRIBUTED, unattributedReason, productId, channelKeyword, colonelKeyword, orderStatus, startTime, endTime);
    }

    @Operation(summary = "获取订单详情")
    @GetMapping("/{orderId}")
    public ApiResult<OrderDetailResponse> getOrderDetail(@PathVariable String orderId) {
        return ok(orderQueryService.getOrderDetail(orderId));
    }

    @Operation(summary = "获取订单统计")
    @GetMapping("/stats")
    public ApiResult<OrderStats> getStats(
            @RequestParam(required = false) String attributionStatus,
            @RequestParam(required = false) String unattributedReason,
            @RequestParam(required = false) String productId,
            @RequestParam(required = false) String channelKeyword,
            @RequestParam(required = false) String colonelKeyword,
            @RequestParam(required = false) Integer orderStatus,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        LambdaQueryWrapper<ColonelsettlementOrder> wrapper = buildWrapper(
                attributionStatus,
                unattributedReason,
                productId,
                channelKeyword,
                colonelKeyword,
                orderStatus,
                startTime,
                endTime
        );
        List<ColonelsettlementOrder> rows = orderMapper.selectList(wrapper);
        OrderStats stats = new OrderStats();
        stats.setTotalOrders((long) rows.size());
        stats.setAttributedOrders(rows.stream().filter(row -> AttributionService.STATUS_ATTRIBUTED.equals(row.getAttributionStatus())).count());
        stats.setUnattributedOrders(rows.stream().filter(row -> AttributionService.STATUS_UNATTRIBUTED.equals(row.getAttributionStatus())).count());
        stats.setSyncFailedOrders(rows.stream().filter(row -> AttributionService.REASON_SYNC_FAILED.equals(row.getAttributionRemark())).count());
        stats.setLastSyncTime(orderSyncService.getLastSyncTime());
        stats.setUnattributedReasons(rows.stream()
                .filter(row -> AttributionService.STATUS_UNATTRIBUTED.equals(row.getAttributionStatus()))
                .map(ColonelsettlementOrder::getAttributionRemark)
                .filter(StringUtils::hasText)
                .collect(java.util.stream.Collectors.groupingBy(reason -> reason, java.util.stream.Collectors.counting()))
                .entrySet()
                .stream()
                .map(entry -> new ReasonCount(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingLong(ReasonCount::count).reversed())
                .toList());
        return ok(stats);
    }

    @Operation(summary = "获取订单筛选选项")
    @GetMapping("/filter-options")
    public ApiResult<OrderFilterOptions> getFilterOptions(
            @RequestParam(required = false) String keyword) {
        QueryConditions conditions = new QueryConditions(keyword);
        OrderFilterOptions options = new OrderFilterOptions();
        options.setOrderStatuses(orderMapper.selectMaps(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ColonelsettlementOrder>()
                        .select("distinct order_status as value")
                        .isNotNull("order_status")
                        .orderByAsc("order_status")
                        .last("limit 20"))
                .stream()
                .map(row -> toOrderStatusOption(row.get("value")))
                .filter(Objects::nonNull)
                .toList());
        options.setAttributionStatuses(orderMapper.selectMaps(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ColonelsettlementOrder>()
                        .select("distinct attribution_status as value")
                        .isNotNull("attribution_status")
                        .orderByAsc("attribution_status")
                        .last("limit 20"))
                .stream()
                .map(row -> toStatusOption(asText(row.get("value"))))
                .filter(Objects::nonNull)
                .toList());
        options.setUnattributedReasons(orderMapper.selectMaps(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ColonelsettlementOrder>()
                        .select("distinct attribution_remark as value")
                        .eq("attribution_status", AttributionService.STATUS_UNATTRIBUTED)
                        .isNotNull("attribution_remark")
                        .orderByAsc("attribution_remark")
                        .last("limit 50"))
                .stream()
                .map(row -> toReasonOption(asText(row.get("value"))))
                .filter(Objects::nonNull)
                .toList());
        options.setProducts(orderMapper.selectMaps(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ColonelsettlementOrder>()
                        .select("distinct product_id as value", "product_name as label")
                        .isNotNull("product_id")
                        .and(conditions.hasKeyword(), wrapper -> wrapper
                                .like("product_name", conditions.keyword())
                                .or()
                                .like("product_id", conditions.keyword()))
                        .last("limit 100"))
                .stream()
                .map(this::toOptionItem)
                .filter(item -> StringUtils.hasText(item.value()))
                .toList());
        options.setChannels(orderMapper.selectMaps(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ColonelsettlementOrder>()
                        .select("distinct channel_user_name as value", "channel_user_name as label")
                        .isNotNull("channel_user_name")
                        .and(conditions.hasKeyword(), wrapper -> wrapper.like("channel_user_name", conditions.keyword()))
                        .last("limit 100"))
                .stream()
                .map(this::toOptionItem)
                .filter(item -> StringUtils.hasText(item.value()))
                .toList());
        options.setColonels(orderMapper.selectMaps(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ColonelsettlementOrder>()
                        .select("distinct colonel_user_name as value", "colonel_user_name as label")
                        .isNotNull("colonel_user_name")
                        .and(conditions.hasKeyword(), wrapper -> wrapper.like("colonel_user_name", conditions.keyword()))
                        .last("limit 100"))
                .stream()
                .map(this::toOptionItem)
                .filter(item -> StringUtils.hasText(item.value()))
                .toList());
        return ok(options);
    }

    private LambdaQueryWrapper<ColonelsettlementOrder> buildWrapper(
            String attributionStatus,
            String unattributedReason,
            String productId,
            String channelKeyword,
            String colonelKeyword,
            Integer orderStatus,
            String startTime,
            String endTime) {
        LocalDateTime start = parseLocalDateTime(startTime);
        LocalDateTime end = parseLocalDateTime(endTime);
        return new LambdaQueryWrapper<ColonelsettlementOrder>()
                .eq(StringUtils.hasText(attributionStatus), ColonelsettlementOrder::getAttributionStatus, attributionStatus)
                .eq(StringUtils.hasText(unattributedReason), ColonelsettlementOrder::getAttributionRemark, unattributedReason)
                .eq(StringUtils.hasText(productId), ColonelsettlementOrder::getProductId, productId)
                .eq(orderStatus != null, ColonelsettlementOrder::getOrderStatus, orderStatus)
                .and(StringUtils.hasText(channelKeyword), wrapper -> wrapper
                        .like(ColonelsettlementOrder::getChannelUserName, channelKeyword)
                        .or()
                        .like(ColonelsettlementOrder::getChannelUserId, channelKeyword))
                .and(StringUtils.hasText(colonelKeyword), wrapper -> wrapper
                        .like(ColonelsettlementOrder::getColonelUserName, colonelKeyword)
                        .or()
                        .like(ColonelsettlementOrder::getColonelUserId, colonelKeyword))
                .ge(start != null, ColonelsettlementOrder::getCreateTime, start)
                .le(end != null, ColonelsettlementOrder::getCreateTime, end);
    }

    private void normalizeOrderRow(ColonelsettlementOrder order) {
        order.setUnattributedReason(order.getAttributionRemark());
    }

    private OptionItem toOptionItem(Map<String, Object> row) {
        String value = asText(row.get("value"));
        String label = row.get("label") == null ? value : String.valueOf(row.get("label"));
        return new OptionItem(value, StringUtils.hasText(label) ? label : value);
    }

    private OptionItem toOrderStatusOption(Object rawValue) {
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
        return new OptionItem(String.valueOf(value), orderStatusLabel(value));
    }

    private OptionItem toStatusOption(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return new OptionItem(value, attributionStatusLabel(value));
    }

    private OptionItem toReasonOption(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return new OptionItem(value, unattributedReasonLabel(value));
    }

    private String asText(Object raw) {
        return raw == null ? null : String.valueOf(raw);
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
            default -> "状态 " + value;
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
            case AttributionService.REASON_PRODUCT_NOT_FOUND -> "未匹配到本地商品库";
            case AttributionService.REASON_ACTIVITY_NOT_FOUND -> "商品未关联活动";
            case AttributionService.REASON_CHANNEL_NOT_FOUND -> "未匹配到渠道负责人";
            case AttributionService.REASON_SYNC_FAILED, "订单同步失败" -> "订单同步失败";
            case AttributionService.REASON_ATTRIBUTED -> "已确认业绩";
            default -> value;
        };
    }

    private long parseDateTime(String text) {
        if (text == null) return 0L;
        try {
            return LocalDateTime.parse(text, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    .toEpochSecond(ZoneOffset.ofHours(8));
        } catch (Exception e) {
            return 0L;
        }
    }

    private LocalDateTime parseLocalDateTime(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return LocalDateTime.parse(text, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (Exception e) {
            return null;
        }
    }

    @Data
    public static class SyncRequest {
        private String startTime;
        private String endTime;
    }

    @Data
    public static class OrderStats {
        private Long totalOrders;
        private Long attributedOrders;
        private Long unattributedOrders;
        private Long syncFailedOrders;
        private LocalDateTime lastSyncTime;
        private List<ReasonCount> unattributedReasons;
    }

    public record ReasonCount(String reason, Long count) {
    }

    @Data
    public static class OrderFilterOptions {
        private List<OptionItem> orderStatuses;
        private List<OptionItem> attributionStatuses;
        private List<OptionItem> unattributedReasons;
        private List<OptionItem> products;
        private List<OptionItem> channels;
        private List<OptionItem> colonels;
    }

    public record OptionItem(String value, String label) {
    }

    private record QueryConditions(String keyword) {
        boolean hasKeyword() {
            return StringUtils.hasText(keyword);
        }
    }
}
