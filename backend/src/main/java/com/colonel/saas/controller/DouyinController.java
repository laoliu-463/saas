package com.colonel.saas.controller;

import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.common.base.BaseController;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.douyin.DouyinApiException;
import com.colonel.saas.douyin.DouyinTokenService;
import com.colonel.saas.gateway.douyin.DouyinActivityGateway;
import com.colonel.saas.gateway.douyin.DouyinOrderGateway;
import com.colonel.saas.gateway.douyin.DouyinProductGateway;
import com.colonel.saas.gateway.douyin.DouyinPromotionGateway;
import com.colonel.saas.gateway.douyin.DouyinTokenGateway;
import com.colonel.saas.service.DouyinWebhookEventService;
import com.colonel.saas.domain.order.infrastructure.OrderSyncPersistenceService;
import com.colonel.saas.service.settlement.SettlementOrderGateway;
import com.colonel.saas.service.settlement.SettlementOrderPage;
import com.colonel.saas.service.settlement.SettlementOrderQuery;
import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.validation.annotation.Validated;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 抖音联调控制器.
 *
 * <p>抖音开放平台联调与调试专用控制器，提供活动管理、订单查询、Token 管理、
 * 推广链接探针、Webhook 事件重放等 14 个端点，用于验证上游 SDK 链路可用性。</p>
 *
 * <p>API 路径前缀：{@code /douyin}</p>
 *
 * <p>访问权限：仅管理员。所有端点均标注 {@code @SecurityRequirement(name = "bearerAuth")}，
 * 需通过 Bearer Token 认证后方可访问。</p>
 *
 * <p>本控制器为联调调试用途，端点返回结构包含上游原始响应，不适合直接用于生产前端。</p>
 *
 * @see DouyinActivityGateway
 * @see DouyinProductGateway
 * @see DouyinOrderGateway
 * @see DouyinPromotionGateway
 * @see DouyinTokenGateway
 * @see DouyinTokenService
 * @see DouyinWebhookEventService
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/douyin")
@RequireRoles({RoleCodes.ADMIN})
@Tag(name = "抖音联调")
@SecurityRequirement(name = "bearerAuth")
public class DouyinController extends BaseController {

    /** 抖音活动网关，负责活动 CRUD 与商品取消操作 */
    private final DouyinActivityGateway douyinActivityGateway;
    /** 抖音商品网关，负责活动商品查询与 SKU 查询 */
    private final DouyinProductGateway douyinProductGateway;
    /** 抖音订单网关，负责结算订单查询 */
    private final DouyinOrderGateway douyinOrderGateway;
    /** 1603 结算口径网关，负责默认结算 probe */
    private final SettlementOrderGateway instituteSettlementGateway;
    /** 抖音推广网关，负责推广链接转换与 RAW 探针 */
    private final DouyinPromotionGateway douyinPromotionGateway;
    /** 抖音 Token 网关，负责机构信息查询与 Token 创建探针 */
    private final DouyinTokenGateway douyinTokenGateway;
    /** 抖音 Token 服务，负责 Token 缓存管理、刷新与初始化 */
    private final DouyinTokenService douyinTokenService;
    /** Webhook 事件服务，负责事件重放与补偿消费 */
    private final DouyinWebhookEventService douyinWebhookEventService;

    /**
     * 构造注入.
     *
     * @param douyinActivityGateway   抖音活动网关
     * @param douyinProductGateway    抖音商品网关
     * @param douyinOrderGateway      抖音订单网关
     * @param douyinPromotionGateway  抖音推广网关
     * @param douyinTokenGateway      抖音 Token 网关
     * @param douyinTokenService      抖音 Token 服务
     * @param douyinWebhookEventService Webhook 事件服务
     */
    public DouyinController(
            DouyinActivityGateway douyinActivityGateway,
            DouyinProductGateway douyinProductGateway,
            DouyinOrderGateway douyinOrderGateway,
            @Qualifier("instituteOrderColonelSettlementGateway") SettlementOrderGateway instituteSettlementGateway,
            DouyinPromotionGateway douyinPromotionGateway,
            DouyinTokenGateway douyinTokenGateway,
            DouyinTokenService douyinTokenService,
            DouyinWebhookEventService douyinWebhookEventService) {
        this.douyinActivityGateway = douyinActivityGateway;
        this.douyinProductGateway = douyinProductGateway;
        this.douyinOrderGateway = douyinOrderGateway;
        this.instituteSettlementGateway = instituteSettlementGateway;
        this.douyinPromotionGateway = douyinPromotionGateway;
        this.douyinTokenGateway = douyinTokenGateway;
        this.douyinTokenService = douyinTokenService;
        this.douyinWebhookEventService = douyinWebhookEventService;
    }

    /**
     * 重放未完成 Webhook 事件.
     *
     * <p>扫描 Webhook 收件箱中状态为 RECEIVED 或 FAILED 的事件，
     * 重新投递到事件消费链路。用于本地消费补偿与 real-pre 环境排障。</p>
     *
     * @param limit 单次最大重放数量，默认 20，服务层上限 100
     * @return 重放结果，包含 scanned / consumed / failed 计数
     */
    @Operation(summary = "[联调] 重放未完成 Webhook 事件", description = "重放 Webhook 收件箱中 RECEIVED / FAILED 状态的事件，用于本地消费补偿与 real-pre 排障。")
    @PostMapping("/webhook-events/replay")
    public ApiResult<Map<String, Object>> replayWebhookEvents(
            @Parameter(description = "单次最多重放数量，默认 20，服务层上限 100。") @RequestParam(name = "limit", defaultValue = "20") int limit) {
        DouyinWebhookEventService.ReplayResult result = douyinWebhookEventService.replayUnfinished(limit);
        Map<String, Object> data = new HashMap<>();
        data.put("scanned", result.scanned());
        data.put("consumed", result.consumed());
        data.put("failed", result.failed());
        return ok(data);
    }

    /**
     * 联调探针：查询团长活动列表.
     *
     * <p>验证上游 {@code alliance.instituteColonelActivityList} 能力是否可用。
     * 使用默认参数调用上游接口，返回原始响应结构，用于检查 SDK 链路连通性。</p>
     *
     * @param appId 抖音应用 appId（可选），不传则使用系统默认配置
     * @return 联调探针结果，包含 upstream 原始响应或错误信息
     */
    @Operation(summary = "[联调] 查询活动列表", description = "验证上游 alliance.instituteColonelActivityList 能力是否可用，检查当前 appId 下团长活动列表查询链路。")
    @GetMapping("/activities")
    public ApiResult<Map<String, Object>> huodongLiebiao(
            @Parameter(description = "抖音应用 appId；不传则使用系统默认应用配置。") @RequestParam(name = "appId", required = false) String appId) {
        Map<String, Object> result = new HashMap<>();
        result.put("module", "M1.2 Douyin SDK");
        result.put("endpoint", "alliance.instituteColonelActivityList");
        result.put("appId", appId);
        try {
            DouyinActivityGateway.ActivityListResult gatewayResult = douyinActivityGateway.listActivities(
                    new DouyinActivityGateway.ActivityListQuery(appId, 0, 0L, 1L, 1L, 20L, null)
            );
            result.put("remoteResponse", gatewayResult.toMap());
            result.put("status", "success");
        } catch (DouyinApiException | BusinessException | IllegalArgumentException | IllegalStateException e) {
            log.error("Douyin activity test call failed", e);
            fillError(result, e);
        }
        return ok(result);
    }

    /**
     * 联调探针：查询活动详情.
     *
     * <p>验证上游 {@code buyin.colonelActivityDetail} 能力是否可用。
     * 按活动 ID 查询指定活动的详细信息，返回原始响应结构。</p>
     *
     * @param appId      抖音应用 appId（可选）
     * @param activityId 团长活动 ID
     * @return 联调探针结果，包含活动详情原始响应或错误信息
     */
    @Operation(summary = "[联调] 查询活动详情", description = "验证上游 buyin.colonelActivityDetail 能力是否可用，检查指定活动详情查询链路。")
    @GetMapping("/activities/{activityId}")
    public ApiResult<Map<String, Object>> huodongXiangqing(
            @Parameter(description = "抖音应用 appId；不传则使用系统默认应用配置。") @RequestParam(name = "appId", required = false) String appId,
            @Parameter(description = "团长活动 ID。") @PathVariable("activityId") String activityId) {
        Map<String, Object> result = new HashMap<>();
        result.put("module", "M1.2 Douyin SDK");
        result.put("endpoint", "buyin.colonelActivityDetail");
        result.put("appId", appId);
        result.put("activityId", activityId);
        try {
            result.put("remoteResponse", douyinActivityGateway.activityDetail(appId, activityId));
            result.put("status", "success");
        } catch (DouyinApiException | BusinessException | IllegalArgumentException | IllegalStateException e) {
            log.error("Douyin activity detail call failed", e);
            fillError(result, e);
        }
        return ok(result);
    }

    /**
     * 联调探针：查询活动商品活动列表.
     *
     * <p>验证上游 {@code alliance.instituteColonelActivityList} 活动商品查询链路。
     * 支持按活动状态、搜索类型、排序类型等参数进行筛选。
     * 该接口保留 pageSize 命名以对齐上游 SDK 参数。</p>
     *
     * @param appId        抖音应用 appId（可选）
     * @param status       活动状态（可选）
     * @param searchType   搜索类型（可选）
     * @param sortType     排序类型（可选）
     * @param page         页码（可选）
     * @param pageSize     每页条数（可选）
     * @param activityInfo 活动信息关键字（可选）
     * @return 联调探针结果
     */
    @Operation(summary = "[联调] 查询活动商品活动列表", description = "验证上游 alliance.instituteColonelActivityList 活动商品查询链路。该接口保留 pageSize 命名以对齐上游 SDK 参数。")
    @GetMapping("/activity-products")
    public ApiResult<Map<String, Object>> huodongShangpin(
            @Parameter(description = "抖音应用 appId；不传则使用系统默认应用配置。") @RequestParam(name = "appId", required = false) String appId,
            @Parameter(description = "活动状态。待确认：取值含义请联系产品或上游 SDK 文档。") @RequestParam(name = "status", required = false) Integer status,
            @Parameter(description = "搜索类型。待确认：取值含义请联系产品或上游 SDK 文档。") @RequestParam(name = "searchType", required = false) Long searchType,
            @Parameter(description = "排序类型。待确认：取值含义请联系产品或上游 SDK 文档。") @RequestParam(name = "sortType", required = false) Long sortType,
            @Parameter(description = "页码，从 1 开始。") @RequestParam(name = "page", required = false) Long page,
            @Parameter(description = "每页条数。联调接口保留 pageSize 命名以对齐上游 SDK。") @RequestParam(name = "pageSize", required = false) Long pageSize,
            @Parameter(description = "活动信息关键字，用于按活动名称或信息筛选。") @RequestParam(name = "activityInfo", required = false) String activityInfo) {
        Map<String, Object> result = new HashMap<>();
        result.put("module", "M1.2 Douyin SDK");
        result.put("endpoint", "alliance.instituteColonelActivityList");
        result.put("appId", appId);
        try {
            DouyinActivityGateway.ActivityListResult gatewayResult = douyinActivityGateway.listActivities(
                    new DouyinActivityGateway.ActivityListQuery(appId, status, searchType, sortType, page, pageSize, activityInfo)
            );
            result.put("remoteResponse", gatewayResult.toMap());
            result.put("status", "success");
        } catch (DouyinApiException | BusinessException | IllegalArgumentException | IllegalStateException e) {
            log.error("Douyin product activities test call failed", e);
            fillError(result, e);
        }
        return ok(result);
    }

    /**
     * 联调探针：查询活动商品列表.
     *
     * <p>验证上游 {@code alliance.colonelActivityProduct} 按活动查询商品的能力。
     * 使用游标分页模式拉取商品列表，主要用于联调上游返回结构，
     * 不等同于本地商品主链路接口。</p>
     *
     * @param appId      抖音应用 appId（可选）
     * @param activityId 团长活动 ID
     * @param count      每次拉取商品数量（可选）
     * @param cursor     游标，继续翻页时使用（可选）
     * @return 联调探针结果，包含活动商品列表原始响应
     */
    @Operation(summary = "[联调] 查询活动商品列表", description = "验证上游 alliance.colonelActivityProduct 按活动查询商品的能力。该接口主要用于联调上游返回结构，不等同于本地商品主链路接口。")
    @GetMapping("/activity-product-list")
    public ApiResult<Map<String, Object>> shangpinLiebiao(
            @Parameter(description = "抖音应用 appId；不传则使用系统默认应用配置。") @RequestParam(name = "appId", required = false) String appId,
            @Parameter(description = "团长活动 ID。") @RequestParam(name = "activityId") String activityId,
            @Parameter(description = "每次拉取商品数量。") @RequestParam(name = "count", required = false) Integer count,
            @Parameter(description = "游标，继续翻页时使用。") @RequestParam(name = "cursor", required = false) String cursor) {
        Map<String, Object> result = new HashMap<>();
        result.put("module", "M1.2 Douyin SDK");
        result.put("endpoint", "alliance.colonelActivityProduct");
        result.put("appId", appId);
        result.put("activityId", activityId);
        try {
            DouyinProductGateway.ActivityProductListResult gatewayResult = douyinProductGateway.queryActivityProducts(
                    new DouyinProductGateway.ActivityProductQueryRequest(
                            appId,
                            activityId,
                            4L,
                            1L,
                            count,
                            null,
                            null,
                            null,
                            null,
                            1L,
                            cursor,
                            null
                    )
            );
            result.put("remoteResponse", gatewayResult.toMap());
            result.put("status", "success");
        } catch (DouyinApiException | BusinessException | IllegalArgumentException | IllegalStateException e) {
            log.error("Douyin products by activity test call failed", e);
            fillError(result, e);
        }
        return ok(result);
    }

    /**
     * 联调探针：1603 查询团长订单（结算口径）.
     *
     * <p>验证上游 {@code buyin.instituteOrderColonel} 作为结算订单主接口的只读查询能力。
     * {@code buyin.colonelMultiSettlementOrders} 仅作为 fallback / probe / 对照，不再作为默认写库主链路。</p>
     *
     * @param appId          抖音应用 appId（可选）
     * @param size           每次拉取条数，最大 100
     * @param cursor         游标，继续翻页时使用
     * @param timeType       时间类型，如 update
     * @param startTime      开始时间，格式 yyyy-MM-dd HH:mm:ss（可选）
     * @param endTime        结束时间，格式 yyyy-MM-dd HH:mm:ss（可选）
     * @param orderIds       订单号列表，逗号分隔（可选，优先使用 camelCase 入参）
     * @param orderIdsLegacy 订单号列表（snake_case 兼容参数）
     * @return 联调探针结果，包含 1603 结算口径原始响应
     */
    @Operation(summary = "[联调] 1603 查询团长订单（结算口径）", description = "验证上游 buyin.instituteOrderColonel 结算口径。2704 buyin.colonelMultiSettlementOrders 仅作为 fallback / probe / 对照。")
    @GetMapping("/order-settlements")
    public ApiResult<Map<String, Object>> dingdanJiesuan(
            @Parameter(description = "抖音应用 appId；不传则使用系统默认应用配置。") @RequestParam(name = "appId", required = false) String appId,
            @Parameter(description = "每次拉取条数，最大 100。") @RequestParam(name = "size", required = false, defaultValue = "20") @Min(1) @Max(100) Integer size,
            @Parameter(description = "游标，继续翻页时使用。") @RequestParam(name = "cursor", required = false, defaultValue = "0") String cursor,
            @Parameter(description = "时间类型，默认 update。") @RequestParam(name = "timeType", required = false, defaultValue = "update") String timeType,
            @Parameter(description = "开始时间，格式 yyyy-MM-dd HH:mm:ss。") @RequestParam(name = "startTime", required = false) String startTime,
            @Parameter(description = "结束时间，格式 yyyy-MM-dd HH:mm:ss。") @RequestParam(name = "endTime", required = false) String endTime,
            @Parameter(description = "订单号列表，逗号分隔，最多 100 个；与时间范围二选一，优先使用 camelCase 入参。") @RequestParam(name = "orderIds", required = false) String orderIds,
            @RequestParam(name = "order_ids", required = false) String orderIdsLegacy) {
        Map<String, Object> result = new HashMap<>();
        result.put("module", "M1.2 Douyin SDK");
        result.put("endpoint", "buyin.instituteOrderColonel");
        result.put("source", OrderSyncPersistenceService.SYNC_SOURCE_INSTITUTE_SETTLEMENT);
        result.put("fallbackEndpoint", "buyin.colonelMultiSettlementOrders");
        result.put("appId", appId);
        String normalizedOrderIds = StringUtils.hasText(orderIds) ? orderIds : orderIdsLegacy;
        List<String> orderIdList = StringUtils.hasText(normalizedOrderIds)
                ? java.util.Arrays.stream(normalizedOrderIds.split(","))
                        .map(String::trim)
                        .filter(StringUtils::hasText)
                        .toList()
                : List.of();
        Map<String, Object> query = new HashMap<>();
        query.put("size", size);
        query.put("cursor", cursor);
        query.put("timeType", timeType);
        query.put("startTime", startTime);
        query.put("endTime", endTime);
        query.put("orderIds", normalizedOrderIds);
        result.put("query", query);
        try {
            SettlementOrderPage page = instituteSettlementGateway.fetch(new SettlementOrderQuery(
                    startTime,
                    endTime,
                    timeType,
                    size == null ? 20 : size,
                    cursor,
                    orderIdList,
                    1,
                    size == null ? 20 : size,
                    false
            ));
            result.put("apiMethod", page.apiMethod());
            result.put("fetched", page.orders() == null ? 0 : page.orders().size());
            result.put("hasMore", page.hasMore());
            result.put("nextCursor", page.nextCursor());
            result.put("remoteResponse", page.rawResponse());
            if (!orderIdList.isEmpty()) {
                result.put("warning", "order_ids_ignored_by_1603_unless_upstream_support_is_confirmed");
            }
            result.put("status", "success");
        } catch (DouyinApiException | BusinessException | IllegalArgumentException | IllegalStateException e) {
            log.error("Douyin 1603 settlement probe failed", e);
            fillError(result, e);
        }
        return ok(result);
    }

    @Operation(summary = "[联调] 取消活动商品", description = "验证上游 alliance.colonelActivityProductCancel 终止合作能力。")
    @PostMapping("/activity-product-cancellations")
    public ApiResult<Map<String, Object>> quxiaoHuodongShangpin(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "活动商品取消请求体。",
                    required = true,
                    content = @Content(examples = @ExampleObject(value = "{\"appId\":\"test-app\",\"activityId\":123,\"productIds\":[\"1001\"],\"reason\":\"终止合作\"}"))
            )
            @Valid @RequestBody ActivityProductCancelRequest request) {
        Map<String, Object> result = new HashMap<>();
        result.put("module", "M1.2 Douyin SDK");
        result.put("endpoint", "alliance.colonelActivityProductCancel");
        result.put("appId", request.getAppId());
        try {
            Map<String, Object> payload = request.toPayload();
            result.put("payload", payload);
            result.put("remoteResponse", douyinActivityGateway.cancelActivityProduct(request.getAppId(), payload));
            result.put("status", "success");
        } catch (DouyinApiException | BusinessException | IllegalArgumentException | IllegalStateException e) {
            log.error("Douyin activity product cancel call failed", e);
            fillError(result, e);
        }
        return ok(result);
    }

    @Operation(summary = "[联调] 取消活动商品（原始请求）", description = "使用原始 JSON 直接调用 alliance.colonelActivityProductCancel，便于联调阶段快速验证不同 payload。")
    @PostMapping("/activity-product-cancellations/raw")
    public ApiResult<Map<String, Object>> quxiaoHuodongShangpinYuanshi(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "原始取消请求体，需包含 appId，其他字段按上游接口要求传入。",
                    required = true,
                    content = @Content(examples = @ExampleObject(value = "{\"appId\":\"test-app\",\"activity_id\":123,\"product_ids\":[\"1001\"]}"))
            )
            @RequestBody Map<String, Object> request) {
        Map<String, Object> result = new HashMap<>();
        result.put("module", "M1.2 Douyin SDK");
        result.put("endpoint", "alliance.colonelActivityProductCancel");
        try {
            if (request == null || request.isEmpty()) {
                throw new IllegalArgumentException("request body is required");
            }
            Map<String, Object> payload = new HashMap<>(request);
            Object appIdValue = payload.remove("appId");
            String appId = appIdValue == null ? null : String.valueOf(appIdValue).trim();
            result.put("appId", appId);
            result.put("payload", payload);
            result.put("remoteResponse", douyinActivityGateway.cancelActivityProduct(appId, payload));
            result.put("status", "success");
        } catch (DouyinApiException | BusinessException | IllegalArgumentException | IllegalStateException e) {
            log.error("Douyin activity product cancel raw call failed", e);
            fillError(result, e);
        }
        return ok(result);
    }

    @Operation(summary = "[联调] 推广链接 RAW 探针", description = "使用原始 JSON 直接调用指定推广相关上游方法，便于核对 instPickSourceConvert / kolProductShare / getProductShareMaterial 的真实返回结构。")
    @RequireRoles({RoleCodes.ADMIN})
    @PostMapping("/promotion-link-probes/raw")
    public ApiResult<Map<String, Object>> tuiguangLianjieYuanshi(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "原始推广探针请求体，需包含 appId、method，其他字段按上游接口要求透传。",
                    required = true,
                    content = @Content(examples = @ExampleObject(value = "{\"appId\":\"test-app\",\"method\":\"buyin.instPickSourceConvert\",\"product_url\":\"https://haohuo.jinritemai.com/ecommerce/trade/detail/index.html?id=1\",\"pick_extra\":\"channel_demo\"}"))
            )
            @RequestBody Map<String, Object> request) {
        Map<String, Object> result = new HashMap<>();
        result.put("module", "M1.3 Promotion Raw Probe");
        try {
            if (request == null || request.isEmpty()) {
                throw new IllegalArgumentException("request body is required");
            }
            Map<String, Object> payload = new HashMap<>(request);
            String method = asTrimmedText(payload.remove("method"));
            String appId = asTrimmedText(payload.remove("appId"));
            if (!StringUtils.hasText(method)) {
                throw new IllegalArgumentException("method is required");
            }
            result.put("endpoint", method);
            result.put("appId", appId);
            result.put("payload", payload);
            if ("buyin.productSkus.v2".equals(method)) {
                String productId = asTrimmedText(payload.get("product_id"));
                if (!StringUtils.hasText(productId)) {
                    productId = asTrimmedText(payload.get("productId"));
                }
                if (!StringUtils.hasText(productId)) {
                    throw new IllegalArgumentException("product_id is required for buyin.productSkus.v2");
                }
                result.put("remoteResponse", Map.of(
                        "data", Map.of("skus", douyinProductGateway.queryProductSkus(productId))
                ));
                result.put("status", "success");
            } else {
                result.put("remoteResponse", douyinPromotionGateway.rawUpstreamPost(appId, method, payload));
                result.put("status", "success");
            }
        } catch (DouyinApiException | BusinessException | IllegalArgumentException | IllegalStateException e) {
            log.error("Douyin promotion raw probe failed", e);
            fillError(result, e);
        }
        return ok(result);
    }

    @Operation(summary = "[联调] 订单同步 RAW 探针", description = "使用原始 JSON 直接调用 buyin.instituteOrderColonel，便于联调阶段先拿真实订单原始返回，再补本地映射。")
    @RequireRoles({RoleCodes.ADMIN})
    @PostMapping("/order-sync-probes/raw")
    public ApiResult<Map<String, Object>> dingdanTongbuYuanshi(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "原始订单同步探针请求体，需包含 appId，其他字段按上游接口要求透传。",
                    required = true,
                    content = @Content(examples = @ExampleObject(value = "{\"appId\":\"test-app\",\"start_time\":1711900800,\"end_time\":1711987200,\"page\":1,\"count\":20}"))
            )
            @RequestBody Map<String, Object> request) {
        Map<String, Object> result = new HashMap<>();
        result.put("module", "M1.4 Order Raw Probe");
        result.put("endpoint", "buyin.instituteOrderColonel");
        try {
            if (request == null || request.isEmpty()) {
                throw new IllegalArgumentException("request body is required");
            }
            String appId = asTrimmedText(request.get("appId"));
            Object startRaw = request.get("start_time");
            Object endRaw = request.get("end_time");
            String cursor = asTrimmedText(request.get("cursor"));
            int count = request.get("count") instanceof Number number ? number.intValue() : 20;
            result.put("appId", appId);
            result.put("payload", request);
            result.put("remoteResponse", douyinOrderGateway.listInstituteOrders(
                    new DouyinOrderGateway.DouyinOrderQueryRequest(
                            parseFlexibleEpoch(startRaw, "start_time"),
                            parseFlexibleEpoch(endRaw, "end_time"),
                            count,
                            cursor
                    )
            ).rawResponse());
            result.put("status", "success");
        } catch (DouyinApiException | BusinessException | IllegalArgumentException | IllegalStateException e) {
            log.error("Douyin order sync raw probe failed", e);
            fillError(result, e);
        }
        return ok(result);
    }

    @Operation(summary = "[联调] 创建活动", description = "验证上游 alliance.colonelActivityCreateOrUpdate 创建团长活动能力。")
    @RequireRoles({RoleCodes.ADMIN})
    @PostMapping("/activities")
    public ApiResult<Map<String, Object>> chuangjianHuodong(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "创建或更新团长活动请求体。",
                    required = true,
                    content = @Content(examples = @ExampleObject(value = "{\"appId\":\"test-app\",\"applicationLimited\":false,\"activityName\":\"测试活动\",\"activityDesc\":\"联调用活动\",\"applyStartTime\":\"2026-04-28 10:00:00\",\"applyEndTime\":\"2026-04-29 10:00:00\",\"commissionRate\":\"10\",\"serviceRate\":\"5\",\"estimatedSingleSale\":\"1000\",\"activityType\":1,\"online\":true}"))
            )
            @Valid @RequestBody ActivityCreateOrUpdateRequest request) {
        return ok(douyinActivityGateway.createOrUpdateActivity(buildActivityMutateCommand(request, request.getActivityId())));
    }

    @Operation(summary = "[联调] 更新活动", description = "验证上游 alliance.colonelActivityCreateOrUpdate 更新团长活动能力。")
    @RequireRoles({RoleCodes.ADMIN})
    @PutMapping("/activities/{activityId}")
    public ApiResult<Map<String, Object>> gengxinHuodong(
            @Parameter(description = "团长活动 ID。") @PathVariable("activityId") Long activityId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "更新团长活动请求体。",
                    required = true,
                    content = @Content(examples = @ExampleObject(value = "{\"appId\":\"test-app\",\"applicationLimited\":false,\"activityName\":\"测试活动-更新\",\"activityDesc\":\"联调用活动\",\"applyStartTime\":\"2026-04-28 10:00:00\",\"applyEndTime\":\"2026-04-29 10:00:00\",\"commissionRate\":\"10\",\"serviceRate\":\"5\",\"estimatedSingleSale\":\"1000\",\"activityType\":1,\"online\":true}"))
            )
            @Valid @RequestBody ActivityCreateOrUpdateRequest request) {
        return ok(douyinActivityGateway.createOrUpdateActivity(buildActivityMutateCommand(request, activityId)));
    }

    @Operation(summary = "[联调] 查询 Token 状态", description = "查看当前 appId 的 Token 缓存状态，用于确认真实联调前授权是否准备完毕。")
    @RequireRoles({RoleCodes.ADMIN})
    @GetMapping("/tokens")
    public ApiResult<DouyinTokenService.TokenStatus> tokenZhuangtai(
            @Parameter(description = "抖音应用 appId；不传则使用系统默认应用配置。") @RequestParam(name = "appId", required = false) String appId) {
        return ok(douyinTokenService.getTokenStatus(appId));
    }

    @Operation(summary = "[联调] 刷新 Token", description = "使用缓存中的 refresh_token 刷新 Token，用于验证 Token 刷新链路。")
    @RequireRoles({RoleCodes.ADMIN})
    @PostMapping("/token-refreshes")
    public ApiResult<DouyinTokenService.TokenStatus> tokenShuaxin(
            @Parameter(description = "抖音应用 appId；不传则使用系统默认应用配置。") @RequestParam(name = "appId", required = false) String appId) {
        douyinTokenService.refreshToken(appId);
        return ok(douyinTokenService.getTokenStatus(appId));
    }

    @Operation(summary = "[联调] 查询授权机构身份", description = "调用 buyin.institutionInfo 验证当前 Token 对应的授权主体、百应身份与角色信息。")
    @RequireRoles({RoleCodes.ADMIN})
    @GetMapping("/institution-info")
    public ApiResult<Map<String, Object>> jigouShenfen(
            @Parameter(description = "抖音应用 appId；不传则使用系统默认应用配置。") @RequestParam(name = "appId", required = false) String appId) {
        Map<String, Object> result = new HashMap<>();
        result.put("module", "M1.2 Douyin SDK");
        result.put("endpoint", "buyin.institutionInfo");
        result.put("appId", appId);
        try {
            result.put("remoteResponse", douyinTokenGateway.institutionInfo(appId));
            result.put("status", "success");
        } catch (DouyinApiException | BusinessException | IllegalArgumentException | IllegalStateException e) {
            log.error("Douyin institution info call failed", e);
            fillError(result, e);
        }
        return ok(result);
    }

    @Operation(summary = "[联调] 初始化 Token", description = "使用 authorization code 或 refresh_token 初始化 Token，适用于真实联调前的授权准备。联调接口，仅管理员可操作。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token 初始化成功")
    })
    @PostMapping("/tokens")
    public ApiResult<DouyinTokenService.TokenStatus> tokenChuangjian(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Token 初始化请求体。",
                    required = true,
                    content = @Content(examples = @ExampleObject(value = "{\"appId\":\"test-app\",\"code\":\"达人或机构授权code\",\"grantType\":\"authorization_code\"}"))
            )
            @Valid @RequestBody TokenCreateRequest request) {
        douyinTokenService.exchangeCodeAndBootstrap(
                request.getAppId(),
                request.getCode(),
                request.getGrantType(),
                request.getTestShop(),
                request.getShopId(),
                request.getAuthId(),
                request.getAuthSubjectType()
        );
        return ok(douyinTokenService.getTokenStatus(request.getAppId()));
    }

    @Operation(summary = "[联调] TokenCreate SDK 裸调探针", description = "直接调用抖店 SDK 的 token.create，不写入 Redis，不走业务缓存。仅返回脱敏后的请求快照与上游原始响应摘要，用于平台提单取证。")
    @RequireRoles({RoleCodes.ADMIN})
    @PostMapping("/token-create-probes")
    public ApiResult<Map<String, Object>> tokenCreateProbe(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "TokenCreateRequest 探针请求体。",
                    required = true,
                    content = @Content(examples = @ExampleObject(value = "{\"appId\":\"7623665273727387199\",\"code\":\"达人或机构授权code\",\"grantType\":\"authorization_code\"}"))
            )
            @Valid @RequestBody TokenCreateRequest request) {
        DouyinTokenGateway.ProbeTokenCreateResult probe = douyinTokenGateway.probeCreateToken(
                new DouyinTokenGateway.TokenCreateCommand(
                        request.getCode(),
                        request.getGrantType(),
                        request.getTestShop(),
                        request.getShopId(),
                        request.getAuthId(),
                        request.getAuthSubjectType()
                )
        );
        Map<String, Object> result = new HashMap<>();
        Map<String, Object> requestSnapshot = new HashMap<>();
        requestSnapshot.put("grantType", probe.grantType());
        requestSnapshot.put("codeState", probe.codeState());
        requestSnapshot.put("testShop", probe.testShop());
        requestSnapshot.put("shopId", probe.shopId());
        requestSnapshot.put("authIdPresent", probe.authIdPresent());
        requestSnapshot.put("authSubjectType", probe.authSubjectType());
        result.put("module", "M1.3 Real SDK Probe");
        result.put("endpoint", "token.create");
        result.put("appId", request.getAppId());
        result.put("status", "completed");
        result.put("requestSnapshot", requestSnapshot);
        result.put("response", probe.response());
        return ok(result);
    }

    private DouyinActivityGateway.ActivityMutateCommand buildActivityMutateCommand(
            ActivityCreateOrUpdateRequest request,
            Long activityId) {
        return new DouyinActivityGateway.ActivityMutateCommand(
                request.getAppId(),
                activityId,
                request.getApplicationLimited(),
                request.getIsNewShop(),
                request.getShopType(),
                request.getActivityName(),
                request.getActivityDesc(),
                request.getApplyStartTime(),
                request.getApplyEndTime(),
                request.getCommissionRate(),
                request.getServiceRate(),
                request.getWechatId(),
                request.getPhoneNum(),
                request.getEstimatedSingleSale(),
                request.getActivityType(),
                request.getSpecifiedShopIds(),
                request.getOnline(),
                request.getCategories(),
                request.getShopScore(),
                request.getMinPromotionDays(),
                request.getThresholdCrossBorder(),
                request.getMinExclusionDuration(),
                request.getAdCommissionRate(),
                request.getAdServiceRate(),
                request.getCosLimitType()
        );
    }

    private void fillError(Map<String, Object> result, Throwable e) {
        result.put("status", "failed");
        if (e instanceof DouyinApiException de) {
            result.put("message", de.getErrorMsg());
            result.put("errorCode", de.getErrorCode());
            if (de.getSubCode() != null && !de.getSubCode().isBlank()) {
                result.put("subCode", de.getSubCode());
            }
            if (de.getLogId() != null && !de.getLogId().isBlank()) {
                result.put("logId", de.getLogId());
            }
            if (de.getEndpoint() != null && !de.getEndpoint().isBlank()) {
                result.put("failedEndpoint", de.getEndpoint());
            }
            return;
        }
        String message = e.getMessage();
        result.put("message", (message == null || message.isBlank()) ? "Douyin SDK call failed" : message);
        result.put("errorType", e.getClass().getSimpleName());
    }

    private String asTrimmedText(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private long parseFlexibleEpoch(Object value, String label) {
        if (value == null) {
            throw new IllegalArgumentException(label + " is required");
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = asTrimmedText(value);
        if (!StringUtils.hasText(text)) {
            throw new IllegalArgumentException(label + " is required");
        }
        if (text.chars().allMatch(Character::isDigit) && text.length() >= 9 && text.length() <= 12) {
            return Long.parseLong(text);
        }
        return parseDateTimeToEpochSecond(text);
    }

    private long parseDateTimeToEpochSecond(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("start_time/end_time must use format yyyy-MM-dd HH:mm:ss");
        }
        return java.time.LocalDateTime.parse(
                value.trim(),
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        ).atZone(com.colonel.saas.common.time.AppZone.ZONE).toEpochSecond();
    }

    public static class TokenCreateRequest {
        @Schema(description = "抖音应用 appId。", example = "test-app")
        @JsonAlias({"app_id"})
        private String appId;

        @Schema(description = "达人或机构回调返回的授权 code。", example = "code_xxx")
        @JsonAlias({"authorizationCode", "authorization_code"})
        private String code;

        @Schema(description = "授权类型，联盟自研固定为 authorization_code。", example = "authorization_code")
        @JsonAlias({"grant_type"})
        private String grantType;

        @JsonAlias({"test_shop", "testShop"})
        private String testShop;

        @Schema(description = "预留字段，联盟授权换 token 时通常不需要。", example = "123456789")
        @JsonAlias({"shop_id", "shopId"})
        private String shopId;

        @Schema(description = "预留字段，联盟授权换 token 时通常不需要。", example = "auth_xxx")
        @JsonAlias({"auth_id", "authId"})
        private String authId;

        @Schema(description = "预留字段，联盟授权换 token 时通常不需要。", example = "1")
        @JsonAlias({"auth_subject_type", "authSubjectType", "auth_type", "type"})
        private String authSubjectType;

        public String getAppId() { return appId; }
        public void setAppId(String appId) { this.appId = appId; }
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getGrantType() { return grantType; }
        public void setGrantType(String grantType) { this.grantType = grantType; }
        public String getTestShop() { return testShop; }
        public void setTestShop(String testShop) { this.testShop = testShop; }
        public String getShopId() { return shopId; }
        public void setShopId(String shopId) { this.shopId = shopId; }
        public String getAuthId() { return authId; }
        public void setAuthId(String authId) { this.authId = authId; }
        public String getAuthSubjectType() { return authSubjectType; }
        public void setAuthSubjectType(String authSubjectType) { this.authSubjectType = authSubjectType; }
    }

    public static class ActivityProductCancelRequest {
        @Schema(description = "抖音应用 appId。", example = "test-app")
        private String appId;

        @Schema(description = "活动 ID。", example = "123")
        private Long activityId;

        @Schema(description = "申请单 ID 列表。", example = "[1,2]")
        private List<Long> applyIds;

        @Schema(description = "商品 ID 列表。", example = "[\"1001\",\"1002\"]")
        private List<String> productIds;

        @Schema(description = "原始商品对象列表。", example = "[{\"product_id\":\"1001\"}]")
        private List<Map<String, Object>> products;

        @Schema(description = "取消原因。", example = "终止合作")
        private String reason;

        public String getAppId() { return appId; }
        public void setAppId(String appId) { this.appId = appId; }
        public Long getActivityId() { return activityId; }
        public void setActivityId(Long activityId) { this.activityId = activityId; }
        public List<Long> getApplyIds() { return applyIds; }
        public void setApplyIds(List<Long> applyIds) { this.applyIds = applyIds; }
        public List<String> getProductIds() { return productIds; }
        public void setProductIds(List<String> productIds) { this.productIds = productIds; }
        public List<Map<String, Object>> getProducts() { return products; }
        public void setProducts(List<Map<String, Object>> products) { this.products = products; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }

        public Map<String, Object> toPayload() {
            boolean hasApplyIds = applyIds != null && !applyIds.isEmpty();
            boolean hasProductIds = productIds != null && !productIds.isEmpty();
            if (activityId == null && !hasApplyIds && !hasProductIds) {
                throw new IllegalArgumentException("activityId, applyIds, productIds at least one required");
            }
            Map<String, Object> payload = new HashMap<>();
            if (activityId != null) {
                payload.put("activity_id", activityId);
            }
            if (hasApplyIds) {
                payload.put("apply_ids", applyIds);
            }
            if (hasProductIds) {
                payload.put("product_ids", productIds);
                payload.put("products", productIds.stream().map(id -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("product_id", id);
                    return item;
                }).collect(Collectors.toList()));
            }
            if (reason != null && !reason.isBlank()) {
                payload.put("reason", reason.trim());
            }
            return payload;
        }
    }

    public static class ActivityCreateOrUpdateRequest {
        @Schema(description = "抖音应用 appId。", example = "test-app")
        private String appId;
        @Schema(description = "活动 ID；创建时可为空。", example = "123")
        private Long activityId;
        @Schema(description = "是否限制报名。", example = "false")
        @NotNull(message = "applicationLimited cannot be empty")
        private Boolean applicationLimited;
        @Schema(description = "是否新店。", example = "false")
        private Boolean isNewShop;
        @Schema(description = "店铺类型。待确认：取值含义请参考上游 SDK 文档。", example = "normal")
        private String shopType;
        @Schema(description = "活动名称。", example = "测试活动")
        @NotBlank(message = "activityName cannot be empty")
        private String activityName;
        @Schema(description = "活动说明。", example = "联调用活动")
        @NotBlank(message = "activityDesc cannot be empty")
        private String activityDesc;
        @Schema(description = "报名开始时间，格式 yyyy-MM-dd HH:mm:ss。", example = "2026-04-28 10:00:00")
        @NotBlank(message = "applyStartTime cannot be empty")
        private String applyStartTime;
        @Schema(description = "报名结束时间，格式 yyyy-MM-dd HH:mm:ss。", example = "2026-04-29 10:00:00")
        @NotBlank(message = "applyEndTime cannot be empty")
        private String applyEndTime;
        @Schema(description = "佣金率。", example = "10")
        @NotBlank(message = "commissionRate cannot be empty")
        private String commissionRate;
        @Schema(description = "服务费率。", example = "5")
        @NotBlank(message = "serviceRate cannot be empty")
        private String serviceRate;
        @Schema(description = "微信号。", example = "wechat-demo")
        private String wechatId;
        @Schema(description = "手机号。", example = "13800000000")
        private String phoneNum;
        @Schema(description = "预估单场销售额。", example = "1000")
        @NotBlank(message = "estimatedSingleSale cannot be empty")
        private String estimatedSingleSale;
        @Schema(description = "活动类型。待确认：取值含义请参考上游 SDK 文档。", example = "1")
        @NotNull(message = "activityType cannot be empty")
        private Integer activityType;
        @Schema(description = "指定店铺 ID 列表字符串。", example = "1001,1002")
        private String specifiedShopIds;
        @Schema(description = "是否上线。", example = "true")
        @NotNull(message = "online cannot be empty")
        private Boolean online;
        @Schema(description = "类目配置。", example = "服饰")
        private String categories;
        @Schema(description = "店铺评分。", example = "90")
        private Integer shopScore;
        @Schema(description = "最小推广天数。", example = "7")
        private Integer minPromotionDays;
        @Schema(description = "跨境阈值。", example = "0")
        private Integer thresholdCrossBorder;
        @Schema(description = "最小排除时长。", example = "0")
        private Integer minExclusionDuration;
        @Schema(description = "广告佣金率。", example = "0")
        private String adCommissionRate;
        @Schema(description = "广告服务费率。", example = "0")
        private String adServiceRate;
        @Schema(description = "COS 限制类型。待确认：取值含义请参考上游 SDK 文档。", example = "0")
        private Integer cosLimitType;

        public String getAppId() { return appId; }
        public void setAppId(String appId) { this.appId = appId; }
        public Long getActivityId() { return activityId; }
        public void setActivityId(Long activityId) { this.activityId = activityId; }
        public Boolean getApplicationLimited() { return applicationLimited; }
        public void setApplicationLimited(Boolean applicationLimited) { this.applicationLimited = applicationLimited; }
        public Boolean getIsNewShop() { return isNewShop; }
        public void setIsNewShop(Boolean isNewShop) { this.isNewShop = isNewShop; }
        public String getShopType() { return shopType; }
        public void setShopType(String shopType) { this.shopType = shopType; }
        public String getActivityName() { return activityName; }
        public void setActivityName(String activityName) { this.activityName = activityName; }
        public String getActivityDesc() { return activityDesc; }
        public void setActivityDesc(String activityDesc) { this.activityDesc = activityDesc; }
        public String getApplyStartTime() { return applyStartTime; }
        public void setApplyStartTime(String applyStartTime) { this.applyStartTime = applyStartTime; }
        public String getApplyEndTime() { return applyEndTime; }
        public void setApplyEndTime(String applyEndTime) { this.applyEndTime = applyEndTime; }
        public String getCommissionRate() { return commissionRate; }
        public void setCommissionRate(String commissionRate) { this.commissionRate = commissionRate; }
        public String getServiceRate() { return serviceRate; }
        public void setServiceRate(String serviceRate) { this.serviceRate = serviceRate; }
        public String getWechatId() { return wechatId; }
        public void setWechatId(String wechatId) { this.wechatId = wechatId; }
        public String getPhoneNum() { return phoneNum; }
        public void setPhoneNum(String phoneNum) { this.phoneNum = phoneNum; }
        public String getEstimatedSingleSale() { return estimatedSingleSale; }
        public void setEstimatedSingleSale(String estimatedSingleSale) { this.estimatedSingleSale = estimatedSingleSale; }
        public Integer getActivityType() { return activityType; }
        public void setActivityType(Integer activityType) { this.activityType = activityType; }
        public String getSpecifiedShopIds() { return specifiedShopIds; }
        public void setSpecifiedShopIds(String specifiedShopIds) { this.specifiedShopIds = specifiedShopIds; }
        public Boolean getOnline() { return online; }
        public void setOnline(Boolean online) { this.online = online; }
        public String getCategories() { return categories; }
        public void setCategories(String categories) { this.categories = categories; }
        public Integer getShopScore() { return shopScore; }
        public void setShopScore(Integer shopScore) { this.shopScore = shopScore; }
        public Integer getMinPromotionDays() { return minPromotionDays; }
        public void setMinPromotionDays(Integer minPromotionDays) { this.minPromotionDays = minPromotionDays; }
        public Integer getThresholdCrossBorder() { return thresholdCrossBorder; }
        public void setThresholdCrossBorder(Integer thresholdCrossBorder) { this.thresholdCrossBorder = thresholdCrossBorder; }
        public Integer getMinExclusionDuration() { return minExclusionDuration; }
        public void setMinExclusionDuration(Integer minExclusionDuration) { this.minExclusionDuration = minExclusionDuration; }
        public String getAdCommissionRate() { return adCommissionRate; }
        public void setAdCommissionRate(String adCommissionRate) { this.adCommissionRate = adCommissionRate; }
        public String getAdServiceRate() { return adServiceRate; }
        public void setAdServiceRate(String adServiceRate) { this.adServiceRate = adServiceRate; }
        public Integer getCosLimitType() { return cosLimitType; }
        public void setCosLimitType(Integer cosLimitType) { this.cosLimitType = cosLimitType; }
    }
}
