package com.colonel.saas.service;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * COMMISSION-RULE-SOURCE-001 placeholder.
 * Enable after business provides commission rule source (COMMISSION-RULE-IMPLEMENT-001).
 */
@Disabled("BLOCKED_BY_COMMISSION_RULE_SOURCE: awaiting differentiated commission rules from business")
class CommissionRuleSourceAlignmentTest {

    @Test
    @DisplayName("2026-06-08..13 daily gross profit should match user baseline after rule import")
    void dailyGrossProfit_shouldMatchUserBaseline_afterRuleImport() {
        // TODO COMMISSION-RULE-IMPLEMENT-001:
        // 1. seed commissions or order-level snapshots from business export
        // 2. recalculate performance_records for controlled date range
        // 3. assert API /api/data/orders/summary grossProfit per day
        throw new UnsupportedOperationException("Not implemented until commission rule source is provided");
    }

    @Test
    @DisplayName("commission rule priority: import > product > activity > user > global")
    void commissionRulePriority_shouldResolveCorrectly() {
        throw new UnsupportedOperationException("Not implemented until commission rule source is provided");
    }
}
