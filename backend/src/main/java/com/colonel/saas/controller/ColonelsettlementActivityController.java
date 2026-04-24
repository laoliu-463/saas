package com.colonel.saas.controller;

import com.colonel.saas.annotation.RequireRoles;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.colonel.saas.common.base.BaseController;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.common.result.PageResult;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.douyin.api.ActivityApi;
import com.colonel.saas.entity.ColonelsettlementActivity;
import com.colonel.saas.service.ColonelsettlementActivityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@Validated
@Tag(name = "活动管理")
@RestController
@RequestMapping("/activities")
@RequireRoles({RoleCodes.BIZ_LEADER, RoleCodes.BIZ_STAFF, RoleCodes.CHANNEL_LEADER, RoleCodes.CHANNEL_STAFF})
public class ColonelsettlementActivityController extends BaseController {

    private final ColonelsettlementActivityService activityService;
    private final ActivityApi activityApi;

    public ColonelsettlementActivityController(
            ColonelsettlementActivityService activityService,
            ActivityApi activityApi) {
        this.activityService = activityService;
        this.activityApi = activityApi;
    }

    @Operation(summary = "活动分页列表")
    @GetMapping
    public ApiResult<PageResult<ColonelsettlementActivity>> page(
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) long size,
            @RequestParam(required = false) Integer status) {
        IPage<ColonelsettlementActivity> result = activityService.getPage(page, size, status);
        return okPage(result);
    }

    /**
     * 获取团长活动详情 — buyin.colonelActivityDetail
     * <p>
     * 调用抖音开放平台获取指定活动的完整详情，包含佣金率、服务费率、
     * 招商类目、店铺门槛、双佣金字段等 27 个字段。
     */
    @Operation(summary = "团长活动详情（抖音真实接口）",
               description = "调用 buyin.colonelActivityDetail 获取指定团长活动的完整详情")
    @GetMapping("/{activityId}/douyin-detail")
    public ApiResult<Map<String, Object>> douyinDetail(
            @RequestParam(required = false) String appId,
            @org.springframework.web.bind.annotation.PathVariable String activityId) {
        log.info("获取团长活动详情, activityId={}", activityId);
        Map<String, Object> response = activityApi.detail(appId, activityId);
        return ok(response);
    }
}
