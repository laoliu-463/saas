package com.colonel.saas.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.common.result.PageResult;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.dto.product.ProductFilterOptionItem;
import com.colonel.saas.dto.product.ProductFilterOptionsDTO;
import com.colonel.saas.entity.Product;
import com.colonel.saas.gateway.douyin.DouyinPromotionGateway;
import com.colonel.saas.service.ColonelPartnerSyncService;
import com.colonel.saas.domain.product.application.ProductLibraryPageQueryService;
import com.colonel.saas.domain.product.application.ProductQuickSampleApplicationService;
import com.colonel.saas.domain.product.application.dto.ProductLibraryCursorPage;
import com.colonel.saas.domain.product.application.dto.ProductLibraryPageQuery;
import com.colonel.saas.domain.user.policy.CurrentUserPermissionChecker;
import com.colonel.saas.domain.user.policy.CurrentUserPermissionPolicy;
import com.colonel.saas.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.validation.constraints.Max;
import java.time.LocalDateTime;
import java.lang.reflect.Method;
import java.util.Arrays;
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
    @Mock
    private ProductQuickSampleApplicationService productQuickSampleApplicationService;
    @Mock
    private ProductLibraryPageQueryService productLibraryPageQueryService;
    @Mock
    private ColonelPartnerSyncService colonelPartnerSyncService;

    private ProductController productController;

    @BeforeEach
    void setUp() {
        productController = new ProductController(
                productService,
                productQuickSampleApplicationService,
                productLibraryPageQueryService,
                colonelPartnerSyncService,
                new CurrentUserPermissionChecker(new CurrentUserPermissionPolicy()));
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
    void approveManagedProduct_shouldCallAuditAndUseManagePath() throws NoSuchMethodException {
        UUID relationId = UUID.randomUUID();
        Product product = new Product();
        product.setId(relationId);
        product.setCheckStatus(2);
        when(productService.auditProduct(eq(relationId), eq(true), eq("素材完整"), any())).thenReturn(product);

        ProductController.ProductManageApproveRequest request = new ProductController.ProductManageApproveRequest();
        request.setRemark("素材完整");
        request.setExclusivePriceRemark("直播间专属价 129 元");
        request.setShippingInfo("48 小时内发货");
        request.setSellingPoints(Arrays.asList("高复购", "夏季场景强"));
        request.setPromotionScript("主打复购和囤货场景");
        request.setSupportsAds(false);
        request.setRewardRemark("破 3 万 GMV 额外奖励");
        request.setParticipationRequirements("近 30 天有成交记录");
        request.setCampaignTimeRemark("6 月 1 日至 6 月 15 日");
        request.setMaterialFiles(Arrays.asList("https://example.com/card.png"));

        var response = productController.approveManagedProduct(relationId, request);

        assertThat(response.getData().getCheckStatus()).isEqualTo(2);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> supplementCaptor = ArgumentCaptor.forClass(Map.class);
        verify(productService).auditProduct(eq(relationId), eq(true), eq("素材完整"), supplementCaptor.capture());
        assertThat(supplementCaptor.getValue())
                .containsEntry("exclusivePriceRemark", "直播间专属价 129 元")
                .containsEntry("shippingInfo", "48 小时内发货")
                .containsEntry("promotionScript", "主打复购和囤货场景")
                .containsEntry("supportsAds", false)
                .containsEntry("rewardRemark", "破 3 万 GMV 额外奖励")
                .containsEntry("participationRequirements", "近 30 天有成交记录")
                .containsEntry("campaignTimeRemark", "6 月 1 日至 6 月 15 日");
        assertThat(supplementCaptor.getValue().get("sellingPoints"))
                .asList()
                .containsExactly("高复购", "夏季场景强");
        assertThat(supplementCaptor.getValue().get("materialFiles"))
                .asList()
                .containsExactly("https://example.com/card.png");
        Method method = ProductController.class.getMethod(
                "approveManagedProduct",
                UUID.class,
                ProductController.ProductManageApproveRequest.class);
        assertThat(method.getAnnotation(org.springframework.web.bind.annotation.PostMapping.class).value())
                .containsExactly("/manage/{relationId}/approve");
    }

    @Test
    void rejectManagedProduct_shouldCallAuditAndUseManagePath() throws NoSuchMethodException {
        UUID relationId = UUID.randomUUID();
        Product product = new Product();
        product.setId(relationId);
        product.setCheckStatus(3);
        when(productService.auditProduct(relationId, false, "不符合商品库要求")).thenReturn(product);

        ProductController.ProductManageRejectRequest request = new ProductController.ProductManageRejectRequest();
        request.setReason("不符合商品库要求");

        var response = productController.rejectManagedProduct(relationId, request);

        assertThat(response.getData().getCheckStatus()).isEqualTo(3);
        verify(productService).auditProduct(relationId, false, "不符合商品库要求");
        Method method = ProductController.class.getMethod(
                "rejectManagedProduct",
                UUID.class,
                ProductController.ProductManageRejectRequest.class);
        assertThat(method.getAnnotation(org.springframework.web.bind.annotation.PostMapping.class).value())
                .containsExactly("/manage/{relationId}/reject");
    }

    @Test
    void pausePublish_shouldCallServiceAndUseRelationPath() throws NoSuchMethodException {
        UUID relationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        Product product = new Product();
        product.setId(relationId);
        product.setSelectedToLibrary(true);
        product.setDisplayStatus("HIDDEN");
        when(productService.pausePublish(relationId, userId, deptId)).thenReturn(product);

        var response = productController.pausePublish(relationId, userId, deptId);

        assertThat(response.getData().getDisplayStatus()).isEqualTo("HIDDEN");
        verify(productService).pausePublish(relationId, userId, deptId);
        Method method = ProductController.class.getMethod(
                "pausePublish",
                UUID.class,
                UUID.class,
                UUID.class);
        assertThat(method.getAnnotation(org.springframework.web.bind.annotation.PostMapping.class).value())
                .containsExactly("/{relationId}/pause");
        RequireRoles roles = method.getAnnotation(RequireRoles.class);
        assertThat(roles.value()).containsExactly(RoleCodes.BIZ_LEADER, RoleCodes.BIZ_STAFF);
    }

    @Test
    void resumePublish_shouldCallServiceAndUseRelationPath() throws NoSuchMethodException {
        UUID relationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        Product product = new Product();
        product.setId(relationId);
        product.setSelectedToLibrary(true);
        product.setDisplayStatus("PENDING");
        when(productService.resumePublish(relationId, userId, deptId)).thenReturn(product);

        var response = productController.resumePublish(relationId, userId, deptId);

        assertThat(response.getData().getDisplayStatus()).isEqualTo("PENDING");
        verify(productService).resumePublish(relationId, userId, deptId);
        Method method = ProductController.class.getMethod(
                "resumePublish",
                UUID.class,
                UUID.class,
                UUID.class);
        assertThat(method.getAnnotation(org.springframework.web.bind.annotation.PostMapping.class).value())
                .containsExactly("/{relationId}/resume");
        RequireRoles roles = method.getAnnotation(RequireRoles.class);
        assertThat(roles.value()).containsExactly(RoleCodes.BIZ_LEADER, RoleCodes.BIZ_STAFF);
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

        assertThat(response.getData().pickSource()).isEqualTo("ABC12345");
        assertThat(response.getData().pickExtra()).isEqualTo("ABC12345");
        assertThat(response.getData().shortId()).isEqualTo("ABC12345");
        assertThat(response.getData().shortLink()).isEqualTo("https://s.link");
        assertThat(response.getData().promoteLink()).isEqualTo("https://p.link");
        assertThat(response.getData().uuidSeed()).isEqualTo(result.uuidSeed());
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
        when(productLibraryPageQueryService.getSelectedLibraryPage(eq(1L), eq(10L), any(ProductLibraryPageQuery.class))).thenReturn(page);

        var response = productController.page(
                1, 10, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null);

        assertThat(response.getData().getTotal()).isEqualTo(1);
        assertThat(response.getData().getRecords().get(0).getName()).isEqualTo("共享商品");
        verify(productLibraryPageQueryService).getSelectedLibraryPage(eq(1L), eq(10L), any(ProductLibraryPageQuery.class));
    }

    @Test
    void page_shouldPassProductIdToSelectedLibraryFilter() {
        Page<Product> page = new Page<>(1, 10);
        when(productLibraryPageQueryService.getSelectedLibraryPage(eq(1L), eq(10L), any(ProductLibraryPageQuery.class))).thenReturn(page);

        productController.page(
                1, 10, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, "9001");

        ArgumentCaptor<ProductLibraryPageQuery> filterCaptor =
                ArgumentCaptor.forClass(ProductLibraryPageQuery.class);
        verify(productLibraryPageQueryService).getSelectedLibraryPage(eq(1L), eq(10L), filterCaptor.capture());
        assertThat(filterCaptor.getValue().productId()).isEqualTo("9001");
    }

    @Test
    void page_shouldReturnCursorFieldsWhenCursorQueryIsSupported() {
        Product product = new Product();
        product.setProductId("9001");
        when(productLibraryPageQueryService.getSelectedLibraryCursorPage(eq("cursor-1"), eq(500L), any(ProductLibraryPageQuery.class)))
                .thenReturn(new ProductLibraryCursorPage(List.of(product), 500L, true, "cursor-2"));

        var response = productController.page(
                1, 20, "cursor-1", 500L,
                null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null);

        assertThat(response.getData().getRecords()).singleElement().extracting("productId").isEqualTo("9001");
        assertThat(response.getData().getHasMore()).isTrue();
        assertThat(response.getData().getNextCursor()).isEqualTo("cursor-2");
        assertThat(response.getData().getSize()).isEqualTo(500L);
        verify(productLibraryPageQueryService).getSelectedLibraryCursorPage(eq("cursor-1"), eq(500L), any(ProductLibraryPageQuery.class));
    }

    @Test
    void page_shouldExposeProductIdRequestParam() throws NoSuchMethodException {
        Method pageMethod = selectedLibraryPageMethod();

        assertThat(Arrays.stream(pageMethod.getParameters())
                .map(parameter -> parameter.getAnnotation(RequestParam.class))
                .filter(java.util.Objects::nonNull)
                .map(ProductControllerTest::requestParamName))
                .contains("productId", "cursor", "limit");
    }

    @Test
    void selectedLibraryPageSize_shouldNotUseHundredRowValidationLimit() throws NoSuchMethodException {
        Method pageMethod = selectedLibraryPageMethod();
        Method pickPageMethod = ProductController.class.getMethod(
                "pickPage",
                long.class,
                long.class,
                Integer.class,
                UUID.class,
                List.class);
        Method historyMethod = ProductController.class.getMethod(
                "promotionLinkHistory",
                String.class,
                long.class,
                long.class);

        assertThat(pageMethod.getParameters()[1].getAnnotation(Max.class)).isNull();
        assertThat(pickPageMethod.getParameters()[1].getAnnotation(Max.class).value()).isEqualTo(100);
        assertThat(historyMethod.getParameters()[2].getAnnotation(Max.class).value()).isEqualTo(100);
    }

    @Test
    void adminCounts_shouldReturnSeparatedSnapshotRelationAndDisplayingCounts() throws NoSuchMethodException {
        ProductService.AdminProductCounts counts = new ProductService.AdminProductCounts(
                7_979,
                7_979,
                7_020,
                2_974,
                196,
                4_809,
                24);
        when(productService.getAdminCounts()).thenReturn(counts);

        var response = productController.adminCounts();

        assertThat(response.getData().snapshotTotal()).isEqualTo(7_979);
        assertThat(response.getData().relationTotal()).isEqualTo(7_979);
        assertThat(response.getData().displayingTotal()).isEqualTo(2_974);
        verify(productService).getAdminCounts();
        Method method = ProductController.class.getMethod("adminCounts");
        assertThat(method.getAnnotation(org.springframework.web.bind.annotation.GetMapping.class).value())
                .containsExactly("/admin/counts");
        assertThat(method.getAnnotation(RequireRoles.class).value())
                .containsExactly(RoleCodes.ADMIN);
    }

    @Test
    void libraryCategories_shouldReturnDistinctCategoryNames() {
        when(productService.listLibraryCategories()).thenReturn(List.of("食品饮料", "美妆护肤"));

        var response = productController.libraryCategories();

        assertThat(response.getData()).containsExactly("食品饮料", "美妆护肤");
        verify(productService).listLibraryCategories();
    }

    @Test
    void filterOptions_shouldReturnCategoryOptions() {
        when(productService.listLibraryCategories()).thenReturn(List.of("食品饮料", "美妆护肤"));

        var response = productController.filterOptions();

        assertThat(response.getData()).isInstanceOf(ProductFilterOptionsDTO.class);
        assertThat(response.getData().categories())
                .extracting(ProductFilterOptionItem::label)
                .containsExactly("食品饮料", "美妆护肤");
        assertThat(response.getData().categories())
                .extracting(ProductFilterOptionItem::value)
                .containsExactly("食品饮料", "美妆护肤");
    }

    @Test
    void controllerRoleAnnotations_shouldKeepSharedLibraryVisibleToBusinessRoles() throws NoSuchMethodException {
        Method pageMethod = selectedLibraryPageMethod();
        RequireRoles pageRoles = pageMethod.getAnnotation(RequireRoles.class);
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

    private static Method selectedLibraryPageMethod() throws NoSuchMethodException {
        return Arrays.stream(ProductController.class.getDeclaredMethods())
                .filter(method -> "page".equals(method.getName()))
                .filter(method -> method.getParameterTypes().length > 2)
                .findFirst()
                .orElseThrow(() -> new NoSuchMethodException("page"));
    }

    private static String requestParamName(RequestParam requestParam) {
        if (!requestParam.name().isBlank()) {
            return requestParam.name();
        }
        return requestParam.value();
    }
}
