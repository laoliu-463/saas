package com.colonel.saas.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.common.base.BaseController;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.common.result.PageResult;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.entity.OperationLog;
import com.colonel.saas.service.OperationLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Validated
@Tag(name = "操作日志中心", description = "管理员统一追溯系统关键操作的查询接口。")
@RestController
@RequestMapping("/operation-logs")
@RequireRoles({RoleCodes.ADMIN})
public class OperationLogController extends BaseController {

    private final OperationLogService operationLogService;

    public OperationLogController(OperationLogService operationLogService) {
        this.operationLogService = operationLogService;
    }

    @Operation(summary = "分页查询操作日志", description = "默认返回最近 90 天的关键操作日志。")
    @GetMapping
    public ApiResult<PageResult<OperationLog>> page(
            @Parameter(description = "模块关键字。") @RequestParam(required = false) String module,
            @Parameter(description = "动作关键字。") @RequestParam(required = false) String action,
            @Parameter(description = "操作人关键字。") @RequestParam(required = false) String username,
            @Parameter(description = "请求方法，支持 POST/PUT/PATCH/DELETE。") @RequestParam(required = false) String requestMethod,
            @Parameter(description = "开始日期，格式 yyyy-MM-dd。") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "结束日期，格式 yyyy-MM-dd。") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @Parameter(description = "页码，从 1 开始。") @RequestParam(defaultValue = "1") @Min(1) long page,
            @Parameter(description = "每页条数。") @RequestParam(defaultValue = "20") @Min(1) @Max(200) long size) {
        IPage<OperationLog> result = operationLogService.findPage(
                module,
                action,
                username,
                requestMethod,
                startDate,
                endDate,
                page,
                size
        );
        return okPage(result);
    }
}
