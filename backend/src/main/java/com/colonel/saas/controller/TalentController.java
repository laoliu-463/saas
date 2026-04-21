package com.colonel.saas.controller;

import com.colonel.saas.annotation.RequireRoles;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.colonel.saas.common.base.BaseController;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.common.result.PageResult;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.entity.Talent;
import com.colonel.saas.service.TalentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Validated
@Tag(name = "达人CRM")
@RestController
@RequestMapping("/talents")
@RequireRoles({RoleCodes.CHANNEL_LEADER, RoleCodes.CHANNEL_STAFF})
public class TalentController extends BaseController {

    private final TalentService talentService;

    public TalentController(TalentService talentService) {
        this.talentService = talentService;
    }

    @Operation(summary = "达人分页列表")
    @GetMapping("/page")
    public ApiResult<PageResult<Talent>> page(
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) long size,
            @RequestParam(required = false) String keyword,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope) {
        IPage<Talent> result = talentService.page(page, size, keyword, dataScope, userId, deptId);
        return okPage(result);
    }

    @Operation(summary = "达人详情")
    @GetMapping("/{id}")
    public ApiResult<Talent> detail(@PathVariable UUID id) {
        return ok(talentService.getById(id));
    }

    @Operation(summary = "新增达人")
    @PostMapping
    public ApiResult<Talent> create(@RequestBody Talent request) {
        return ok(talentService.create(request));
    }

    @Operation(summary = "编辑达人")
    @PutMapping("/{id}")
    public ApiResult<Talent> update(@PathVariable UUID id, @RequestBody Talent request) {
        return ok(talentService.update(id, request));
    }

    @Operation(summary = "删除达人")
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable UUID id) {
        talentService.delete(id);
        return ok();
    }

    @Operation(summary = "公海达人列表")
    @GetMapping("/public")
    public ApiResult<List<Talent>> publicPool() {
        return ok(talentService.getPublicPool());
    }

    @Operation(summary = "私海达人列表")
    @GetMapping("/private")
    public ApiResult<List<Talent>> privatePool(@RequestAttribute("userId") UUID userId) {
        return ok(talentService.getPrivatePool(userId));
    }

    @Operation(summary = "认领达人")
    @PostMapping("/{id}/claim")
    public ApiResult<Talent> claim(
            @PathVariable("id") UUID talentId,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId) {
        return ok(talentService.claim(talentId, userId, deptId));
    }

    @Operation(summary = "独家达人判断")
    @GetMapping("/{id}/exclusive-check")
    public ApiResult<TalentService.ExclusiveCheckResult> exclusiveCheck(
            @PathVariable UUID id,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope) {
        return ok(talentService.evaluateExclusive(id, dataScope, userId, deptId));
    }
}
