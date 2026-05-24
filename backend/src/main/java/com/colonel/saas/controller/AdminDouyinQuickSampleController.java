package com.colonel.saas.controller;

import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.common.base.BaseController;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.dto.douyin.DouyinQuickSampleStatusResponse;
import com.colonel.saas.gateway.douyin.DouyinQuickSampleGateway;
import com.colonel.saas.service.ProductQuickSampleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "抖店快速寄样诊断", description = "外部抖店 quick_sample_apply 联通状态诊断。")
@RestController
@RequestMapping("/admin/douyin/quick-sample")
@RequireRoles({RoleCodes.ADMIN, RoleCodes.OPS_STAFF})
public class AdminDouyinQuickSampleController extends BaseController {

    private final DouyinQuickSampleGateway douyinQuickSampleGateway;
    private final boolean douyinQuickSampleEnabled;

    public AdminDouyinQuickSampleController(
            DouyinQuickSampleGateway douyinQuickSampleGateway,
            @Value("${app.douyin.quick-sample.enabled:false}") boolean douyinQuickSampleEnabled) {
        this.douyinQuickSampleGateway = douyinQuickSampleGateway;
        this.douyinQuickSampleEnabled = douyinQuickSampleEnabled;
    }

    @Operation(summary = "抖店快速寄样状态", description = "返回 SDK 是否支持外部 quick_sample_apply 及 LOCAL_FALLBACK 策略。")
    @GetMapping("/status")
    public ApiResult<DouyinQuickSampleStatusResponse> status() {
        DouyinQuickSampleGateway.SupportStatus supportStatus = douyinQuickSampleGateway.supportStatus();
        boolean supported = douyinQuickSampleGateway.isSupported();
        String statusName = supportStatus == null ? ProductQuickSampleService.GATEWAY_STATUS_UNSUPPORTED : supportStatus.name();
        return ok(DouyinQuickSampleStatusResponse.builder()
                .supported(supported)
                .status(statusName)
                .realConnected(false)
                .message("当前 SDK 未支持 quick_sample_apply")
                .fallbackEnabled(true)
                .build());
    }
}
