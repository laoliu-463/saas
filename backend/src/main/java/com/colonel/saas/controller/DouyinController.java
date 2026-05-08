package com.colonel.saas.controller;

import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.common.base.BaseController;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.douyin.DoudianTokenGateway;
import com.colonel.saas.douyin.DouyinApiClient;
import com.colonel.saas.douyin.DouyinApiException;
import com.colonel.saas.douyin.DouyinTokenService;
import com.colonel.saas.douyin.api.ActivityApi;
import com.colonel.saas.douyin.api.InstitutionApi;
import com.colonel.saas.douyin.api.OrderApi;
import com.colonel.saas.douyin.api.ProductApi;
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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@Validated
@RestController
@RequestMapping("/douyin")
@RequireRoles({RoleCodes.ADMIN, RoleCodes.BIZ_LEADER})
@Tag(name = "抖音联调")
@SecurityRequirement(name = "bearerAuth")
public class DouyinController extends BaseController {

    private final ActivityApi activityApi;
    private final ProductApi productApi;
    private final OrderApi orderApi;
    private final InstitutionApi institutionApi;
    private final DouyinTokenService douyinTokenService;
    private final DoudianTokenGateway doudianTokenGateway;
    private final DouyinApiClient douyinApiClient;

    public DouyinController(
            ActivityApi activityApi,
            ProductApi productApi,
            OrderApi orderApi,
            InstitutionApi institutionApi,
            DouyinTokenService douyinTokenService,
            DoudianTokenGateway doudianTokenGateway,
            DouyinApiClient douyinApiClient) {
        this.activityApi = activityApi;
        this.productApi = productApi;
        this.orderApi = orderApi;
        this.institutionApi = institutionApi;
        this.douyinTokenService = douyinTokenService;
        this.doudianTokenGateway = doudianTokenGateway;
        this.douyinApiClient = douyinApiClient;
    }

    @Operation(summary = "[联调] 查询活动列表", description = "验证上游 alliance.instituteColonelActivityList 能力是否可用，检查当前 appId 下团长活动列表查询链路。")
    @GetMapping("/activities")
    public ApiResult<Map<String, Object>> huodongLiebiao(
            @Parameter(description = "抖音应用 appId；不传则使用系统默认应用配置。") @RequestParam(required = false) String appId) {
        Map<String, Object> result = new HashMap<>();
        result.put("module", "M1.2 Douyin SDK");
        result.put("endpoint", "alliance.instituteColonelActivityList");
        result.put("appId", appId);
        try {
            result.put("remoteResponse", activityApi.list(appId));
            result.put("status", "success");
        } catch (Throwable e) {
            log.error("Douyin activity test call failed", e);
            fillError(result, e);
        }
        return ok(result);
    }

    @Operation(summary = "[联调] 查询活动详情", description = "验证上游 buyin.colonelActivityDetail 能力是否可用，检查指定活动详情查询链路。")
    @GetMapping("/activities/{activityId}")
    public ApiResult<Map<String, Object>> huodongXiangqing(
            @Parameter(description = "抖音应用 appId；不传则使用系统默认应用配置。") @RequestParam(required = false) String appId,
            @Parameter(description = "团长活动 ID。") @PathVariable String activityId) {
        Map<String, Object> result = new HashMap<>();
        result.put("module", "M1.2 Douyin SDK");
        result.put("endpoint", "buyin.colonelActivityDetail");
        result.put("appId", appId);
        result.put("activityId", activityId);
        try {
            result.put("remoteResponse", activityApi.detail(appId, activityId));
            result.put("status", "success");
        } catch (Throwable e) {
            log.error("Douyin activity detail call failed", e);
            fillError(result, e);
        }
        return ok(result);
    }

    @Operation(summary = "[联调] 查询活动商品活动列表", description = "验证上游 alliance.instituteColonelActivityList 活动商品查询链路。该接口保留 pageSize 命名以对齐上游 SDK 参数。")
    @GetMapping("/activity-products")
    public ApiResult<Map<String, Object>> huodongShangpin(
            @Parameter(description = "抖音应用 appId；不传则使用系统默认应用配置。") @RequestParam(required = false) String appId,
            @Parameter(description = "活动状态。待确认：取值含义请联系产品或上游 SDK 文档。") @RequestParam(required = false) Integer status,
            @Parameter(description = "搜索类型。待确认：取值含义请联系产品或上游 SDK 文档。") @RequestParam(required = false) Long searchType,
            @Parameter(description = "排序类型。待确认：取值含义请联系产品或上游 SDK 文档。") @RequestParam(required = false) Long sortType,
            @Parameter(description = "页码，从 1 开始。") @RequestParam(required = false) Long page,
            @Parameter(description = "每页条数。联调接口保留 pageSize 命名以对齐上游 SDK。") @RequestParam(required = false) Long pageSize,
            @Parameter(description = "活动信息关键字，用于按活动名称或信息筛选。") @RequestParam(required = false) String activityInfo) {
        Map<String, Object> result = new HashMap<>();
        result.put("module", "M1.2 Douyin SDK");
        result.put("endpoint", "alliance.instituteColonelActivityList");
        result.put("appId", appId);
        try {
            result.put("remoteResponse", productApi.listActivities(
                    appId, status, searchType, sortType, page, pageSize, activityInfo));
            result.put("status", "success");
        } catch (Throwable e) {
            log.error("Douyin product activities test call failed", e);
            fillError(result, e);
        }
        return ok(result);
    }

    @Operation(summary = "[联调] 查询活动商品列表", description = "验证上游 alliance.colonelActivityProduct 按活动查询商品的能力。该接口主要用于联调上游返回结构，不等同于本地商品主链路接口。")
    @GetMapping("/activity-product-list")
    public ApiResult<Map<String, Object>> shangpinLiebiao(
            @Parameter(description = "抖音应用 appId；不传则使用系统默认应用配置。") @RequestParam(required = false) String appId,
            @Parameter(description = "团长活动 ID。") @RequestParam String activityId,
            @Parameter(description = "每次拉取商品数量。") @RequestParam(required = false) Integer count,
            @Parameter(description = "游标，继续翻页时使用。") @RequestParam(required = false) String cursor) {
        Map<String, Object> result = new HashMap<>();
        result.put("module", "M1.2 Douyin SDK");
        result.put("endpoint", "alliance.colonelActivityProduct");
        result.put("appId", appId);
        result.put("activityId", activityId);
        try {
            result.put("remoteResponse", productApi.listProductsByActivity(appId, activityId, count, cursor));
            result.put("status", "success");
        } catch (Throwable e) {
            log.error("Douyin products by activity test call failed", e);
            fillError(result, e);
        }
        return ok(result);
    }

    @Operation(summary = "[联调] 查询商品素材状态", description = "验证上游 buyin.materialsProductStatus 商品素材状态查询能力。")
    @PostMapping("/product-material-status-checks")
    public ApiResult<Map<String, Object>> shangpinSucaiZhuangtai(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "商品素材状态查询请求体。",
                    required = true,
                    content = @Content(examples = @ExampleObject(value = "{\"appId\":\"test-app\",\"products\":[\"101\",\"102\"]}"))
            )
            @Valid @RequestBody ProductMaterialStatusRequest request) {
        Map<String, Object> result = new HashMap<>();
        result.put("module", "M1.2 Douyin SDK");
        result.put("endpoint", "buyin.materialsProductStatus");
        result.put("appId", request.getAppId());
        try {
            result.put("remoteResponse", productApi.materialsProductStatus(request.getAppId(), request.getProducts()));
            result.put("status", "success");
        } catch (Throwable e) {
            log.error("Douyin product material status call failed", e);
            fillError(result, e);
        }
        return ok(result);
    }

    @Operation(summary = "[联调] 查询订单结算", description = "验证上游 buyin.colonelMultiSettlementOrders 团长多结算订单查询能力。")
    @GetMapping("/order-settlements")
    public ApiResult<Map<String, Object>> dingdanJiesuan(
            @Parameter(description = "抖音应用 appId；不传则使用系统默认应用配置。") @RequestParam(required = false) String appId,
            @Parameter(description = "每次拉取条数。") @RequestParam(required = false, defaultValue = "20") Integer size,
            @Parameter(description = "游标，继续翻页时使用。") @RequestParam(required = false, defaultValue = "0") String cursor,
            @Parameter(description = "时间类型，如 update。待确认：更多取值请参考上游 SDK 文档。") @RequestParam(required = false, defaultValue = "update") String timeType,
            @Parameter(description = "开始时间，格式 yyyy-MM-dd HH:mm:ss。") @RequestParam(required = false) String startTime,
            @Parameter(description = "结束时间，格式 yyyy-MM-dd HH:mm:ss。") @RequestParam(required = false) String endTime) {
        Map<String, Object> result = new HashMap<>();
        result.put("module", "M1.2 Douyin SDK");
        result.put("endpoint", "buyin.colonelMultiSettlementOrders");
        result.put("appId", appId);
        Map<String, Object> query = new HashMap<>();
        query.put("size", size);
        query.put("cursor", cursor);
        query.put("timeType", timeType);
        query.put("startTime", startTime);
        query.put("endTime", endTime);
        result.put("query", query);
        try {
            result.put("remoteResponse", orderApi.listColonelMultiSettlementOrders(
                    appId, size, cursor, timeType, startTime, endTime, null));
            result.put("status", "success");
        } catch (Throwable e) {
            log.error("Douyin order settlement call failed", e);
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
            result.put("remoteResponse", activityApi.cancelActivityProduct(request.getAppId(), payload));
            result.put("status", "success");
        } catch (Throwable e) {
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
            result.put("remoteResponse", activityApi.cancelActivityProduct(appId, payload));
            result.put("status", "success");
        } catch (Throwable e) {
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
            if (StringUtils.hasText(appId)) {
                payload.put("appId", appId);
            }
            result.put("remoteResponse", douyinApiClient.post(method, payload));
            result.put("status", "success");
        } catch (Throwable e) {
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
            Map<String, Object> payload = new HashMap<>(request);
            String appId = asTrimmedText(payload.remove("appId"));
            result.put("appId", appId);
            result.put("payload", payload);
            if (StringUtils.hasText(appId)) {
                payload.put("appId", appId);
            }
            result.put("remoteResponse", douyinApiClient.post("buyin.instituteOrderColonel", payload));
            result.put("status", "success");
        } catch (Throwable e) {
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
        return ok(activityApi.createOrUpdate(buildActivityCommand(request, request.getActivityId())));
    }

    @Operation(summary = "[联调] 更新活动", description = "验证上游 alliance.colonelActivityCreateOrUpdate 更新团长活动能力。")
    @RequireRoles({RoleCodes.ADMIN})
    @PutMapping("/activities/{activityId}")
    public ApiResult<Map<String, Object>> gengxinHuodong(
            @Parameter(description = "团长活动 ID。") @PathVariable Long activityId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "更新团长活动请求体。",
                    required = true,
                    content = @Content(examples = @ExampleObject(value = "{\"appId\":\"test-app\",\"applicationLimited\":false,\"activityName\":\"测试活动-更新\",\"activityDesc\":\"联调用活动\",\"applyStartTime\":\"2026-04-28 10:00:00\",\"applyEndTime\":\"2026-04-29 10:00:00\",\"commissionRate\":\"10\",\"serviceRate\":\"5\",\"estimatedSingleSale\":\"1000\",\"activityType\":1,\"online\":true}"))
            )
            @Valid @RequestBody ActivityCreateOrUpdateRequest request) {
        return ok(activityApi.createOrUpdate(buildActivityCommand(request, activityId)));
    }

    @Operation(summary = "[联调] 查询 Token 状态", description = "查看当前 appId 的 Token 缓存状态，用于确认真实联调前授权是否准备完毕。")
    @RequireRoles({RoleCodes.ADMIN})
    @GetMapping("/tokens")
    public ApiResult<DouyinTokenService.TokenStatus> tokenZhuangtai(
            @Parameter(description = "抖音应用 appId；不传则使用系统默认应用配置。") @RequestParam(required = false) String appId) {
        return ok(douyinTokenService.getTokenStatus(appId));
    }

    @Operation(summary = "[联调] 刷新 Token", description = "使用缓存中的 refresh_token 刷新 Token，用于验证 Token 刷新链路。")
    @RequireRoles({RoleCodes.ADMIN})
    @PostMapping("/token-refreshes")
    public ApiResult<DouyinTokenService.TokenStatus> tokenShuaxin(
            @Parameter(description = "抖音应用 appId；不传则使用系统默认应用配置。") @RequestParam(required = false) String appId) {
        douyinTokenService.refreshToken(appId);
        return ok(douyinTokenService.getTokenStatus(appId));
    }

    @Operation(summary = "[联调] 查询授权机构身份", description = "调用 buyin.institutionInfo 验证当前 Token 对应的授权主体、百应身份与角色信息。")
    @RequireRoles({RoleCodes.ADMIN})
    @GetMapping("/institution-info")
    public ApiResult<Map<String, Object>> jigouShenfen(
            @Parameter(description = "抖音应用 appId；不传则使用系统默认应用配置。") @RequestParam(required = false) String appId) {
        Map<String, Object> result = new HashMap<>();
        result.put("module", "M1.2 Douyin SDK");
        result.put("endpoint", "buyin.institutionInfo");
        result.put("appId", appId);
        try {
            result.put("remoteResponse", institutionApi.info(appId));
            result.put("status", "success");
        } catch (Throwable e) {
            log.error("Douyin institution info call failed", e);
            fillError(result, e);
        }
        return ok(result);
    }

    @Operation(summary = "[联调] 初始化 Token", description = "使用 authorization code 或 refresh_token 初始化 Token，适用于真实联调前的授权准备。联调接口，无需角色校验。")
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
        DoudianTokenGateway.TokenCreateProbeResult probe = doudianTokenGateway.probeCreateToken(
                new DoudianTokenGateway.TokenCreateCommand(
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
        result.put("response", probe.responseView());
        return ok(result);
    }

    private ActivityApi.ActivityCreateOrUpdateCommand buildActivityCommand(
            ActivityCreateOrUpdateRequest request,
            Long activityId) {
        return new ActivityApi.ActivityCreateOrUpdateCommand(
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

    public static class ProductMaterialStatusRequest {
        @Schema(description = "抖音应用 appId。", example = "test-app")
        private String appId;

        @Schema(description = "商品 ID 列表。", example = "[\"101\",\"102\"]")
        @NotEmpty(message = "products cannot be empty")
        private List<String> products;

        public String getAppId() { return appId; }
        public void setAppId(String appId) { this.appId = appId; }
        public List<String> getProducts() { return products; }
        public void setProducts(List<String> products) { this.products = products; }
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
