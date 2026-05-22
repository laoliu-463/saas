package com.colonel.saas.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.auth.service.SysUserService;
import com.colonel.saas.entity.ProductOperationLog;
import com.colonel.saas.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ColonelActivityProductControllerTest {

    @Mock
    private ProductService productService;
    @Mock
    private SysUserService sysUserService;

    private ColonelActivityProductController controller;

    @BeforeEach
    void setUp() {
        controller = new ColonelActivityProductController(productService, sysUserService);
    }

    @Test
    void follow_shouldCallSharedProductService() {
        UUID userId = UUID.randomUUID();
        when(productService.startTalentFollow(
                eq("10001"),
                eq("9001"),
                any(),
                eq("达人A"),
                eq("INVITED"),
                eq("已发送邀约"),
                any(),
                eq(userId),
                eq("操作人")
        )).thenReturn(Map.of("bizStatus", "FOLLOWING"));

        ColonelActivityProductController.TalentFollowRequest request = new ColonelActivityProductController.TalentFollowRequest();
        request.setTalentName("达人A");
        request.setFollowStatus("INVITED");
        request.setContent("已发送邀约");
        request.setOperatorName("操作人");

        var response = controller.follow("10001", "9001", request, userId);

        assertThat(response.getData().get("bizStatus")).isEqualTo("FOLLOWING");
        verify(productService).startTalentFollow("10001", "9001", null, "达人A", "INVITED", "已发送邀约", null, userId, "操作人");
    }

    @Test
    void operationLogs_shouldReturnPage() {
        Page<ProductOperationLog> page = new Page<>(1, 20);
        page.setTotal(1);
        when(productService.getOperationLogs("10001", "9001", 1, 20)).thenReturn(page);

        var response = controller.operationLogs("10001", "9001", 1, 20);

        assertThat(response.getData().getTotal()).isEqualTo(1);
    }

    @Test
    void assign_shouldReturnAssignedStatus() {
        UUID assigneeId = UUID.randomUUID();
        when(productService.assignProduct("10001", "9001", assigneeId, null, null))
                .thenReturn(Map.of(
                        "bizStatus", "ASSIGNED",
                        "bizStatusLabel", "已分配招商",
                        "assigneeId", assigneeId
                ));

        ColonelActivityProductController.AssignRequest request = new ColonelActivityProductController.AssignRequest();
        request.setAssigneeId(assigneeId);

        var response = controller.assign("10001", "9001", request, null, null, List.of(com.colonel.saas.constant.RoleCodes.BIZ_LEADER));

        assertThat(response.getData().get("bizStatus")).isEqualTo("ASSIGNED");
        assertThat(response.getData().get("assigneeId")).isEqualTo(assigneeId);
        verify(sysUserService).assertAssignableUser(assigneeId, List.of(com.colonel.saas.constant.RoleCodes.BIZ_LEADER), null);
        verify(productService).assignProduct("10001", "9001", assigneeId, null, null);
    }

    @Test
    void assignAuditOwner_shouldKeepPendingAuditStatus() {
        UUID assigneeId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        List<String> roleCodes = List.of(RoleCodes.BIZ_LEADER);
        when(productService.assignAuditOwner("10001", "9001", assigneeId, userId, deptId))
                .thenReturn(Map.of(
                        "bizStatus", "PENDING_AUDIT",
                        "bizStatusLabel", "待审核",
                        "assigneeId", assigneeId
                ));

        ColonelActivityProductController.AssignRequest request = new ColonelActivityProductController.AssignRequest();
        request.setAssigneeId(assigneeId);

        var response = controller.assignAuditOwner("10001", "9001", request, userId, deptId, roleCodes);

        assertThat(response.getData().get("bizStatus")).isEqualTo("PENDING_AUDIT");
        assertThat(response.getData().get("assigneeId")).isEqualTo(assigneeId);
        verify(sysUserService).assertAssignableUser(assigneeId, roleCodes, deptId);
        verify(productService).assignAuditOwner("10001", "9001", assigneeId, userId, deptId);
    }

    @Test
    void audit_shouldPassSupplementFields() {
        when(productService.auditProduct(
                eq("10001"),
                eq("9001"),
                eq(true),
                eq(null),
                any(),
                eq(null),
                eq(null)
        )).thenReturn(Map.of("bizStatus", "APPROVED"));

        ColonelActivityProductController.AuditRequest request = new ColonelActivityProductController.AuditRequest();
        request.setApproved(true);
        request.setExclusivePriceRemark("专属价 129");
        request.setShippingInfo("48 小时发货");
        request.setSellingPoints(List.of("高复购", "场景强"));
        request.setPromotionScript("主打复购场景");
        request.setSupportsAds(true);
        request.setRewardRemark("破峰值有奖励");
        request.setParticipationRequirements("食品饮料达人优先");
        request.setCampaignTimeRemark("4 月第一波活动");
        request.setMaterialFiles(List.of("https://example.com/material.png"));

        var response = controller.audit("10001", "9001", request, null, null);

        assertThat(response.getData().get("bizStatus")).isEqualTo("APPROVED");
        verify(productService).auditProduct(
                eq("10001"),
                eq("9001"),
                eq(true),
                eq(null),
                eq(Map.of(
                        "exclusivePriceRemark", "专属价 129",
                        "shippingInfo", "48 小时发货",
                        "sellingPoints", List.of("高复购", "场景强"),
                        "promotionScript", "主打复购场景",
                        "supportsAds", true,
                        "rewardRemark", "破峰值有奖励",
                        "participationRequirements", "食品饮料达人优先",
                        "campaignTimeRemark", "4 月第一波活动",
                        "materialFiles", List.of("https://example.com/material.png")
                )),
                eq(null),
                eq(null)
        );
    }

    @Test
    void putIntoLibrary_shouldCallProductService() {
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        when(productService.putIntoLibrary("10001", "9001", userId, deptId))
                .thenReturn(Map.of("selectedToLibrary", true, "libraryVisible", true));

        var response = controller.putIntoLibrary("10001", "9001", userId, deptId);

        assertThat(response.getData().get("selectedToLibrary")).isEqualTo(true);
        verify(productService).putIntoLibrary("10001", "9001", userId, deptId);
    }

    @Test
    void roleAnnotations_shouldMatchBizWorkflow() throws NoSuchMethodException {
        RequireRoles auditRoles = ColonelActivityProductController.class
                .getMethod("audit", String.class, String.class, ColonelActivityProductController.AuditRequest.class, UUID.class, UUID.class)
                .getAnnotation(RequireRoles.class);
        RequireRoles bindRoles = ColonelActivityProductController.class
                .getMethod("bindActivity", String.class, String.class, ColonelActivityProductController.BindActivityRequest.class, UUID.class, UUID.class)
                .getAnnotation(RequireRoles.class);
        RequireRoles assignAuditOwnerRoles = ColonelActivityProductController.class
                .getMethod("assignAuditOwner", String.class, String.class, ColonelActivityProductController.AssignRequest.class, UUID.class, UUID.class, List.class)
                .getAnnotation(RequireRoles.class);
        RequireRoles decisionRoles = ColonelActivityProductController.class
                .getMethod("decision", String.class, String.class, ColonelActivityProductController.DecisionRequest.class, UUID.class, UUID.class)
                .getAnnotation(RequireRoles.class);
        RequireRoles libraryRoles = ColonelActivityProductController.class
                .getMethod("putIntoLibrary", String.class, String.class, UUID.class, UUID.class)
                .getAnnotation(RequireRoles.class);

        assertThat(auditRoles.value()).containsExactly(RoleCodes.BIZ_STAFF);
        assertThat(bindRoles.value()).containsExactly(RoleCodes.BIZ_LEADER, RoleCodes.COLONEL_LEADER);
        assertThat(assignAuditOwnerRoles.value()).containsExactly(RoleCodes.BIZ_LEADER, RoleCodes.COLONEL_LEADER);
        assertThat(decisionRoles.value()).containsExactly(RoleCodes.BIZ_STAFF);
        assertThat(libraryRoles.value()).containsExactly(RoleCodes.BIZ_STAFF);
    }

    @Test
    void promotionLinkRequest_shouldKeepDefaultsAndTreatNullNeedShortLinkAsTrue() {
        ColonelActivityProductController.PromotionLinkRequest request =
                new ColonelActivityProductController.PromotionLinkRequest();

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
    void auditRequest_shouldNormalizeSupplementMap() {
        ColonelActivityProductController.AuditRequest request = new ColonelActivityProductController.AuditRequest();
        request.setApproved(true);
        request.setReason(" ok ");
        request.setExclusivePriceRemark("  专属价  ");
        request.setShippingInfo("  48小时发货  ");
        request.setPromotionScript("  主打复购  ");
        request.setRewardRemark("  返佣奖励  ");
        request.setParticipationRequirements("  食品类目  ");
        request.setCampaignTimeRemark("  本周  ");
        request.setSupportsAds(Boolean.TRUE);
        request.setSampleThresholdSales(30000L);
        request.setSampleThresholdLevel(2);
        request.setSampleThresholdRemark("  需出镜  ");
        request.setSellingPoints(List.of(" 高复购 ", "", "  "));
        request.setMaterialFiles(List.of(" https://example.test/a.png ", " "));

        Map<String, Object> supplement = request.toSupplementMap();

        assertThat(request.isApproved()).isTrue();
        assertThat(request.getReason()).isEqualTo(" ok ");
        assertThat(supplement)
                .containsEntry("exclusivePriceRemark", "专属价")
                .containsEntry("shippingInfo", "48小时发货")
                .containsEntry("promotionScript", "主打复购")
                .containsEntry("rewardRemark", "返佣奖励")
                .containsEntry("participationRequirements", "食品类目")
                .containsEntry("campaignTimeRemark", "本周")
                .containsEntry("supportsAds", true)
                .containsEntry("sampleThresholdSales", 30000L)
                .containsEntry("sampleThresholdLevel", 2)
                .containsEntry("sampleThresholdRemark", "需出镜")
                .containsEntry("sellingPoints", List.of("高复购"))
                .containsEntry("materialFiles", List.of("https://example.test/a.png"));
    }

    @Test
    void simpleRequestTypes_shouldExposeAssignedValues() {
        UUID assigneeId = UUID.randomUUID();
        ColonelActivityProductController.BindActivityRequest bind = new ColonelActivityProductController.BindActivityRequest();
        bind.setBoundActivityId("A-100");
        ColonelActivityProductController.AssignRequest assign = new ColonelActivityProductController.AssignRequest();
        assign.setAssigneeId(assigneeId);
        ColonelActivityProductController.DecisionRequest decision = new ColonelActivityProductController.DecisionRequest();
        decision.setDecisionLevel("MAIN");
        decision.setReason("佣金高");

        assertThat(bind.getBoundActivityId()).isEqualTo("A-100");
        assertThat(assign.getAssigneeId()).isEqualTo(assigneeId);
        assertThat(decision.getDecisionLevel()).isEqualTo("MAIN");
        assertThat(decision.getReason()).isEqualTo("佣金高");
    }
}
