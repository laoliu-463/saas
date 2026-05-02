package com.colonel.saas.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.common.base.BaseController;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.common.result.PageResult;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.entity.ProductOperationLog;
import com.colonel.saas.gateway.douyin.DouyinPromotionGateway;
import com.colonel.saas.service.ProductService;
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
import java.util.Map;
import java.util.UUID;

@Validated
@RestController
@Tag(name = "活动商品主链路", description = "团长活动下商品的详情、绑定、分配、审核、转链、达人跟进与操作日志接口。")
@RequestMapping("/colonel/activities/{activityId}/products")
@RequireRoles({RoleCodes.BIZ_LEADER, RoleCodes.BIZ_STAFF, RoleCodes.CHANNEL_LEADER, RoleCodes.CHANNEL_STAFF, RoleCodes.ADMIN})
public class ColonelActivityProductController extends BaseController {

    private final ProductService productService;

    public ColonelActivityProductController(ProductService productService) {
        this.productService = productService;
    }

    @Operation(summary = "活动商品详情", description = "查询指定活动下单个商品的业务详情。")
    @GetMapping("/{productId}")
    public ApiResult<Map<String, Object>> detail(
            @Parameter(description = "团长活动 ID。") @PathVariable String activityId,
            @Parameter(description = "商品 ID。") @PathVariable String productId) {
        return ok(productService.getActivityProductDetail(activityId, productId));
    }

    @Operation(summary = "活动商品绑定活动", description = "为活动商品补绑或修正关联活动。")
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
            @RequestAttribute(value = "deptId", required = false) UUID deptId) {
        return ok(productService.assignProduct(activityId, productId, request.getAssigneeId(), userId, deptId));
    }

    @Operation(summary = "活动商品审核", description = "提交活动商品审核结果。")
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
        return ok(productService.auditProduct(activityId, productId, request.isApproved(), request.getReason(), userId, deptId));
    }

    @Operation(summary = "活动商品推进判断", description = "记录商品主推、次推、暂缓或放弃等人工推进判断，不改变商品主状态。")
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
                safeRequest.getTalentId()
        );
        return ok(result);
    }

    @Operation(summary = "活动商品达人跟进", description = "记录活动商品的达人跟进信息，用于达人侧协作与后续回访。")
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

    public static class AuditRequest {
        @Schema(description = "是否审核通过。", example = "true")
        private boolean approved;

        @Schema(description = "审核备注。", example = "素材完整，允许推进")
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
