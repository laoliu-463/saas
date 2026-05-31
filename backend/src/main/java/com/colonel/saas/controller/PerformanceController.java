package com.colonel.saas.controller;

import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.common.base.BaseController;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.dto.performance.PerformanceBatchRequest;
import com.colonel.saas.dto.performance.PerformanceBatchResponse;
import com.colonel.saas.dto.performance.PerformanceDetailDTO;
import com.colonel.saas.dto.performance.PerformanceListQuery;
import com.colonel.saas.dto.performance.PerformancePageResponse;
import com.colonel.saas.dto.performance.PerformanceRecalculateMonthRequest;
import com.colonel.saas.dto.performance.PerformanceRecalculateMonthResponse;
import com.colonel.saas.dto.performance.PerformanceSummaryQuery;
import com.colonel.saas.dto.performance.PerformanceSummaryResponse;
import com.colonel.saas.service.ExclusiveMerchantQueryService;
import com.colonel.saas.service.OperationLogService;
import com.colonel.saas.service.PerformanceExportService;
import com.colonel.saas.service.PerformanceMonthRecalculationService;
import com.colonel.saas.service.PerformanceQueryService;
import com.colonel.saas.service.PerformanceSummaryService;
import com.colonel.saas.service.performance.PerformanceAccessContext;
import com.colonel.saas.service.performance.PerformanceAccessScope;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 业绩域 REST 控制器。
 *
 * <p>负责将业绩数据暴露给前端看板和管理后台，核心职责：
 * <ul>
 *   <li>单笔 / 批量 / 分页业绩查询；</li>
 *   <li>指标卡片汇总（订单量、GMV、佣金等聚合指标）；</li>
 *   <li>业绩明细 Excel 导出（仅 Leader 角色可触发）；</li>
 *   <li>管理员手动重算指定月份业绩（仅 ADMIN 角色）。</li>
 * </ul>
 *
 * <p><b>架构角色：</b>REST 控制器层（Controller），
 * 位于 API 网关 → Controller → Service 的最外层，
 * 负责参数校验、访问上下文组装和响应封装，不包含业务计算逻辑。
 *
 * <p><b>API 前缀：</b>{@code /performance}
 *
 * <p><b>业务域：</b>业绩域（Performance Domain）
 *
 * <p><b>数据范围控制：</b>所有查询接口通过 {@link PerformanceAccessContext}
 * 组装当前用户的 userId / deptId / dataScope / roleCodes，
 * 下沉至 Service 层做行级数据过滤（self / group / all）。
 *
 * <p><b>访问控制：</b>
 * <ul>
 *   <li>类级别 {@code @RequireRoles} 限制为 6 种预置角色可访问；</li>
 *   <li>导出接口额外限制为 Leader 角色（ADMIN / BIZ_LEADER / CHANNEL_LEADER）；</li>
 *   <li>重算接口仅限 ADMIN 角色。</li>
 * </ul>
 *
 * @see PerformanceQueryService 业绩查询服务
 * @see PerformanceSummaryService 业绩汇总服务
 * @see PerformanceExportService 业绩导出服务
 * @see PerformanceMonthRecalculationService 月度重算服务
 * @see PerformanceAccessContext 业绩数据访问上下文
 * @see PerformanceAccessScope 业绩访问权限判定工具
 */
@Validated
@Tag(name = "业绩域", description = "业绩查询、汇总、导出与管理员重算接口。")
@RestController
@RequestMapping("/performance")
@RequireRoles({
        RoleCodes.ADMIN,
        RoleCodes.OPS_STAFF,
        RoleCodes.BIZ_LEADER,
        RoleCodes.BIZ_STAFF,
        RoleCodes.CHANNEL_LEADER,
        RoleCodes.CHANNEL_STAFF
})
public class PerformanceController extends BaseController {

    /** 业绩查询服务：负责单笔、批量、分页业绩数据查询 */
    private final PerformanceQueryService performanceQueryService;

    /** 业绩汇总服务：负责指标卡片聚合计算（订单量、GMV、佣金等） */
    private final PerformanceSummaryService performanceSummaryService;

    /** 业绩导出服务：负责将查询结果序列化为 Excel 文件 */
    private final PerformanceExportService performanceExportService;

    /** 月度业绩重算服务：管理员可对指定月份的未结算订单重新计算业绩归属 */
    private final PerformanceMonthRecalculationService monthRecalculationService;

    /** 操作日志服务：记录敏感操作（如导出、重算）的审计日志 */
    private final OperationLogService operationLogService;

    public PerformanceController(
            PerformanceQueryService performanceQueryService,
            PerformanceSummaryService performanceSummaryService,
            PerformanceExportService performanceExportService,
            PerformanceMonthRecalculationService monthRecalculationService,
            OperationLogService operationLogService) {
        this.performanceQueryService = performanceQueryService;
        this.performanceSummaryService = performanceSummaryService;
        this.performanceExportService = performanceExportService;
        this.monthRecalculationService = monthRecalculationService;
        this.operationLogService = operationLogService;
    }

    /**
     * 单笔订单业绩查询。
     *
     * <p>处理流程：
     * <ol>
     *   <li>从请求属性中提取当前用户的访问上下文（userId / deptId / dataScope / roleCodes）；</li>
     *   <li>调用 {@link PerformanceQueryService#getByOrderId} 查询指定订单的业绩明细；</li>
     *   <li>Service 层根据数据范围进行行级权限过滤。</li>
     * </ol>
     *
     * @param orderId   订单 ID（业务订单号，非主键）
     * @param userId    当前登录用户 ID（由拦截器注入）
     * @param deptId    当前用户所属部门 ID（可选）
     * @param dataScope 数据范围枚举（ALL / GROUP / PERSONAL）
     * @param roleCodes 当前用户角色编码列表
     * @return 业绩明细 DTO
     */
    @Operation(summary = "单笔订单业绩查询")
    @GetMapping("/{orderId}")
    public ApiResult<PerformanceDetailDTO> getByOrderId(
            @PathVariable String orderId,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope,
            @RequestAttribute(value = "roleCodes", required = false) List<String> roleCodes) {
        // 第一步：组装数据访问上下文，传入 Service 层做行级过滤
        return ok(performanceQueryService.getByOrderId(
                orderId,
                PerformanceAccessContext.of(userId, deptId, dataScope, roleCodes)));
    }

    /**
     * 批量订单业绩查询。
     *
     * <p>处理流程：
     * <ol>
     *   <li>从请求体中提取订单 ID 列表，若请求体为 null 则视为空列表；</li>
     *   <li>组装当前用户的访问上下文；</li>
     *   <li>调用 {@link PerformanceQueryService#batchGet} 批量查询业绩数据；</li>
     *   <li>Service 层根据数据范围对每条记录做行级权限过滤。</li>
     * </ol>
     *
     * @param request   批量查询请求体，包含订单 ID 列表
     * @param userId    当前登录用户 ID（由拦截器注入）
     * @param deptId    当前用户所属部门 ID（可选）
     * @param dataScope 数据范围枚举（ALL / GROUP / PERSONAL）
     * @param roleCodes 当前用户角色编码列表
     * @return 批量业绩查询结果，包含命中和未命中的订单信息
     */
    @Operation(summary = "批量订单业绩查询")
    @PostMapping("/batch")
    public ApiResult<PerformanceBatchResponse> batchGet(
            @RequestBody PerformanceBatchRequest request,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope,
            @RequestAttribute(value = "roleCodes", required = false) List<String> roleCodes) {
        return ok(performanceQueryService.batchGet(
                request == null ? List.of() : request.getOrderIds(),
                PerformanceAccessContext.of(userId, deptId, dataScope, roleCodes)));
    }

    /**
     * 业绩列表分页查询。
     *
     * <p>处理流程：
     * <ol>
     *   <li>从请求参数中收集所有筛选条件（订单号、商品、达人、渠道、时间范围等）；</li>
     *   <li>通过 {@link #buildListQuery} 将参数组装为 {@link PerformanceListQuery} 查询对象；</li>
     *   <li>组装当前用户的访问上下文；</li>
     *   <li>调用 {@link PerformanceQueryService#list} 执行分页查询，Service 层自动叠加行级数据过滤。</li>
     * </ol>
     *
     * @param orderId        订单号（模糊匹配）
     * @param productId      商品 ID（精确匹配）
     * @param productName    商品名称（模糊匹配）
     * @param partnerId      合作伙伴 ID
     * @param partnerName    合作伙伴名称（模糊匹配）
     * @param activityId     活动 ID
     * @param talentId       达人 ID
     * @param channelId      渠道 ID
     * @param recruiterId    招募者 ID
     * @param orderStatus    订单状态
     * @param timeFilterType 时间筛选类型，默认 pay（付款时间）
     * @param timeStart      时间范围起始（DateTime 格式）
     * @param timeEnd        时间范围结束（DateTime 格式）
     * @param amountTrack    金额轨道，both / exclusive / normal
     * @param page           页码，从 1 开始
     * @param pageSize       每页条数，最大 100
     * @param sortBy         排序字段
     * @param sortOrder      排序方向（asc / desc）
     * @param userId         当前登录用户 ID（由拦截器注入）
     * @param deptId         当前用户所属部门 ID（可选）
     * @param dataScope      数据范围枚举（ALL / GROUP / PERSONAL）
     * @param roleCodes      当前用户角色编码列表
     * @return 分页业绩列表，包含总数和当前页数据
     */
    @Operation(summary = "业绩列表分页查询")
    @GetMapping
    public ApiResult<PerformancePageResponse> list(
            @RequestParam(required = false) String orderId,
            @RequestParam(required = false) String productId,
            @RequestParam(required = false) String productName,
            @RequestParam(required = false) Long partnerId,
            @RequestParam(required = false) String partnerName,
            @RequestParam(required = false) String activityId,
            @RequestParam(required = false) UUID talentId,
            @RequestParam(required = false) UUID channelId,
            @RequestParam(required = false) UUID recruiterId,
            @RequestParam(required = false) String orderStatus,
            @RequestParam(required = false, defaultValue = "pay") String timeFilterType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime timeStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime timeEnd,
            @RequestParam(required = false, defaultValue = "both") String amountTrack,
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) long pageSize,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope,
            @RequestAttribute(value = "roleCodes", required = false) List<String> roleCodes) {
        PerformanceListQuery query = buildListQuery(
                orderId, productId, productName, partnerId, partnerName, activityId, talentId,
                channelId, recruiterId, orderStatus, timeFilterType, timeStart, timeEnd, amountTrack,
                page, pageSize, sortBy, sortOrder);
        return ok(performanceQueryService.list(
                query,
                PerformanceAccessContext.of(userId, deptId, dataScope, roleCodes)));
    }

    /**
     * 业绩指标卡片汇总。
     *
     * <p>为前端看板提供聚合指标数据（订单量、GMV、佣金等），
     * 支持按时间范围、渠道、活动、商品等多维度筛选。
     *
     * <p>处理流程：
     * <ol>
     *   <li>从请求参数收集筛选条件，支持 LocalDateTime 和 LocalDate 两种时间格式；</li>
     *   <li>通过 {@link #resolveStart} / {@link #resolveEnd} 统一时间格式为 LocalDateTime；</li>
     *   <li>组装 {@link PerformanceSummaryQuery} 查询对象；</li>
     *   <li>调用 {@link PerformanceSummaryService#getSummary} 执行聚合计算；</li>
     *   <li>Service 层根据数据范围叠加行级数据过滤后再聚合。</li>
     * </ol>
     *
     * @param timeFilterType 时间筛选类型，默认 pay（付款时间）
     * @param timeStart      时间范围起始（DateTime 格式）
     * @param timeEnd        时间范围结束（DateTime 格式）
     * @param startDate      时间范围起始（Date 格式，与 timeStart 二选一）
     * @param endDate        时间范围结束（Date 格式，与 timeEnd 二选一）
     * @param channelId      渠道 ID
     * @param recruiterId    招募者 ID
     * @param activityId     活动 ID
     * @param productId      商品 ID
     * @param orderStatus    订单状态
     * @param partnerId      合作伙伴 ID
     * @param talentId       达人 ID
     * @param userId         当前登录用户 ID（由拦截器注入）
     * @param deptId         当前用户所属部门 ID（可选）
     * @param dataScope      数据范围枚举（ALL / GROUP / PERSONAL）
     * @param roleCodes      当前用户角色编码列表
     * @return 业绩汇总指标卡片数据
     */
    @Operation(summary = "业绩指标卡片汇总")
    @GetMapping("/summary")
    public ApiResult<PerformanceSummaryResponse> summary(
            @RequestParam(required = false, defaultValue = "pay") String timeFilterType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime timeStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime timeEnd,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) UUID channelId,
            @RequestParam(required = false) UUID recruiterId,
            @RequestParam(required = false) String activityId,
            @RequestParam(required = false) String productId,
            @RequestParam(required = false) String orderStatus,
            @RequestParam(required = false) Long partnerId,
            @RequestParam(required = false) UUID talentId,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope,
            @RequestAttribute(value = "roleCodes", required = false) List<String> roleCodes) {
        // 第一步：构建汇总查询对象，统一时间格式
        PerformanceSummaryQuery query = new PerformanceSummaryQuery();
        query.setTimeFilterType(timeFilterType);
        query.setTimeStart(resolveStart(timeStart, startDate));
        query.setTimeEnd(resolveEnd(timeEnd, endDate));
        query.setChannelId(channelId);
        query.setRecruiterId(recruiterId);
        query.setActivityId(activityId);
        query.setProductId(productId);
        query.setOrderStatus(orderStatus);
        query.setPartnerId(partnerId);
        query.setTalentId(talentId);
        return ok(performanceSummaryService.getSummary(
                query,
                PerformanceAccessContext.of(userId, deptId, dataScope, roleCodes)));
    }

    /**
     * 业绩明细导出。
     *
     * <p>将筛选条件命中的业绩明细导出为 Excel（.xlsx）文件，
     * 仅 Leader 角色（ADMIN / BIZ_LEADER / CHANNEL_LEADER）可触发。
     *
     * <p>处理流程：
     * <ol>
     *   <li>组装当前用户的访问上下文；</li>
     *   <li>通过 {@link PerformanceAccessScope#canExport} 校验导出权限，不满足则抛出 403；</li>
     *   <li>构建查询对象，page 固定为 1，pageSize 取 {@code EXPORT_MAX_ROWS} 上限；</li>
     *   <li>调用 {@link PerformanceExportService#exportXlsx} 生成 Excel 字节数组；</li>
     *   <li>设置响应头（Content-Type / Content-Disposition）并将字节流写入输出流；</li>
     *   <li>通过 {@link OperationLogService} 记录导出操作审计日志。</li>
     * </ol>
     *
     * @param orderId        订单号（模糊匹配）
     * @param productId      商品 ID
     * @param productName    商品名称（模糊匹配）
     * @param partnerId      合作伙伴 ID
     * @param partnerName    合作伙伴名称（模糊匹配）
     * @param activityId     活动 ID
     * @param talentId       达人 ID
     * @param channelId      渠道 ID
     * @param recruiterId    招募者 ID
     * @param orderStatus    订单状态
     * @param timeFilterType 时间筛选类型，默认 pay
     * @param timeStart      时间范围起始（DateTime 格式）
     * @param timeEnd        时间范围结束（DateTime 格式）
     * @param startDate      时间范围起始（Date 格式）
     * @param endDate        时间范围结束（Date 格式）
     * @param amountTrack    金额轨道
     * @param sortBy         排序字段
     * @param sortOrder      排序方向
     * @param userId         当前登录用户 ID（由拦截器注入）
     * @param deptId         当前用户所属部门 ID（可选）
     * @param dataScope      数据范围枚举（ALL / GROUP / PERSONAL）
     * @param roleCodes      当前用户角色编码列表
     * @param response       HTTP 响应对象，用于写入 Excel 文件流
     * @throws IOException 写入响应输出流失败时抛出
     */
    @Operation(summary = "业绩明细导出")
    @GetMapping("/export")
    @RequireRoles({RoleCodes.ADMIN, RoleCodes.BIZ_LEADER, RoleCodes.CHANNEL_LEADER})
    public void export(
            @RequestParam(required = false) String orderId,
            @RequestParam(required = false) String productId,
            @RequestParam(required = false) String productName,
            @RequestParam(required = false) Long partnerId,
            @RequestParam(required = false) String partnerName,
            @RequestParam(required = false) String activityId,
            @RequestParam(required = false) UUID talentId,
            @RequestParam(required = false) UUID channelId,
            @RequestParam(required = false) UUID recruiterId,
            @RequestParam(required = false) String orderStatus,
            @RequestParam(required = false, defaultValue = "pay") String timeFilterType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime timeStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime timeEnd,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false, defaultValue = "both") String amountTrack,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope,
            @RequestAttribute(value = "roleCodes", required = false) List<String> roleCodes,
            HttpServletResponse response) throws IOException {
        // 第一步：组装访问上下文并校验导出权限
        PerformanceAccessContext context = PerformanceAccessContext.of(userId, deptId, dataScope, roleCodes);
        if (!PerformanceAccessScope.canExport(context)) {
            throw BusinessException.forbidden("无权导出业绩明细");
        }
        // 第二步：构建查询，page=1，pageSize 取导出上限
        PerformanceListQuery query = buildListQuery(
                orderId, productId, productName, partnerId, partnerName, activityId, talentId,
                channelId, recruiterId, orderStatus, timeFilterType,
                resolveStart(timeStart, startDate), resolveEnd(timeEnd, endDate),
                amountTrack, 1, PerformanceQueryService.EXPORT_MAX_ROWS, sortBy, sortOrder);
        // 第三步：生成 Excel 字节数组
        byte[] bytes = performanceExportService.exportXlsx(query, context);
        // 第四步：设置响应头并写入文件流
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=\"performance-export.xlsx\"");
        response.getOutputStream().write(bytes);
        // 第五步：记录导出操作审计日志
        operationLogService.recordSystemAction(
                userId,
                "业绩域",
                "导出业绩明细",
                "GET",
                "performance_export",
                null,
                "performance-export.xlsx",
                "rows=" + (bytes.length > 0 ? "ok" : "0"));
    }

    /**
     * 重算指定月份业绩（仅未结算订单）。
     *
     * <p>管理员可对指定月份的所有未结算订单重新计算业绩归属，
     * 仅限 ADMIN 角色操作。
     *
     * <p>处理流程：
     * <ol>
     *   <li>从请求体中提取目标月份和操作原因，null 安全处理；</li>
     *   <li>调用 {@link PerformanceMonthRecalculationService#recalculateMonth} 执行重算任务；</li>
     *   <li>重算完成后，通过 {@link OperationLogService} 记录审计日志，包含 jobId、扫描数、更新数；</li>
     *   <li>返回重算结果，包含 jobId（可用于追踪异步任务状态）。</li>
     * </ol>
     *
     * @param request  重算请求体，包含月份（yyyy-MM）和操作原因
     * @param userId   当前登录用户 ID（由拦截器注入）
     * @return 重算结果，包含 jobId、扫描订单数、更新订单数
     */
    @Operation(summary = "重算指定月份业绩（仅未结算订单）")
    @PostMapping("/recalculate-month")
    @RequireRoles({RoleCodes.ADMIN})
    public ApiResult<PerformanceRecalculateMonthResponse> recalculateMonth(
            @RequestBody PerformanceRecalculateMonthRequest request,
            @RequestAttribute("userId") UUID userId) {
        // 第一步：调用月度重算服务执行重算任务
        PerformanceRecalculateMonthResponse result = monthRecalculationService.recalculateMonth(
                request == null ? null : request.getMonth(),
                request == null ? null : request.getReason());
        // 第二步：记录管理员操作审计日志
        operationLogService.recordSystemAction(
                userId,
                "业绩域",
                "重算指定月份业绩",
                "POST",
                "performance_recalculate_month",
                result.getJobId(),
                result.getMonth(),
                String.format("reason=%s, scanned=%d, upserted=%d",
                        request == null ? "" : request.getReason(),
                        result.getScanned(),
                        result.getUpserted()));
        return ok(result);
    }

    /**
     * 构建业绩列表查询对象。
     *
     * <p>将控制器层收集到的所有查询参数封装为 {@link PerformanceListQuery}，
     * 避免在多个接口方法中重复组装逻辑。
     *
     * @param orderId        订单号
     * @param productId      商品 ID
     * @param productName    商品名称
     * @param partnerId      合作伙伴 ID
     * @param partnerName    合作伙伴名称
     * @param activityId     活动 ID
     * @param talentId       达人 ID
     * @param channelId      渠道 ID
     * @param recruiterId    招募者 ID
     * @param orderStatus    订单状态
     * @param timeFilterType 时间筛选类型
     * @param timeStart      时间范围起始
     * @param timeEnd        时间范围结束
     * @param amountTrack    金额轨道
     * @param page           页码
     * @param pageSize       每页条数
     * @param sortBy         排序字段
     * @param sortOrder      排序方向
     * @return 组装好的查询对象
     */
    private PerformanceListQuery buildListQuery(
            String orderId,
            String productId,
            String productName,
            Long partnerId,
            String partnerName,
            String activityId,
            UUID talentId,
            UUID channelId,
            UUID recruiterId,
            String orderStatus,
            String timeFilterType,
            LocalDateTime timeStart,
            LocalDateTime timeEnd,
            String amountTrack,
            long page,
            long pageSize,
            String sortBy,
            String sortOrder) {
        PerformanceListQuery query = new PerformanceListQuery();
        query.setOrderId(orderId);
        query.setProductId(productId);
        query.setProductName(productName);
        query.setPartnerId(partnerId);
        query.setPartnerName(partnerName);
        query.setActivityId(activityId);
        query.setTalentId(talentId);
        query.setChannelId(channelId);
        query.setRecruiterId(recruiterId);
        query.setOrderStatus(orderStatus);
        query.setTimeFilterType(timeFilterType);
        query.setTimeStart(timeStart);
        query.setTimeEnd(timeEnd);
        query.setAmountTrack(amountTrack);
        query.setPage(page);
        query.setPageSize(pageSize);
        query.setSortBy(sortBy);
        query.setSortOrder(sortOrder);
        return query;
    }

    /**
     * 解析时间范围起始值，优先使用 LocalDateTime，回退到 LocalDate。
     *
     * <p>前端可能传 LocalDateTime（精确到秒）或 LocalDate（仅日期），
     * 此方法统一转为 LocalDateTime：LocalDate 取当天 00:00:00。
     *
     * @param timeStart  精确时间起始（优先）
     * @param startDate  日期起始（回退）
     * @return 解析后的 LocalDateTime，两者均为空时返回 null
     */
    private LocalDateTime resolveStart(LocalDateTime timeStart, LocalDate startDate) {
        if (timeStart != null) {
            return timeStart;
        }
        return startDate == null ? null : startDate.atStartOfDay();
    }

    /**
     * 解析时间范围结束值，优先使用 LocalDateTime，回退到 LocalDate。
     *
     * <p>LocalDate 回退时取次日 00:00:00（即 endDate 的下一整天起始），
     * 确保查询范围覆盖 endDate 当天所有时刻。
     *
     * @param timeEnd  精确时间结束（优先）
     * @param endDate  日期结束（回退）
     * @return 解析后的 LocalDateTime，两者均为空时返回 null
     */
    private LocalDateTime resolveEnd(LocalDateTime timeEnd, LocalDate endDate) {
        if (timeEnd != null) {
            return timeEnd;
        }
        return endDate == null ? null : endDate.plusDays(1).atStartOfDay();
    }
}
