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

/**
 * 商品置顶控制器.
 *
 * <p>负责查询当前登录用户的置顶商品列表，属于商品域。
 * 置顶商品在 24 小时内有效，用于用户快速访问常用商品。</p>
 *
 * <p>API 路径前缀：{@code /colonel/pinned-products}</p>
 *
 * <p>访问权限：业务负责人、业务人员、渠道负责人、渠道人员、管理员、团长负责人。</p>
 *
 * @see ProductPinService
 */
@RestController
@Tag(name = "商品置顶", description = "商品域 get_pinned_products。")
@RequestMapping("/colonel/pinned-products")
@RequireRoles({RoleCodes.BIZ_LEADER, RoleCodes.BIZ_STAFF, RoleCodes.CHANNEL_LEADER, RoleCodes.CHANNEL_STAFF, RoleCodes.ADMIN, RoleCodes.COLONEL_LEADER})
public class ColonelPinnedProductController extends BaseController {

    /** 商品置顶服务 */
    private final ProductPinService productPinService;

    /**
     * 构造注入.
     *
     * @param productPinService 商品置顶服务
     */
    public ColonelPinnedProductController(ProductPinService productPinService) {
        this.productPinService = productPinService;
    }

    /**
     * 查询当前用户置顶商品列表.
     *
     * <p>返回 24 小时内仍有效的置顶商品列表，供用户快速访问常用商品。</p>
     *
     * @param userId 当前登录用户 ID（由拦截器注入 RequestAttribute）
     * @return 置顶商品列表
     * @see ProductPinService#listPinnedProducts(UUID)
     */
    @Operation(summary = "当前用户置顶商品", description = "返回 24h 内仍有效的置顶列表（get_pinned_products）。")
    @GetMapping
    public ApiResult<List<PinnedProductVO>> listPinnedProducts(@RequestAttribute("userId") UUID userId) {
        return ok(productPinService.listPinnedProducts(userId));
    }
}
