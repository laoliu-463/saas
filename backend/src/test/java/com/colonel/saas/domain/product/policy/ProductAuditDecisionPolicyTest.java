package com.colonel.saas.domain.product.policy;

import com.colonel.saas.common.enums.ProductBizStatus;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.constant.ProductDisplayStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductAuditDecisionPolicyTest {

    private final ProductAuditDecisionPolicy policy = new ProductAuditDecisionPolicy();

    @Test
    void approve_shouldRequireSupplementAndBuildStatusLogDecision() {
        ProductAuditDecisionPolicy.AuditDecision decision = policy.resolve(
                true,
                null,
                fullSupplement());

        assertThat(decision.targetStatus()).isEqualTo(ProductBizStatus.APPROVED);
        assertThat(decision.operationType()).isEqualTo("AUDIT");
        assertThat(decision.operationRemark()).isEqualTo("审核通过，已加入商品库");
        assertThat(decision.auditStatus()).isEqualTo(2);
        assertThat(decision.auditRemark()).isNull();
        assertThat(decision.normalizedSupplement()).containsEntry("supportsAds", true);
        assertThat(decision.payload())
                .containsEntry("eventLabel", "审核通过并加入商品库")
                .containsEntry("selectedToLibrary", true)
                .containsEntry("libraryVisible", true);
    }

    @Test
    void approve_shouldRejectMissingRequiredSupplement() {
        assertThatThrownBy(() -> policy.resolve(true, null, Map.of("supportsAds", true)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("审核通过前请补充：专属价说明");
    }

    @Test
    void reject_shouldBuildHiddenStatusDecisionAndTrimReason() {
        ProductAuditDecisionPolicy.AuditDecision decision = policy.resolve(
                false,
                "  不符合商品库要求  ",
                null);

        assertThat(decision.targetStatus()).isEqualTo(ProductBizStatus.REJECTED);
        assertThat(decision.operationType()).isEqualTo("AUDIT");
        assertThat(decision.operationRemark()).isEqualTo("审核拒绝");
        assertThat(decision.auditStatus()).isEqualTo(3);
        assertThat(decision.auditRemark()).isEqualTo("不符合商品库要求");
        assertThat(decision.displayStatus()).isEqualTo(ProductDisplayStatus.HIDDEN.name());
        assertThat(decision.hiddenReason()).isEqualTo("审核拒绝");
        assertThat(decision.payload()).containsEntry("eventLabel", "审核拒绝");
    }

    @Test
    void reject_shouldRequireReason() {
        assertThatThrownBy(() -> policy.resolve(false, " ", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("审核拒绝时必须填写原因");
    }

    private Map<String, Object> fullSupplement() {
        return Map.of(
                "exclusivePriceRemark", "专属价",
                "shippingInfo", "48小时发货",
                "sellingPoints", List.of("卖点1"),
                "promotionScript", "推广话术",
                "supportsAds", true,
                "rewardRemark", "奖励说明",
                "participationRequirements", "参与要求",
                "campaignTimeRemark", "活动时间",
                "materialFiles", List.of("hand-card.png")
        );
    }
}
