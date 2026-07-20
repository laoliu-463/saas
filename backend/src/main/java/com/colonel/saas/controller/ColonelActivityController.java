package com.colonel.saas.controller;

import com.colonel.saas.annotation.RequirePermission;
import com.colonel.saas.auth.service.SysUserService;
import com.colonel.saas.common.base.BaseController;
import com.colonel.saas.service.ColonelActivityListSyncService;
import lombok.extern.slf4j.Slf4j;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.common.exception.UpstreamErrorCode;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.douyin.DouyinApiException;
import com.colonel.saas.entity.ColonelsettlementActivity;
import com.colonel.saas.entity.SysUser;
import com.colonel.saas.domain.product.application.dto.ActivityProductRefreshRequest;
import com.colonel.saas.domain.product.policy.ProductDisplayPolicy;
import com.colonel.saas.domain.user.facade.UserDomainFacade;
import com.colonel.saas.service.activity.ActivityAccessService;
import com.colonel.saas.service.ColonelsettlementActivityService;
import com.colonel.saas.service.ProductActivityManualSyncService;
import com.colonel.saas.service.ProductService;
import com.colonel.saas.service.ShortTtlCacheService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
@RequirePermission("colonel-activity:access")
public class ColonelActivityController extends BaseController {

    private static final Duration ACTIVITY_LIST_CACHE_TTL = Duration.ofSeconds(60);
    private static final String ACTIVITY_LIST_CACHE_PREFIX = "activities:list:";

    private final ProductService productService;
    private final ShortTtlCacheService shortTtlCacheService;
    private final SysUserService sysUserService;
    private final ColonelsettlementActivityService colonelActivityService;
    private final ProductActivityManualSyncService productActivityManualSyncService;
    private final UserDomainFacade userDomainFacade;
    private final ActivityAccessService activityAccessService;
    private final ProductDisplayPolicy productDisplayPolicy;
    private final ColonelActivityListSyncService activityListSyncService;

    public ColonelActivityController(
            ProductService productService,
            ShortTtlCacheService shortTtlCacheService,
            SysUserService sysUserService,
            ColonelsettlementActivityService colonelActivityService,
            ProductActivityManualSyncService productActivityManualSyncService,
            UserDomainFacade userDomainFacade,
            ActivityAccessService activityAccessService,
            ProductDisplayPolicy productDisplayPolicy,
            ColonelActivityListSyncService activityListSyncService) {
        this.productService = productService;
        this.shortTtlCacheService = shortTtlCacheService;
        this.sysUserService = sysUserService;
        this.colonelActivityService = colonelActivityService;
        this.productActivityManualSyncService = productActivityManualSyncService;
        this.userDomainFacade = userDomainFacade;
        this.activityAccessService = activityAccessService;
        this.productDisplayPolicy = productDisplayPolicy;
        this.activityListSyncService = activityListSyncService;
    }

    @Operation(summary = "团长活动列表", description = "查询机构创建的团长活动列表，并回填本地分配信息。默认仅查本地 DB；DB 为空时返回 needSync=true 提示先同步活动，永不在线调抖音。")
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
        Collection<String> normalizedRoles = activityAccessService.normalizeRoles(roleCodes);
        String effectiveFilter = activityAccessService.resolveEffectiveAssignmentFilter(assignmentFilter, normalizedRoles);
        // 改造后路径（504 根因修复）：
        // 永远走本地 DB，admin+all filter 也不调抖音，避免上游超时/慢响应导致 504
        try {
            Map<String, Object> payload = colonelActivityService.buildAssignmentListPage(
                    page,
                    pageSize,
                    status,
                    effectiveFilter,
                    userId,
                    activityInfo,
                    this::resolveUserName);
            // 探测 DB 是否为空：total=0 且当前 page=1 时给前端 needSync 提示
            Object totalNode = payload.get("total");
            long total = totalNode instanceof Number n ? n.longValue() : 0L;
            if (total == 0L && page == 1L) {
                Map<String, Object> hintPayload = new LinkedHashMap<>(payload);
                hintPayload.put("needSync", Boolean.TRUE);
                hintPayload.put("errorCode", UpstreamErrorCode.DATA_NOT_READY.name());
                hintPayload.put("message", "活动列表尚未同步，请先点击「同步活动」");
                return ok(hintPayload);
            }
            return ok(payload);
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("[活动列表] 异常, status={}, filter={}", status, effectiveFilter, e);
            throw BusinessException.upstream(UpstreamErrorCode.EXTERNAL_GENERIC,
                    "活动列表查询失败: " + e.getMessage(), e);
        }
    }

    @Operation(summary = "分配活动招商组长", description = "管理员将活动分配给招商组长，并级联该活动下商品负责人。")
    @RequirePermission("colonel-activity:assign-activity")
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

    @Operation(summary = "触发活动商品后台同步", description = "提交活动商品同步后台任务，接口立即返回，不阻塞前端列表查询。")
    @PostMapping("/{activityId}/products/sync")
    public ApiResult<Map<String, Object>> syncProducts(
            @Parameter(description = "团长活动 ID。") @PathVariable("activityId") String activityId,
            @Parameter(description = "抖音应用 appId。") @RequestParam(name = "appId", required = false) String appId,
            @RequestBody(required = false) ProductSyncRequest request,
            @RequestAttribute(value = "userId", required = false) UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "roleCodes", required = false) Object roleCodes) {
        activityAccessService.assertActivityReadable(
                activityId,
                userId,
                deptId,
                activityAccessService.normalizeRoles(roleCodes));
        ProductActivityManualSyncService.SyncOptions syncOptions = toSyncOptions(request);
        ProductActivityManualSyncService.SyncTriggerResult triggerResult =
                syncOptions == null
                        ? productActivityManualSyncService.trigger(activityId, appId, userId)
                        : productActivityManualSyncService.trigger(activityId, appId, userId, syncOptions);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("activityId", triggerResult.activityId());
        payload.put("jobId", triggerResult.jobId());
        payload.put("syncStatus", triggerResult.syncStatus());
        if (syncOptions != null) {
            payload.put("syncMode", syncOptions.syncMode());
            payload.put("maxRowsPerActivity", syncOptions.maxRowsPerActivity());
            payload.put("priorityStatuses", syncOptions.priorityStatuses());
        }
        String message;
        if ("QUEUED".equals(triggerResult.syncStatus())) {
            message = "商品同步已排队，系统将在当前同步资源可用后自动执行";
        } else if ("QUEUE_FULL".equals(triggerResult.syncStatus())) {
            message = StringUtils.hasText(triggerResult.message())
                    ? triggerResult.message()
                    : "活动商品同步队列已满，请稍后重试";
        } else if ("RUNNING".equals(triggerResult.syncStatus())) {
            message = "商品同步已在后台执行，请稍后刷新列表";
        } else if ("LOCKED".equals(triggerResult.syncStatus())) {
            message = StringUtils.hasText(triggerResult.message())
                    ? triggerResult.message()
                    : "后台商品同步正在执行，请稍后重试";
            payload.put("lockKey", triggerResult.lockKey());
            payload.put("lockOwner", triggerResult.lockOwner());
            payload.put("lockTtlSeconds", triggerResult.lockTtlSeconds());
        } else {
            message = "商品同步已转入后台执行";
        }
        payload.put("message", message);
        return ok(payload);
    }

    private ProductActivityManualSyncService.SyncOptions toSyncOptions(ProductSyncRequest request) {
        if (request == null) {
            return null;
        }
        boolean hasSyncMode = StringUtils.hasText(request.getSyncMode());
        boolean hasMaxRows = request.getMaxRowsPerActivity() != null;
        boolean hasPriorityStatuses = request.getPriorityStatuses() != null && !request.getPriorityStatuses().isEmpty();
        if (!hasSyncMode && !hasMaxRows && !hasPriorityStatuses) {
            return null;
        }
        return new ProductActivityManualSyncService.SyncOptions(
                request.getSyncMode(),
                request.getMaxRowsPerActivity(),
                request.getPriorityStatuses());
    }

    @Operation(summary = "查询活动商品同步任务状态", description = "查询一键同步商品后台任务状态，前端用于在 SUCCESS/PARTIAL/FAILED 终态后刷新活动商品列表。")
    @GetMapping("/{activityId}/products/sync-jobs/{jobId}")
    public ApiResult<Map<String, Object>> getProductSyncJob(
            @Parameter(description = "团长活动 ID。") @PathVariable("activityId") String activityId,
            @Parameter(description = "同步任务 ID。") @PathVariable("jobId") String jobId,
            @RequestAttribute(value = "userId", required = false) UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "roleCodes", required = false) Object roleCodes) {
        activityAccessService.assertActivityReadable(
                activityId,
                userId,
                deptId,
                activityAccessService.normalizeRoles(roleCodes));
        ProductActivityManualSyncService.SyncJobStatus status =
                productActivityManualSyncService.getJobStatus(jobId);
        if (!String.valueOf(activityId).trim().equals(status.activityId())) {
            throw BusinessException.notFound("未找到对应的活动商品同步任务");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("activityId", status.activityId());
        payload.put("jobId", status.jobId());
        payload.put("syncStatus", status.syncStatus());
        payload.put("fetchedRows", status.fetchedRows());
        payload.put("distinctProductIds", status.distinctProductIds());
        payload.put("createdCount", status.createdCount());
        payload.put("updatedCount", status.updatedCount());
        payload.put("skippedCount", status.skippedCount());
        payload.put("failedCount", status.failedCount());
        payload.put("startedAt", status.startedAt());
        payload.put("finishedAt", status.finishedAt());
        payload.put("errorMessage", status.errorMessage());
        return ok(payload);
    }

    @Operation(summary = "活动商品列表", description = "查询团长活动下的商品列表。优先使用本地快照构造业务视图；本地无快照且未要求 refresh 时返回 needSync=true 提示先同步，永不在线调抖音。")
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
        if (!productDisplayPolicy.isSupportedActivityProductQueryStatus(status)) {
            throw BusinessException.param(productDisplayPolicy.activityProductQueryStatusHint());
        }
        activityAccessService.assertActivityReadable(
                activityId,
                userId,
                deptId,
                activityAccessService.normalizeRoles(roleCodes));
        try {
            // 改造后路径（504 根因修复）：
            // 1) refresh=true 强制走抖音同步（用户主动触发，已知耗时）
            // 2) refresh=false 且 DB 有快照 → 走 DB（理想路径）
            // 3) refresh=false 且 DB 无快照 → 返回 needSync=true + DATA_NOT_READY 提示
            //    **永不在线调抖音** —— 这是 504 根因
            if (Boolean.TRUE.equals(refresh)) {
                // P0 修复：refresh=true 直接写库绕过了锁协议，已迁移到异步 POST 同步接口。
                // 返回已有快照数据 + 提示，引导前端使用 POST /colonel/activities/{id}/products/sync
                if (productService.hasActivitySnapshots(activityId)) {
                    Map<String, Object> payload = productService.buildActivityProductListViewFromDb(
                            activityId, count, cursor, productInfo, bizStatus, status, sortBy, goodsTags, productTags);
                    payload.put("refreshDeprecated", Boolean.TRUE);
                    payload.put("message", "refresh=true 已废弃，请使用「同步商品」按钮触发异步同步");
                    return ok(payload);
                }
                Map<String, Object> hintPayload = new LinkedHashMap<>();
                hintPayload.put("items", java.util.List.of());
                hintPayload.put("total", 0L);
                hintPayload.put("activityId", activityId);
                hintPayload.put("needSync", Boolean.TRUE);
                hintPayload.put("refreshDeprecated", Boolean.TRUE);
                hintPayload.put("errorCode", UpstreamErrorCode.DATA_NOT_READY.name());
                hintPayload.put("message", "该活动尚未同步商品，请先点击「同步商品」");
                return ok(hintPayload);
            }
            if (productService.hasActivitySnapshots(activityId)) {
                return ok(productService.buildActivityProductListViewFromDb(
                        activityId, count, cursor, productInfo, bizStatus, status, sortBy, goodsTags, productTags));
            }
            // DB 无快照：返回 needSync=true 提示，引导前端调 syncProducts
            Map<String, Object> hintPayload = new LinkedHashMap<>();
            hintPayload.put("items", java.util.List.of());
            hintPayload.put("total", 0L);
            hintPayload.put("activityId", activityId);
            hintPayload.put("needSync", Boolean.TRUE);
            hintPayload.put("errorCode", UpstreamErrorCode.DATA_NOT_READY.name());
            hintPayload.put("message", "该活动尚未同步商品，请先点击「同步商品」");
            hintPayload.put("lastSyncAt", null);
            hintPayload.put("activityStatusSyncedAt", null);
            return ok(hintPayload);
        } catch (DouyinApiException e) {
            // refresh=true 路径调用抖音，捕获 DouyinApiException 映射为带 errorCode 的 BusinessException
            throw mapProductError(e);
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("[活动商品列表] 异常, activityId={}, refresh={}", activityId, refresh, e);
            throw BusinessException.upstream(UpstreamErrorCode.EXTERNAL_GENERIC,
                    "活动商品查询失败: " + e.getMessage(), e);
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
                if (assignment.getActivityStatusSyncedAt() != null) {
                    item.put("activityStatusSyncedAt", assignment.getActivityStatusSyncedAt());
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
        String name = userDomainFacade.loadUserDisplayNamesByIds(List.of(userId)).get(userId);
        return name == null ? "" : name;
    }

    private BusinessException mapActivityError(DouyinApiException e) {
        String subCode = e.getSubCode() == null ? "" : e.getSubCode();
        // 改造后：错误码分类映射到 UpstreamErrorCode，前端可按 errorCode 分支提示
        if (e.getErrorCode() == 50002 && subCode.contains("4197")) {
            return BusinessException.upstream(UpstreamErrorCode.DOUYIN_TOKEN_INVALID,
                    "当前账号未完成招商团长授权，请检查抖店授权状态");
        }
        if (e.getErrorCode() == 50002 && subCode.contains("4200")) {
            return BusinessException.upstream(UpstreamErrorCode.DOUYIN_TOKEN_INVALID,
                    "抖店账号状态异常，请检查账号可用状态");
        }
        if (e.getErrorCode() == 40004 && subCode.contains("257")) {
            return BusinessException.param("查询参数不合法，请检查筛选条件");
        }
        if (e.getErrorCode() == 20000 && subCode.contains("256")) {
            return BusinessException.upstream(UpstreamErrorCode.UPSTREAM_SERVICE_ERROR,
                    "抖店服务异常，请稍后重试");
        }
        if (e.getErrorCode() == 429) {
            return BusinessException.upstream(UpstreamErrorCode.UPSTREAM_RATE_LIMIT,
                    "抖店接口限流，请稍后重试");
        }
        return BusinessException.upstream(UpstreamErrorCode.UPSTREAM_SERVICE_ERROR,
                "团长活动查询失败: " + e.getErrorMsg());
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
            return BusinessException.upstream(UpstreamErrorCode.DOUYIN_TOKEN_INVALID,
                    "当前账号未完成招商团长授权，请检查抖店授权状态");
        }
        if (e.getErrorCode() == 50002 && subCode.contains("4200")) {
            return BusinessException.upstream(UpstreamErrorCode.DOUYIN_TOKEN_INVALID,
                    "抖店账号状态异常，请检查账号可用状态");
        }
        if (e.getErrorCode() == 50002 && subCode.contains("257")) {
            return BusinessException.param("查询参数不合法，请检查筛选条件");
        }
        if (e.getErrorCode() == 20000 && subCode.contains("256")) {
            return BusinessException.upstream(UpstreamErrorCode.UPSTREAM_SERVICE_ERROR,
                    "抖店服务异常，请稍后重试");
        }
        if (e.getErrorCode() == 429) {
            return BusinessException.upstream(UpstreamErrorCode.UPSTREAM_RATE_LIMIT,
                    "抖店接口限流，请稍后重试");
        }
        return BusinessException.upstream(UpstreamErrorCode.UPSTREAM_SERVICE_ERROR,
                "活动商品查询失败: " + e.getErrorMsg());
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

    public static class ProductSyncRequest {
        private String syncMode;
        private Integer maxRowsPerActivity;
        private List<Integer> priorityStatuses;

        public String getSyncMode() {
            return syncMode;
        }

        public void setSyncMode(String syncMode) {
            this.syncMode = syncMode;
        }

        public Integer getMaxRowsPerActivity() {
            return maxRowsPerActivity;
        }

        public void setMaxRowsPerActivity(Integer maxRowsPerActivity) {
            this.maxRowsPerActivity = maxRowsPerActivity;
        }

        public List<Integer> getPriorityStatuses() {
            return priorityStatuses;
        }

        public void setPriorityStatuses(List<Integer> priorityStatuses) {
            this.priorityStatuses = priorityStatuses;
        }
    }

    // ==================== 活动列表异步同步 ====================

    @Operation(summary = "触发活动列表异步同步", description = "异步拉取抖店活动列表，更新本地活动状态/名称/时间窗口。返回 jobId 用于轮询状态。")
    @PostMapping("/sync")
    @RequirePermission("colonel-activity:trigger-activity-list-sync")
    public ApiResult<?> triggerActivityListSync(
            @RequestAttribute(value = "userId", required = false) UUID userId) {
        ColonelActivityListSyncService.SyncTriggerResult result = activityListSyncService.triggerSync(userId);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("jobId", result.jobId());
        payload.put("syncStatus", result.status());
        payload.put("reused", result.reused());
        if (result.message() != null) {
            payload.put("message", result.message());
        }
        return ok(payload);
    }

    @Operation(summary = "查询活动列表同步任务状态", description = "根据 jobId 轮询活动列表同步进度。")
    @GetMapping("/sync-jobs/{jobId}")
    public ApiResult<?> getActivityListSyncStatus(@PathVariable String jobId) {
        ColonelActivityListSyncService.SyncJobStatus status = activityListSyncService.getJobStatus(jobId);
        if (status == null) {
            throw BusinessException.notFound("同步任务不存在");
        }
        return ok(status);
    }
}
