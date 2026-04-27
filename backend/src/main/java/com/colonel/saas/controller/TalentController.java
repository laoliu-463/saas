package com.colonel.saas.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.common.base.BaseController;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.common.result.PageResult;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.dto.talent.TalentDetailResponse;
import com.colonel.saas.entity.Talent;
import com.colonel.saas.entity.TalentEnrichTask;
import com.colonel.saas.job.TalentWeeklyRefreshJob;
import com.colonel.saas.service.TalentQueryService;
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
    private final TalentQueryService talentQueryService;
    private final TalentWeeklyRefreshJob talentWeeklyRefreshJob;

    public TalentController(
            TalentService talentService,
            TalentQueryService talentQueryService,
            TalentWeeklyRefreshJob talentWeeklyRefreshJob) {
        this.talentService = talentService;
        this.talentQueryService = talentQueryService;
        this.talentWeeklyRefreshJob = talentWeeklyRefreshJob;
    }

    @Operation(summary = "达人分页列表")
    @GetMapping
    public ApiResult<PageResult<Talent>> page(
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String poolStatus,
            @RequestParam(required = false) String ownerKeyword,
            @RequestParam(required = false) Long minFans,
            @RequestParam(required = false) Long maxFans,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope) {
        IPage<Talent> result = talentQueryService.page(
                page, size, keyword, region, poolStatus, ownerKeyword, minFans, maxFans, dataScope, userId, deptId);
        return okPage(result);
    }

    @Operation(summary = "达人详情")
    @GetMapping("/{id}")
    public ApiResult<TalentDetailResponse> detail(@PathVariable UUID id) {
        return ok(talentQueryService.detail(id));
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
    @GetMapping("/pools/public")
    public ApiResult<List<Talent>> publicPool() {
        return ok(talentService.getPublicPool());
    }

    @Operation(summary = "私海达人列表")
    @GetMapping("/pools/private")
    public ApiResult<List<Talent>> privatePool(@RequestAttribute("userId") UUID userId) {
        return ok(talentService.getPrivatePool(userId));
    }

    @Operation(summary = "认领达人")
    @PostMapping("/{id}/claims")
    public ApiResult<Talent> claim(
            @PathVariable("id") UUID talentId,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId) {
        return ok(talentService.claim(talentId, userId, deptId));
    }

    @Operation(summary = "释放达人")
    @PostMapping("/{id}/release")
    public ApiResult<Talent> release(
            @PathVariable("id") UUID talentId,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "roleCodes", required = false) List<String> roleCodes) {
        return ok(talentService.release(talentId, userId, deptId, roleCodes));
    }

    @Operation(summary = "刷新达人信息")
    @PostMapping("/{id}/refresh")
    public ApiResult<Talent> refresh(@PathVariable("id") UUID talentId) {
        return ok(talentService.refresh(talentId));
    }

    @Operation(summary = "手动触发每周批量刷新")
    @PostMapping("/refresh/weekly")
    public ApiResult<Void> refreshWeekly() {
        talentWeeklyRefreshJob.weeklyRefreshActiveTalents();
        return ok();
    }

    @Operation(summary = "手动补全达人信息")
    @PutMapping("/{id}/manual-fill")
    public ApiResult<Talent> manualFill(@PathVariable("id") UUID talentId, @RequestBody Talent request) {
        return ok(talentService.manualFill(talentId, request));
    }

    @Operation(summary = "获取达人最新补全任务")
    @GetMapping("/{id}/enrich-task/latest")
    public ApiResult<TalentEnrichTask> latestEnrichTask(@PathVariable("id") UUID talentId) {
        return ok(talentService.getLatestEnrichTask(talentId));
    }

    @Operation(summary = "独家达人判断")
    @GetMapping("/{id}/exclusive-status")
    public ApiResult<TalentService.ExclusiveCheckResult> exclusiveCheck(
            @PathVariable UUID id,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope) {
        return ok(talentService.evaluateExclusive(id, dataScope, userId, deptId));
    }
}
