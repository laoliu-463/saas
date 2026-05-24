package com.colonel.saas.controller;

import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.common.base.BaseController;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.dto.logistics.LogisticsGatewayHealthResponse;
import com.colonel.saas.dto.logistics.LogisticsGatewayTestRequest;
import com.colonel.saas.dto.logistics.LogisticsGatewayTestResponse;
import com.colonel.saas.service.LogisticsGatewayHealthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "物流 Gateway 诊断", description = "物流 provider 配置与联调诊断，不伪造真实联通。")
@RestController
@RequestMapping("/admin/logistics/gateway")
@RequireRoles({RoleCodes.ADMIN, RoleCodes.OPS_STAFF})
public class AdminLogisticsGatewayController extends BaseController {

    private final LogisticsGatewayHealthService logisticsGatewayHealthService;

    public AdminLogisticsGatewayController(LogisticsGatewayHealthService logisticsGatewayHealthService) {
        this.logisticsGatewayHealthService = logisticsGatewayHealthService;
    }

    @Operation(summary = "物流 Gateway 状态", description = "返回当前 provider 配置与联通诊断状态。")
    @GetMapping("/status")
    public ApiResult<LogisticsGatewayHealthResponse> status() {
        return ok(logisticsGatewayHealthService.diagnoseCurrentProvider());
    }

    @Operation(summary = "物流 Gateway 联调测试", description = "按 provider 发起一次物流查询测试；test 环境不请求真实 API。")
    @PostMapping("/test")
    public ApiResult<LogisticsGatewayTestResponse> test(@Valid @RequestBody LogisticsGatewayTestRequest request) {
        return ok(logisticsGatewayHealthService.testQuery(request));
    }
}
