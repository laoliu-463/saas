package com.colonel.saas.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.entity.Product;
import com.colonel.saas.gateway.douyin.DouyinPromotionGateway;
import com.colonel.saas.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductControllerTest {

    @Mock
    private ProductService productService;

    private ProductController productController;

    @BeforeEach
    void setUp() {
        productController = new ProductController(productService);
    }

    @Test
    void page_shouldReturnPage() {
        Page<Product> page = new Page<>(1, 10);
        page.setRecords(List.of(new Product()));
        page.setTotal(1);
        when(productService.getPage(1, 10, 1)).thenReturn(page);

        var response = productController.page(1, 10, 1);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData().getTotal()).isEqualTo(1);
    }

    @Test
    void bindActivity_shouldCallService() {
        UUID id = UUID.randomUUID();
        UUID activityId = UUID.randomUUID();
        Product product = new Product();
        product.setId(id);
        product.setActivityId(activityId);
        when(productService.bindActivity(id, activityId)).thenReturn(product);

        ProductController.BindActivityRequest request = new ProductController.BindActivityRequest();
        request.setActivityId(activityId);

        var response = productController.bindActivity(id, request);

        assertThat(response.getData().getActivityId()).isEqualTo(activityId);
        verify(productService).bindActivity(id, activityId);
    }

    @Test
    void assign_shouldCallService() {
        UUID id = UUID.randomUUID();
        UUID assigneeId = UUID.randomUUID();
        Product product = new Product();
        product.setId(id);
        product.setAssigneeId(assigneeId);
        when(productService.assignProduct(id, assigneeId)).thenReturn(product);

        ProductController.AssignProductRequest request = new ProductController.AssignProductRequest();
        request.setAssigneeId(assigneeId);

        var response = productController.assign(id, request);

        assertThat(response.getData().getAssigneeId()).isEqualTo(assigneeId);
        verify(productService).assignProduct(id, assigneeId);
    }

    @Test
    void audit_shouldCallService() {
        UUID id = UUID.randomUUID();
        Product product = new Product();
        product.setId(id);
        product.setCheckStatus(2);
        when(productService.auditProduct(id, true, null)).thenReturn(product);

        ProductController.AuditProductRequest request = new ProductController.AuditProductRequest();
        request.setApproved(true);

        var response = productController.audit(id, request);

        assertThat(response.getData().getCheckStatus()).isEqualTo(2);
        verify(productService).auditProduct(id, true, null);
    }

    @Test
    void generatePromotionLink_shouldCallService() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        DouyinPromotionGateway.PromotionLinkResult result =
                new DouyinPromotionGateway.PromotionLinkResult(
                        "ABC12345",
                        "ABC12345",
                        "ABC12345",
                        "https://s.link",
                        "https://p.link",
                        UUID.randomUUID().toString()
                );
        when(productService.generatePromotionLink(eq(id), eq(userId), eq(deptId), any(), any(), anyBoolean(), any(), any()))
                .thenReturn(result);

        ProductController.PromotionLinkRequest request = new ProductController.PromotionLinkRequest();
        request.setExternalUniqueId("ext-1");
        request.setPromotionScene(4);
        request.setNeedShortLink(true);

        var response = productController.generatePromotionLink(id, request, userId, deptId);

        assertThat(response.getData().shortId()).isEqualTo("ABC12345");
        verify(productService).generatePromotionLink(id, userId, deptId, "ext-1", 4, true, "PRODUCT_LIBRARY", null);
    }

    @Test
    void follow_shouldCallService() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(productService.startTalentFollow(eq(id), any(), eq("达人A"), eq("INVITED"), eq("已发送邀约"), any(), eq(userId), eq("操作人")))
                .thenReturn(Map.of("bizStatus", "FOLLOWING"));

        ProductController.TalentFollowRequest request = new ProductController.TalentFollowRequest();
        request.setTalentName("达人A");
        request.setFollowStatus("INVITED");
        request.setContent("已发送邀约");
        request.setOperatorName("操作人");

        var response = productController.follow(id, request, userId);

        assertThat(response.getData().get("bizStatus")).isEqualTo("FOLLOWING");
        verify(productService).startTalentFollow(id, null, "达人A", "INVITED", "已发送邀约", null, userId, "操作人");
    }
}
