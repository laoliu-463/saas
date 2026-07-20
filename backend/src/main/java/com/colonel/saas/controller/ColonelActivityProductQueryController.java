package com.colonel.saas.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.colonel.saas.common.base.BaseController;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.common.result.PageResult;
import com.colonel.saas.annotation.RequirePermission;
import com.colonel.saas.entity.ProductOperationLog;
import com.colonel.saas.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 活动商品查询控制器 (Controller 切片 1.1).
 *
 * <p>从 ColonelActivityProductController 拆分出查询类 endpoint:</p>
 * <ul>
 *   <li>GET /{productId} - 详情查询</li>
 *   <li>GET /{productId}/skus - SKU 列表</li>
 *   <li>GET /{productId}/operation-logs - 操作日志分页</li>
 * </ul>
 *
 * <p>切片原则: 保留 HTTP 路径 100% 一致, 业务逻辑 (ProductService 委派) 不变.</p>
 */
@Tag(name = "活动商品查询", description = "活动商品详情、SKU、操作日志查询接口。")
@RequestMapping("/colonel/activities/{activityId}/products")
@RequirePermission("colonel-activity-product-query:access")
@RestController
public class ColonelActivityProductQueryController extends BaseController {

    private final ProductService productService;

    public ColonelActivityProductQueryController(ProductService productService) {
        this.productService = productService;
    }

    @Operation(summary = "活动商品详情", description = "查询指定活动下单个商品的业务详情。")
    @GetMapping("/{productId}")
    public ApiResult<Map<String, Object>> detail(
            @Parameter(description = "团长活动 ID。") @PathVariable String activityId,
            @Parameter(description = "商品 ID。") @PathVariable String productId) {
        return ok(productService.getActivityProductDetail(activityId, productId));
    }

    @Operation(summary = "活动商品 SKU 列表", description = "调用抖店 buyin.productSkus.v2 查询商品规格 SKU。")
    @GetMapping("/{productId}/skus")
    public ApiResult<List<Map<String, Object>>> skus(
            @Parameter(description = "团长活动 ID。") @PathVariable String activityId,
            @Parameter(description = "商品 ID。") @PathVariable String productId) {
        return ok(productService.listActivityProductSkus(productId));
    }

    @Operation(summary = "活动商品操作日志", description = "分页查询活动商品操作日志。")
    @GetMapping("/{productId}/operation-logs")
    public ApiResult<PageResult<ProductOperationLog>> operationLogs(
            @Parameter(description = "团长活动 ID。") @PathVariable String activityId,
            @Parameter(description = "商品 ID。") @PathVariable String productId,
            @Parameter(description = "页码，从 1 开始。") @RequestParam(defaultValue = "1") long page,
            @Parameter(description = "每页条数。") @RequestParam(defaultValue = "20") long size) {
        IPage<ProductOperationLog> result = productService.getOperationLogs(activityId, productId, page, size);
        return okPage(result);
    }
}