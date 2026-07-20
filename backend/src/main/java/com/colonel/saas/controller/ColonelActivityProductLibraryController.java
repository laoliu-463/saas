package com.colonel.saas.controller;

import com.colonel.saas.common.base.BaseController;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.annotation.RequirePermission;
import com.colonel.saas.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * 活动商品库控制器 (Controller 切片 2.2).
 *
 * <p>从 ColonelActivityProductController 拆出商品库入库类 endpoint:</p>
 * <ul>
 *   <li>POST /{productId}/library-entry - 单商品入库</li>
 *   <li>POST /batch-library-entry - 批量入库</li>
 * </ul>
 *
 * <p>batch-library-entry 携带简化版 runProductBatch helper (与原 controller 重复).
 * 后续可考虑抽 BatchOperationSupport 公共 helper (P-DDD 切片 2.3+).</p>
 */
@Tag(name = "活动商品库", description = "活动商品加入共享商品库与批量入库接口。")
@RequestMapping("/colonel/activities/{activityId}/products")
@RequirePermission("colonel-activity-product-library:access")
@RestController
public class ColonelActivityProductLibraryController extends BaseController {

    private final ProductService productService;

    public ColonelActivityProductLibraryController(ProductService productService) {
        this.productService = productService;
    }

    @Operation(summary = "加入商品库", description = "将当前选品结果沉淀到共享商品库，供全员查看。")
    @RequirePermission("colonel-activity-product-library:put-into-library")
    @PostMapping("/{productId}/library-entry")
    public ApiResult<Map<String, Object>> putIntoLibrary(
            @Parameter(description = "团长活动 ID。") @PathVariable String activityId,
            @Parameter(description = "商品 ID。") @PathVariable String productId,
            @RequestAttribute(value = "userId", required = false) UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId) {
        return ok(productService.putIntoLibrary(activityId, productId, userId, deptId));
    }

    @Operation(summary = "批量加入商品库", description = "批量将活动商品沉淀为共享商品库展示资产；单个商品失败不影响其他商品。")
    @RequirePermission("colonel-activity-product-library:batch-put-into-library")
    @PostMapping("/batch-library-entry")
    public ApiResult<Map<String, Object>> batchPutIntoLibrary(
            @Parameter(description = "团长活动 ID。") @PathVariable String activityId,
            @Valid @RequestBody BatchProductIdsRequest request,
            @RequestAttribute(value = "userId", required = false) UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId) {
        return ok(runLibraryBatch(request.getProductIds(), productId ->
                productService.putIntoLibrary(activityId, productId, userId, deptId)));
    }

    /**
     * 简化版 runProductBatch helper (与原 ColonelActivityProductController 重复).
     * 后续 BatchOperationSupport 抽出时统一替换.
     */
    private Map<String, Object> runLibraryBatch(List<String> rawProductIds,
                                                 Function<String, Object> action) {
        List<String> productIds = rawProductIds == null ? List.of()
                : rawProductIds.stream().filter(id -> id != null && !id.isBlank()).distinct().toList();
        Map<String, Object> details = new LinkedHashMap<>();
        int succeeded = 0;
        int failed = 0;
        for (String productId : productIds) {
            try {
                Object result = action.apply(productId);
                details.put(productId, Map.of("success", true, "result", result == null ? Map.of() : result));
                succeeded++;
            } catch (RuntimeException ex) {
                details.put(productId, Map.of("success", false, "error", ex.getClass().getSimpleName() + ": " + ex.getMessage()));
                failed++;
            }
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", productIds.size());
        summary.put("succeeded", succeeded);
        summary.put("failed", failed);
        summary.put("details", details);
        return summary;
    }

    /**
     * 批量商品 ID 请求体 (从原 ColonelActivityProductController 复制).
     */
    public static class BatchProductIdsRequest {
        @NotEmpty(message = "商品 ID 列表不能为空")
        private List<String> productIds;

        public List<String> getProductIds() {
            return productIds;
        }

        public void setProductIds(List<String> productIds) {
            this.productIds = productIds;
        }
    }
}