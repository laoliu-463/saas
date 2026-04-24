package com.colonel.saas.controller;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.common.base.BaseController;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.douyin.DouyinApiException;
import com.colonel.saas.douyin.DouyinTokenService;
import com.colonel.saas.douyin.api.ActivityApi;
import com.colonel.saas.douyin.api.OrderApi;
import com.colonel.saas.douyin.api.ProductApi;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
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
public class DouyinController extends BaseController {

    private final ActivityApi activityApi;
    private final ProductApi productApi;
    private final OrderApi orderApi;
    private final DouyinTokenService douyinTokenService;

    public DouyinController(ActivityApi activityApi, ProductApi productApi, OrderApi orderApi, DouyinTokenService douyinTokenService) {
        this.activityApi = activityApi;
        this.productApi = productApi;
        this.orderApi = orderApi;
        this.douyinTokenService = douyinTokenService;
    }

    @Tag(name = "抖音接口")
    @Operation(summary = "活动列表", description = "验证 alliance.instituteColonelActivityList 调用链路")
    @GetMapping("/activities")
    public ApiResult<Map<String, Object>> huodongLiebiao(@RequestParam(required = false) String appId) {
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

    @Operation(summary = "活动详情", description = "验证 buyin.colonelActivityDetail 调用链路")
    @GetMapping("/activities/{activityId}")
    public ApiResult<Map<String, Object>> huodongXiangqing(
            @RequestParam(required = false) String appId,
            @PathVariable String activityId) {
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

    @Operation(summary = "活动商品", description = "验证活动关联的商品列表")
    @GetMapping("/activity-products")
    public ApiResult<Map<String, Object>> huodongShangpin(
            @RequestParam(required = false) String appId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long searchType,
            @RequestParam(required = false) Long sortType,
            @RequestParam(required = false) Long page,
            @RequestParam(required = false) Long pageSize,
            @RequestParam(required = false) String activityInfo) {
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

    @Operation(summary = "商品列表", description = "验证 alliance.colonelActivityProduct 按活动查询商品")
    @GetMapping("/activities/{activityId}/products")
    public ApiResult<Map<String, Object>> shangpinLiebiao(
            @RequestParam(required = false) String appId,
            @PathVariable String activityId,
            @RequestParam(required = false) Integer count,
            @RequestParam(required = false) String cursor) {
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

    @Operation(summary = "商品素材状态", description = "验证 buyin.materialsProductStatus 商品素材状态查询")
    @PostMapping("/product-material-status-checks")
    public ApiResult<Map<String, Object>> shangpinSucaiZhuangtai(
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

    @Operation(summary = "订单结算", description = "验证 buyin.colonelMultiSettlementOrders 团长多结算订单")
    @GetMapping("/order-settlements")
    public ApiResult<Map<String, Object>> dingdanJiesuan(
            @RequestParam(required = false) String appId,
            @RequestParam(required = false, defaultValue = "20") Integer size,
            @RequestParam(required = false, defaultValue = "0") String cursor,
            @RequestParam(required = false, defaultValue = "update") String timeType,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        Map<String, Object> result = new HashMap<>();
        result.put("module", "M1.2 Douyin SDK");
        result.put("endpoint", "buyin.colonelMultiSettlementOrders");
        result.put("appId", appId);
        result.put("query", Map.of("size", size, "cursor", cursor, "timeType", timeType));
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

    @Operation(summary = "活动商品取消", description = "调用 alliance.colonelActivityProductCancel 终止合作")
    @PostMapping("/activity-product-cancellations")
    public ApiResult<Map<String, Object>> quxiaoHuodongShangpin(
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

    @PostMapping("/activity-product-cancellations/raw")
    public ApiResult<Map<String, Object>> quxiaoHuodongShangpinYuanshi(
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

    @Operation(summary = "活动创建更新", description = "调用 alliance.colonelActivityCreateOrUpdate 创建或编辑团长活动")
    @RequireRoles({RoleCodes.ADMIN})
    @PostMapping("/activities")
    public ApiResult<Map<String, Object>> chuangjianHuodong(
            @Valid @RequestBody ActivityCreateOrUpdateRequest request) {
        return ok(activityApi.createOrUpdate(buildActivityCommand(request, request.getActivityId())));
    }

    @Operation(summary = "活动更新", description = "调用 alliance.colonelActivityCreateOrUpdate 编辑团长活动")
    @RequireRoles({RoleCodes.ADMIN})
    @PutMapping("/activities/{activityId}")
    public ApiResult<Map<String, Object>> gengxinHuodong(
            @PathVariable Long activityId,
            @Valid @RequestBody ActivityCreateOrUpdateRequest request) {
        return ok(activityApi.createOrUpdate(buildActivityCommand(request, activityId)));
    }

    @Operation(summary = "Token状态", description = "查看当前 appId 的 Token 状态")
    @RequireRoles({RoleCodes.ADMIN})
    @GetMapping("/tokens")
    public ApiResult<DouyinTokenService.TokenStatus> tokenZhuangtai(
            @RequestParam(required = false) String appId) {
        return ok(douyinTokenService.getTokenStatus(appId));
    }

    @Operation(summary = "Token刷新", description = "使用缓存 refresh_token 刷新 Token")
    @RequireRoles({RoleCodes.ADMIN})
    @PostMapping("/token-refreshes")
    public ApiResult<DouyinTokenService.TokenStatus> tokenShuaxin(
            @RequestParam(required = false) String appId) {
        douyinTokenService.refreshToken(appId);
        return ok(douyinTokenService.getTokenStatus(appId));
    }

    @Operation(summary = "Token创建", description = "初始化 Token（refresh_token 或 authorization_self）")
    @RequireRoles({RoleCodes.ADMIN})
    @PostMapping("/tokens")
    public ApiResult<DouyinTokenService.TokenStatus> tokenChuangjian(
            @Valid @RequestBody TokenCreateRequest request) {
        douyinTokenService.exchangeCodeAndBootstrap(
                request.getAppId(),
                request.getCode(),
                request.getGrantType(),
                null, null, null, null
        );
        return ok(douyinTokenService.getTokenStatus(request.getAppId()));
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

    public static class TokenCreateRequest {
        @JsonAlias({"app_id"})
        private String appId;
        @JsonAlias({"authorizationCode", "authorization_code"})
        private String code;
        @JsonAlias({"grant_type"})
        private String grantType;

        public String getAppId() { return appId; }
        public void setAppId(String appId) { this.appId = appId; }
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getGrantType() { return grantType; }
        public void setGrantType(String grantType) { this.grantType = grantType; }
    }

    public static class ProductMaterialStatusRequest {
        private String appId;
        @NotEmpty(message = "products cannot be empty")
        private List<String> products;

        public String getAppId() { return appId; }
        public void setAppId(String appId) { this.appId = appId; }
        public List<String> getProducts() { return products; }
        public void setProducts(List<String> products) { this.products = products; }
    }

    public static class ActivityProductCancelRequest {
        private String appId;
        private Long activityId;
        private List<Long> applyIds;
        private List<String> productIds;
        private List<Map<String, Object>> products;
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
        private String appId;
        private Long activityId;
        @NotNull(message = "applicationLimited cannot be empty")
        private Boolean applicationLimited;
        private Boolean isNewShop;
        private String shopType;
        @NotBlank(message = "activityName cannot be empty")
        private String activityName;
        @NotBlank(message = "activityDesc cannot be empty")
        private String activityDesc;
        @NotBlank(message = "applyStartTime cannot be empty")
        private String applyStartTime;
        @NotBlank(message = "applyEndTime cannot be empty")
        private String applyEndTime;
        @NotBlank(message = "commissionRate cannot be empty")
        private String commissionRate;
        @NotBlank(message = "serviceRate cannot be empty")
        private String serviceRate;
        private String wechatId;
        private String phoneNum;
        @NotBlank(message = "estimatedSingleSale cannot be empty")
        private String estimatedSingleSale;
        @NotNull(message = "activityType cannot be empty")
        private Integer activityType;
        private String specifiedShopIds;
        @NotNull(message = "online cannot be empty")
        private Boolean online;
        private String categories;
        private Integer shopScore;
        private Integer minPromotionDays;
        private Integer thresholdCrossBorder;
        private Integer minExclusionDuration;
        private String adCommissionRate;
        private String adServiceRate;
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
