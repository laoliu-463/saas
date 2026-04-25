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
@Tag(name = "活动商品主链路")
@RequestMapping("/colonel/activities/{activityId}/products")
@RequireRoles({RoleCodes.BIZ_LEADER, RoleCodes.BIZ_STAFF, RoleCodes.CHANNEL_LEADER, RoleCodes.CHANNEL_STAFF, RoleCodes.ADMIN})
public class ColonelActivityProductController extends BaseController {

    private final ProductService productService;

    public ColonelActivityProductController(ProductService productService) {
        this.productService = productService;
    }

    @Operation(summary = "活动商品详情")
    @GetMapping("/{productId}")
    public ApiResult<Map<String, Object>> detail(
            @PathVariable String activityId,
            @PathVariable String productId) {
        return ok(productService.getActivityProductDetail(activityId, productId));
    }

    @Operation(summary = "活动商品绑定活动")
    @PutMapping("/{productId}/bind-activity")
    public ApiResult<Map<String, Object>> bindActivity(
            @PathVariable String activityId,
            @PathVariable String productId,
            @Valid @RequestBody BindActivityRequest request,
            @RequestAttribute(value = "userId", required = false) UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId) {
        return ok(productService.bindActivity(activityId, productId, request.getBoundActivityId(), userId, deptId));
    }

    @Operation(summary = "活动商品分配招商")
    @PutMapping("/{productId}/assignee")
    public ApiResult<Map<String, Object>> assign(
            @PathVariable String activityId,
            @PathVariable String productId,
            @Valid @RequestBody AssignRequest request,
            @RequestAttribute(value = "userId", required = false) UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId) {
        return ok(productService.assignProduct(activityId, productId, request.getAssigneeId(), userId, deptId));
    }

    @Operation(summary = "活动商品审核")
    @PutMapping("/{productId}/audit-result")
    public ApiResult<Map<String, Object>> audit(
            @PathVariable String activityId,
            @PathVariable String productId,
            @Valid @RequestBody AuditRequest request,
            @RequestAttribute(value = "userId", required = false) UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId) {
        return ok(productService.auditProduct(activityId, productId, request.isApproved(), request.getReason(), userId, deptId));
    }

    @Operation(summary = "活动商品转链")
    @PostMapping("/{productId}/promotion-links")
    public ApiResult<DouyinPromotionGateway.PromotionLinkResult> generatePromotionLink(
            @PathVariable String activityId,
            @PathVariable String productId,
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
                safeRequest.getNeedShortLink()
        );
        return ok(result);
    }

    @Operation(summary = "活动商品达人跟进")
    @PostMapping("/{productId}/follow")
    public ApiResult<Map<String, Object>> follow(
            @PathVariable String activityId,
            @PathVariable String productId,
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

    @Operation(summary = "活动商品操作日志")
    @GetMapping("/{productId}/operation-logs")
    public ApiResult<PageResult<ProductOperationLog>> operationLogs(
            @PathVariable String activityId,
            @PathVariable String productId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        IPage<ProductOperationLog> result = productService.getOperationLogs(activityId, productId, page, size);
        return okPage(result);
    }

    public static class BindActivityRequest {
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
        private boolean approved;
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
        private String externalUniqueId;
        private Integer promotionScene = 4;
        private Boolean needShortLink = Boolean.TRUE;

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
    }

    public static class TalentFollowRequest {
        private UUID talentId;
        private String talentName;
        @NotBlank(message = "followStatus 不能为空")
        private String followStatus;
        private String content;
        private LocalDateTime nextFollowTime;
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
