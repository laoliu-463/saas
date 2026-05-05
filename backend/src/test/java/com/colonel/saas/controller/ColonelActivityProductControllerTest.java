package com.colonel.saas.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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

    private ColonelActivityProductController controller;

    @BeforeEach
    void setUp() {
        controller = new ColonelActivityProductController(productService);
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

        var response = controller.assign("10001", "9001", request, null, null);

        assertThat(response.getData().get("bizStatus")).isEqualTo("ASSIGNED");
        assertThat(response.getData().get("assigneeId")).isEqualTo(assigneeId);
        verify(productService).assignProduct("10001", "9001", assigneeId, null, null);
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
}
