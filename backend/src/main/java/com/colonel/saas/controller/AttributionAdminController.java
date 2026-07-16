package com.colonel.saas.controller;

import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.common.base.BaseController;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.service.AttributionOwnerReconciliationService;
import com.colonel.saas.service.OperationLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** 管理员专用的订单归因治理工具。 */
@Tag(name = "订单归因治理", description = "预览并分类未固化归属维度的历史推广链接。")
@RestController
@RequestMapping("/api/order-attribution/admin")
public class AttributionAdminController extends BaseController {

    private final AttributionOwnerReconciliationService reconciliationService;
    private final OperationLogService operationLogService;

    public AttributionAdminController(
            AttributionOwnerReconciliationService reconciliationService,
            OperationLogService operationLogService) {
        this.reconciliationService = reconciliationService;
        this.operationLogService = operationLogService;
    }

    @Operation(summary = "分类历史推广链接归属类型", description = "默认仅预览；实际写入必须传 dryRun=false 且 confirm=true。")
    @RequireRoles({RoleCodes.ADMIN})
    @PostMapping("/mapping-owner-reconcile")
    public ApiResult<AttributionOwnerReconciliationService.ReconcileResult> reconcileMappingOwners(
            @RequestBody(required = false) AttributionOwnerReconciliationService.ReconcileRequest request,
            @RequestAttribute("userId") UUID userId) {
        AttributionOwnerReconciliationService.ReconcileRequest safeRequest = request == null
                ? new AttributionOwnerReconciliationService.ReconcileRequest(null, null, null, null)
                : request;
        AttributionOwnerReconciliationService.ReconcileResult result = reconciliationService.reconcile(safeRequest);
        operationLogService.recordSystemAction(
                userId,
                "订单归因",
                "分类历史推广链接归属类型",
                "POST",
                "attribution_owner_reconcile",
                safeRequest.userIds() == null ? null : safeRequest.userIds().toString(),
                result.dryRun() ? "dry-run" : "apply",
                String.format("scanned=%d, classifiable=%d, conflicts=%d, updated=%d",
                        result.scanned(), result.classifiable(), result.conflicts(), result.updated()));
        return ok(result);
    }
}
