package com.colonel.saas.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.common.result.PageResult;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.dto.display.ForceDisplayCancelRequest;
import com.colonel.saas.dto.display.ForceDisplaySwitchRequest;
import com.colonel.saas.entity.ProductDisplayAuditLog;
import com.colonel.saas.service.display.AdminProductDisplayService;
import com.colonel.saas.service.display.ProductDisplayAuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/products/display")
public class AdminProductDisplayController {

    private final AdminProductDisplayService adminProductDisplayService;
    private final ProductDisplayAuditService productDisplayAuditService;

    public AdminProductDisplayController(
            AdminProductDisplayService adminProductDisplayService,
            ProductDisplayAuditService productDisplayAuditService) {
        this.adminProductDisplayService = adminProductDisplayService;
        this.productDisplayAuditService = productDisplayAuditService;
    }

    @Operation(summary = "管理员强制切换展示记录")
    @RequireRoles({RoleCodes.ADMIN, RoleCodes.BIZ_LEADER})
    @PostMapping("/force-switch")
    public ApiResult<Void> forceSwitch(
            @Valid @RequestBody ForceDisplaySwitchRequest request,
            @RequestAttribute("userId") UUID userId) {
        adminProductDisplayService.forceSwitch(
                request.relationId(),
                userId,
                request.reason(),
                request.until());
        return ApiResult.ok(null);
    }

    @Operation(summary = "取消管理员强制展示")
    @RequireRoles({RoleCodes.ADMIN, RoleCodes.BIZ_LEADER})
    @PostMapping("/cancel-force")
    public ApiResult<Void> cancelForce(
            @Valid @RequestBody ForceDisplayCancelRequest request,
            @RequestAttribute("userId") UUID userId) {
        adminProductDisplayService.cancelForce(request.relationId(), userId);
        return ApiResult.ok(null);
    }

    @Operation(summary = "展示规则审计日志")
    @RequireRoles({RoleCodes.ADMIN, RoleCodes.BIZ_LEADER, RoleCodes.BIZ_STAFF})
    @GetMapping("/audit-logs")
    public ApiResult<PageResult<ProductDisplayAuditLog>> auditLogs(
            @Parameter(description = "商品 ID") @RequestParam(required = false) String productId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        Page<ProductDisplayAuditLog> result = productDisplayAuditService.pageAuditLogs(productId, page, size);
        return ApiResult.ok(PageResult.of(result));
    }
}
