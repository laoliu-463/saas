package com.colonel.saas.controller;

import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.common.base.BaseController;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.service.ProductDisplayRuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商品库展示状态运维修复接口。
 */
@RestController
@Tag(name = "商品库运维修复", description = "商品库展示状态巡检与灰度修复接口。")
@RequestMapping("/colonel")
@RequireRoles({RoleCodes.ADMIN})
public class ProductLibraryRepairController extends BaseController {

    private final ProductDisplayRuleService productDisplayRuleService;

    public ProductLibraryRepairController(ProductDisplayRuleService productDisplayRuleService) {
        this.productDisplayRuleService = productDisplayRuleService;
    }

    @Operation(summary = "按活动修复商品库展示状态", description = "支持 dryRun 先返回差异，确认后再写入并触发展示规则重算。")
    @PostMapping("/activities/{activityId}/products/repair-library-state")
    public ApiResult<ProductDisplayRuleService.LibraryRepairResult> repairActivityLibraryState(
            @Parameter(description = "团长活动 ID。") @PathVariable("activityId") String activityId,
            @RequestBody(required = false) LibraryRepairRequest request) {
        LibraryRepairRequest safeRequest = request == null ? new LibraryRepairRequest(true, 1000) : request;
        return ok(productDisplayRuleService.repairLibraryStateForActivity(
                activityId,
                safeRequest.dryRun() == null || safeRequest.dryRun(),
                safeRequest.limit() == null ? 1000 : safeRequest.limit()));
    }

    @Operation(summary = "商品库展示状态巡检", description = "只读输出商品库状态断链指标。")
    @GetMapping("/products/library/health")
    public ApiResult<ProductDisplayRuleService.LibraryHealthResult> libraryHealth() {
        return ok(productDisplayRuleService.inspectLibraryHealth());
    }

    public record LibraryRepairRequest(Boolean dryRun, Integer limit) {
    }
}
