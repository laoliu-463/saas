package com.colonel.saas.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.common.base.BaseController;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.common.result.PageResult;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.dto.rulecenter.RuleCenterBatchUpdateRequest;
import com.colonel.saas.dto.rulecenter.RuleCenterChangeLogView;
import com.colonel.saas.dto.rulecenter.RuleCenterEventStatusResponse;
import com.colonel.saas.dto.rulecenter.RuleCenterGroupUpdateRequest;
import com.colonel.saas.dto.rulecenter.RuleCenterSchemaResponse;
import com.colonel.saas.dto.rulecenter.RuleCenterUpdateResponse;
import com.colonel.saas.dto.rulecenter.RuleCenterValidateRequest;
import com.colonel.saas.dto.rulecenter.RuleCenterValidateResponse;
import com.colonel.saas.dto.rulecenter.RuleCenterValuesResponse;
import com.colonel.saas.service.RuleCenterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "规则中心", description = "业务规则维护入口，按分组展示与保存系统配置。")
@RestController
@RequestMapping("/rule-center")
@RequireRoles({RoleCodes.ADMIN})
public class RuleCenterController extends BaseController {

    private final RuleCenterService ruleCenterService;

    public RuleCenterController(RuleCenterService ruleCenterService) {
        this.ruleCenterService = ruleCenterService;
    }

    @Operation(summary = "规则中心 Schema", description = "返回页面元数据与字段定义。")
    @GetMapping("/schema")
    public ApiResult<RuleCenterSchemaResponse> schema() {
        return ok(ruleCenterService.schema());
    }

    @Operation(summary = "当前规则值", description = "返回规则中心全部配置项当前值。")
    @GetMapping
    public ApiResult<RuleCenterValuesResponse> values() {
        return ok(ruleCenterService.currentValues());
    }

    @Operation(summary = "保存前校验", description = "校验配置值范围与业务告警。")
    @PostMapping("/validate")
    public ApiResult<RuleCenterValidateResponse> validate(@RequestBody RuleCenterValidateRequest request) {
        return ok(ruleCenterService.validate(request.values()));
    }

    @Operation(summary = "保存分组规则", description = "按分组批量保存规则。")
    @PutMapping("/groups/{groupCode}")
    public ApiResult<RuleCenterUpdateResponse> updateGroup(
            @Parameter(description = "分组编码") @PathVariable String groupCode,
            @RequestBody RuleCenterGroupUpdateRequest request,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "username", required = false) String username) {
        return ok(ruleCenterService.updateGroup(
                groupCode,
                request.values(),
                request.changeReason(),
                userId,
                username));
    }

    @Operation(summary = "批量保存规则", description = "跨分组批量保存规则。")
    @PutMapping("/batch")
    public ApiResult<RuleCenterUpdateResponse> batchUpdate(
            @RequestBody RuleCenterBatchUpdateRequest request,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "username", required = false) String username) {
        return ok(ruleCenterService.batchUpdate(
                request.values(),
                request.changeReason(),
                userId,
                username));
    }

    @Operation(summary = "变更历史", description = "查询规则中心配置变更日志。")
    @GetMapping("/change-logs")
    public ApiResult<PageResult<RuleCenterChangeLogView>> changeLogs(
            @RequestParam(required = false) String key,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        IPage<RuleCenterChangeLogView> result = ruleCenterService.changeLogs(key, page, size);
        return okPage(result);
    }

    @Operation(summary = "事件分发状态", description = "查看配置变更事件消费状态。")
    @GetMapping("/events")
    public ApiResult<RuleCenterEventStatusResponse> eventStatus(
            @RequestParam UUID eventId) {
        return ok(ruleCenterService.eventStatus(eventId));
    }
}
