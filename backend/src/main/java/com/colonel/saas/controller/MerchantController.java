package com.colonel.saas.controller;

import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.common.base.BaseController;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.dto.talent.OverrideAssigneeRequest;
import com.colonel.saas.entity.Merchant;
import com.colonel.saas.service.MerchantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Validated
@Tag(name = "商家管理", description = "商家归属与基本信息管理。")
@RestController
@RequestMapping("/merchants")
@RequireRoles({RoleCodes.BIZ_LEADER, RoleCodes.CHANNEL_LEADER})
public class MerchantController extends BaseController {

    private final MerchantService merchantService;

    public MerchantController(MerchantService merchantService) {
        this.merchantService = merchantService;
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
