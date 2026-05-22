package com.colonel.saas.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.common.result.PageResult;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.entity.Product;
import com.colonel.saas.gateway.douyin.DouyinPromotionGateway;
import com.colonel.saas.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
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
@SuppressWarnings("deprecation")
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
        when(productService.getPage(1, 10, 1, null)).thenReturn(page);

        var response = productController.pickPage(1, 10, 1, null, List.of(RoleCodes.BIZ_LEADER));

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData().getTotal()).isEqualTo(1);
    }

    @Test
    void pickPage_shouldLimitBizStaffToOwnAssignedProducts() {
        UUID userId = UUID.randomUUID();
        Page<Product> page = new Page<>(1, 10);
        page.setRecords(List.of(new Product()));
        page.setTotal(1);
        when(productService.getPage(1, 10, null, userId)).thenReturn(page);

        var response = productController.pickPage(1, 10, null, userId, List.of(RoleCodes.BIZ_STAFF));

        assertThat(response.getCode()).isEqualTo(200);
        verify(productService).getPage(1, 10, null, userId);
    }

    @Test
    void bindActivity_shouldCallService() {
        UUID id = UUID.randomUUID();
        UUID activityId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        Product product = new Product();
        product.setId(id);
        product.setActivityId(activityId);
        when(productService.bindActivity(id, activityId, userId, deptId)).thenReturn(product);

        ProductController.BindActivityRequest request = new ProductController.BindActivityRequest();
        request.setActivityId(activityId);

        var response = productController.bindActivity(id, request, userId, deptId);

        assertThat(response.getData().getActivityId()).isEqualTo(activityId);
        verify(productService).bindActivity(id, activityId, userId, deptId);
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
        when(productService.generatePromotionLink(eq(id), eq(userId), eq(deptId), any(), any(), anyBoolean(), any(), any(), any()))
                .thenReturn(result);

        ProductController.PromotionLinkRequest request = new ProductController.PromotionLinkRequest();
        request.setExternalUniqueId("ext-1");
        request.setPromotionScene(4);
        request.setNeedShortLink(true);

        var response = productController.generatePromotionLink(id, request, null, userId, deptId);

        assertThat(response.getData().shortId()).isEqualTo("ABC12345");
        verify(productService).generatePromotionLink(id, userId, deptId, "ext-1", 4, true, "PRODUCT_LIBRARY", null, null);
    }

    @Test
    void promotionLinkRequest_shouldKeepDefaultsAndHandleNullNeedShortLink() {
        ProductController.PromotionLinkRequest request = new ProductController.PromotionLinkRequest();

        assertThat(request.getPromotionScene()).isEqualTo(4);
        assertThat(request.getNeedShortLink()).isTrue();
        assertThat(request.getScene()).isEqualTo("PRODUCT_LIBRARY");

        request.setExternalUniqueId("external-1");
        request.setPromotionScene(8);
        request.setNeedShortLink(null);
        request.setScene("DETAIL");
        request.setTalentId("talent-1");

        assertThat(request.getExternalUniqueId()).isEqualTo("external-1");
        assertThat(request.getPromotionScene()).isEqualTo(8);
        assertThat(request.getNeedShortLink()).isTrue();
        assertThat(request.getScene()).isEqualTo("DETAIL");
        assertThat(request.getTalentId()).isEqualTo("talent-1");

        request.setNeedShortLink(Boolean.FALSE);
        assertThat(request.getNeedShortLink()).isFalse();
    }

    @Test
    void auditAndFollowRequestTypes_shouldExposeAssignedValues() {
        UUID talentId = UUID.randomUUID();
        LocalDateTime nextFollowTime = LocalDateTime.of(2026, 4, 29, 10, 0);

        ProductController.AuditProductRequest audit = new ProductController.AuditProductRequest();
        audit.setApproved(false);
        audit.setReason("素材不足");

        ProductController.TalentFollowRequest follow = new ProductController.TalentFollowRequest();
        follow.setTalentId(talentId);
        follow.setTalentName("达人A");
        follow.setFollowStatus("FOLLOWING");
        follow.setContent("已加微信跟进");
        follow.setNextFollowTime(nextFollowTime);
        follow.setOperatorName("渠道A");

        assertThat(audit.isApproved()).isFalse();
        assertThat(audit.getReason()).isEqualTo("素材不足");
        assertThat(follow.getTalentId()).isEqualTo(talentId);
        assertThat(follow.getTalentName()).isEqualTo("达人A");
        assertThat(follow.getFollowStatus()).isEqualTo("FOLLOWING");
        assertThat(follow.getContent()).isEqualTo("已加微信跟进");
        assertThat(follow.getNextFollowTime()).isEqualTo(nextFollowTime);
        assertThat(follow.getOperatorName()).isEqualTo("渠道A");
    }

    @Test
    void promotionLinkHistory_shouldCallService() {
        PageResult<Map<String, Object>> pageResult = new PageResult<>();
        pageResult.setPage(1);
        pageResult.setSize(20);
        pageResult.setTotal(1);
        pageResult.setRecords(List.of(Map.of("productId", "3810562766247428542")));
        when(productService.getPromotionLinkHistory("3810562766247428542", 1, 20)).thenReturn(pageResult);

        var response = productController.promotionLinkHistory("3810562766247428542", 1, 20);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData().getTotal()).isEqualTo(1);
        verify(productService).getPromotionLinkHistory("3810562766247428542", 1, 20);
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

    @Test
    void page_shouldReturnSelectedLibraryProducts() {
        Page<Product> page = new Page<>(1, 10);
        Product product = new Product();
        product.setName("共享商品");
        page.setRecords(List.of(product));
        page.setTotal(1);
        when(productService.getSelectedLibraryPage(eq(1L), eq(10L), any(ProductService.SelectedLibraryFilter.class))).thenReturn(page);

        var response = productController.page(
                1,
                10,
                null,
                "共享",
                "测试店铺",
                "食品",
                "gte30000",
                "LINKED",
                "promoting",
                "gt20",
                "1",
                "assigned",
                "traffic",
                "MAIN");

        assertThat(response.getData().getTotal()).isEqualTo(1);
        assertThat(response.getData().getRecords().get(0).getName()).isEqualTo("共享商品");
        verify(productService).getSelectedLibraryPage(eq(1L), eq(10L), any(ProductService.SelectedLibraryFilter.class));
    }

    @Test
    void controllerRoleAnnotations_shouldKeepSharedLibraryVisibleToBusinessRoles() throws NoSuchMethodException {
        RequireRoles pageRoles = ProductController.class.getMethod(
                        "page",
                        long.class,
                        long.class,
                        Integer.class,
                        String.class,
                        String.class,
                        String.class,
                        String.class,
                        String.class,
                        String.class,
                        String.class,
                        String.class,
                        String.class,
                        String.class,
                        String.class)
                .getAnnotation(RequireRoles.class);
        RequireRoles detailRoles = ProductController.class.getMethod("detail", UUID.class)
                .getAnnotation(RequireRoles.class);
        RequireRoles bindRoles = ProductController.class.getMethod(
                        "bindActivity",
                        UUID.class,
                        ProductController.BindActivityRequest.class,
                        UUID.class,
                        UUID.class)
                .getAnnotation(RequireRoles.class);

        assertThat(pageRoles.value()).containsExactly(
                RoleCodes.BIZ_LEADER,
                RoleCodes.BIZ_STAFF,
                RoleCodes.CHANNEL_LEADER,
                RoleCodes.CHANNEL_STAFF
        );
        assertThat(detailRoles.value()).containsExactly(
                RoleCodes.BIZ_LEADER,
                RoleCodes.BIZ_STAFF,
                RoleCodes.CHANNEL_LEADER,
                RoleCodes.CHANNEL_STAFF
        );
        assertThat(bindRoles.value()).containsExactly(RoleCodes.BIZ_LEADER);
        RequireRoles historyRoles = ProductController.class
                .getMethod("promotionLinkHistory", String.class, long.class, long.class)
                .getAnnotation(RequireRoles.class);
        assertThat(historyRoles.value()).containsExactly(
                RoleCodes.BIZ_LEADER,
                RoleCodes.BIZ_STAFF,
                RoleCodes.CHANNEL_LEADER,
                RoleCodes.CHANNEL_STAFF
        );
    }
}
