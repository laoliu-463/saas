package com.colonel.saas.controller;

import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.common.base.BaseController;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.douyin.DouyinApiException;
import com.colonel.saas.gateway.douyin.DouyinActivityGateway;
import com.colonel.saas.gateway.douyin.DouyinProductGateway;
import com.colonel.saas.service.ProductService;
import com.colonel.saas.service.ShortTtlCacheService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;

@Validated
@RestController
@Tag(name = "团长活动管理", description = "团长活动列表及活动下商品查询接口。")
@RequestMapping("/colonel/activities")
@RequireRoles({RoleCodes.BIZ_LEADER, RoleCodes.ADMIN, RoleCodes.COLONEL_LEADER})
public class ColonelActivityController extends BaseController {

    private static final Duration ACTIVITY_LIST_CACHE_TTL = Duration.ofSeconds(60);
    private static final String ACTIVITY_LIST_CACHE_PREFIX = "activities:list:";

    private final DouyinActivityGateway douyinActivityGateway;
    private final DouyinProductGateway douyinProductGateway;
    private final ProductService productService;
    private final ShortTtlCacheService shortTtlCacheService;

    public ColonelActivityController(
            DouyinActivityGateway douyinActivityGateway,
            DouyinProductGateway douyinProductGateway,
            ProductService productService,
            ShortTtlCacheService shortTtlCacheService) {
        this.douyinActivityGateway = douyinActivityGateway;
        this.douyinProductGateway = douyinProductGateway;
        this.productService = productService;
        this.shortTtlCacheService = shortTtlCacheService;
    }

    @Operation(summary = "团长活动列表", description = "查询机构创建的团长活动列表。该接口服务于业务页活动筛选，不是原始联调接口。")
    @GetMapping
    public ApiResult<Map<String, Object>> list(
            @Parameter(description = "活动状态。待确认：取值含义请联系产品或参考上游 SDK 文档。") @RequestParam(defaultValue = "0") Integer status,
            @Parameter(description = "搜索类型。待确认：取值含义请联系产品或参考上游 SDK 文档。") @RequestParam(defaultValue = "0") Long searchType,
            @Parameter(description = "排序类型。待确认：取值含义请联系产品或参考上游 SDK 文档。") @RequestParam(defaultValue = "1") Long sortType,
            @Parameter(description = "页码，从 1 开始。") @RequestParam(defaultValue = "1") @Min(1) Long page,
            @Parameter(description = "每页条数。当前仍保留 pageSize 命名，后续是否统一为 size 需单独决策。") @RequestParam(defaultValue = "20") @Min(1) @Max(20) Long pageSize,
            @Parameter(description = "活动信息关键字。") @RequestParam(required = false) String activityInfo,
            @Parameter(description = "抖音应用 appId；不传则使用系统默认应用配置。") @RequestParam(required = false) String appId) {
        try {
            String cacheKey = ACTIVITY_LIST_CACHE_PREFIX + cacheKey(status, searchType, sortType, page, pageSize, activityInfo, appId);
            return ok(shortTtlCacheService.get(cacheKey, ACTIVITY_LIST_CACHE_TTL, () -> douyinActivityGateway.listActivities(
                    new DouyinActivityGateway.ActivityListQuery(
                            appId, status, searchType, sortType, page, pageSize, activityInfo
                    )
            ).toMap()));
        } catch (DouyinApiException e) {
            throw mapActivityError(e);
        }
    }

    @Operation(summary = "活动商品列表", description = "查询团长活动下的商品列表。优先使用本地快照构造业务视图，未命中时回退到上游接口后再落库。")
    @GetMapping("/{activityId}/products")
    public ApiResult<Map<String, Object>> listProducts(
            @Parameter(description = "团长活动 ID。") @PathVariable String activityId,
            @Parameter(description = "搜索类型。待确认：取值含义请联系产品或参考上游 SDK 文档。") @RequestParam(defaultValue = "4") Long searchType,
            @Parameter(description = "排序类型。待确认：取值含义请联系产品或参考上游 SDK 文档。") @RequestParam(defaultValue = "1") Long sortType,
            @Parameter(description = "每次查询条数，最大 20。") @RequestParam(defaultValue = "20") @Min(1) @Max(20) Integer count,
            @Parameter(description = "合作信息关键字。") @RequestParam(required = false) String cooperationInfo,
            @Parameter(description = "合作类型。待确认：取值含义请联系产品或参考上游 SDK 文档。") @RequestParam(defaultValue = "0") Integer cooperationType,
            @Parameter(description = "商品信息关键字。") @RequestParam(required = false) String productInfo,
            @Parameter(description = "商品状态。待确认：取值含义请联系产品或参考上游 SDK 文档。") @RequestParam(required = false) Integer status,
            @Parameter(description = "拉取模式。待确认：取值含义请联系产品或参考上游 SDK 文档。") @RequestParam(defaultValue = "1") Long retrieveMode,
            @Parameter(description = "游标，继续翻页时使用。") @RequestParam(required = false) String cursor,
            @Parameter(description = "页码。当前仅在部分上游模式下使用。") @RequestParam(required = false) @Min(1) Long page,
            @Parameter(description = "抖音应用 appId；不传则使用系统默认应用配置。") @RequestParam(required = false) String appId,
            @Parameter(description = "是否绕过本地快照并强制刷新上游数据。") @RequestParam(defaultValue = "false") Boolean refresh) {
        try {
            if (!Boolean.TRUE.equals(refresh) && productService.hasActivitySnapshots(activityId)) {
                return ok(productService.buildActivityProductListViewFromDb(
                        activityId,
                        count,
                        cursor,
                        productInfo,
                        status == null ? null : status.toString()
                ));
            }
            DouyinProductGateway.ActivityProductQueryRequest queryRequest =
                    new DouyinProductGateway.ActivityProductQueryRequest(
                            appId,
                            activityId,
                            searchType,
                            sortType,
                            count,
                            cooperationInfo,
                            cooperationType,
                            productInfo,
                            status,
                            retrieveMode,
                            cursor,
                            page
                    );
            if (Boolean.TRUE.equals(refresh)) {
                productService.refreshActivitySnapshots(queryRequest);
            } else {
                DouyinProductGateway.ActivityProductListResult result =
                        douyinProductGateway.queryActivityProducts(queryRequest);
                productService.upsertSnapshots(activityId, result.items());
            }
            return ok(productService.buildActivityProductListViewFromDb(
                    activityId,
                    count,
                    cursor,
                    productInfo,
                    status == null ? null : status.toString()
            ));
        } catch (DouyinApiException e) {
            throw mapProductError(e);
        }
    }

    private BusinessException mapActivityError(DouyinApiException e) {
        String subCode = e.getSubCode() == null ? "" : e.getSubCode();
        if (e.getErrorCode() == 50002 && subCode.contains("4197")) {
            return new BusinessException("当前账号未完成招商团长授权，请检查抖店授权状态");
        }
        if (e.getErrorCode() == 50002 && subCode.contains("4200")) {
            return new BusinessException("抖店账号状态异常，请检查账号可用状态");
        }
        if (e.getErrorCode() == 40004 && subCode.contains("257")) {
            return new BusinessException("查询参数不合法，请检查筛选条件");
        }
        if (e.getErrorCode() == 20000 && subCode.contains("256")) {
            return new BusinessException("抖店服务异常，请稍后重试");
        }
        return new BusinessException("团长活动查询失败: " + e.getErrorMsg());
    }

    private BusinessException mapProductError(DouyinApiException e) {
        String subCode = e.getSubCode() == null ? "" : e.getSubCode();
        if (e.getErrorCode() == 50002 && subCode.contains("4097")) {
            return new BusinessException("每页最多查询 20 条商品");
        }
        if (e.getErrorCode() == 50002 && subCode.contains("8197")) {
            return new BusinessException("不允许继续翻页，请使用游标模式加载更多");
        }
        if (e.getErrorCode() == 50002 && subCode.contains("4197")) {
            return new BusinessException("当前账号未完成招商团长授权，请检查抖店授权状态");
        }
        if (e.getErrorCode() == 50002 && subCode.contains("4200")) {
            return new BusinessException("抖店账号状态异常，请检查账号可用状态");
        }
        if (e.getErrorCode() == 50002 && subCode.contains("257")) {
            return new BusinessException("查询参数不合法，请检查筛选条件");
        }
        if (e.getErrorCode() == 20000 && subCode.contains("256")) {
            return new BusinessException("抖店服务异常，请稍后重试");
        }
        return new BusinessException("活动商品查询失败: " + e.getErrorMsg());
    }

    private String cacheKey(Object... values) {
        StringBuilder builder = new StringBuilder();
        for (Object value : values) {
            if (builder.length() > 0) {
                builder.append('|');
            }
            builder.append(value == null ? "" : value);
        }
        return builder.toString();
    }
}
