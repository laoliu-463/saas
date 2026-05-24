package com.colonel.saas.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.common.base.BaseController;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.common.result.PageResult;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.auth.service.SysUserService;
import com.colonel.saas.entity.ProductOperationLog;
import com.colonel.saas.gateway.douyin.DouyinPromotionGateway;
import com.colonel.saas.service.ProductPinService;
import com.colonel.saas.service.ProductService;
import com.colonel.saas.service.PromotionCopyBriefService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Validated
@RestController
@Tag(name = "活动商品主链路", description = "团长活动下商品的详情、绑定、分配、审核、转链、达人跟进与操作日志接口。")
@RequestMapping("/colonel/activities/{activityId}/products")
@RequireRoles({RoleCodes.BIZ_LEADER, RoleCodes.BIZ_STAFF, RoleCodes.CHANNEL_LEADER, RoleCodes.CHANNEL_STAFF, RoleCodes.ADMIN, RoleCodes.COLONEL_LEADER})
public class ColonelActivityProductController extends BaseController {

    private final ProductService productService;
    private final ProductPinService productPinService;
    private final PromotionCopyBriefService promotionCopyBriefService;
    private final SysUserService sysUserService;

    public ColonelActivityProductController(
            ProductService productService,
            ProductPinService productPinService,
            PromotionCopyBriefService promotionCopyBriefService,
            SysUserService sysUserService) {
        this.productService = productService;
        this.productPinService = productPinService;
        this.promotionCopyBriefService = promotionCopyBriefService;
        this.sysUserService = sysUserService;
    }

    @Operation(summary = "活动商品详情", description = "查询指定活动下单个商品的业务详情。")
    @GetMapping("/{productId}")
    public ApiResult<Map<String, Object>> detail(
            @Parameter(description = "团长活动 ID。") @PathVariable String activityId,
            @Parameter(description = "商品 ID。") @PathVariable String productId) {
        return ok(productService.getActivityProductDetail(activityId, productId));
    }

    @Operation(summary = "活动商品 SKU 列表", description = "调用抖店 buyin.productSkus.v2 查询商品规格 SKU。")
    @GetMapping("/{productId}/skus")
    public ApiResult<List<Map<String, Object>>> skus(
            @Parameter(description = "团长活动 ID。") @PathVariable String activityId,
            @Parameter(description = "商品 ID。") @PathVariable String productId) {
        return ok(productService.listActivityProductSkus(productId));
    }

    @Operation(summary = "活动商品绑定活动", description = "为活动商品补绑或修正关联活动。")
    @RequireRoles({RoleCodes.BIZ_LEADER, RoleCodes.COLONEL_LEADER})
    @PutMapping("/{productId}/bind-activity")
    public ApiResult<Map<String, Object>> bindActivity(
            @Parameter(description = "团长活动 ID。") @PathVariable String activityId,
            @Parameter(description = "商品 ID。") @PathVariable String productId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "活动绑定请求体。",
                    required = true,
                    content = @Content(examples = @ExampleObject(value = "{\"boundActivityId\":\"ACTIVITY_001\"}"))
            )
            @Valid @RequestBody BindActivityRequest request,
            @RequestAttribute(value = "userId", required = false) UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId) {
        return ok(productService.bindActivity(activityId, productId, request.getBoundActivityId(), userId, deptId));
    }

    @Operation(summary = "活动商品分配招商", description = "为活动商品指定招商负责人。")
    @RequireRoles({RoleCodes.BIZ_LEADER, RoleCodes.COLONEL_LEADER})
    @PutMapping("/{productId}/assignee")
    public ApiResult<Map<String, Object>> assign(
            @Parameter(description = "团长活动 ID。") @PathVariable String activityId,
            @Parameter(description = "商品 ID。") @PathVariable String productId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "招商负责人分配请求体。",
                    required = true,
                    content = @Content(examples = @ExampleObject(value = "{\"assigneeId\":\"22222222-2222-2222-2222-222222222222\"}"))
            )
            @Valid @RequestBody AssignRequest request,
            @RequestAttribute(value = "userId", required = false) UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "roleCodes", required = false) List<String> roleCodes) {
        sysUserService.assertAssignableUser(request.getAssigneeId(), roleCodes, deptId);
        return ok(productService.assignProduct(activityId, productId, request.getAssigneeId(), userId, deptId));
    }

    @Operation(summary = "活动商品分配审核人", description = "为待审核活动商品指定招商专员审核负责人，不改变商品主状态。")
    @RequireRoles({RoleCodes.BIZ_LEADER, RoleCodes.COLONEL_LEADER})
    @PutMapping("/{productId}/audit-assignee")
    public ApiResult<Map<String, Object>> assignAuditOwner(
            @Parameter(description = "团长活动 ID。") @PathVariable String activityId,
            @Parameter(description = "商品 ID。") @PathVariable String productId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "审核负责人分配请求体。",
                    required = true,
                    content = @Content(examples = @ExampleObject(value = "{\"assigneeId\":\"22222222-2222-2222-2222-222222222222\"}"))
            )
            @Valid @RequestBody AssignRequest request,
            @RequestAttribute(value = "userId", required = false) UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "roleCodes", required = false) List<String> roleCodes) {
        sysUserService.assertAssignableUser(request.getAssigneeId(), roleCodes, deptId);
        return ok(productService.assignAuditOwner(activityId, productId, request.getAssigneeId(), userId, deptId));
    }

    @Operation(summary = "活动商品审核", description = "提交活动商品审核结果。")
    @RequireRoles({RoleCodes.BIZ_STAFF})
    @PutMapping("/{productId}/audit-result")
    public ApiResult<Map<String, Object>> audit(
            @Parameter(description = "团长活动 ID。") @PathVariable String activityId,
            @Parameter(description = "商品 ID。") @PathVariable String productId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "审核结果请求体。",
                    required = true,
                    content = @Content(examples = @ExampleObject(value = "{\"approved\":true,\"reason\":\"素材完整，允许推进\"}"))
            )
            @Valid @RequestBody AuditRequest request,
            @RequestAttribute(value = "userId", required = false) UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId) {
        return ok(productService.auditProduct(
                activityId,
                productId,
                request.isApproved(),
                request.getReason(),
                request.toSupplementMap(),
                userId,
                deptId
        ));
    }

    @Operation(summary = "活动商品推进判断", description = "记录商品主推、次推、暂缓或放弃等人工推进判断，不改变商品主状态。")
    @RequireRoles({RoleCodes.BIZ_STAFF})
    @PutMapping("/{productId}/decision")
    public ApiResult<Map<String, Object>> decision(
            @Parameter(description = "团长活动 ID。") @PathVariable String activityId,
            @Parameter(description = "商品 ID。") @PathVariable String productId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "推进判断请求体。",
                    required = true,
                    content = @Content(examples = @ExampleObject(value = "{\"decisionLevel\":\"MAIN\",\"reason\":\"佣金高，适合优先推\"}"))
            )
            @Valid @RequestBody DecisionRequest request,
            @RequestAttribute(value = "userId", required = false) UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId) {
        return ok(productService.recordProductDecision(
                activityId,
                productId,
                request.getDecisionLevel(),
                request.getReason(),
                userId,
                deptId
        ));
    }

    @Operation(summary = "活动商品转链", description = "为活动商品生成推广链接。")
    @RequireRoles({RoleCodes.CHANNEL_LEADER, RoleCodes.CHANNEL_STAFF})
    @PostMapping("/{productId}/promotion-links")
    public ApiResult<DouyinPromotionGateway.PromotionLinkResult> generatePromotionLink(
            @Parameter(description = "团长活动 ID。") @PathVariable String activityId,
            @Parameter(description = "商品 ID。") @PathVariable String productId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "转链请求体，可为空；为空时使用默认场景。",
                    content = @Content(examples = @ExampleObject(value = "{\"scene\":\"PRODUCT_LIBRARY\",\"talentId\":\"test_talent_001\"}"))
            )
            @RequestBody(required = false) PromotionLinkRequest request,
            @Parameter(description = "幂等键，相同键在 24h 内重复提交将返回首次转链结果。")
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(value = "userId", required = false) UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId) {
        PromotionLinkRequest safeRequest = request == null ? new PromotionLinkRequest() : request;
        DouyinPromotionGateway.PromotionLinkResult result = productService.generatePromotionLink(
                activityId,
                productId,
                userId,
                deptId,
                safeRequest.getExternalUniqueId(),
                safeRequest.getPromotionScene(),
                safeRequest.getNeedShortLink(),
                safeRequest.getScene(),
                safeRequest.getTalentId(),
                idempotencyKey
        );
        return ok(result);
    }

    @Operation(summary = "活动商品达人跟进", description = "记录活动商品的达人跟进信息，用于达人侧协作与后续回访。")
    @RequireRoles({RoleCodes.CHANNEL_LEADER, RoleCodes.CHANNEL_STAFF})
    @PostMapping("/{productId}/follow")
    public ApiResult<Map<String, Object>> follow(
            @Parameter(description = "团长活动 ID。") @PathVariable String activityId,
            @Parameter(description = "商品 ID。") @PathVariable String productId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "达人跟进请求体。",
                    required = true,
                    content = @Content(examples = @ExampleObject(value = "{\"talentName\":\"达人A\",\"followStatus\":\"FOLLOWING\",\"content\":\"已加微信跟进\",\"operatorName\":\"渠道A\"}"))
            )
            @Valid @RequestBody TalentFollowRequest request,
            @RequestAttribute(value = "userId", required = false) UUID userId) {
        return ok(productService.startTalentFollow(
                activityId,
                productId,
                request.getTalentId(),
                request.getTalentName(),
                request.getFollowStatus(),
                request.getContent(),
                request.getNextFollowTime(),
                userId,
                request.getOperatorName()
        ));
    }

    @Operation(summary = "活动商品操作日志", description = "分页查询活动商品操作日志。")
    @GetMapping("/{productId}/operation-logs")
    public ApiResult<PageResult<ProductOperationLog>> operationLogs(
            @Parameter(description = "团长活动 ID。") @PathVariable String activityId,
            @Parameter(description = "商品 ID。") @PathVariable String productId,
            @Parameter(description = "页码，从 1 开始。") @RequestParam(defaultValue = "1") long page,
            @Parameter(description = "每页条数。") @RequestParam(defaultValue = "20") long size) {
        IPage<ProductOperationLog> result = productService.getOperationLogs(activityId, productId, page, size);
        return okPage(result);
    }

    @Deprecated(since = "2026-05-24", forRemoval = true)
    @Operation(summary = "[已废弃] 渲染复制讲解文案", description = "请改用 POST .../promotion-links + 前端模板渲染。保留仅为兼容旧调用。")
    @GetMapping("/{productId}/copy-brief")
    public ApiResult<Map<String, String>> renderCopyBrief(
            @Parameter(description = "团长活动 ID。") @PathVariable String activityId,
            @Parameter(description = "商品 ID。") @PathVariable String productId,
            @RequestParam(required = false) String productName,
            @RequestParam(required = false) String commissionRate,
            @RequestParam(required = false) String shortLink,
            @RequestParam(required = false) String pickSource) {
        String text = promotionCopyBriefService.render(productName, commissionRate, shortLink, pickSource);
        return ok(Map.of(
                "activityId", activityId,
                "productId", productId,
                "text", text));
    }

    @Operation(summary = "招商置顶商品", description = "置顶 24 小时，每位招商最多 10 个规格（P-05）。")
    @RequireRoles({RoleCodes.BIZ_LEADER, RoleCodes.BIZ_STAFF})
    @PostMapping("/{productId}/pin")
    public ApiResult<Map<String, Object>> pinProduct(
            @Parameter(description = "团长活动 ID。") @PathVariable String activityId,
            @Parameter(description = "商品 ID。") @PathVariable String productId,
            @RequestAttribute("userId") UUID userId) {
        var state = productPinService.pin(activityId, productId, userId);
        return ok(Map.of(
                "activityId", activityId,
                "productId", productId,
                "pinned", true,
                "pinnedUntil", state.getPinnedUntil()));
    }

    @Operation(summary = "取消招商置顶", description = "取消当前商品的置顶状态。")
    @RequireRoles({RoleCodes.BIZ_LEADER, RoleCodes.BIZ_STAFF})
    @DeleteMapping("/{productId}/pin")
    public ApiResult<Map<String, Object>> unpinProduct(
            @Parameter(description = "团长活动 ID。") @PathVariable String activityId,
            @Parameter(description = "商品 ID。") @PathVariable String productId,
            @RequestAttribute("userId") UUID userId) {
        productPinService.unpin(activityId, productId, userId);
        return ok(Map.of("activityId", activityId, "productId", productId, "pinned", false));
    }

    @Operation(summary = "加入商品库", description = "将当前选品结果沉淀到共享商品库，供全员查看。")
    @RequireRoles({RoleCodes.BIZ_STAFF})
    @PostMapping("/{productId}/library-entry")
    public ApiResult<Map<String, Object>> putIntoLibrary(
            @Parameter(description = "团长活动 ID。") @PathVariable String activityId,
            @Parameter(description = "商品 ID。") @PathVariable String productId,
            @RequestAttribute(value = "userId", required = false) UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId) {
        return ok(productService.putIntoLibrary(activityId, productId, userId, deptId));
    }

    @Operation(summary = "批量分配招商", description = "批量为活动商品指定招商负责人；单个商品失败不影响其他商品。")
    @RequireRoles({RoleCodes.BIZ_LEADER, RoleCodes.COLONEL_LEADER})
    @PostMapping("/batch-assign")
    public ApiResult<Map<String, Object>> batchAssign(
            @Parameter(description = "团长活动 ID。") @PathVariable String activityId,
            @Valid @RequestBody BatchAssignRequest request,
            @RequestAttribute(value = "userId", required = false) UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "roleCodes", required = false) List<String> roleCodes) {
        sysUserService.assertAssignableUser(request.getAssigneeId(), roleCodes, deptId);
        return ok(runProductBatch(request.getProductIds(), productId ->
                productService.assignProduct(activityId, productId, request.getAssigneeId(), userId, deptId)));
    }

    @Operation(summary = "批量加入商品库", description = "批量将活动商品沉淀为共享商品库展示资产；单个商品失败不影响其他商品。")
    @RequireRoles({RoleCodes.BIZ_STAFF})
    @PostMapping("/batch-library-entry")
    public ApiResult<Map<String, Object>> batchPutIntoLibrary(
            @Parameter(description = "团长活动 ID。") @PathVariable String activityId,
            @Valid @RequestBody BatchProductIdsRequest request,
            @RequestAttribute(value = "userId", required = false) UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId) {
        return ok(runProductBatch(request.getProductIds(), productId ->
                productService.putIntoLibrary(activityId, productId, userId, deptId)));
    }

    @Operation(summary = "批量置顶商品", description = "批量置顶活动商品 24 小时；单个商品失败不影响其他商品。")
    @RequireRoles({RoleCodes.BIZ_LEADER, RoleCodes.BIZ_STAFF})
    @PostMapping("/batch-pin")
    public ApiResult<Map<String, Object>> batchPin(
            @Parameter(description = "团长活动 ID。") @PathVariable String activityId,
            @Valid @RequestBody BatchProductIdsRequest request,
            @RequestAttribute("userId") UUID userId) {
        return ok(runProductBatch(request.getProductIds(), productId -> {
            var state = productPinService.pin(activityId, productId, userId);
            return Map.of(
                    "activityId", activityId,
                    "productId", productId,
                    "pinned", true,
                    "pinnedUntil", state.getPinnedUntil());
        }));
    }

    private Map<String, Object> runProductBatch(List<String> rawProductIds, ProductBatchAction action) {
        List<String> productIds = normalizeBatchProductIds(rawProductIds);
        List<Map<String, Object>> items = new ArrayList<>();
        List<Map<String, Object>> failures = new ArrayList<>();
        int succeeded = 0;
        for (String productId : productIds) {
            try {
                Map<String, Object> data = action.apply(productId);
                items.add(Map.of(
                        "productId", productId,
                        "success", true,
                        "data", data == null ? Map.of() : data));
                succeeded++;
            } catch (Exception ex) {
                String reason = StringUtils.hasText(ex.getMessage()) ? ex.getMessage() : ex.getClass().getSimpleName();
                Map<String, Object> failure = Map.of(
                        "productId", productId,
                        "success", false,
                        "reason", reason);
                items.add(failure);
                failures.add(failure);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", productIds.size());
        result.put("succeeded", succeeded);
        result.put("failed", failures.size());
        result.put("items", items);
        result.put("failures", failures);
        return result;
    }

    private List<String> normalizeBatchProductIds(List<String> rawProductIds) {
        if (rawProductIds == null || rawProductIds.isEmpty()) {
            throw com.colonel.saas.common.exception.BusinessException.param("productIds 不能为空");
        }
        List<String> productIds = new ArrayList<>();
        for (String rawProductId : rawProductIds) {
            if (!StringUtils.hasText(rawProductId)) {
                continue;
            }
            String productId = rawProductId.trim();
            if (!productIds.contains(productId)) {
                productIds.add(productId);
            }
        }
        if (productIds.isEmpty()) {
            throw com.colonel.saas.common.exception.BusinessException.param("productIds 不能为空");
        }
        if (productIds.size() > 100) {
            throw com.colonel.saas.common.exception.BusinessException.param("单次批量商品操作最多 100 个");
        }
        return productIds;
    }

    @FunctionalInterface
    private interface ProductBatchAction {
        Map<String, Object> apply(String productId);
    }

    public static class BindActivityRequest {
        @Schema(description = "要绑定的活动 ID。", example = "ACTIVITY_001")
        @NotBlank(message = "boundActivityId 不能为空")
        private String boundActivityId;

        public String getBoundActivityId() {
            return boundActivityId;
        }

        public void setBoundActivityId(String boundActivityId) {
            this.boundActivityId = boundActivityId;
        }
    }

    public static class AssignRequest {
        @Schema(description = "招商负责人用户 ID，使用 UUID 格式。", example = "22222222-2222-2222-2222-222222222222")
        @NotNull(message = "assigneeId 不能为空")
        private UUID assigneeId;

        public UUID getAssigneeId() {
            return assigneeId;
        }

        public void setAssigneeId(UUID assigneeId) {
            this.assigneeId = assigneeId;
        }
    }

    public static class BatchProductIdsRequest {
        @Schema(description = "商品 ID 列表，单次最多 100 个。", example = "[\"9001\",\"9002\"]")
        private List<String> productIds;

        public List<String> getProductIds() {
            return productIds;
        }

        public void setProductIds(List<String> productIds) {
            this.productIds = productIds;
        }
    }

    public static class BatchAssignRequest extends BatchProductIdsRequest {
        @Schema(description = "招商负责人用户 ID，使用 UUID 格式。", example = "22222222-2222-2222-2222-222222222222")
        @NotNull(message = "assigneeId 不能为空")
        private UUID assigneeId;

        public UUID getAssigneeId() {
            return assigneeId;
        }

        public void setAssigneeId(UUID assigneeId) {
            this.assigneeId = assigneeId;
        }
    }

    public static class AuditRequest {
        @Schema(description = "是否审核通过。", example = "true")
        private boolean approved;

        @Schema(description = "审核备注。", example = "素材完整，允许推进")
        private String reason;

        @Schema(description = "专属价说明。", example = "直播间专属价 129 元，日常到手价 149 元。")
        private String exclusivePriceRemark;

        @Schema(description = "发货信息。", example = "48 小时内发货，江浙沪次日达。")
        private String shippingInfo;

        @Schema(description = "商品卖点列表。", example = "[\"高复购刚需品\", \"夏季场景强\", \"赠品感知明显\"]")
        private List<String> sellingPoints;

        @Schema(description = "推广话术。", example = "可主打复购和夏季囤货场景。")
        private String promotionScript;

        @Schema(description = "是否支持投流。", example = "true")
        private Boolean supportsAds;

        @Schema(description = "投流规则说明。", example = "投流比例1:0.5，保量10万曝光")
        private String adsRule;

        @Schema(description = "奖励说明。", example = "破 3 万 GMV 额外返 2 个点。")
        private String rewardRemark;

        @Schema(description = "参与要求。", example = "近 30 天食品饮料类目有成交。")
        private String participationRequirements;

        @Schema(description = "活动时间说明。", example = "4 月 1 日至 4 月 15 日，分两波开团。")
        private String campaignTimeRemark;

        @Schema(description = "手卡或素材文件列表。", example = "[\"https://example.com/material-1.png\"]")
        private List<String> materialFiles;

        @Schema(description = "货品标签列表。", example = "[\"家居\", \"零食\"]")
        private List<String> goodsTags;

        @Schema(description = "商品标签列表。", example = "[\"主推\", \"商品链组\"]")
        private List<String> productTags;

        @Schema(description = "30天销售额门槛。", example = "30000")
        private Long sampleThresholdSales;

        @Schema(description = "达人等级门槛。", example = "1")
        private Integer sampleThresholdLevel;

        @Schema(description = "寄样补充要求。", example = "需真人出镜，粉丝量>10万")
        private String sampleThresholdRemark;

        public boolean isApproved() {
            return approved;
        }

        public void setApproved(boolean approved) {
            this.approved = approved;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }

        public String getExclusivePriceRemark() {
            return exclusivePriceRemark;
        }

        public void setExclusivePriceRemark(String exclusivePriceRemark) {
            this.exclusivePriceRemark = exclusivePriceRemark;
        }

        public String getShippingInfo() {
            return shippingInfo;
        }

        public void setShippingInfo(String shippingInfo) {
            this.shippingInfo = shippingInfo;
        }

        public List<String> getSellingPoints() {
            return sellingPoints;
        }

        public void setSellingPoints(List<String> sellingPoints) {
            this.sellingPoints = sellingPoints;
        }

        public String getPromotionScript() {
            return promotionScript;
        }

        public void setPromotionScript(String promotionScript) {
            this.promotionScript = promotionScript;
        }

        public Boolean getSupportsAds() {
            return supportsAds;
        }

        public void setSupportsAds(Boolean supportsAds) {
            this.supportsAds = supportsAds;
        }

        public String getAdsRule() {
            return adsRule;
        }

        public void setAdsRule(String adsRule) {
            this.adsRule = adsRule;
        }

        public String getRewardRemark() {
            return rewardRemark;
        }

        public void setRewardRemark(String rewardRemark) {
            this.rewardRemark = rewardRemark;
        }

        public String getParticipationRequirements() {
            return participationRequirements;
        }

        public void setParticipationRequirements(String participationRequirements) {
            this.participationRequirements = participationRequirements;
        }

        public String getCampaignTimeRemark() {
            return campaignTimeRemark;
        }

        public void setCampaignTimeRemark(String campaignTimeRemark) {
            this.campaignTimeRemark = campaignTimeRemark;
        }

        public List<String> getMaterialFiles() {
            return materialFiles;
        }

        public void setMaterialFiles(List<String> materialFiles) {
            this.materialFiles = materialFiles;
        }

        public List<String> getGoodsTags() {
            return goodsTags;
        }

        public void setGoodsTags(List<String> goodsTags) {
            this.goodsTags = goodsTags;
        }

        public List<String> getProductTags() {
            return productTags;
        }

        public void setProductTags(List<String> productTags) {
            this.productTags = productTags;
        }

        public Long getSampleThresholdSales() {
            return sampleThresholdSales;
        }

        public void setSampleThresholdSales(Long sampleThresholdSales) {
            this.sampleThresholdSales = sampleThresholdSales;
        }

        public Integer getSampleThresholdLevel() {
            return sampleThresholdLevel;
        }

        public void setSampleThresholdLevel(Integer sampleThresholdLevel) {
            this.sampleThresholdLevel = sampleThresholdLevel;
        }

        public String getSampleThresholdRemark() {
            return sampleThresholdRemark;
        }

        public void setSampleThresholdRemark(String sampleThresholdRemark) {
            this.sampleThresholdRemark = sampleThresholdRemark;
        }

        public Map<String, Object> toSupplementMap() {
            Map<String, Object> supplement = new LinkedHashMap<>();
            putText(supplement, "exclusivePriceRemark", exclusivePriceRemark);
            putText(supplement, "shippingInfo", shippingInfo);
            putText(supplement, "promotionScript", promotionScript);
            putText(supplement, "rewardRemark", rewardRemark);
            putText(supplement, "participationRequirements", participationRequirements);
            putText(supplement, "campaignTimeRemark", campaignTimeRemark);
            putText(supplement, "sampleThresholdRemark", sampleThresholdRemark);
            if (supportsAds != null) {
                supplement.put("supportsAds", supportsAds);
            }
            putText(supplement, "adsRule", adsRule);
            if (sampleThresholdSales != null) {
                supplement.put("sampleThresholdSales", sampleThresholdSales);
            }
            if (sampleThresholdLevel != null) {
                supplement.put("sampleThresholdLevel", sampleThresholdLevel);
            }
            List<String> normalizedSellingPoints = normalizeList(sellingPoints);
            if (!normalizedSellingPoints.isEmpty()) {
                supplement.put("sellingPoints", normalizedSellingPoints);
            }
            List<String> normalizedMaterialFiles = normalizeList(materialFiles);
            if (!normalizedMaterialFiles.isEmpty()) {
                supplement.put("materialFiles", normalizedMaterialFiles);
            }
            List<String> normalizedGoodsTags = normalizeList(goodsTags);
            if (!normalizedGoodsTags.isEmpty()) {
                supplement.put("goodsTags", normalizedGoodsTags);
            }
            List<String> normalizedProductTags = normalizeList(productTags);
            if (!normalizedProductTags.isEmpty()) {
                supplement.put("productTags", normalizedProductTags);
            }
            return supplement;
        }

        private void putText(Map<String, Object> supplement, String key, String value) {
            if (StringUtils.hasText(value)) {
                supplement.put(key, value.trim());
            }
        }

        private List<String> normalizeList(List<String> values) {
            if (values == null || values.isEmpty()) {
                return List.of();
            }
            List<String> normalized = new ArrayList<>();
            for (String value : values) {
                if (StringUtils.hasText(value)) {
                    normalized.add(value.trim());
                }
            }
            return normalized;
        }
    }

    public static class DecisionRequest {
        @Schema(description = "推进判断。可选值：MAIN 主推，SECONDARY 次推，PAUSE 暂缓，DROP 放弃。", example = "MAIN")
        @NotBlank(message = "decisionLevel 不能为空")
        private String decisionLevel;

        @Schema(description = "判断原因。", example = "佣金高，适合优先推")
        @NotBlank(message = "reason 不能为空")
        private String reason;

        public String getDecisionLevel() {
            return decisionLevel;
        }

        public void setDecisionLevel(String decisionLevel) {
            this.decisionLevel = decisionLevel;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }
    }

    public static class PromotionLinkRequest {
        @Schema(description = "外部幂等标识，不传则按默认逻辑生成。", example = "external-unique-id-001")
        private String externalUniqueId;

        @Schema(description = "推广场景编码。待确认：取值含义请联系产品。", example = "4")
        private Integer promotionScene = 4;

        @Schema(description = "是否需要短链。", example = "true")
        private Boolean needShortLink = Boolean.TRUE;

        @Schema(description = "业务场景标识。", example = "PRODUCT_LIBRARY")
        private String scene = "PRODUCT_LIBRARY";

        @Schema(description = "达人标识。", example = "test_talent_001")
        private String talentId;

        public String getExternalUniqueId() {
            return externalUniqueId;
        }

        public void setExternalUniqueId(String externalUniqueId) {
            this.externalUniqueId = externalUniqueId;
        }

        public Integer getPromotionScene() {
            return promotionScene;
        }

        public void setPromotionScene(Integer promotionScene) {
            this.promotionScene = promotionScene;
        }

        public Boolean getNeedShortLink() {
            return needShortLink == null || needShortLink;
        }

        public void setNeedShortLink(Boolean needShortLink) {
            this.needShortLink = needShortLink;
        }

        public String getScene() {
            return scene;
        }

        public void setScene(String scene) {
            this.scene = scene;
        }

        public String getTalentId() {
            return talentId;
        }

        public void setTalentId(String talentId) {
            this.talentId = talentId;
        }
    }

    public static class TalentFollowRequest {
        @Schema(description = "达人主键 ID，使用 UUID 格式。", example = "33333333-3333-3333-3333-333333333333")
        private UUID talentId;

        @Schema(description = "达人名称。", example = "达人A")
        private String talentName;

        @Schema(description = "跟进状态。待确认：取值含义请联系产品。", example = "FOLLOWING")
        @NotBlank(message = "followStatus 不能为空")
        private String followStatus;

        @Schema(description = "跟进内容。", example = "已加微信跟进")
        private String content;

        @Schema(description = "下次跟进时间。", example = "2026-04-29T10:00:00")
        private LocalDateTime nextFollowTime;

        @Schema(description = "操作人显示名称。", example = "渠道A")
        private String operatorName;

        public UUID getTalentId() {
            return talentId;
        }

        public void setTalentId(UUID talentId) {
            this.talentId = talentId;
        }

        public String getTalentName() {
            return talentName;
        }

        public void setTalentName(String talentName) {
            this.talentName = talentName;
        }

        public String getFollowStatus() {
            return followStatus;
        }

        public void setFollowStatus(String followStatus) {
            this.followStatus = followStatus;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public LocalDateTime getNextFollowTime() {
            return nextFollowTime;
        }

        public void setNextFollowTime(LocalDateTime nextFollowTime) {
            this.nextFollowTime = nextFollowTime;
        }

        public String getOperatorName() {
            return operatorName;
        }

        public void setOperatorName(String operatorName) {
            this.operatorName = operatorName;
        }
    }
}
