package com.colonel.saas.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.common.base.BaseController;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.common.result.PageResult;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.entity.Product;
import com.colonel.saas.gateway.douyin.DouyinPromotionGateway;
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

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @Operation(summary = "商品库分页", description = "查询已从选品库沉淀到共享商品库的商品列表，对全员可见。")
    @RequireRoles({RoleCodes.BIZ_LEADER, RoleCodes.BIZ_STAFF, RoleCodes.CHANNEL_LEADER, RoleCodes.CHANNEL_STAFF})
    @GetMapping
    public ApiResult<PageResult<Product>> page(
            @Parameter(description = "页码，从 1 开始。") @RequestParam(defaultValue = "1") @Min(1) long page,
            @Parameter(description = "每页条数。") @RequestParam(defaultValue = "20") @Min(1) @Max(100) long size,
            @Parameter(description = "商品状态。待确认：取值含义请联系产品。") @RequestParam(required = false) Integer status,
            @Parameter(description = "商品关键字，可匹配商品名称。") @RequestParam(required = false) String keyword) {
        IPage<Product> result = productService.getSelectedLibraryPage(page, size, keyword, status);
        return okPage(result);
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
            @Valid @RequestBody BindActivityRequest request) {
        return ok(productService.bindActivity(id, request.getActivityId()));
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
                safeRequest.getTalentId()
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
