package com.colonel.saas.controller;

import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.common.base.BaseController;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.service.ProductActivityBackfillService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

    private final ProductActivityBackfillService backfillService;

    public ProductSyncAdminController(ProductActivityBackfillService backfillService) {
        this.backfillService = backfillService;
    }

    @Operation(summary = "活动商品全量回补", description = "支持 dry-run 和 confirm 后真实 backfill；不会改变 /products 的 DISPLAYING 展示口径。")
    @RequireRoles({RoleCodes.ADMIN})
    @PostMapping("/backfill-activity-products")
    public ApiResult<ProductActivityBackfillService.BackfillResult> backfillActivityProducts(
            @RequestBody ProductActivityBackfillService.BackfillRequest request,
            @RequestAttribute("userId") UUID userId) {
        return ok(backfillService.backfill(request, userId));
    }
}
