package com.colonel.saas.controller;

import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.common.base.BaseController;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.common.result.PageResult;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.dto.talent.OverrideAssigneeRequest;
import com.colonel.saas.entity.Merchant;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Validated
@Tag(name = "商家管理", description = "商家归属与基本信息管理。")
@RestController
@RequestMapping("/merchants")
@RequireRoles({RoleCodes.ADMIN})
public class MerchantController extends BaseController {

    private final MerchantService merchantService;

    public MerchantController(MerchantService merchantService) {
        this.merchantService = merchantService;
    }

    @Operation(summary = "查询合作方列表", description = "从活动商品快照和商家沉淀数据中查询商家型合作方，用于商品域 list_partners。")
    @GetMapping
    @RequireRoles({RoleCodes.ADMIN, RoleCodes.BIZ_LEADER, RoleCodes.BIZ_STAFF})
    public ApiResult<PageResult<PartnerVO>> listPartners(
            @RequestParam(required = false) String keyword,
            @RequestParam(name = "type", required = false) String partnerType,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return okPage(merchantService.listPartners(keyword, partnerType, page, size));
    }

    @Operation(summary = "查询合作方详情", description = "查询商家型合作方的基础信息和商品聚合统计，用于商品域 get_partner_detail。")
    @GetMapping("/{id}")
    @RequireRoles({RoleCodes.ADMIN, RoleCodes.BIZ_LEADER, RoleCodes.BIZ_STAFF})
    public ApiResult<PartnerDetailVO> getPartnerDetail(
            @Parameter(description = "合作方 ID，商家型通常为 shop_id 或 merchant_id。") @PathVariable("id") String partnerId,
            @RequestParam(name = "type", required = false) String partnerType) {
        return ok(merchantService.getPartnerDetail(partnerId, partnerType));
    }

    @Operation(summary = "查询合作方商品", description = "分页查询商家型合作方关联的活动商品快照，用于商品域 get_partner_products。")
    @GetMapping("/{id}/products")
    @RequireRoles({RoleCodes.ADMIN, RoleCodes.BIZ_LEADER, RoleCodes.BIZ_STAFF})
    public ApiResult<PageResult<PartnerProductVO>> listPartnerProducts(
            @Parameter(description = "合作方 ID；商家型为 shop_id/merchant_id，团长型为 colonel_buyin_id。") @PathVariable("id") String partnerId,
            @RequestParam(name = "type", required = false) String partnerType,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return okPage(merchantService.listPartnerProducts(partnerId, partnerType, page, size));
    }

    @Operation(summary = "归属覆盖", description = "组长级别手动覆盖商家的当前归属人，同时记录覆盖原因。")
    @PostMapping("/{id}/override-assignee")
    public ApiResult<Merchant> overrideAssignee(
            @Parameter(description = "商家 merchant_id，字符串格式。") @PathVariable("id") String merchantId,
            @RequestBody @jakarta.validation.Valid OverrideAssigneeRequest request,
            @RequestAttribute("userId") UUID userId) {
        return ok(merchantService.overrideMerchantAssignment(merchantId, request.newUserId(), request.reason(), userId));
    }
}
