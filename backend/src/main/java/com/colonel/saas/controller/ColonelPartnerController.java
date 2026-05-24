package com.colonel.saas.controller;

import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.common.base.BaseController;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.common.result.PageResult;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.service.MerchantService;
import com.colonel.saas.vo.PartnerDetailVO;
import com.colonel.saas.vo.PartnerProductVO;
import com.colonel.saas.vo.PartnerVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商品域合作方接口（list_partners / get_partner_detail / get_partner_products 契约别名）。
 */
@Validated
@RestController
@Tag(name = "商品域合作方", description = "商品域 list_partners 等接口；商家型来自商品快照/商家表，团长型来自订单/活动/归因映射聚合。")
@RequestMapping("/colonel/partners")
@RequireRoles({RoleCodes.BIZ_LEADER, RoleCodes.BIZ_STAFF, RoleCodes.ADMIN})
public class ColonelPartnerController extends BaseController {

    private final MerchantService merchantService;

    public ColonelPartnerController(MerchantService merchantService) {
        this.merchantService = merchantService;
    }

    @Operation(summary = "合作方列表", description = "商品域 list_partners。")
    @GetMapping
    public ApiResult<PageResult<PartnerVO>> listPartners(
            @RequestParam(required = false) String keyword,
            @RequestParam(name = "type", required = false) String partnerType,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return okPage(merchantService.listPartners(keyword, partnerType, page, size));
    }

    @Operation(summary = "合作方详情", description = "商品域 get_partner_detail。")
    @GetMapping("/{id}")
    public ApiResult<PartnerDetailVO> getPartnerDetail(
            @Parameter(description = "合作方 ID。") @PathVariable("id") String partnerId,
            @RequestParam(name = "type", required = false) String partnerType) {
        return ok(merchantService.getPartnerDetail(partnerId, partnerType));
    }

    @Operation(summary = "合作方商品", description = "商品域 get_partner_products。")
    @GetMapping("/{id}/products")
    public ApiResult<PageResult<PartnerProductVO>> listPartnerProducts(
            @Parameter(description = "合作方 ID。") @PathVariable("id") String partnerId,
            @RequestParam(name = "type", required = false) String partnerType,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return okPage(merchantService.listPartnerProducts(partnerId, partnerType, page, size));
    }
}
