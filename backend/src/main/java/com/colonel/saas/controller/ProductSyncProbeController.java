package com.colonel.saas.controller;

import com.colonel.saas.annotation.RequirePermission;
import com.colonel.saas.common.base.BaseController;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.service.ProductSyncDryRunProbeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商品同步只读探针。
 */
@RestController
@RequestMapping("/product-sync-probes")
@RequirePermission("product-sync-probe:access")
@Tag(name = "商品同步只读探针")
public class ProductSyncProbeController extends BaseController {

    private final ProductSyncDryRunProbeService probeService;

    public ProductSyncProbeController(ProductSyncDryRunProbeService probeService) {
        this.probeService = probeService;
    }

    @Operation(summary = "单活动活动商品 deep dry-run", description = "只读调用上游活动商品分页，不写 product_snapshot / product_operation_state。")
    @PostMapping("/activity-products-deep-dry-run")
    public ApiResult<ProductSyncDryRunProbeService.ActivityDryRunResult> deepDryRun(
            @RequestBody ProductSyncDryRunProbeService.ActivityDeepDryRunRequest request) {
        return ok(probeService.deepDryRun(request));
    }

    @Operation(summary = "多活动活动商品 full dry-run", description = "只读扫描本地活动列表并逐活动调用上游分页，不推进 checkpoint。")
    @PostMapping("/full-products-dry-run")
    public ApiResult<ProductSyncDryRunProbeService.FullDryRunResult> fullDryRun(
            @RequestBody ProductSyncDryRunProbeService.FullDryRunRequest request) {
        return ok(probeService.fullDryRun(request));
    }
}
