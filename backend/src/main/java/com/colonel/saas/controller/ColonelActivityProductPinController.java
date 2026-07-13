package com.colonel.saas.controller;

import com.colonel.saas.common.base.BaseController;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.service.ProductPinService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * 活动商品置顶控制器 (Controller 切片 2.1).
 *
 * <p>从 ColonelActivityProductController 拆出置顶类 endpoint:</p>
 * <ul>
 *   <li>POST /{productId}/pin - 招商置顶商品</li>
 *   <li>DELETE /{productId}/pin - 取消置顶</li>
 * </ul>
 */
@Tag(name = "活动商品置顶", description = "招商商品置顶与取消置顶接口。")
@RequestMapping("/colonel/activities/{activityId}/products")
@RequireRoles({RoleCodes.BIZ_LEADER, RoleCodes.BIZ_STAFF, RoleCodes.CHANNEL_LEADER, RoleCodes.CHANNEL_STAFF, RoleCodes.ADMIN})
@RestController
public class ColonelActivityProductPinController extends BaseController {

    private final ProductPinService productPinService;

    public ColonelActivityProductPinController(ProductPinService productPinService) {
        this.productPinService = productPinService;
    }

    @Operation(summary = "招商置顶商品", description = "置顶 24 小时，每位招商最多 10 个规格（P-05）。")
    @RequireRoles({RoleCodes.BIZ_LEADER, RoleCodes.BIZ_STAFF})
    @PostMapping("/{productId}/pin")
    public ApiResult<Map<String, Object>> pinProduct(
            @Parameter(description = "团长活动 ID。") @PathVariable String activityId,
            @Parameter(description = "商品 ID。") @PathVariable String productId,
            @RequestAttribute("userId") UUID userId) {
        // 委托 ProductPinService 执行置顶操作，返回置顶状态
        var state = productPinService.pin(activityId, productId, userId);
        return ok(Map.of(
                "activityId", activityId,
                "productId", productId,
                "pinned", true,
                "pinnedUntil", state.getPinnedUntil()));
    }

    @Operation(summary = "取消招商置顶", description = "取消当前商品的置顶状态。")
    @RequireRoles({RoleCodes.BIZ_LEADER, RoleCodes.BIZ_STAFF})
    @DeleteMapping("/{productId}/pin")
    public ApiResult<Map<String, Object>> unpinProduct(
            @Parameter(description = "团长活动 ID。") @PathVariable String activityId,
            @Parameter(description = "商品 ID。") @PathVariable String productId,
            @RequestAttribute("userId") UUID userId) {
        // 委托 ProductPinService 取消商品置顶
        productPinService.unpin(activityId, productId, userId);
        return ok(Map.of("activityId", activityId, "productId", productId, "pinned", false));
    }
}