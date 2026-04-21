package com.colonel.saas.controller;

import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.common.base.BaseController;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.service.OrderSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "订单同步任务")
@RestController
@RequestMapping({"/api/order/sync", "/order/sync"})
public class OrderSyncController extends BaseController {

    private final OrderSyncService orderSyncService;

    public OrderSyncController(OrderSyncService orderSyncService) {
        this.orderSyncService = orderSyncService;
    }

    @Operation(summary = "手动触发订单同步")
    @RequireRoles({RoleCodes.ADMIN})
    @PostMapping("/trigger")
    public ApiResult<OrderSyncService.SyncResult> triggerSync() {
        return ok(orderSyncService.triggerManualSync());
    }
}
