package com.colonel.saas.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.common.base.BaseController;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.common.result.PageResult;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.dto.talent.OverrideAssigneeRequest;
import com.colonel.saas.dto.talent.TalentDetailResponse;
import com.colonel.saas.dto.talent.TalentOperateRequest;
import com.colonel.saas.dto.talent.TalentPageQuery;
import com.colonel.saas.entity.Talent;
import com.colonel.saas.entity.TalentEnrichTask;
import com.colonel.saas.job.TalentWeeklyRefreshJob;
import com.colonel.saas.service.TalentQueryService;
import com.colonel.saas.service.TalentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Validated
@Tag(name = "达人CRM", description = "达人池、公海私海、认领释放与达人信息补全相关接口。")
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

    @Operation(summary = "达人分页列表", description = "按关键字、地区、粉丝量与池状态分页查询达人列表，用于达人 CRM 主页面。")
    @RequireRoles({RoleCodes.BIZ_LEADER, RoleCodes.BIZ_STAFF, RoleCodes.CHANNEL_LEADER, RoleCodes.CHANNEL_STAFF})
    @GetMapping
    public ApiResult<PageResult<Talent>> page(
            @Parameter(description = "达人分页查询参数。") TalentPageQuery query,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope) {
        query.setUserId(userId);
        query.setDeptId(deptId);
        query.setDataScope(dataScope);
        IPage<Talent> result = talentQueryService.page(query);
        return okPage(result);
    }

    @Operation(summary = "达人详情", description = "查询单个达人的详情、关联信息与补全结果，用于达人侧边栏或详情弹窗。")
    @RequireRoles({RoleCodes.BIZ_LEADER, RoleCodes.BIZ_STAFF, RoleCodes.CHANNEL_LEADER, RoleCodes.CHANNEL_STAFF})
    @GetMapping("/{id}")
    public ApiResult<TalentDetailResponse> detail(
            @Parameter(description = "达人主键 ID，使用 UUID 格式。") @PathVariable UUID id,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope) {
        return ok(talentQueryService.detail(id, userId, deptId, dataScope));
    }

    @Operation(summary = "新增达人", description = "手动新增达人基础资料，用于 CRM 人工补录。")
    @PostMapping
    public ApiResult<Talent> create(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "达人新增请求体。",
                    required = true,
                    content = @Content(examples = @ExampleObject(value = "{\"nickname\":\"达人A\",\"douyinUid\":\"test_talent_001\"}"))
            )
            @RequestBody Talent request) {
        return ok(talentService.create(request));
    }

    @Operation(summary = "编辑达人", description = "更新达人基础资料。")
    @PutMapping("/{id}")
    public ApiResult<Talent> update(
            @Parameter(description = "达人主键 ID，使用 UUID 格式。") @PathVariable UUID id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "达人更新请求体。",
                    required = true,
                    content = @Content(examples = @ExampleObject(value = "{\"nickname\":\"达人A-更新\"}"))
            )
            @RequestBody Talent request) {
        return ok(talentService.update(id, request));
    }

    @Operation(summary = "删除达人", description = "删除达人资料。请确认该达人未处于关键业务链路中。")
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(
            @Parameter(description = "达人主键 ID，使用 UUID 格式。") @PathVariable UUID id) {
        talentService.delete(id);
        return ok();
    }

    @Operation(summary = "公海达人列表", description = "查询当前可被认领的公海达人列表。")
    @RequireRoles({RoleCodes.BIZ_LEADER, RoleCodes.BIZ_STAFF, RoleCodes.CHANNEL_LEADER, RoleCodes.CHANNEL_STAFF})
    @GetMapping("/pools/public")
    public ApiResult<List<Talent>> publicPool() {
        return ok(talentService.getPublicPool());
    }

    @Operation(summary = "私海达人列表", description = "查询当前登录用户已认领的私海达人列表。")
    @RequireRoles({RoleCodes.BIZ_LEADER, RoleCodes.BIZ_STAFF, RoleCodes.CHANNEL_LEADER, RoleCodes.CHANNEL_STAFF})
    @GetMapping("/pools/private")
    public ApiResult<List<Talent>> privatePool(@RequestAttribute("userId") UUID userId) {
        return ok(talentService.getPrivatePool(userId));
    }

    @Operation(summary = "认领达人", description = "认领动作，生成认领记录并将达人从公海转入当前负责人的私海。")
    @PostMapping("/{id}/claims")
    public ApiResult<Talent> claim(
            @Parameter(description = "达人主键 ID，使用 UUID 格式。") @PathVariable("id") UUID talentId,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId) {
        return ok(talentService.claim(talentId, userId, deptId));
    }

    @Operation(summary = "释放达人", description = "释放动作，解除该达人的锁定状态并将其从当前负责人的私海释放回公共池。当前路径为动作语义，非资源集合。")
    @PostMapping("/{id}/release")
    public ApiResult<Talent> release(
            @Parameter(description = "达人主键 ID，使用 UUID 格式。") @PathVariable("id") UUID talentId,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "roleCodes", required = false) List<String> roleCodes) {
        return ok(talentService.release(talentId, userId, deptId, roleCodes));
    }

    @Operation(summary = "归属覆盖", description = "组长级别手动覆盖达人的当前归属人，同时记录覆盖原因。")
    @RequireRoles({RoleCodes.BIZ_LEADER, RoleCodes.CHANNEL_LEADER})
    @PostMapping("/{id}/override-assignee")
    public ApiResult<Talent> overrideAssignee(
            @Parameter(description = "达人主键 ID，使用 UUID 格式。") @PathVariable("id") UUID talentId,
            @RequestBody @jakarta.validation.Valid OverrideAssigneeRequest request,
            @RequestAttribute("userId") UUID userId) {
        return ok(talentService.overrideTalentAssignment(talentId, request.newUserId(), request.reason(), userId));
    }

    @Operation(summary = "拉黑达人", description = "将达人标记为黑名单，避免继续进入公海与合作流转。")
    @PostMapping("/{id}/blacklist")
    public ApiResult<Talent> blacklist(
            @Parameter(description = "达人主键 ID，使用 UUID 格式。") @PathVariable("id") UUID talentId,
            @RequestBody(required = false) TalentOperateRequest request) {
        return ok(talentService.blacklist(talentId, request == null ? null : request.getReason()));
    }

    @Operation(summary = "解除达人黑名单", description = "取消达人黑名单标记，恢复达人正常经营状态。")
    @PostMapping("/{id}/unblacklist")
    public ApiResult<Talent> unblacklist(
            @Parameter(description = "达人主键 ID，使用 UUID 格式。") @PathVariable("id") UUID talentId) {
        return ok(talentService.unblacklist(talentId));
    }

    @Operation(summary = "刷新达人信息", description = "立即触发单个达人信息刷新，适用于需要同步最新达人资料的场景。")
    @PostMapping("/{id}/refresh")
    public ApiResult<Talent> refresh(
            @Parameter(description = "达人主键 ID，使用 UUID 格式。") @PathVariable("id") UUID talentId) {
        return ok(talentService.refresh(talentId));
    }

    @Operation(summary = "手动触发每周刷新", description = "手动执行每周批量刷新任务，用于校验达人定时刷新链路。")
    @PostMapping("/refresh/weekly")
    public ApiResult<Void> refreshWeekly() {
        talentWeeklyRefreshJob.weeklyRefreshActiveTalents();
        return ok();
    }

    @Operation(summary = "手动补全达人信息", description = "人工补全达人资料，用于修正自动抓取或导入后的缺失字段。")
    @PutMapping("/{id}/manual-fill")
    public ApiResult<Talent> manualFill(
            @Parameter(description = "达人主键 ID，使用 UUID 格式。") @PathVariable("id") UUID talentId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "达人补全请求体。",
                    required = true,
                    content = @Content(examples = @ExampleObject(value = "{\"nickname\":\"达人A\",\"fans\":20000}"))
            )
            @RequestBody Talent request) {
        return ok(talentService.manualFill(talentId, request));
    }

    @Operation(summary = "获取最新补全任务", description = "查询指定达人最近一次补全任务记录，用于排查补全链路。")
    @RequireRoles({RoleCodes.BIZ_LEADER, RoleCodes.BIZ_STAFF, RoleCodes.CHANNEL_LEADER, RoleCodes.CHANNEL_STAFF})
    @GetMapping("/{id}/enrich-task/latest")
    public ApiResult<TalentEnrichTask> latestEnrichTask(
            @Parameter(description = "达人主键 ID，使用 UUID 格式。") @PathVariable("id") UUID talentId) {
        return ok(talentService.getLatestEnrichTask(talentId));
    }

    @Operation(summary = "独家达人判断", description = "判断指定达人是否满足独家条件，用于业务分配与跟进决策。")
    @RequireRoles({RoleCodes.BIZ_LEADER, RoleCodes.BIZ_STAFF, RoleCodes.CHANNEL_LEADER, RoleCodes.CHANNEL_STAFF})
    @GetMapping("/{id}/exclusive-status")
    public ApiResult<TalentService.ExclusiveCheckResult> exclusiveCheck(
            @Parameter(description = "达人主键 ID，使用 UUID 格式。") @PathVariable UUID id,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope) {
        return ok(talentService.evaluateExclusive(id, dataScope, userId, deptId));
    }
}
