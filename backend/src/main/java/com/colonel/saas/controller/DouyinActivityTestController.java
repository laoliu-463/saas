package com.colonel.saas.controller;

import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.common.base.BaseController;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.douyin.api.ActivityApi;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Tag(name = "抖音SDK测试")
@RestController
@RequestMapping("/douyin")
@RequireRoles({RoleCodes.ADMIN, RoleCodes.BIZ_LEADER})
public class DouyinActivityTestController extends BaseController {

    private final ActivityApi activityApi;

    public DouyinActivityTestController(ActivityApi activityApi) {
        this.activityApi = activityApi;
    }

    @Operation(summary = "测试活动列表接口", description = "验证 M1.2 抖音 SDK 调用链路是否可用")
    @GetMapping("/test")
    public ApiResult<Map<String, Object>> test() {
        Map<String, Object> result = new HashMap<>();
        result.put("module", "M1.2 Douyin SDK");
        result.put("endpoint", "buyin.colonel.activity.list");
        try {
            result.put("remoteResponse", activityApi.list());
            result.put("status", "success");
        } catch (Exception e) {
            log.error("Douyin activity test call failed", e);
            result.put("status", "failed");
            result.put("message", "抖音SDK调用失败，请检查日志");
        }
        return ok(result);
    }
}
