package com.colonel.saas.controller;

import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.common.base.BaseController;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.douyin.DouyinApiException;
import com.colonel.saas.gateway.douyin.DouyinColonelActivityGateway;
import com.colonel.saas.gateway.douyin.DouyinProductGateway;
import com.colonel.saas.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Validated
@RestController
@Tag(name = "团长活动管理")
@RequestMapping("/colonel/activities")
@RequireRoles({RoleCodes.BIZ_LEADER, RoleCodes.BIZ_STAFF, RoleCodes.CHANNEL_LEADER, RoleCodes.CHANNEL_STAFF, RoleCodes.ADMIN})
public class ColonelActivityController extends BaseController {

    private final DouyinColonelActivityGateway douyinColonelActivityGateway;
    private final DouyinProductGateway douyinProductGateway;
    private final ProductService productService;

    public ColonelActivityController(
            DouyinColonelActivityGateway douyinColonelActivityGateway,
            DouyinProductGateway douyinProductGateway,
            ProductService productService) {
        this.douyinColonelActivityGateway = douyinColonelActivityGateway;
        this.douyinProductGateway = douyinProductGateway;
        this.productService = productService;
    }

    @Operation(summary = "团长活动列表", description = "查询机构创建的团长活动列表")
    @GetMapping
    public ApiResult<Map<String, Object>> list(
            @RequestParam(defaultValue = "0") Integer status,
            @RequestParam(defaultValue = "0") Long searchType,
            @RequestParam(defaultValue = "1") Long sortType,
            @RequestParam(defaultValue = "1") @Min(1) Long page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(20) Long pageSize,
            @RequestParam(required = false) String activityInfo,
            @RequestParam(required = false) String appId) {
        try {
            return ok(douyinColonelActivityGateway.listActivities(
                    new DouyinColonelActivityGateway.ActivityListQuery(
                            appId, status, searchType, sortType, page, pageSize, activityInfo
                    )
            ).toMap());
        } catch (DouyinApiException e) {
            throw mapActivityError(e);
        }
    }

    @Operation(summary = "活动商品列表", description = "查询团长活动下的商品列表，默认游标模式")
    @GetMapping("/{activityId}/products")
    public ApiResult<Map<String, Object>> listProducts(
            @PathVariable String activityId,
            @RequestParam(defaultValue = "4") Long searchType,
            @RequestParam(defaultValue = "1") Long sortType,
            @RequestParam(defaultValue = "20") @Min(1) @Max(20) Integer count,
            @RequestParam(required = false) String cooperationInfo,
            @RequestParam(defaultValue = "0") Integer cooperationType,
            @RequestParam(required = false) String productInfo,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Long retrieveMode,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) @Min(1) Long page,
            @RequestParam(required = false) String appId) {
        try {
            if (productService.hasActivitySnapshots(activityId)) {
                return ok(productService.buildActivityProductListViewFromDb(
                        activityId,
                        count,
                        cursor,
                        productInfo,
                        status == null ? null : status.toString()
                ));
            }
            DouyinProductGateway.ActivityProductListResult result =
                    douyinProductGateway.queryActivityProducts(
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
                            )
                    );
            productService.upsertSnapshots(activityId, result.items());
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
}
