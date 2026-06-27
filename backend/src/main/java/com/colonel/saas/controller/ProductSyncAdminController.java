package com.colonel.saas.controller;

import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.common.base.BaseController;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.domain.product.application.ProductActivityBackfillApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 商品同步管理员接口。
 */
@RestController
@RequestMapping("/product-sync/admin")
@Tag(name = "商品同步管理")
public class ProductSyncAdminController extends BaseController {

    private final ProductActivityBackfillApplicationService backfillApplicationService;

    public ProductSyncAdminController(ProductActivityBackfillApplicationService backfillApplicationService) {
        this.backfillApplicationService = backfillApplicationService;
    }

    @Operation(summary = "活动商品全量回补", description = "支持 dry-run 和 confirm 后真实 backfill；不会改变 /products 的 DISPLAYING 展示口径。")
    @RequireRoles({RoleCodes.ADMIN})
    @PostMapping("/backfill-activity-products")
    public ApiResult<ProductActivityBackfillApplicationService.BackfillResult> backfillActivityProducts(
            @RequestBody ProductActivityBackfillApplicationService.BackfillCommand request,
            @RequestAttribute("userId") UUID userId) {
        return ok(backfillApplicationService.backfill(request, userId));
    }

    @Operation(summary = "活动商品全量回补（异步提交）", description = "提交后立即返回任务 ID，任务异步执行。")
    @RequireRoles({RoleCodes.ADMIN})
    @PostMapping("/backfill-activity-products/async")
    public ApiResult<ProductActivityBackfillApplicationService.BackfillAsyncResponse> backfillActivityProductsAsync(
            @RequestBody ProductActivityBackfillApplicationService.BackfillCommand request,
            @RequestAttribute("userId") UUID userId) {
        return ok(backfillApplicationService.backfillAsync(request, userId));
    }

    @Operation(summary = "查询活动商品回补任务状态")
    @RequireRoles({RoleCodes.ADMIN})
    @GetMapping("/backfill-jobs/{jobId}")
    public ApiResult<ProductActivityBackfillApplicationService.BackfillJobStatus> getBackfillJobStatus(
            @PathVariable("jobId") String jobId) {
        return ok(backfillApplicationService.getJobStatus(jobId));
    }
}
