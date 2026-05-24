package com.colonel.saas.controller;

import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.common.base.BaseController;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.dto.performance.ExclusiveMerchantDetailDTO;
import com.colonel.saas.service.ExclusiveMerchantQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "独家商家", description = "独家商家查询（V1 保留接口，是否启用由配置控制）。")
@RestController
@RequestMapping("/exclusive-merchants")
@RequireRoles({RoleCodes.ADMIN, RoleCodes.BIZ_LEADER, RoleCodes.BIZ_STAFF, RoleCodes.COLONEL_LEADER})
public class ExclusiveMerchantController extends BaseController {

    private final ExclusiveMerchantQueryService exclusiveMerchantQueryService;

    public ExclusiveMerchantController(ExclusiveMerchantQueryService exclusiveMerchantQueryService) {
        this.exclusiveMerchantQueryService = exclusiveMerchantQueryService;
    }

    @Operation(summary = "我的独家商家")
    @GetMapping("/my")
    public ApiResult<List<ExclusiveMerchantDetailDTO>> myExclusiveMerchants(
            @RequestAttribute("userId") UUID userId) {
        return ok(exclusiveMerchantQueryService.listMyExclusiveMerchants(userId));
    }

    @Operation(summary = "查询合作方独家状态")
    @GetMapping("/{partnerId}")
    public ApiResult<ExclusiveMerchantDetailDTO> getByPartnerId(@PathVariable String partnerId) {
        return ok(exclusiveMerchantQueryService.getByPartnerId(partnerId));
    }
}
