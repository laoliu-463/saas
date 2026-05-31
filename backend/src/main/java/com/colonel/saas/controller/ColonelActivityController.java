package com.colonel.saas.controller;

import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.auth.service.SysUserService;
import com.colonel.saas.common.base.BaseController;
import lombok.extern.slf4j.Slf4j;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.douyin.DouyinApiException;
import com.colonel.saas.entity.ColonelsettlementActivity;
import com.colonel.saas.entity.SysUser;
import com.colonel.saas.gateway.douyin.DouyinActivityGateway;
import com.colonel.saas.gateway.douyin.DouyinProductGateway;
import com.colonel.saas.mapper.SysUserMapper;
import com.colonel.saas.service.activity.ActivityAccessService;
import com.colonel.saas.service.activity.ActivityPromotionSupport;
import com.colonel.saas.service.ColonelsettlementActivityService;
import com.colonel.saas.service.ProductService;
import com.colonel.saas.service.ShortTtlCacheService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 团长活动管理控制器。
 */
@Slf4j
@Validated
@RestController
@Tag(name = "团长活动管理", description = "团长活动列表及活动下商品查询接口。")
@RequestMapping("/colonel/activities")
@RequireRoles({RoleCodes.BIZ_LEADER, RoleCodes.ADMIN, RoleCodes.BIZ_STAFF})
public class ColonelActivityController extends BaseController {

    private static final Duration ACTIVITY_LIST_CACHE_TTL = Duration.ofSeconds(60);
    private static final String ACTIVITY_LIST_CACHE_PREFIX = "activities:list:";

    private final DouyinActivityGateway douyinActivityGateway;
    private final DouyinProductGateway douyinProductGateway;
    private final ProductService productService;
    private final ShortTtlCacheService shortTtlCacheService;
    private final SysUserService sysUserService;
    private final ColonelsettlementActivityService colonelActivityService;
    private final SysUserMapper sysUserMapper;
    private final ActivityAccessService activityAccessService;

    public ColonelActivityController(
            DouyinActivityGateway douyinActivityGateway,
            DouyinProductGateway douyinProductGateway,
            ProductService productService,
            ShortTtlCacheService shortTtlCacheService,
            SysUserService sysUserService,
            ColonelsettlementActivityService colonelActivityService,
            SysUserMapper sysUserMapper,
            ActivityAccessService activityAccessService) {
        this.douyinActivityGateway = douyinActivityGateway;
        this.douyinProductGateway = douyinProductGateway;
        this.productService = productService;
        this.shortTtlCacheService = shortTtlCacheService;
        this.sysUserService = sysUserService;
        this.colonelActivityService = colonelActivityService;
        this.sysUserMapper = sysUserMapper;
        this.activityAccessService = activityAccessService;
    }

    @Operation(summary = "团长活动列表", description = "查询机构创建的团长活动列表，并回填本地分配信息。")
    @GetMapping
    public ApiResult<Map<String, Object>> list(
            @Parameter(description = "活动状态。") @RequestParam(name = "status", defaultValue = "0") Integer status,
            @Parameter(description = "搜索类型。") @RequestParam(name = "searchType", defaultValue = "0") Long searchType,
            @Parameter(description = "排序类型。") @RequestParam(name = "sortType", defaultValue = "1") Long sortType,
            @Parameter(description = "页码，从 1 开始。") @RequestParam(name = "page", defaultValue = "1") @Min(1) Long page,
            @Parameter(description = "每页条数，最大 20。") @RequestParam(name = "pageSize", defaultValue = "20") @Min(1) @Max(20) Long pageSize,
            @Parameter(description = "活动信息关键字。") @RequestParam(name = "activityInfo", required = false) String activityInfo,
            @Parameter(description = "抖音应用 appId。") @RequestParam(name = "appId", required = false) String appId,
            @Parameter(description = "分配筛选：all=全部（默认）、assigned=已分配、unassigned=未分配、mine=分配给我。")
            @RequestParam(name = "assignmentFilter", defaultValue = "all") String assignmentFilter,
            @RequestAttribute(value = "userId", required = false) UUID userId,
            @RequestAttribute(value = "roleCodes", required = false) Object roleCodes) {
        Collection<String> normalizedRoles = ActivityAccessService.normalizeRoleCodes(roleCodes);
        String effectiveFilter = activityAccessService.resolveEffectiveAssignmentFilter(assignmentFilter, normalizedRoles);
        try {
            if (activityAccessService.shouldUseLocalAssignmentList(effectiveFilter, normalizedRoles)) {
                Map<String, Object> payload = colonelActivityService.buildAssignmentListPage(
                        page,
                        pageSize,
                        status,
                        effectiveFilter,
                        userId,
                        activityInfo,
                        this::resolveUserName);
                return ok(payload);
            }
            String cacheKey = ACTIVITY_LIST_CACHE_PREFIX + cacheKey(status, searchType, sortType, page, pageSize, activityInfo, appId);
            Map<String, Object> payload = shortTtlCacheService.get(cacheKey, ACTIVITY_LIST_CACHE_TTL, () -> {
                DouyinActivityGateway.ActivityListResult result = douyinActivityGateway.listActivities(
                        new DouyinActivityGateway.ActivityListQuery(
                                appId, status, searchType, sortType, page, pageSize, activityInfo
                        ));
                if (result.activityList() != null) {
                    for (DouyinActivityGateway.ActivityItem item : result.activityList()) {
                        colonelActivityService.syncFromGatewayItem(item);
                    }
                }
                log.info("[活动列表] status={}, upstreamTotal={}, returned={}, items={}",
                        status, result.total(),
                        result.activityList() == null ? 0 : result.activityList().size(),
                        result.activityList() == null ? "[]" :
                                result.activityList().stream()
                                        .map(i -> String.format("{id=%d,status=%d,text=%s}", i.activityId(), i.status(), i.statusText()))
                                        .toList());
                return enrichActivityList(result.toMap());
            });
            return ok(payload);
        } catch (DouyinApiException e) {
            throw mapActivityError(e);
        }
    }

    @Operation(summary = "分配活动招商组长", description = "管理员将活动分配给招商组长，并级联该活动下商品负责人。")
    @RequireRoles({RoleCodes.ADMIN})
    @PutMapping("/{activityId}/assignee")
    public ApiResult<Map<String, Object>> assignActivity(
            @Parameter(description = "团长活动 ID。") @PathVariable("activityId") String activityId,
            @Valid @RequestBody ActivityAssigneeRequest request,
            @RequestAttribute(value = "userId", required = false) UUID userId) {
        if (request.getAssigneeId() != null) {
            sysUserService.assertRecruiterUser(request.getAssigneeId());
        }
        Map<String, Object> payload = productService.assignActivity(activityId, request.getAssigneeId(), userId);
        shortTtlCacheService.evictByPrefix(ACTIVITY_LIST_CACHE_PREFIX);
        return ok(payload);
    }

    @Operation(summary = "活动商品列表", description = "查询团长活动下的商品列表。优先使用本地快照构造业务视图，未命中时回退到上游接口后再落库。")
    @GetMapping("/{activityId}/products")
    public ApiResult<Map<String, Object>> listProducts(
            @Parameter(description = "团长活动 ID。") @PathVariable("activityId") String activityId,
            @Parameter(description = "搜索类型。") @RequestParam(name = "searchType", defaultValue = "4") Long searchType,
            @Parameter(description = "排序类型。") @RequestParam(name = "sortType", defaultValue = "1") Long sortType,
            @Parameter(description = "每次查询条数，最大 20。") @RequestParam(name = "count", defaultValue = "20") @Min(1) @Max(20) Integer count,
            @Parameter(description = "合作信息关键字。") @RequestParam(name = "cooperationInfo", required = false) String cooperationInfo,
            @Parameter(description = "合作类型。") @RequestParam(name = "cooperationType", defaultValue = "0") Integer cooperationType,
            @Parameter(description = "商品信息关键字。") @RequestParam(name = "productInfo", required = false) String productInfo,
            @Parameter(description = "业务状态筛选。") @RequestParam(name = "bizStatus", required = false) String bizStatus,
            @Parameter(description = "商品状态。") @RequestParam(name = "status", required = false) Integer status,
            @Parameter(description = "拉取模式。") @RequestParam(name = "retrieveMode", defaultValue = "1") Long retrieveMode,
            @Parameter(description = "游标。") @RequestParam(name = "cursor", required = false) String cursor,
            @Parameter(description = "页码。") @RequestParam(name = "page", required = false) @Min(1) Long page,
            @Parameter(description = "抖音应用 appId。") @RequestParam(name = "appId", required = false) String appId,
            @Parameter(description = "是否强制刷新上游。") @RequestParam(name = "refresh", defaultValue = "false") Boolean refresh,
            @Parameter(description = "本地视图排序。") @RequestParam(name = "sortBy", required = false) String sortBy,
            @Parameter(description = "货品标签。") @RequestParam(name = "goodsTags", required = false) String goodsTags,
            @Parameter(description = "商品标签。") @RequestParam(name = "productTags", required = false) String productTags,
            @RequestAttribute(value = "userId", required = false) UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "roleCodes", required = false) Object roleCodes) {
        activityAccessService.assertActivityReadable(
                activityId,
                userId,
                deptId,
                ActivityAccessService.normalizeRoleCodes(roleCodes));
        try {
            if (!Boolean.TRUE.equals(refresh) && productService.hasActivitySnapshots(activityId)) {
                return ok(productService.buildActivityProductListViewFromDb(
                        activityId, count, cursor, productInfo, bizStatus, status, sortBy, goodsTags, productTags));
            }
            DouyinProductGateway.ActivityProductQueryRequest queryRequest =
                    new DouyinProductGateway.ActivityProductQueryRequest(
                            appId, activityId, searchType, sortType, count, cooperationInfo, cooperationType,
                            productInfo, status, retrieveMode, cursor, page);
            Map<String, Object> payload = productService.buildActivityProductListViewFromDb(
                    activityId, count, cursor, productInfo, bizStatus, status, sortBy, goodsTags, productTags);
            if (Boolean.TRUE.equals(refresh)) {
                colonelActivityService.syncActivitySummaryFromUpstream(activityId, appId);
                ProductService.ActivityProductRefreshResult refreshResult =
                        productService.refreshActivitySnapshots(queryRequest);
                Map<String, Object> syncStats = new LinkedHashMap<>();
                syncStats.put("syncedProductCount", refreshResult.syncedProductCount());
                syncStats.put("libraryEntryCount", refreshResult.libraryEntryCount());
                syncStats.put("createdCount", refreshResult.createdCount());
                syncStats.put("updatedCount", refreshResult.updatedCount());
                syncStats.put("skippedCount", refreshResult.skippedCount());
                ColonelsettlementActivity activity = colonelActivityService.findByActivityId(activityId);
                syncStats.put("autoLibraryEligible", activity != null
                        && ActivityPromotionSupport.shouldForceLibraryDisplay(activity));
                payload.put("syncStats", syncStats);
            } else {
                DouyinProductGateway.ActivityProductListResult result = douyinProductGateway.queryActivityProducts(queryRequest);
                productService.upsertSnapshots(activityId, result.items());
            }
            return ok(payload);
        } catch (DouyinApiException e) {
            throw mapProductError(e);
        }
    }

    private Map<String, Object> enrichActivityList(Map<String, Object> raw) {
        Map<String, Object> result = new LinkedHashMap<>(raw);
        Object listNode = raw.get("activityList");
        if (!(listNode instanceof List<?> rows)) {
            return result;
        }
        List<String> activityIds = new ArrayList<>();
        for (Object row : rows) {
            if (row instanceof Map<?, ?> map) {
                Object activityId = map.get("activityId");
                if (activityId != null) {
                    activityIds.add(String.valueOf(activityId));
                }
            }
        }
        Map<String, ColonelsettlementActivity> assignments = colonelActivityService.findAssignmentsByActivityIds(activityIds);
        List<Map<String, Object>> enriched = new ArrayList<>();
        for (Object row : rows) {
            if (!(row instanceof Map<?, ?> map)) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            map.forEach((key, value) -> item.put(String.valueOf(key), value));
            String activityId = String.valueOf(item.get("activityId"));
            ColonelsettlementActivity assignment = assignments.get(activityId);
            if (assignment != null) {
                if (assignment.getRecruiterUserId() != null) {
                    String assigneeName = resolveUserName(assignment.getRecruiterUserId());
                    item.put("activityAssigneeId", assignment.getRecruiterUserId());
                    item.put("activityAssigneeName", assigneeName);
                    item.put("assigneeId", assignment.getRecruiterUserId());
                    item.put("assigneeName", assigneeName);
                    item.put("recruiterName", assigneeName);
                    item.put("recruiterUserId", assignment.getRecruiterUserId());
                    item.put("recruiterUserName", assigneeName);
                }
                if (assignment.getAssignedAt() != null) {
                    item.put("assignedAt", assignment.getAssignedAt());
                }
                if (assignment.getAssignedBy() != null) {
                    item.put("assignedBy", assignment.getAssignedBy());
                }
                if (assignment.getLastSyncAt() != null) {
                    item.put("lastSyncAt", assignment.getLastSyncAt());
                }
            }
            enriched.add(item);
        }
        result.put("activityList", enriched);
        return result;
    }

    private String resolveUserName(UUID userId) {
        if (userId == null) {
            return "";
        }
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            return "";
        }
        if (user.getRealName() != null && !user.getRealName().isBlank()) {
            return user.getRealName().trim();
        }
        return user.getUsername() == null ? "" : user.getUsername();
    }

    private BusinessException mapActivityError(DouyinApiException e) {
        String subCode = e.getSubCode() == null ? "" : e.getSubCode();
        if (e.getErrorCode() == 50002 && subCode.contains("4197")) {
            return BusinessException.stateInvalid("当前账号未完成招商团长授权，请检查抖店授权状态");
        }
        if (e.getErrorCode() == 50002 && subCode.contains("4200")) {
            return BusinessException.stateInvalid("抖店账号状态异常，请检查账号可用状态");
        }
        if (e.getErrorCode() == 40004 && subCode.contains("257")) {
            return BusinessException.param("查询参数不合法，请检查筛选条件");
        }
        if (e.getErrorCode() == 20000 && subCode.contains("256")) {
            return BusinessException.external("抖店服务异常，请稍后重试");
        }
        return BusinessException.external("团长活动查询失败: " + e.getErrorMsg());
    }

    private BusinessException mapProductError(DouyinApiException e) {
        String subCode = e.getSubCode() == null ? "" : e.getSubCode();
        if (e.getErrorCode() == 50002 && subCode.contains("4097")) {
            return BusinessException.param("每页最多查询 20 条商品");
        }
        if (e.getErrorCode() == 50002 && subCode.contains("8197")) {
            return BusinessException.stateInvalid("不允许继续翻页，请使用游标模式加载更多");
        }
        if (e.getErrorCode() == 50002 && subCode.contains("4197")) {
            return BusinessException.stateInvalid("当前账号未完成招商团长授权，请检查抖店授权状态");
        }
        if (e.getErrorCode() == 50002 && subCode.contains("4200")) {
            return BusinessException.stateInvalid("抖店账号状态异常，请检查账号可用状态");
        }
        if (e.getErrorCode() == 50002 && subCode.contains("257")) {
            return BusinessException.param("查询参数不合法，请检查筛选条件");
        }
        if (e.getErrorCode() == 20000 && subCode.contains("256")) {
            return BusinessException.external("抖店服务异常，请稍后重试");
        }
        return BusinessException.external("活动商品查询失败: " + e.getErrorMsg());
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

    public static class ActivityAssigneeRequest {
        /**
         * 招商组长用户 ID，null 表示清除分配。
         */
        private UUID assigneeId;

        public UUID getAssigneeId() {
            return assigneeId;
        }

        public void setAssigneeId(UUID assigneeId) {
            this.assigneeId = assigneeId;
        }
    }
}
