package com.colonel.saas.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.common.base.BaseController;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.common.result.PageResult;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.douyin.api.PromotionApi;
import com.colonel.saas.entity.Product;
import com.colonel.saas.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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

import java.util.UUID;

@Validated
@Tag(name = "Product")
@RestController
@RequestMapping("/products")
@RequireRoles({RoleCodes.BIZ_LEADER, RoleCodes.BIZ_STAFF, RoleCodes.CHANNEL_LEADER, RoleCodes.CHANNEL_STAFF})
public class ProductController extends BaseController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @Operation(summary = "Product page")
    @GetMapping("/page")
    public ApiResult<PageResult<Product>> page(
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) long size,
            @RequestParam(required = false) Integer status) {
        IPage<Product> result = productService.getPage(page, size, status);
        return okPage(result);
    }

    @Operation(summary = "Product detail")
    @GetMapping("/{id}")
    public ApiResult<Product> detail(@PathVariable UUID id) {
        return ok(productService.getById(id));
    }

    @Operation(summary = "Bind product to activity")
    @RequireRoles({RoleCodes.BIZ_LEADER, RoleCodes.BIZ_STAFF})
    @PutMapping("/{id}/bind-activity")
    public ApiResult<Product> bindActivity(@PathVariable UUID id, @Valid @RequestBody BindActivityRequest request) {
        return ok(productService.bindActivity(id, request.getActivityId()));
    }

    @Operation(summary = "Assign product to biz owner")
    @RequireRoles({RoleCodes.BIZ_LEADER})
    @PutMapping("/{id}/assign")
    public ApiResult<Product> assign(@PathVariable UUID id, @Valid @RequestBody AssignProductRequest request) {
        return ok(productService.assignProduct(id, request.getAssigneeId()));
    }

    @Operation(summary = "Audit product approve/reject")
    @RequireRoles({RoleCodes.BIZ_LEADER, RoleCodes.BIZ_STAFF})
    @PutMapping("/{id}/audit")
    public ApiResult<Product> audit(@PathVariable UUID id, @Valid @RequestBody AuditProductRequest request) {
        return ok(productService.auditProduct(id, request.isApproved(), request.getReason()));
    }

    @Operation(summary = "Generate promotion link and persist mapping")
    @PostMapping("/{id}/promotion-link")
    public ApiResult<PromotionApi.PromotionLinkResult> generatePromotionLink(
            @PathVariable UUID id,
            @RequestBody(required = false) PromotionLinkRequest request,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId) {
        PromotionLinkRequest safeRequest = request == null ? new PromotionLinkRequest() : request;
        PromotionApi.PromotionLinkResult result = productService.generatePromotionLink(
                id,
                userId,
                deptId,
                safeRequest.getExternalUniqueId(),
                safeRequest.getPromotionScene(),
                safeRequest.getNeedShortLink()
        );
        return ok(result);
    }

    public static class BindActivityRequest {
        @NotNull(message = "activityId is required")
        private UUID activityId;

        public UUID getActivityId() {
            return activityId;
        }

        public void setActivityId(UUID activityId) {
            this.activityId = activityId;
        }
    }

    public static class AssignProductRequest {
        @NotNull(message = "assigneeId is required")
        private UUID assigneeId;

        public UUID getAssigneeId() {
            return assigneeId;
        }

        public void setAssigneeId(UUID assigneeId) {
            this.assigneeId = assigneeId;
        }
    }

    public static class AuditProductRequest {
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
}
