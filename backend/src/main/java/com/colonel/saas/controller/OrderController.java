package com.colonel.saas.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.common.base.BaseController;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.mapper.ColonelsettlementOrderMapper;
import com.colonel.saas.service.OrderSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Tag(name = "订单管理")
@RestController
@RequestMapping("/orders")
public class OrderController extends BaseController {

    private final OrderSyncService orderSyncService;
    private final ColonelsettlementOrderMapper orderMapper;

    public OrderController(OrderSyncService orderSyncService, ColonelsettlementOrderMapper orderMapper) {
        this.orderSyncService = orderSyncService;
        this.orderMapper = orderMapper;
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
            @RequestParam(required = false) String attributionStatus) {
        Page<ColonelsettlementOrder> query = new Page<>(page, pageSize);
        LambdaQueryWrapper<ColonelsettlementOrder> wrapper = new LambdaQueryWrapper<ColonelsettlementOrder>()
                .eq(attributionStatus != null, ColonelsettlementOrder::getAttributionStatus, attributionStatus)
                .orderByDesc(ColonelsettlementOrder::getUpdateTime)
                .orderByDesc(ColonelsettlementOrder::getCreateTime);
        return ok(orderMapper.selectPage(query, wrapper));
    }

    @Operation(summary = "获取未归因订单")
    @GetMapping("/unattributed")
    public ApiResult<IPage<ColonelsettlementOrder>> getUnattributedOrders(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long pageSize) {
        return getOrders(page, pageSize, "UNATTRIBUTED");
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

    @Data
    public static class SyncRequest {
        private String startTime;
        private String endTime;
    }
}
