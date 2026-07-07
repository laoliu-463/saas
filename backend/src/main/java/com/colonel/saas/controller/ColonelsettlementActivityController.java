package com.colonel.saas.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.common.base.BaseController;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.common.result.PageResult;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.domain.colonel.application.ColonelActivityDetailQueryService;
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

/**
 * 活动管理控制器
 * <p>
 * 提供团长活动的本地分页查询和抖音真实活动详情补充接口。
 * 本地活动数据通过分页接口展示，同时支持调用抖音开放平台的
 * buyin.colonelActivityDetail 接口获取远端活动详情，
 * 用于本地活动信息补充与真实联调排查。
 * </p>
 *
 * <p>API 路径前缀：{@code /activities}</p>
 * <p>所属业务领域：活动管理（活动分页、抖音活动详情补充）</p>
 * <p>访问权限：业务负责人、渠道负责人、渠道专员、团长负责人</p>
 *
 * @see com.colonel.saas.service.ColonelsettlementActivityService
 * @see ColonelActivityDetailQueryService
 */
@Slf4j
@Validated
@Tag(name = "活动管理", description = "本地活动分页与抖音真实活动详情补充接口。")
@RestController
@RequestMapping("/activities")
@RequireRoles({RoleCodes.BIZ_LEADER, RoleCodes.CHANNEL_LEADER, RoleCodes.CHANNEL_STAFF})
public class ColonelsettlementActivityController extends BaseController {

    /** 活动服务，负责本地活动数据的查询与管理 */
    private final ColonelsettlementActivityService activityService;

    /** 团长活动详情查询应用服务。 */
    private final ColonelActivityDetailQueryService activityDetailQueryService;

    /**
     * 构造函数，注入活动服务与活动详情查询服务。
     *
     * @param activityService      活动服务实例
     * @param activityDetailQueryService 活动详情查询服务
     */
    public ColonelsettlementActivityController(
            ColonelsettlementActivityService activityService,
            ColonelActivityDetailQueryService activityDetailQueryService) {
        this.activityService = activityService;
        this.activityDetailQueryService = activityDetailQueryService;
    }

    /**
     * 活动分页列表
     * <p>
     * 分页查询本地已同步的活动列表，支持按活动状态筛选。
     * 返回的活动数据来源于本地数据库，经过分页处理后返回。
     * </p>
     *
     * <p>HTTP 方法：GET</p>
     * <p>请求路径：{@code /activities}</p>
     *
     * @param page   页码，从 1 开始，默认值 1
     * @param size   每页条数，默认值 10，最大 100
     * @param status 活动状态筛选条件（可选），取值含义请联系产品确认
     * @return 分页后的活动列表，包含总数和当前页数据
     * @throws jakarta.validation.ConstraintViolationException 当 page < 1 或 size 超出范围时
     */
    @Operation(summary = "活动分页列表", description = "分页查询本地活动列表。")
    @GetMapping
    public ApiResult<PageResult<ColonelsettlementActivity>> page(
            @Parameter(description = "页码，从 1 开始。") @RequestParam(defaultValue = "1") @Min(1) long page,
            @Parameter(description = "每页条数。") @RequestParam(defaultValue = "10") @Min(1) @Max(100) long size,
            @Parameter(description = "活动状态。待确认：取值含义请联系产品。") @RequestParam(required = false) Integer status) {
        // 调用活动服务获取分页数据
        IPage<ColonelsettlementActivity> result = activityService.getPage(page, size, status);
        return okPage(result);
    }

    /**
     * 团长活动详情（抖音真实接口）
     * <p>
     * 调用抖音开放平台的 buyin.colonelActivityDetail 接口，
     * 获取指定团长活动的完整详情信息。主要用于：
     * <ul>
     *   <li>本地活动信息的补充和校验</li>
     *   <li>真实环境联调排查</li>
     *   <li>对比本地数据与远端数据的一致性</li>
     * </ul>
     * </p>
     *
     * <p>HTTP 方法：GET</p>
     * <p>请求路径：{@code /activities/{activityId}/douyin-detail}</p>
     *
     * @param appId      抖音应用 appId（可选），不传则使用系统默认应用配置
     * @param activityId 团长活动 ID，路径参数
     * @return 抖音开放平台返回的活动详情原始数据（Map 结构）
     */
    @Operation(summary = "团长活动详情（抖音真实接口）", description = "调用 buyin.colonelActivityDetail 获取指定团长活动的完整详情，用于本地活动信息补充与真实联调排查。")
    @GetMapping("/{activityId}/douyin-detail")
    public ApiResult<Map<String, Object>> douyinDetail(
            @Parameter(description = "抖音应用 appId；不传则使用系统默认应用配置。") @RequestParam(required = false) String appId,
            @Parameter(description = "团长活动 ID。") @PathVariable String activityId) {
        log.info("Get colonel activity detail, activityId={}", activityId);
        Map<String, Object> response = activityDetailQueryService.getDouyinDetail(appId, activityId);
        return ok(response);
    }
}
