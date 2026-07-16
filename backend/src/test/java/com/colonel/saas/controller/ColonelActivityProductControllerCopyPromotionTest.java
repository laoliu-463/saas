package com.colonel.saas.controller;

import com.colonel.saas.auth.service.SysUserService;
import com.colonel.saas.domain.product.application.CopyPromotionApplicationService;
import com.colonel.saas.service.ProductPinService;
import com.colonel.saas.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.constant.RoleCodes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ColonelActivityProductControllerCopyPromotionTest {

    @Mock
    private ProductService productService;
    @Mock
    private CopyPromotionApplicationService copyPromotionApplicationService;
    @Mock
    private ProductPinService productPinService;
    @Mock
    private SysUserService sysUserService;

    private ColonelActivityProductController controller;

    @BeforeEach
    void setUp() {
        controller = new ColonelActivityProductController(
                productService,
                copyPromotionApplicationService,
                productPinService,
                sysUserService);
    }

    @Test
    void generatePromotionLink_shouldDelegateCopyPromotionApplicationService() {
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        com.colonel.saas.domain.product.application.dto.PromotionLinkCopyResult expected =
                new com.colonel.saas.domain.product.application.dto.PromotionLinkCopyResult(
                "copy text",
                true,
                "https://s.link",
                "PS-1",
                null,
                true,
                true);
        when(copyPromotionApplicationService.copyPromotion(
                "ACT-1",
                "P-1",
                userId,
                deptId,
                "ext-1",
                4,
                true,
                "PRODUCT_LIBRARY",
                "talent-1",
                "idem-1")).thenReturn(expected);

        ColonelActivityProductController.PromotionLinkRequest request =
                new ColonelActivityProductController.PromotionLinkRequest();
        request.setExternalUniqueId("ext-1");
        request.setPromotionScene(4);
        request.setNeedShortLink(true);
        request.setScene("PRODUCT_LIBRARY");
        request.setTalentId("talent-1");

        var response = controller.generatePromotionLink(
                "ACT-1",
                "P-1",
                request,
                "idem-1",
                userId,
                deptId);

        assertThat(response.getData()).isSameAs(expected);
        verify(copyPromotionApplicationService).copyPromotion(
                "ACT-1",
                "P-1",
                userId,
                deptId,
                "ext-1",
                4,
                true,
                "PRODUCT_LIBRARY",
                "talent-1",
                "idem-1");
        verifyNoInteractions(productService);
    }

    @Test
    void generatePromotionLink_shouldAllowChannelAndRecruiterRoles() throws Exception {
        var method = ColonelActivityProductController.class.getMethod(
                "generatePromotionLink",
                String.class,
                String.class,
                ColonelActivityProductController.PromotionLinkRequest.class,
                String.class,
                UUID.class,
                UUID.class);

        assertThat(method.getAnnotation(RequireRoles.class).value()).containsExactly(
                RoleCodes.CHANNEL_LEADER,
                RoleCodes.CHANNEL_STAFF,
                RoleCodes.BIZ_LEADER,
                RoleCodes.BIZ_STAFF);
    }

    @Test
    void generatePromotionLink_shouldRouteThroughCopyPromotionApplicationService() throws Exception {
        Path sourcePath = Path.of("src/main/java/com/colonel/saas/controller/ColonelActivityProductController.java");
        if (!Files.exists(sourcePath)) {
            sourcePath = Path.of("backend/src/main/java/com/colonel/saas/controller/ColonelActivityProductController.java");
        }
        String source = Files.readString(sourcePath);

        assertThat(source)
                .contains("CopyPromotionApplicationService")
                .doesNotContain("productService.generatePromotionLinkCopy");
    }

    @Test
    void audit_shouldPassSupplementFieldsToProductService() {
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        Map<String, Object> expected = Map.of("status", "APPROVED");
        when(productService.auditProduct(eq("ACT-1"), eq("P-1"), eq(true), eq("素材完整"), any(), same(userId), same(deptId)))
                .thenReturn(expected);

        ColonelActivityProductController.AuditRequest request =
                new ColonelActivityProductController.AuditRequest();
        request.setApproved(true);
        request.setReason("素材完整");
        request.setExclusivePriceRemark(" 直播间专属价 129 元 ");
        request.setShippingInfo("48 小时内发货");
        request.setSellingPoints(List.of("高复购", " ", "夏季场景强"));
        request.setPromotionScript("主打复购和囤货场景");
        request.setSupportsAds(true);
        request.setAdsRule("投流 1:0.5");
        request.setRewardRemark("破 3 万 GMV 额外奖励");
        request.setParticipationRequirements("近 30 天有成交记录");
        request.setCampaignTimeRemark("6 月 1 日至 6 月 15 日");
        request.setMaterialFiles(List.of("https://example.com/card.png", " "));
        request.setGoodsTags(List.of("食品", "夏季"));
        request.setProductTags(List.of("主推"));
        request.setSampleThresholdSales(30000L);
        request.setSampleThresholdLevel(2);
        request.setSampleThresholdRemark("需真人出镜");

        var response = controller.audit("ACT-1", "P-1", request, userId, deptId);

        assertThat(response.getData()).isSameAs(expected);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> supplementCaptor = ArgumentCaptor.forClass(Map.class);
        verify(productService).auditProduct(
                eq("ACT-1"),
                eq("P-1"),
                eq(true),
                eq("素材完整"),
                supplementCaptor.capture(),
                same(userId),
                same(deptId));
        assertThat(supplementCaptor.getValue())
                .containsEntry("exclusivePriceRemark", "直播间专属价 129 元")
                .containsEntry("shippingInfo", "48 小时内发货")
                .containsEntry("promotionScript", "主打复购和囤货场景")
                .containsEntry("supportsAds", true)
                .containsEntry("adsRule", "投流 1:0.5")
                .containsEntry("rewardRemark", "破 3 万 GMV 额外奖励")
                .containsEntry("participationRequirements", "近 30 天有成交记录")
                .containsEntry("campaignTimeRemark", "6 月 1 日至 6 月 15 日")
                .containsEntry("sampleThresholdSales", 30000L)
                .containsEntry("sampleThresholdLevel", 2)
                .containsEntry("sampleThresholdRemark", "需真人出镜");
        assertThat(supplementCaptor.getValue().get("sellingPoints"))
                .asList()
                .containsExactly("高复购", "夏季场景强");
        assertThat(supplementCaptor.getValue().get("materialFiles"))
                .asList()
                .containsExactly("https://example.com/card.png");
        assertThat(supplementCaptor.getValue().get("goodsTags"))
                .asList()
                .containsExactly("食品", "夏季");
        assertThat(supplementCaptor.getValue().get("productTags"))
                .asList()
                .containsExactly("主推");
    }
}
