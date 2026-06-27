package com.colonel.saas.domain.product.policy;

import com.colonel.saas.common.enums.ProductBizStatus;
import com.colonel.saas.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductOperationDecisionPolicyTest {

    private final ProductOperationDecisionPolicy policy = new ProductOperationDecisionPolicy();

    @Test
    void libraryEntry_shouldBuildOperationLogDecision() {
        ProductOperationDecisionPolicy.OperationDecision decision = policy.libraryEntry("测试商品");

        assertThat(decision.operationType()).isEqualTo("LIBRARY_ENTRY");
        assertThat(decision.operationRemark()).isEqualTo("上游状态为推广中，已加入商品库");
        assertThat(decision.payload())
                .containsEntry("eventLabel", "加入商品库")
                .containsEntry("productTitle", "测试商品");
    }

    @Test
    void bindActivity_shouldBuildOperationLogDecision() {
        ProductOperationDecisionPolicy.OperationDecision decision = policy.bindActivity("ACT-2");

        assertThat(decision.operationType()).isEqualTo("BIND_ACTIVITY");
        assertThat(decision.operationRemark()).isEqualTo("绑定活动成功");
        assertThat(decision.payload())
                .containsEntry("boundActivityId", "ACT-2")
                .containsEntry("eventLabel", "商品活动绑定已更新");
    }

    @Test
    void assignProduct_shouldBuildStatusOperationDecision() {
        UUID assigneeId = UUID.randomUUID();
        UUID operatorId = UUID.randomUUID();

        ProductOperationDecisionPolicy.OperationDecision decision =
                policy.assignProduct(assigneeId, "招商组长", operatorId, "管理员");

        assertThat(decision.targetStatus()).isEqualTo(ProductBizStatus.ASSIGNED);
        assertThat(decision.operationType()).isEqualTo("ASSIGN");
        assertThat(decision.operationRemark()).isEqualTo("分配招商成功");
        assertThat(decision.payload())
                .containsEntry("assigneeId", assigneeId)
                .containsEntry("assigneeName", "招商组长")
                .containsEntry("operatorId", operatorId)
                .containsEntry("operatorName", "管理员")
                .containsEntry("eventLabel", "商品已分配给招商组长");
    }

    @Test
    void assignAuditOwner_shouldOnlyAllowPendingAudit() {
        assertThatThrownBy(() -> policy.assignAuditOwner(
                ProductBizStatus.APPROVED,
                UUID.randomUUID(),
                "审核人",
                UUID.randomUUID(),
                "管理员"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅待审核商品可分配审核人");
    }

    @Test
    void progressDecision_shouldNormalizeDecisionLevelAndTrimReason() {
        UUID operatorId = UUID.randomUUID();

        ProductOperationDecisionPolicy.OperationDecision decision =
                policy.progressDecision(" secondary ", "  保留观察  ", operatorId, "管理员");

        assertThat(decision.operationType()).isEqualTo("DECISION");
        assertThat(decision.operationRemark()).isEqualTo("保留观察");
        assertThat(decision.payload())
                .containsEntry("decisionLevel", "SECONDARY")
                .containsEntry("decisionLabel", "次推")
                .containsEntry("operatorId", operatorId)
                .containsEntry("eventLabel", "商品推进判断已更新");
    }

    @Test
    void progressDecision_shouldRejectUnknownLevelAndBlankReason() {
        assertThatThrownBy(() -> policy.progressDecision("UNKNOWN", "原因", null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未知推进判断");
        assertThatThrownBy(() -> policy.progressDecision("MAIN", " ", null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("推进判断原因不能为空");
    }
}
