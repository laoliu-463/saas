package com.colonel.saas.domain.product.policy;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 商品展示去重策略输出（DDD-PRODUCT-002）。
 */
public record ProductDisplayPolicyResult(
        UUID selectedRelationId,
        List<UUID> hiddenRelationIds,
        String displayDecision,
        Map<UUID, String> hideReasons,
        Map<UUID, String> displayReasons,
        boolean whetherNeedEvent,
        List<UUID> eventCandidates,
        UUID previousDisplayRelationId,
        List<RelationDisplayOutcome> relationOutcomes
) {
    public record RelationDisplayOutcome(
            UUID relationId,
            String nextDisplayStatus,
            String hiddenReason,
            String displayReason
    ) {
    }
}
