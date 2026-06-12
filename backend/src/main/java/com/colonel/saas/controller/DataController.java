package com.colonel.saas.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.common.base.BaseController;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.common.result.PageResult;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.domain.performance.facade.OrderPerformanceQueryFacade;
import com.colonel.saas.entity.ColonelsettlementActivity;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.entity.ExclusiveMerchant;
import com.colonel.saas.entity.ExclusiveTalent;
import com.colonel.saas.mapper.ColonelsettlementActivityMapper;
import com.colonel.saas.mapper.ColonelsettlementOrderMapper;
import com.colonel.saas.mapper.ExclusiveMerchantMapper;
import com.colonel.saas.mapper.ExclusiveTalentMapper;
import com.colonel.saas.domain.user.facade.UserDomainFacade;
import com.colonel.saas.service.CommissionService;
import com.colonel.saas.service.PerformanceMetricsQueryService;
import com.colonel.saas.service.ShortTtlCacheService;
import com.colonel.saas.service.data.DataApplicationService;
import com.colonel.saas.vo.ExclusiveMerchantStatusVO;
import com.colonel.saas.vo.ExclusiveTalentStatusVO;
import com.colonel.saas.vo.data.DualTrackMetricsVO;
import com.colonel.saas.vo.data.MetricsVO;
import com.colonel.saas.vo.data.OrderDetailVO;
import com.colonel.saas.vo.data.OrderSummaryRowVO;
import com.colonel.saas.vo.data.OrderSummaryVO;
import com.colonel.saas.vo.data.OrderVO;
import com.colonel.saas.vo.data.TrendPointVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;


/**
 * 数据平台 HTTP 入口，查询、聚合与导出实现下沉到 DataApplicationService。
 */
@Validated
@Tag(name = "数据平台", description = "数据页专用接口，包括订单数据页、核心指标、导出与运营监控。")
@RestController
@RequestMapping
@RequireRoles({RoleCodes.BIZ_LEADER, RoleCodes.BIZ_STAFF, RoleCodes.CHANNEL_LEADER, RoleCodes.CHANNEL_STAFF})
public class DataController extends DataApplicationService {

    public DataController(
            ColonelsettlementOrderMapper orderMapper,
            CommissionService commissionService,
            ExclusiveTalentMapper exclusiveTalentMapper,
            ExclusiveMerchantMapper exclusiveMerchantMapper,
            ColonelsettlementActivityMapper activityMapper,
            ShortTtlCacheService shortTtlCacheService,
            PerformanceMetricsQueryService performanceMetricsQueryService,
            OrderPerformanceQueryFacade orderPerformanceQueryFacade,
            UserDomainFacade userDomainFacade,
            org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
        super(orderMapper, commissionService, exclusiveTalentMapper, exclusiveMerchantMapper, activityMapper, shortTtlCacheService, performanceMetricsQueryService, orderPerformanceQueryFacade, userDomainFacade, jdbcTemplate);
    }

    @GetMapping("/data/orders")
    @Override
    public ApiResult<PageResult<OrderVO>> getOrderPage(
            @Parameter(description = "页码，从 1 开始，最大 1000。") @RequestParam(defaultValue = "1") @Min(1) @Max(1000) long page,
            @Parameter(description = "每页条数。") @RequestParam(defaultValue = "20") @Min(1) @Max(200) long size,
            @Parameter(description = "订单号，支持模糊匹配。") @RequestParam(required = false) String orderId,
            @Parameter(description = "订单状态，支持 ORDERED、SHIPPED、FINISHED、CANCELLED。") @RequestParam(required = false) String status,
            @Parameter(description = "达人 ID（UUID），精确匹配。") @RequestParam(required = false) UUID talentId,
            @Parameter(description = "商家 merchant_id（字符串），精确匹配。") @RequestParam(required = false) String merchantId,
            @Parameter(description = "商品 ID，精确匹配。") @RequestParam(required = false) String productId,
            @Parameter(description = "商品名称/标题，模糊匹配。") @RequestParam(required = false) String productName,
            @Parameter(description = "店铺名称，模糊匹配。") @RequestParam(required = false) String shopName,
            @Parameter(description = "达人昵称，模糊匹配。") @RequestParam(required = false) String talentName,
            @Parameter(description = "团长/招商负责人名称，模糊匹配。") @RequestParam(required = false) String colonelName,
            @Parameter(description = "渠道负责人名称，模糊匹配。") @RequestParam(required = false) String channelName,
            @Parameter(description = "团长活动 ID，精确匹配。") @RequestParam(required = false) String colonelActivityId,
            @Parameter(description = "招商类型：MERCHANT（商家型招商单） 或 PROMOTION（推广单）。") @RequestParam(required = false) String recruitType,
            @Parameter(description = "开始日期，格式 yyyy-MM-dd。") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "结束日期，格式 yyyy-MM-dd。") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @Parameter(description = "时间字段：createTime（默认）或 settleTime。") @RequestParam(required = false) String timeField,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope) {
        return super.getOrderPage(page, size, orderId, status, talentId, merchantId, productId, productName, shopName, talentName, colonelName, channelName, colonelActivityId, recruitType, startDate, endDate, timeField, userId, deptId, dataScope);
    }

    @GetMapping("/data/orders/detail")
    @Override
    public ApiResult<PageResult<OrderDetailVO>> getOrderDetailPage(
            @Parameter(description = "页码，从 1 开始，最大 1000。") @RequestParam(defaultValue = "1") @Min(1) @Max(1000) long page,
            @Parameter(description = "每页条数。") @RequestParam(defaultValue = "20") @Min(1) @Max(200) long size,
            @Parameter(description = "订单号，支持模糊匹配。") @RequestParam(required = false) String orderId,
            @Parameter(description = "订单状态。") @RequestParam(required = false) String status,
            @Parameter(description = "达人 ID（UUID）。") @RequestParam(required = false) UUID talentId,
            @Parameter(description = "商家 ID。") @RequestParam(required = false) String merchantId,
            @Parameter(description = "商品 ID。") @RequestParam(required = false) String productId,
            @Parameter(description = "商品名称。") @RequestParam(required = false) String productName,
            @Parameter(description = "店铺名称。") @RequestParam(required = false) String shopName,
            @Parameter(description = "达人昵称。") @RequestParam(required = false) String talentName,
            @Parameter(description = "团长名称。") @RequestParam(required = false) String colonelName,
            @Parameter(description = "渠道名称。") @RequestParam(required = false) String channelName,
            @Parameter(description = "活动 ID。") @RequestParam(required = false) String colonelActivityId,
            @Parameter(description = "活动名称。") @RequestParam(required = false) String activityName,
            @Parameter(description = "合作方 ID。") @RequestParam(required = false) String partnerId,
            @Parameter(description = "合作方名称。") @RequestParam(required = false) String partnerName,
            @Parameter(description = "招商名称。") @RequestParam(required = false) String recruiterName,
            @Parameter(description = "招商类型。") @RequestParam(required = false) String recruitType,
            @Parameter(description = "开始日期。") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "结束日期。") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @Parameter(description = "时间字段。") @RequestParam(required = false) String timeField,
            @Parameter(description = "招商部门 ID 列表（CSV）。") @RequestParam(required = false) String recruiterDeptIds,
            @Parameter(description = "渠道部门 ID 列表（CSV）。") @RequestParam(required = false) String channelDeptIds,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope) {
        return super.getOrderDetailPage(page, size, orderId, status, talentId, merchantId, productId, productName, shopName, talentName, colonelName, channelName, colonelActivityId, activityName, partnerId, partnerName, recruiterName, recruitType, startDate, endDate, timeField, recruiterDeptIds, channelDeptIds, userId, deptId, dataScope);
    }

    @GetMapping("/data/orders/summary")
    @Override
    public ApiResult<OrderSummaryVO> getOrderSummary(
            @Parameter(description = "订单号，支持模糊匹配。") @RequestParam(required = false) String orderId,
            @Parameter(description = "订单状态，支持 ORDERED、SHIPPED、FINISHED、CANCELLED。") @RequestParam(required = false) String status,
            @Parameter(description = "达人 ID（UUID），精确匹配。") @RequestParam(required = false) UUID talentId,
            @Parameter(description = "商家 merchant_id（字符串），精确匹配。") @RequestParam(required = false) String merchantId,
            @Parameter(description = "商品 ID，精确匹配。") @RequestParam(required = false) String productId,
            @Parameter(description = "商品名称/标题，模糊匹配。") @RequestParam(required = false) String productName,
            @Parameter(description = "店铺名称，模糊匹配。") @RequestParam(required = false) String shopName,
            @Parameter(description = "达人昵称，模糊匹配。") @RequestParam(required = false) String talentName,
            @Parameter(description = "团长/招商负责人名称，模糊匹配。") @RequestParam(required = false) String colonelName,
            @Parameter(description = "渠道负责人名称，模糊匹配。") @RequestParam(required = false) String channelName,
            @Parameter(description = "团长活动 ID，精确匹配。") @RequestParam(required = false) String colonelActivityId,
            @Parameter(description = "招商类型：MERCHANT（商家型招商单） 或 PROMOTION（推广单）。") @RequestParam(required = false) String recruitType,
            @Parameter(description = "开始日期，格式 yyyy-MM-dd。") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "结束日期，格式 yyyy-MM-dd。") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @Parameter(description = "时间字段：createTime（默认）或 settleTime。") @RequestParam(required = false) String timeField,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope) {
        return super.getOrderSummary(orderId, status, talentId, merchantId, productId, productName, shopName, talentName, colonelName, channelName, colonelActivityId, recruitType, startDate, endDate, timeField, userId, deptId, dataScope);
    }

    @GetMapping("/dashboard/metrics")
    @Override
    public ApiResult<DualTrackMetricsVO> getMetrics(
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope) {
        return super.getMetrics(userId, deptId, dataScope);
    }

    @GetMapping("/orders/exports")
    @RequireRoles({RoleCodes.ADMIN, RoleCodes.BIZ_LEADER, RoleCodes.CHANNEL_LEADER})
    @Override
    public void exportOrders(
            @Parameter(description = "订单号，支持模糊匹配。") @RequestParam(required = false) String orderId,
            @Parameter(description = "订单状态，支持 ORDERED、SHIPPED、FINISHED、CANCELLED。") @RequestParam(required = false) String status,
            @Parameter(description = "达人 ID（UUID），精确匹配。") @RequestParam(required = false) UUID talentId,
            @Parameter(description = "商家 merchant_id（字符串），精确匹配。") @RequestParam(required = false) String merchantId,
            @Parameter(description = "商品 ID，精确匹配。") @RequestParam(required = false) String productId,
            @Parameter(description = "商品名称/标题，模糊匹配。") @RequestParam(required = false) String productName,
            @Parameter(description = "店铺名称，模糊匹配。") @RequestParam(required = false) String shopName,
            @Parameter(description = "达人昵称，模糊匹配。") @RequestParam(required = false) String talentName,
            @Parameter(description = "团长/招商负责人名称，模糊匹配。") @RequestParam(required = false) String colonelName,
            @Parameter(description = "渠道负责人名称，模糊匹配。") @RequestParam(required = false) String channelName,
            @Parameter(description = "团长活动 ID，精确匹配。") @RequestParam(required = false) String colonelActivityId,
            @Parameter(description = "招商类型：MERCHANT（商家型招商单） 或 PROMOTION（推广单）。") @RequestParam(required = false) String recruitType,
            @Parameter(description = "开始日期，格式 yyyy-MM-dd。") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "结束日期，格式 yyyy-MM-dd。") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @Parameter(description = "时间字段：createTime（默认）或 settleTime。") @RequestParam(required = false) String timeField,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope,
            HttpServletResponse response) throws IOException {
        super.exportOrders(orderId, status, talentId, merchantId, productId, productName, shopName, talentName, colonelName, channelName, colonelActivityId, recruitType, startDate, endDate, timeField, userId, deptId, dataScope, response);
    }

    @GetMapping("/orders/exports/detail")
    @RequireRoles({RoleCodes.ADMIN, RoleCodes.BIZ_LEADER, RoleCodes.CHANNEL_LEADER})
    @Override
    public void exportOrderDetail(
            @Parameter(description = "订单号") @RequestParam(required = false) String orderId,
            @Parameter(description = "订单状态") @RequestParam(required = false) String status,
            @Parameter(description = "达人 ID") @RequestParam(required = false) UUID talentId,
            @Parameter(description = "商家 ID") @RequestParam(required = false) String merchantId,
            @Parameter(description = "商品 ID") @RequestParam(required = false) String productId,
            @Parameter(description = "商品名称") @RequestParam(required = false) String productName,
            @Parameter(description = "店铺名称") @RequestParam(required = false) String shopName,
            @Parameter(description = "达人昵称") @RequestParam(required = false) String talentName,
            @Parameter(description = "团长名称") @RequestParam(required = false) String colonelName,
            @Parameter(description = "渠道名称") @RequestParam(required = false) String channelName,
            @Parameter(description = "活动 ID") @RequestParam(required = false) String colonelActivityId,
            @Parameter(description = "活动名称") @RequestParam(required = false) String activityName,
            @Parameter(description = "合作方 ID") @RequestParam(required = false) String partnerId,
            @Parameter(description = "合作方名称") @RequestParam(required = false) String partnerName,
            @Parameter(description = "招商名称") @RequestParam(required = false) String recruiterName,
            @Parameter(description = "招商类型") @RequestParam(required = false) String recruitType,
            @Parameter(description = "开始日期") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "结束日期") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @Parameter(description = "时间字段") @RequestParam(required = false) String timeField,
            @Parameter(description = "招商部门 ID 列表（CSV）。") @RequestParam(required = false) String recruiterDeptIds,
            @Parameter(description = "渠道部门 ID 列表（CSV）。") @RequestParam(required = false) String channelDeptIds,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope,
            HttpServletResponse response) throws IOException {
        super.exportOrderDetail(orderId, status, talentId, merchantId, productId, productName, shopName, talentName, colonelName, channelName, colonelActivityId, activityName, partnerId, partnerName, recruiterName, recruitType, startDate, endDate, timeField, recruiterDeptIds, channelDeptIds, userId, deptId, dataScope, response);
    }

    @GetMapping("/operations/exclusive-talents")
    @RequireRoles({RoleCodes.ADMIN, RoleCodes.BIZ_LEADER, RoleCodes.CHANNEL_LEADER})
    @Override
    public ApiResult<PageResult<ExclusiveTalentStatusVO>> getExclusiveTalentStatus(
            @Parameter(description = "页码，从 1 开始。") @RequestParam(defaultValue = "1") @Min(1) long page,
            @Parameter(description = "每页条数。") @RequestParam(defaultValue = "10") @Min(1) @Max(200) long size,
            @Parameter(description = "生效月份，格式 yyyy-MM。") @RequestParam(required = false) String effectiveMonth,
            @Parameter(description = "达人 UID 关键字，模糊匹配。") @RequestParam(required = false) String keyword,
            @Parameter(description = "状态：1=活跃，0=已过期。") @RequestParam(required = false) Integer status,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope) {
        return super.getExclusiveTalentStatus(page, size, effectiveMonth, keyword, status, userId, deptId, dataScope);
    }

    @GetMapping("/operations/exclusive-merchants")
    @RequireRoles({RoleCodes.ADMIN, RoleCodes.BIZ_LEADER, RoleCodes.CHANNEL_LEADER})
    @Override
    public ApiResult<PageResult<ExclusiveMerchantStatusVO>> getExclusiveMerchantStatus(
            @Parameter(description = "页码，从 1 开始。") @RequestParam(defaultValue = "1") @Min(1) long page,
            @Parameter(description = "每页条数。") @RequestParam(defaultValue = "10") @Min(1) @Max(200) long size,
            @Parameter(description = "生效月份，格式 yyyy-MM。") @RequestParam(required = false) String effectiveMonth,
            @Parameter(description = "商家名称关键字，模糊匹配。") @RequestParam(required = false) String keyword,
            @Parameter(description = "状态：1=活跃，0=已过期。") @RequestParam(required = false) Integer status,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope) {
        return super.getExclusiveMerchantStatus(page, size, effectiveMonth, keyword, status, userId, deptId, dataScope);
    }

    @GetMapping("/activities/exports")
    @RequireRoles({RoleCodes.ADMIN, RoleCodes.BIZ_LEADER, RoleCodes.CHANNEL_LEADER})
    @Override
    public void exportActivities(
            @Parameter(description = "活动名称关键字。") @RequestParam(required = false) String activityName,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope,
            HttpServletResponse response) throws IOException {
        super.exportActivities(activityName, userId, deptId, dataScope, response);
    }
}
