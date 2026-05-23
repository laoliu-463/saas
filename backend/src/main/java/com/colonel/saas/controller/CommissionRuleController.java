package com.colonel.saas.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.common.base.BaseController;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.common.result.PageResult;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.entity.CommissionRule;
import com.colonel.saas.service.CommissionRuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "提成规则", description = "V2 差异化提成规则管理。")
@RestController
@RequestMapping("/commission-rules")
@RequireRoles({RoleCodes.ADMIN})
public class CommissionRuleController extends BaseController {

    private final CommissionRuleService commissionRuleService;

    public CommissionRuleController(CommissionRuleService commissionRuleService) {
        this.commissionRuleService = commissionRuleService;
    }

    @Operation(summary = "分页查询提成规则")
    @GetMapping
    public ApiResult<PageResult<CommissionRule>> page(
            @RequestParam(required = false) String dimensionType,
            @RequestParam(required = false) String commissionType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        IPage<CommissionRule> result = commissionRuleService.findPage(dimensionType, commissionType, page, size);
        return okPage(result);
    }

    @Operation(summary = "创建提成规则")
    @PostMapping
    public ApiResult<CommissionRule> create(@RequestBody CommissionRule rule) {
        return ok(commissionRuleService.create(rule));
    }

    @Operation(summary = "更新提成规则")
    @PutMapping("/{id}")
    public ApiResult<CommissionRule> update(@PathVariable UUID id, @RequestBody CommissionRule rule) {
        return ok(commissionRuleService.update(id, rule));
    }

    @Operation(summary = "删除提成规则")
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable UUID id) {
        commissionRuleService.delete(id);
        return ok(null);
    }
}
