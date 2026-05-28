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

/**
 * 操作日志中心控制器
 * <p>
 * 提供系统关键操作日志的统一查询接口，用于管理员追溯系统操作记录。
 * 默认返回最近 90 天的关键操作日志，支持按模块、动作、操作人、
 * 请求方法和日期范围进行多维度筛选。
 * </p>
 *
 * <p>API 路径前缀：{@code /operation-logs}</p>
 * <p>所属业务领域：系统运维（操作日志追溯）</p>
 * <p>访问权限：仅管理员（ADMIN）</p>
 *
 * @see com.colonel.saas.service.OperationLogService
 */
@Validated
@Tag(name = "操作日志中心", description = "管理员统一追溯系统关键操作的查询接口。")
@RestController
@RequestMapping("/operation-logs")
@RequireRoles({RoleCodes.ADMIN})
public class OperationLogController extends BaseController {

    /** 操作日志服务，负责日志的分页查询 */
    private final OperationLogService operationLogService;

    /**
     * 构造函数，注入操作日志服务依赖
     *
     * @param operationLogService 操作日志服务实例
     */
    public OperationLogController(OperationLogService operationLogService) {
        this.operationLogService = operationLogService;
    }

    /**
     * 分页查询操作日志
     * <p>
     * 按多维度条件分页查询系统操作日志。支持的筛选维度包括：
     * <ul>
     *   <li>模块关键字 — 按业务模块名称模糊匹配</li>
     *   <li>动作关键字 — 按操作动作名称模糊匹配</li>
     *   <li>操作人关键字 — 按操作人用户名模糊匹配</li>
     *   <li>请求方法 — 按 HTTP 方法筛选（POST/PUT/PATCH/DELETE）</li>
     *   <li>日期范围 — 按操作时间区间筛选</li>
     * </ul>
     * </p>
     *
     * <p>HTTP 方法：GET</p>
     * <p>请求路径：{@code /operation-logs}</p>
     *
     * @param module         模块关键字（可选），模糊匹配模块名称
     * @param action         动作关键字（可选），模糊匹配动作名称
     * @param username       操作人关键字（可选），模糊匹配操作人用户名
     * @param requestMethod  HTTP 请求方法（可选），支持 POST/PUT/PATCH/DELETE
     * @param startDate      查询开始日期（可选），格式 yyyy-MM-dd
     * @param endDate        查询结束日期（可选），格式 yyyy-MM-dd
     * @param page           页码，从 1 开始，默认值 1
     * @param size           每页条数，默认值 20，最大 200
     * @return 分页后的操作日志列表，包含总数和当前页数据
     * @throws jakarta.validation.ConstraintViolationException 当 page < 1 或 size 超出范围时
     */
    @Operation(summary = "分页查询操作日志", description = "默认返回最近 90 天的关键操作日志。")
    @GetMapping
    public ApiResult<PageResult<OperationLog>> page(
            @Parameter(description = "模块关键字。") @RequestParam(name = "module", required = false) String module,
            @Parameter(description = "动作关键字。") @RequestParam(name = "action", required = false) String action,
            @Parameter(description = "操作人关键字。") @RequestParam(name = "username", required = false) String username,
            @Parameter(description = "请求方法，支持 POST/PUT/PATCH/DELETE。") @RequestParam(name = "requestMethod", required = false) String requestMethod,
            @Parameter(description = "开始日期，格式 yyyy-MM-dd。") @RequestParam(name = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "结束日期，格式 yyyy-MM-dd。") @RequestParam(name = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @Parameter(description = "页码，从 1 开始。") @RequestParam(name = "page", defaultValue = "1") @Min(1) long page,
            @Parameter(description = "每页条数。") @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(200) long size) {
        // 调用日志服务执行多条件分页查询
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
