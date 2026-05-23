package com.colonel.saas.controller;

import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.common.base.BaseController;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.service.ProductPinService;
import com.colonel.saas.vo.PinnedProductVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "商品置顶", description = "商品域 get_pinned_products。")
@RequestMapping("/colonel/pinned-products")
@RequireRoles({RoleCodes.BIZ_LEADER, RoleCodes.BIZ_STAFF, RoleCodes.CHANNEL_LEADER, RoleCodes.CHANNEL_STAFF, RoleCodes.ADMIN, RoleCodes.COLONEL_LEADER})
public class ColonelPinnedProductController extends BaseController {

    private final ProductPinService productPinService;

    public ColonelPinnedProductController(ProductPinService productPinService) {
        this.productPinService = productPinService;
    }

    @Operation(summary = "当前用户置顶商品", description = "返回 24h 内仍有效的置顶列表（get_pinned_products）。")
    @GetMapping
    public ApiResult<List<PinnedProductVO>> listPinnedProducts(@RequestAttribute("userId") UUID userId) {
        return ok(productPinService.listPinnedProducts(userId));
    }
}
