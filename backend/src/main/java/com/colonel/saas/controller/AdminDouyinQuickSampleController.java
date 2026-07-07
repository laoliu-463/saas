package com.colonel.saas.controller;

import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.common.base.BaseController;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.domain.product.application.ProductQuickSampleStatusQueryService;
import com.colonel.saas.dto.douyin.DouyinQuickSampleStatusResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 抖店快速寄样诊断控制器，供运维人员检查外部抖店 quick_sample_apply 的联通状态。
 *
 * <ul>
 *   <li>查询 SDK 是否支持外部抖店 quick_sample_apply 接口</li>
 *   <li>返回 LOCAL_FALLBACK 策略的启用状态</li>
 * </ul>
 *
 * <p>所属业务领域：抖店集成 / 快速寄样诊断
 * <p>API 路径前缀：{@code /admin/douyin/quick-sample}
 * <p>访问权限：管理员和运维人员（{@link com.colonel.saas.constant.RoleCodes#ADMIN}、{@link com.colonel.saas.constant.RoleCodes#OPS_STAFF}）
 *
 * @see ProductQuickSampleStatusQueryService
 */
@Tag(name = "抖店快速寄样诊断", description = "外部抖店 quick_sample_apply 联通状态诊断。")
@RestController
@RequestMapping("/admin/douyin/quick-sample")
@RequireRoles({RoleCodes.ADMIN, RoleCodes.OPS_STAFF})
public class AdminDouyinQuickSampleController extends BaseController {

    /** 快速寄样状态查询应用服务。 */
    private final ProductQuickSampleStatusQueryService productQuickSampleStatusQueryService;

    public AdminDouyinQuickSampleController(ProductQuickSampleStatusQueryService productQuickSampleStatusQueryService) {
        this.productQuickSampleStatusQueryService = productQuickSampleStatusQueryService;
    }

    /**
     * 查询抖店快速寄样功能的联通状态。
     *
     * <p>处理流程：
     * <ol>
     *   <li>查询 SDK 对 quick_sample_apply 接口的支持状态</li>
     *   <li>判断当前 SDK 版本是否已集成该能力</li>
     *   <li>构建状态响应，包含是否支持、当前状态、是否真实联通、提示消息和 LOCAL_FALLBACK 策略开关</li>
     * </ol>
     *
     * <p>HTTP 方法与路径：{@code GET /admin/douyin/quick-sample/status}
     *
     * @return 快速寄样状态响应，包含支持状态、联通状态和 LOCAL_FALLBACK 策略信息
     */
    @Operation(summary = "抖店快速寄样状态", description = "返回 SDK 是否支持外部 quick_sample_apply 及 LOCAL_FALLBACK 策略。")
    @GetMapping("/status")
    public ApiResult<DouyinQuickSampleStatusResponse> status() {
        return ok(productQuickSampleStatusQueryService.status());
    }
}
