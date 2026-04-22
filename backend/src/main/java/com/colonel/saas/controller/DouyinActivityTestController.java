package com.colonel.saas.controller;

import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.common.base.BaseController;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.douyin.DouyinApiException;
import com.colonel.saas.douyin.DouyinTokenService;
import com.colonel.saas.douyin.api.ActivityApi;
import com.colonel.saas.douyin.api.ProductApi;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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
@Tag(name = "抖店SDK联调")
@RestController
@RequestMapping("/douyin")
@RequireRoles({RoleCodes.ADMIN, RoleCodes.BIZ_LEADER})
public class DouyinActivityTestController extends BaseController {

    private final ActivityApi activityApi;
    private final ProductApi productApi;
    private final DouyinTokenService douyinTokenService;

    public DouyinActivityTestController(ActivityApi activityApi, ProductApi productApi, DouyinTokenService douyinTokenService) {
        this.activityApi = activityApi;
        this.productApi = productApi;
        this.douyinTokenService = douyinTokenService;
    }

    @Operation(summary = "测试活动列表", description = "验证 alliance.instituteColonelActivityList 调用链路")
    @GetMapping({"/test", "/activity/test"})
    public ApiResult<Map<String, Object>> test(@RequestParam(required = false) String appId) {
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

    @Operation(summary = "测试商品活动列表", description = "验证 alliance.instituteColonelActivityList 商品活动查询")
    @GetMapping("/product/activities")
    public ApiResult<Map<String, Object>> testProductActivities(
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

    @Operation(summary = "测试活动商品列表", description = "验证 alliance.colonelActivityProduct 按活动查询商品")
    @GetMapping("/product/list-by-activity")
    public ApiResult<Map<String, Object>> testProductsByActivity(
            @RequestParam(required = false) String appId,
            @RequestParam String activityId,
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

    @Operation(summary = "团长终止招商活动商品合作", description = "调用 alliance.colonelActivityProductCancel")
    @PostMapping("/activity/product/cancel")
    public ApiResult<Map<String, Object>> cancelActivityProduct(
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

    @PostMapping("/activity/product/cancel/raw")
    public ApiResult<Map<String, Object>> cancelActivityProductRaw(
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
        result.put("message", (message == null || message.isBlank()) ? "抖店SDK调用失败" : message);
        result.put("errorType", e.getClass().getSimpleName());
    }

    @Operation(summary = "团长活动创建/编辑", description = "调用 alliance.colonelActivityCreateOrUpdate 创建或编辑团长活动")
    @RequireRoles({RoleCodes.ADMIN})
    @PostMapping("/activity/create-or-update")
    public ApiResult<Map<String, Object>> createOrUpdateActivity(
            @Valid @RequestBody ActivityCreateOrUpdateRequest request) {
        ActivityApi.ActivityCreateOrUpdateCommand command = new ActivityApi.ActivityCreateOrUpdateCommand(
                request.getAppId(),
                request.getActivityId(),
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
        return ok(activityApi.createOrUpdate(command));
    }

    @Operation(summary = "获取Token状态", description = "查看当前 appId 的 Token 状态")
    @RequireRoles({RoleCodes.ADMIN})
    @GetMapping("/token/status")
    public ApiResult<DouyinTokenService.TokenStatus> tokenStatus(
            @RequestParam(required = false) String appId) {
        return ok(douyinTokenService.getTokenStatus(appId));
    }

    @Operation(summary = "刷新Token", description = "使用缓存 refresh_token 刷新 Token")
    @RequireRoles({RoleCodes.ADMIN})
    @PostMapping("/token/refresh")
    public ApiResult<DouyinTokenService.TokenStatus> refreshToken(
            @RequestParam(required = false) String appId) {
        douyinTokenService.refreshToken(appId);
        return ok(douyinTokenService.getTokenStatus(appId));
    }

    @Operation(summary = "初始化Token（refresh_token）", description = "注入 refresh_token 后立即刷新 access_token")
    @RequireRoles({RoleCodes.ADMIN})
    @PostMapping("/token/bootstrap")
    public ApiResult<DouyinTokenService.TokenStatus> bootstrapToken(
            @Valid @RequestBody TokenBootstrapRequest request) {
        douyinTokenService.bootstrapWithRefreshToken(request.getAppId(), request.getRefreshToken());
        return ok(douyinTokenService.getTokenStatus(request.getAppId()));
    }

    @Operation(summary = "授权码换Token", description = "使用授权 code 换取 access_token/refresh_token")
    @RequireRoles({RoleCodes.ADMIN})
    @PostMapping("/token/exchange-code")
    public ApiResult<DouyinTokenService.TokenStatus> exchangeCode(
            @Valid @RequestBody TokenExchangeCodeRequest request) {
        douyinTokenService.exchangeCodeAndBootstrap(
                request.getAppId(),
                request.getAuthorizationCode(),
                request.getGrantType(),
                request.getTestShop(),
                request.getShopId(),
                request.getAuthId(),
                request.getAuthSubjectType()
        );
        return ok(douyinTokenService.getTokenStatus(request.getAppId()));
    }

    public static class TokenBootstrapRequest {
        private String appId;
        @NotBlank(message = "refreshToken 不能为空")
        private String refreshToken;

        public String getAppId() {
            return appId;
        }

        public void setAppId(String appId) {
            this.appId = appId;
        }

        public String getRefreshToken() {
            return refreshToken;
        }

        public void setRefreshToken(String refreshToken) {
            this.refreshToken = refreshToken;
        }
    }

    public static class ActivityProductCancelRequest {
        private String appId;
        private Long activityId;
        private List<Long> applyIds;
        private List<String> productIds;
        private List<Map<String, Object>> products;
        private String reason;

        public String getAppId() {
            return appId;
        }

        public void setAppId(String appId) {
            this.appId = appId;
        }

        public Long getActivityId() {
            return activityId;
        }

        public void setActivityId(Long activityId) {
            this.activityId = activityId;
        }

        public List<Long> getApplyIds() {
            return applyIds;
        }

        public void setApplyIds(List<Long> applyIds) {
            this.applyIds = applyIds;
        }

        public List<String> getProductIds() {
            return productIds;
        }

        public void setProductIds(List<String> productIds) {
            this.productIds = productIds;
        }

        public List<Map<String, Object>> getProducts() {
            return products;
        }

        public void setProducts(List<Map<String, Object>> products) {
            this.products = products;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }

        public Map<String, Object> toPayload() {
            boolean hasApplyIds = applyIds != null && !applyIds.isEmpty();
            boolean hasProductIds = productIds != null && !productIds.isEmpty();
            if (activityId == null && !hasApplyIds && !hasProductIds) {
                throw new IllegalArgumentException("activityId、applyIds、productIds 至少提供一个");
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
                payload.put(
                        "products",
                        productIds.stream()
                                .map(id -> {
                                    Map<String, Object> item = new HashMap<>();
                                    item.put("product_id", id);
                                    return item;
                                })
                                .collect(Collectors.toList())
                );
            }
            if (reason != null && !reason.isBlank()) {
                payload.put("reason", reason.trim());
            }
            return payload;
        }
    }

    public static class TokenExchangeCodeRequest {
        private String appId;
        @NotBlank(message = "authorizationCode 不能为空")
        private String authorizationCode;
        private String grantType;
        private String testShop;
        private String shopId;
        private String authId;
        private String authSubjectType;

        public String getAppId() {
            return appId;
        }

        public void setAppId(String appId) {
            this.appId = appId;
        }

        public String getAuthorizationCode() {
            return authorizationCode;
        }

        public void setAuthorizationCode(String authorizationCode) {
            this.authorizationCode = authorizationCode;
        }

        public String getGrantType() {
            return grantType;
        }

        public void setGrantType(String grantType) {
            this.grantType = grantType;
        }

        public String getTestShop() {
            return testShop;
        }

        public void setTestShop(String testShop) {
            this.testShop = testShop;
        }

        public String getShopId() {
            return shopId;
        }

        public void setShopId(String shopId) {
            this.shopId = shopId;
        }

        public String getAuthId() {
            return authId;
        }

        public void setAuthId(String authId) {
            this.authId = authId;
        }

        public String getAuthSubjectType() {
            return authSubjectType;
        }

        public void setAuthSubjectType(String authSubjectType) {
            this.authSubjectType = authSubjectType;
        }
    }

    public static class ActivityCreateOrUpdateRequest {
        private String appId;
        private Long activityId;
        @NotNull(message = "applicationLimited 不能为空")
        private Boolean applicationLimited;
        private Boolean isNewShop;
        private String shopType;
        @NotBlank(message = "activityName 不能为空")
        private String activityName;
        @NotBlank(message = "activityDesc 不能为空")
        private String activityDesc;
        @NotBlank(message = "applyStartTime 不能为空")
        private String applyStartTime;
        @NotBlank(message = "applyEndTime 不能为空")
        private String applyEndTime;
        @NotBlank(message = "commissionRate 不能为空")
        private String commissionRate;
        @NotBlank(message = "serviceRate 不能为空")
        private String serviceRate;
        private String wechatId;
        private String phoneNum;
        @NotBlank(message = "estimatedSingleSale 不能为空")
        private String estimatedSingleSale;
        @NotNull(message = "activityType 不能为空")
        private Integer activityType;
        private String specifiedShopIds;
        @NotNull(message = "online 不能为空")
        private Boolean online;
        private String categories;
        private Integer shopScore;
        private Integer minPromotionDays;
        private Integer thresholdCrossBorder;
        private Integer minExclusionDuration;
        private String adCommissionRate;
        private String adServiceRate;
        private Integer cosLimitType;

        public String getAppId() {
            return appId;
        }

        public void setAppId(String appId) {
            this.appId = appId;
        }

        public Long getActivityId() {
            return activityId;
        }

        public void setActivityId(Long activityId) {
            this.activityId = activityId;
        }

        public Boolean getApplicationLimited() {
            return applicationLimited;
        }

        public void setApplicationLimited(Boolean applicationLimited) {
            this.applicationLimited = applicationLimited;
        }

        public Boolean getIsNewShop() {
            return isNewShop;
        }

        public void setIsNewShop(Boolean newShop) {
            isNewShop = newShop;
        }

        public String getShopType() {
            return shopType;
        }

        public void setShopType(String shopType) {
            this.shopType = shopType;
        }

        public String getActivityName() {
            return activityName;
        }

        public void setActivityName(String activityName) {
            this.activityName = activityName;
        }

        public String getActivityDesc() {
            return activityDesc;
        }

        public void setActivityDesc(String activityDesc) {
            this.activityDesc = activityDesc;
        }

        public String getApplyStartTime() {
            return applyStartTime;
        }

        public void setApplyStartTime(String applyStartTime) {
            this.applyStartTime = applyStartTime;
        }

        public String getApplyEndTime() {
            return applyEndTime;
        }

        public void setApplyEndTime(String applyEndTime) {
            this.applyEndTime = applyEndTime;
        }

        public String getCommissionRate() {
            return commissionRate;
        }

        public void setCommissionRate(String commissionRate) {
            this.commissionRate = commissionRate;
        }

        public String getServiceRate() {
            return serviceRate;
        }

        public void setServiceRate(String serviceRate) {
            this.serviceRate = serviceRate;
        }

        public String getWechatId() {
            return wechatId;
        }

        public void setWechatId(String wechatId) {
            this.wechatId = wechatId;
        }

        public String getPhoneNum() {
            return phoneNum;
        }

        public void setPhoneNum(String phoneNum) {
            this.phoneNum = phoneNum;
        }

        public String getEstimatedSingleSale() {
            return estimatedSingleSale;
        }

        public void setEstimatedSingleSale(String estimatedSingleSale) {
            this.estimatedSingleSale = estimatedSingleSale;
        }

        public Integer getActivityType() {
            return activityType;
        }

        public void setActivityType(Integer activityType) {
            this.activityType = activityType;
        }

        public String getSpecifiedShopIds() {
            return specifiedShopIds;
        }

        public void setSpecifiedShopIds(String specifiedShopIds) {
            this.specifiedShopIds = specifiedShopIds;
        }

        public Boolean getOnline() {
            return online;
        }

        public void setOnline(Boolean online) {
            this.online = online;
        }

        public String getCategories() {
            return categories;
        }

        public void setCategories(String categories) {
            this.categories = categories;
        }

        public Integer getShopScore() {
            return shopScore;
        }

        public void setShopScore(Integer shopScore) {
            this.shopScore = shopScore;
        }

        public Integer getMinPromotionDays() {
            return minPromotionDays;
        }

        public void setMinPromotionDays(Integer minPromotionDays) {
            this.minPromotionDays = minPromotionDays;
        }

        public Integer getThresholdCrossBorder() {
            return thresholdCrossBorder;
        }

        public void setThresholdCrossBorder(Integer thresholdCrossBorder) {
            this.thresholdCrossBorder = thresholdCrossBorder;
        }

        public Integer getMinExclusionDuration() {
            return minExclusionDuration;
        }

        public void setMinExclusionDuration(Integer minExclusionDuration) {
            this.minExclusionDuration = minExclusionDuration;
        }

        public String getAdCommissionRate() {
            return adCommissionRate;
        }

        public void setAdCommissionRate(String adCommissionRate) {
            this.adCommissionRate = adCommissionRate;
        }

        public String getAdServiceRate() {
            return adServiceRate;
        }

        public void setAdServiceRate(String adServiceRate) {
            this.adServiceRate = adServiceRate;
        }

        public Integer getCosLimitType() {
            return cosLimitType;
        }

        public void setCosLimitType(Integer cosLimitType) {
            this.cosLimitType = cosLimitType;
        }
    }
}
