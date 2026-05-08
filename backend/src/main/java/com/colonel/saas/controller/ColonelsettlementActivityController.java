package com.colonel.saas.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.common.base.BaseController;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.common.result.PageResult;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.douyin.api.ActivityApi;
import com.colonel.saas.entity.ColonelsettlementActivity;
import com.colonel.saas.service.ColonelsettlementActivityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@Validated
@Tag(name = "活动管理", description = "本地活动分页与抖音真实活动详情补充接口。")
@RestController
@RequestMapping("/activities")
@RequireRoles({RoleCodes.BIZ_LEADER, RoleCodes.CHANNEL_LEADER, RoleCodes.CHANNEL_STAFF, RoleCodes.COLONEL_LEADER})
public class ColonelsettlementActivityController extends BaseController {

    private final ColonelsettlementActivityService activityService;
    private final ActivityApi activityApi;

    public ColonelsettlementActivityController(
            ColonelsettlementActivityService activityService,
            ActivityApi activityApi) {
        this.activityService = activityService;
        this.activityApi = activityApi;
    }

    @Operation(summary = "活动分页列表", description = "分页查询本地活动列表。")
    @GetMapping
    public ApiResult<PageResult<ColonelsettlementActivity>> page(
            @Parameter(description = "页码，从 1 开始。") @RequestParam(defaultValue = "1") @Min(1) long page,
            @Parameter(description = "每页条数。") @RequestParam(defaultValue = "10") @Min(1) @Max(100) long size,
            @Parameter(description = "活动状态。待确认：取值含义请联系产品。") @RequestParam(required = false) Integer status) {
        IPage<ColonelsettlementActivity> result = activityService.getPage(page, size, status);
        return okPage(result);
    }

    @Operation(summary = "团长活动详情（抖音真实接口）", description = "调用 buyin.colonelActivityDetail 获取指定团长活动的完整详情，用于本地活动信息补充与真实联调排查。")
    @GetMapping("/{activityId}/douyin-detail")
    public ApiResult<Map<String, Object>> douyinDetail(
            @Parameter(description = "抖音应用 appId；不传则使用系统默认应用配置。") @RequestParam(required = false) String appId,
            @Parameter(description = "团长活动 ID。") @PathVariable String activityId) {
        log.info("Get colonel activity detail, activityId={}", activityId);
        Map<String, Object> response = activityApi.detail(appId, activityId);
        return ok(response);
    }
}
