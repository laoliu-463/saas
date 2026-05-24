package com.colonel.saas.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.common.base.BaseController;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.common.result.PageResult;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.entity.Product;
import com.colonel.saas.gateway.douyin.DouyinPromotionGateway;
import com.colonel.saas.dto.product.ProductFilterOptionItem;
import com.colonel.saas.dto.product.ProductFilterOptionsDTO;
import com.colonel.saas.dto.product.QuickSampleApplyRequest;
import com.colonel.saas.dto.product.QuickSampleApplyResponse;
import com.colonel.saas.entity.ColonelPartner;
import com.colonel.saas.service.ColonelPartnerSyncService;
import com.colonel.saas.service.ProductQuickSampleService;
import com.colonel.saas.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
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
import java.util.List;
import java.util.UUID;

@Validated
@Tag(name = "商品管理（已废弃）", description = "旧版商品兼容接口，仅用于平滑过渡。请优先使用团长活动商品主链路接口。")
@RestController
@RequestMapping("/products")
@RequireRoles({RoleCodes.BIZ_LEADER, RoleCodes.BIZ_STAFF, RoleCodes.CHANNEL_LEADER, RoleCodes.CHANNEL_STAFF})
@Deprecated(since = "2026-04-24", forRemoval = false)
public class ProductController extends BaseController {

    private final ProductService productService;
    private final ProductQuickSampleService productQuickSampleService;
    private final ColonelPartnerSyncService colonelPartnerSyncService;

    public ProductController(
            ProductService productService,
            ProductQuickSampleService productQuickSampleService,
            ColonelPartnerSyncService colonelPartnerSyncService) {
        this.productService = productService;
        this.productQuickSampleService = productQuickSampleService;
        this.colonelPartnerSyncService = colonelPartnerSyncService;
    }

    @Operation(summary = "商品库分页", description = "查询已从选品库沉淀到共享商品库的商品列表，对全员可见。")
    @RequireRoles({RoleCodes.BIZ_LEADER, RoleCodes.BIZ_STAFF, RoleCodes.CHANNEL_LEADER, RoleCodes.CHANNEL_STAFF})
    @GetMapping
    public ApiResult<PageResult<Product>> page(
            @Parameter(description = "页码，从 1 开始。") @RequestParam(defaultValue = "1") @Min(1) long page,
            @Parameter(description = "每页条数。") @RequestParam(defaultValue = "20") @Min(1) @Max(100) long size,
            @Parameter(description = "商品状态。待确认：取值含义请联系产品。") @RequestParam(required = false) Integer status,
            @Parameter(description = "商品关键字，可匹配商品名称、商品 ID、店铺。") @RequestParam(required = false) String keyword,
            @Parameter(description = "店铺 / 合作方名称关键字。") @RequestParam(required = false) String shopKeyword,
            @Parameter(description = "抖音同步类目关键字（单选兼容）。") @RequestParam(required = false) String categoryName,
            @Parameter(description = "商品类目多选，英文逗号分隔。") @RequestParam(required = false) String categories,
            @Parameter(description = "来源活动 ID。") @RequestParam(required = false) String activityId,
            @Parameter(description = "招商负责人用户 ID。") @RequestParam(required = false) String assigneeId,
            @Parameter(description = "服务费率区间：gt20/10_20/lt10。") @RequestParam(required = false) String serviceFee,
            @Parameter(description = "是否支持投流：1/0。") @RequestParam(required = false) String supportsAds,
            @Parameter(description = "近 30 天销量区间：lt100/100_999/1k_29k/gte30000。") @RequestParam(required = false) String salesRange,
            @Parameter(description = "转链状态：PENDING/LINKED/FAILED。") @RequestParam(required = false) String promotionLink,
            @Parameter(description = "联盟推广状态：promoting/pending_audit/rejected/terminated/expired。") @RequestParam(required = false) String allianceStatus,
            @Parameter(description = "佣金区间：gt20/10_20/lt10。") @RequestParam(required = false) String commission,
            @Parameter(description = "是否有寄样规则：1/0。") @RequestParam(required = false) String hasSample,
            @Parameter(description = "负责人过滤：assigned/unassigned。") @RequestParam(required = false) String assignee,
            @Parameter(description = "系统标签：high_commission/traffic/new/high_price。") @RequestParam(required = false) String systemTag,
            @Parameter(description = "推进判断：MAIN/SECONDARY/PAUSE/DROP/NONE。") @RequestParam(required = false) String decision,
            @Parameter(description = "合作方 ID；商家型为 shop_id，团长型为 colonel_buyin_id。") @RequestParam(required = false) String partnerId,
            @Parameter(description = "合作方类型：MERCHANT/COLONEL。") @RequestParam(name = "partnerType", required = false) String partnerType,
            @Parameter(description = "排序：default（置顶优先）/ latest（晚上架优先）。") @RequestParam(name = "sortBy", required = false) String sortBy,
            @Parameter(description = "货品标签，多选时用英文逗号分隔。") @RequestParam(required = false) String goodsTags,
            @Parameter(description = "商品标签，多选时用英文逗号分隔。") @RequestParam(required = false) String productTags,
            @Parameter(description = "团长名称关键字。") @RequestParam(required = false) String colonelName,
            @Parameter(description = "是否已发布转链：1/0。") @RequestParam(required = false) String published,
            @Parameter(description = "合作类型。") @RequestParam(required = false) String cooperationType,
            @Parameter(description = "直播价格下限。") @RequestParam(required = false) String livePriceMin,
            @Parameter(description = "直播价格上限。") @RequestParam(required = false) String livePriceMax,
            @Parameter(description = "佣金率下限。") @RequestParam(required = false) String commissionMin,
            @Parameter(description = "佣金率上限。") @RequestParam(required = false) String commissionMax,
            @Parameter(description = "寄样销售额下限。") @RequestParam(required = false) String sampleSalesMin,
            @Parameter(description = "寄样销售额上限。") @RequestParam(required = false) String sampleSalesMax,
            @Parameter(description = "素材下载：1/0。") @RequestParam(required = false) String materialDownload,
            @Parameter(description = "专属价：1/0。") @RequestParam(required = false) String exclusivePrice,
            @Parameter(description = "商品链组：1/0。") @RequestParam(required = false) String productChain,
            @Parameter(description = "手卡：1/0。") @RequestParam(required = false) String handCard,
            @Parameter(description = "双佣金：1/0。") @RequestParam(required = false) String doubleCommission,
            @Parameter(description = "仅未加入货盘：1/0。") @RequestParam(required = false) String notInLibrary,
            @Parameter(description = "选品去重：1/0。") @RequestParam(required = false) String dedup,
            @Parameter(description = "招商活动ID。") @RequestParam(required = false) String recruitActivityId,
            @Parameter(description = "招商活动名称关键字。") @RequestParam(required = false) String recruitActivityName,
            @Parameter(description = "是否已挂车：1/0。") @RequestParam(required = false) String listed,
            @Parameter(description = "是否有免费样：1/0。") @RequestParam(required = false) String freeSample) {
        IPage<Product> result = productService.getSelectedLibraryPage(
                page,
                size,
                new ProductService.SelectedLibraryFilter(
                        keyword,
                        status,
                        shopKeyword,
                        categoryName,
                        categories,
                        activityId,
                        assigneeId,
                        serviceFee,
                        supportsAds,
                        salesRange,
                        promotionLink,
                        allianceStatus,
                        commission,
                        hasSample,
                        assignee,
                        systemTag,
                        decision,
                        partnerId,
                        partnerType,
                        sortBy,
                        goodsTags,
                        productTags,
                        colonelName,
                        published,
                        cooperationType,
                        livePriceMin,
                        livePriceMax,
                        commissionMin,
                        commissionMax,
                        sampleSalesMin,
                        sampleSalesMax,
                        materialDownload,
                        exclusivePrice,
                        productChain,
                        handCard,
                        doubleCommission,
                        notInLibrary,
                        dedup,
                        recruitActivityId,
                        recruitActivityName,
                        listed,
                        freeSample
                )
        );
        return okPage(result);
    }

    @Operation(summary = "快速寄样", description = "商品库弹窗式快速寄样，支持私海达人多选逐个创建寄样申请。")
    @RequireRoles({RoleCodes.CHANNEL_LEADER, RoleCodes.CHANNEL_STAFF})
    @PostMapping({"/{relationId}/quick-sample", "/{relationId}/quick-sample-apply"})
    public ApiResult<QuickSampleApplyResponse> quickSample(
            @Parameter(description = "商品关联主键（product_snapshot.id）。") @PathVariable UUID relationId,
            @Valid @RequestBody QuickSampleApplyRequest request,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "roleCodes", required = false) List<String> roleCodes) {
        return ok(productQuickSampleService.applyQuickSample(relationId, request, userId, deptId, roleCodes));
    }

    @Operation(summary = "商品库类目选项", description = "从当前展示中的商品库记录动态聚合类目，供筛选多选使用。")
    @GetMapping("/categories")
    public ApiResult<List<String>> libraryCategories() {
        return ok(productService.listLibraryCategories());
    }

    @Operation(summary = "商品库筛选项", description = "返回商品库动态筛选项，当前包含类目。")
    @GetMapping("/filter-options")
    public ApiResult<ProductFilterOptionsDTO> filterOptions() {
        List<ProductFilterOptionItem> categories = productService.listLibraryCategories().stream()
                .map(category -> new ProductFilterOptionItem(category, category, null))
                .toList();
        return ok(new ProductFilterOptionsDTO(categories));
    }

    @Deprecated(since = "2026-05-24", forRemoval = true)
    @Operation(summary = "[已废弃] 商品库团长筛选项", description = "请改用 GET /colonel/partners。前端商品库已走 colonel 主数据路径。")
    @GetMapping("/filter-options/colonel-partners")
    public ApiResult<List<ColonelPartner>> colonelPartnerFilterOptions(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "50") int limit) {
        return ok(colonelPartnerSyncService.listByNameKeyword(keyword, limit));
    }

    @Operation(summary = "[已废弃] 选品候选分页", description = "兼容 Token 缺失场景下的本地选品候选列表。")
    @GetMapping("/picks")
    public ApiResult<PageResult<Product>> pickPage(
            @Parameter(description = "页码，从 1 开始。") @RequestParam(defaultValue = "1") @Min(1) long page,
            @Parameter(description = "每页条数。") @RequestParam(defaultValue = "20") @Min(1) @Max(100) long size,
            @Parameter(description = "商品状态。待确认：取值含义请联系产品。") @RequestParam(required = false) Integer status,
            @RequestAttribute(value = "userId", required = false) UUID userId,
            @RequestAttribute(value = "roleCodes", required = false) List<String> roleCodes) {
        UUID assigneeId = shouldLimitPickPageToSelf(roleCodes) ? userId : null;
        return okPage(productService.getPage(page, size, status, assigneeId));
    }

    @Operation(summary = "[已废弃] 商品详情", description = "兼容旧版商品详情查询。请迁移到 /colonel/activities/{activityId}/products/{productId}。")
    @RequireRoles({RoleCodes.BIZ_LEADER, RoleCodes.BIZ_STAFF, RoleCodes.CHANNEL_LEADER, RoleCodes.CHANNEL_STAFF})
    @GetMapping("/{id}")
    public ApiResult<Product> detail(@Parameter(description = "商品主键 ID，使用 UUID 格式。") @PathVariable UUID id) {
        return ok(productService.getById(id));
    }

    @Operation(summary = "[已废弃] 商品绑定活动", description = "兼容旧版商品绑定活动入口。请迁移到 /colonel/activities/{activityId}/products/{productId}/bind-activity。")
    @RequireRoles({RoleCodes.BIZ_LEADER})
    @PutMapping("/{id}/activity")
    public ApiResult<Product> bindActivity(
            @Parameter(description = "商品主键 ID，使用 UUID 格式。") @PathVariable UUID id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "绑定活动请求体。",
                    required = true,
                    content = @Content(examples = @ExampleObject(value = "{\"activityId\":\"11111111-1111-1111-1111-111111111111\"}"))
            )
            @Valid @RequestBody BindActivityRequest request,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId) {
        return ok(productService.bindActivity(id, request.getActivityId(), userId, deptId));
    }

    @Operation(summary = "[已废弃] 商品分配招商", description = "兼容旧版商品分配招商入口。请迁移到 /colonel/activities/{activityId}/products/{productId}/assignee。")
    @RequireRoles({RoleCodes.BIZ_LEADER})
    @PutMapping("/{id}/assignee")
    public ApiResult<Product> assign(
            @Parameter(description = "商品主键 ID，使用 UUID 格式。") @PathVariable UUID id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "指定招商负责人。",
                    required = true,
                    content = @Content(examples = @ExampleObject(value = "{\"assigneeId\":\"22222222-2222-2222-2222-222222222222\"}"))
            )
            @Valid @RequestBody AssignProductRequest request) {
        return ok(productService.assignProduct(id, request.getAssigneeId()));
    }

    @Operation(summary = "[已废弃] 商品审核", description = "兼容旧版商品审核入口。请迁移到 /colonel/activities/{activityId}/products/{productId}/audit-result。")
    @RequireRoles({RoleCodes.BIZ_STAFF})
    @PutMapping("/{id}/audit-result")
    public ApiResult<Product> audit(
            @Parameter(description = "商品主键 ID，使用 UUID 格式。") @PathVariable UUID id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "审核结果请求体。",
                    required = true,
                    content = @Content(examples = @ExampleObject(value = "{\"approved\":true,\"reason\":\"素材完整，允许推进\"}"))
            )
            @Valid @RequestBody AuditProductRequest request) {
        return ok(productService.auditProduct(id, request.isApproved(), request.getReason()));
    }

    @Operation(summary = "[已废弃] 商品转链", description = "兼容旧版商品转链入口。请迁移到 /colonel/activities/{activityId}/products/{productId}/promotion-links。")
    @RequireRoles({RoleCodes.CHANNEL_LEADER, RoleCodes.CHANNEL_STAFF})
    @PostMapping("/{id}/promotion-links")
    public ApiResult<DouyinPromotionGateway.PromotionLinkResult> generatePromotionLink(
            @Parameter(description = "商品主键 ID，使用 UUID 格式。") @PathVariable UUID id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "转链请求体，可为空；为空时使用默认场景。",
                    content = @Content(examples = @ExampleObject(value = "{\"scene\":\"PRODUCT_LIBRARY\",\"talentId\":\"test_talent_001\"}"))
            )
            @RequestBody(required = false) PromotionLinkRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId) {
        PromotionLinkRequest safeRequest = request == null ? new PromotionLinkRequest() : request;
        DouyinPromotionGateway.PromotionLinkResult result = productService.generatePromotionLink(
                id,
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

    @Operation(summary = "[已废弃] 商品推广记录", description = "兼容旧版商品库按商品ID读取历史推广记录。")
    @RequireRoles({RoleCodes.BIZ_LEADER, RoleCodes.BIZ_STAFF, RoleCodes.CHANNEL_LEADER, RoleCodes.CHANNEL_STAFF})
    @GetMapping("/{productId}/promotion-links/history")
    public ApiResult<PageResult<java.util.Map<String, Object>>> promotionLinkHistory(
            @Parameter(description = "业务商品 ID。") @PathVariable String productId,
            @Parameter(description = "页码，从 1 开始。") @RequestParam(defaultValue = "1") @Min(1) long page,
            @Parameter(description = "每页条数。") @RequestParam(defaultValue = "20") @Min(1) @Max(100) long size) {
        return ok(productService.getPromotionLinkHistory(productId, page, size));
    }

    @Operation(summary = "[已废弃] 商品达人跟进", description = "兼容旧版商品达人跟进入口。请逐步迁移到商品主链路跟进入口。")
    @RequireRoles({RoleCodes.CHANNEL_LEADER, RoleCodes.CHANNEL_STAFF})
    @PostMapping("/{id}/follow")
    public ApiResult<java.util.Map<String, Object>> follow(
            @Parameter(description = "商品主键 ID，使用 UUID 格式。") @PathVariable UUID id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "达人跟进请求体。",
                    required = true,
                    content = @Content(examples = @ExampleObject(value = "{\"talentName\":\"达人A\",\"followStatus\":\"FOLLOWING\",\"content\":\"已加微信跟进\",\"operatorName\":\"渠道A\"}"))
            )
            @Valid @RequestBody TalentFollowRequest request,
            @RequestAttribute(value = "userId", required = false) UUID userId) {
        return ok(productService.startTalentFollow(
                id,
                request.getTalentId(),
                request.getTalentName(),
                request.getFollowStatus(),
                request.getContent(),
                request.getNextFollowTime(),
                userId,
                request.getOperatorName()
        ));
    }

    public static class BindActivityRequest {
        @Schema(description = "活动主键 ID，使用 UUID 格式。", example = "11111111-1111-1111-1111-111111111111")
        @NotNull(message = "activityId 不能为空")
        private UUID activityId;

        public UUID getActivityId() {
            return activityId;
        }

        public void setActivityId(UUID activityId) {
            this.activityId = activityId;
        }
    }

    public static class AssignProductRequest {
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

    public static class AuditProductRequest {
        @Schema(description = "是否审核通过。", example = "true")
        private boolean approved;

        @Schema(description = "审核备注，驳回时建议填写原因。", example = "素材完整，允许推进")
        private String reason;

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
    }

    public static class PromotionLinkRequest {
        @Schema(description = "外部幂等标识，不传则由系统按默认逻辑生成。", example = "external-unique-id-001")
        private String externalUniqueId;

        @Schema(description = "推广场景编码。待确认：取值含义请联系产品。", example = "4")
        private Integer promotionScene = 4;

        @Schema(description = "是否需要短链。", example = "true")
        private Boolean needShortLink = Boolean.TRUE;

        @Schema(description = "业务场景标识。", example = "PRODUCT_LIBRARY")
        private String scene = "PRODUCT_LIBRARY";

        @Schema(description = "达人标识，用于特定转链场景。", example = "test_talent_001")
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

    private boolean shouldLimitPickPageToSelf(List<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return false;
        }
        List<String> normalized = roleCodes.stream()
                .map(String::toLowerCase)
                .toList();
        if (normalized.contains(RoleCodes.ADMIN) || normalized.contains(RoleCodes.BIZ_LEADER)) {
            return false;
        }
        return normalized.contains(RoleCodes.BIZ_STAFF);
    }
}
