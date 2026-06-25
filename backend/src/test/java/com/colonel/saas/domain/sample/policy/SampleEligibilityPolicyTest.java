package com.colonel.saas.domain.sample.policy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 寄样资格评估策略单元测试。
 * <p>纯领域逻辑测试，不依赖 Spring 容器或任何 Mock 框架。</p>
 */
class SampleEligibilityPolicyTest {

    private SampleEligibilityPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new SampleEligibilityPolicy();
    }

    // ── 等级归一化 ──────────────────────────────────────────────

    @Test
    @DisplayName("LV 格式直接保留")
    void normalizeLevel_lvFormat_preserved() {
        assertEquals("LV2", policy.normalizeLevel("LV2"));
        assertEquals("LV0", policy.normalizeLevel("lv0"));
    }

    @Test
    @DisplayName("A/S 映射为 LV2")
    void normalizeLevel_letterA_mapsToLV2() {
        assertEquals("LV2", policy.normalizeLevel("A"));
        assertEquals("LV2", policy.normalizeLevel("S"));
        assertEquals("LV2", policy.normalizeLevel("a"));
    }

    @Test
    @DisplayName("B 映射为 LV1")
    void normalizeLevel_letterB_mapsToLV1() {
        assertEquals("LV1", policy.normalizeLevel("B"));
        assertEquals("LV1", policy.normalizeLevel("b"));
    }

    @Test
    @DisplayName("未知格式映射为 LV0")
    void normalizeLevel_unknown_mapsToLV0() {
        assertEquals("LV0", policy.normalizeLevel("C"));
        assertEquals("LV0", policy.normalizeLevel("unknown"));
    }

    // ── 等级比较 ──────────────────────────────────────────────

    @Test
    @DisplayName("LV2 >= LV1 返回正数或零")
    void compareLevel_higherOrEqual_returnsNonNegative() {
        assertTrue(policy.compareLevel("LV2", "LV1") > 0);
        assertEquals(0, policy.compareLevel("LV2", "LV2"));
    }

    @Test
    @DisplayName("LV0 < LV2 返回负数")
    void compareLevel_lower_returnsNegative() {
        assertTrue(policy.compareLevel("LV0", "LV2") < 0);
    }

    @Test
    @DisplayName("null 等级 rank 为 0")
    void levelRank_null_returnsZero() {
        assertEquals(0, policy.levelRank(null));
        assertEquals(0, policy.levelRank(""));
    }

    // ── 资格评估 ──────────────────────────────────────────────

    @Test
    @DisplayName("全部达标时返回 eligible=true")
    void evaluate_allMet_eligible() {
        SampleEligibilityPolicy.EligibilityResult result = policy.evaluate(
                10000L, "LV1", Map.of(),
                50000L, "LV2",
                false, false);
        assertTrue(result.eligible());
        assertTrue(result.reasons().isEmpty());
    }

    @Test
    @DisplayName("销售额不达标时返回原因")
    void evaluate_salesNotMet_returnsReason() {
        SampleEligibilityPolicy.EligibilityResult result = policy.evaluate(
                10000L, null, Map.of(),
                5000L, null,
                false, false);
        assertFalse(result.eligible());
        assertEquals(1, result.reasons().size());
        assertTrue(result.reasons().get(0).contains("销售额"));
    }

    @Test
    @DisplayName("等级不达标时返回原因")
    void evaluate_levelNotMet_returnsReason() {
        SampleEligibilityPolicy.EligibilityResult result = policy.evaluate(
                null, "LV2", Map.of(),
                null, "LV0",
                false, false);
        assertFalse(result.eligible());
        assertEquals(1, result.reasons().size());
        assertTrue(result.reasons().get(0).contains("等级"));
    }

    @Test
    @DisplayName("销售额 unsupported 时提示填写原因")
    void evaluate_salesUnsupported_promptsReason() {
        SampleEligibilityPolicy.EligibilityResult result = policy.evaluate(
                10000L, null, Map.of(),
                null, null,
                true, false);
        assertFalse(result.eligible());
        assertTrue(result.reasons().get(0).contains("未同步"));
    }

    @Test
    @DisplayName("等级 unsupported 时提示填写原因")
    void evaluate_levelUnsupported_promptsReason() {
        SampleEligibilityPolicy.EligibilityResult result = policy.evaluate(
                null, "LV1", Map.of(),
                null, null,
                false, true);
        assertFalse(result.eligible());
        assertTrue(result.reasons().get(0).contains("未同步"));
    }

    @Test
    @DisplayName("标准不限（null）时跳过校验")
    void evaluate_noStandard_eligible() {
        SampleEligibilityPolicy.EligibilityResult result = policy.evaluate(
                null, null, Map.of(),
                0L, null,
                false, false);
        assertTrue(result.eligible());
    }

    @Test
    @DisplayName("销售额和等级双重不达标时返回两条原因")
    void evaluate_bothNotMet_returnsTwoReasons() {
        SampleEligibilityPolicy.EligibilityResult result = policy.evaluate(
                10000L, "LV2", Map.of(),
                1000L, "LV0",
                false, false);
        assertFalse(result.eligible());
        assertEquals(2, result.reasons().size());
    }

    @Test
    @DisplayName("结果快照包含标准和实际数据")
    void evaluate_snapshotsPopulated() {
        SampleEligibilityPolicy.EligibilityResult result = policy.evaluate(
                10000L, "LV1", Map.of("key", "val"),
                50000L, "LV2",
                false, false);
        assertEquals(10000L, result.standard().min30DaySales());
        assertEquals("LV1", result.standard().minLevel());
        assertEquals(50000L, result.actual().monthlySales());
        assertEquals("LV2", result.actual().level());
    }

    @Test
    @DisplayName("classifyFailureRules 映射不达标原因编码")
    void classifyFailureRules_shouldMapReasonKeywords() {
        assertThat(policy.classifyFailureRules(List.of("近30天销售额未达到 1000")))
                .containsExactly("min30DaySales");
        assertThat(policy.classifyFailureRules(List.of("达人等级未达到 LV2")))
                .containsExactly("minLevel");
        assertThat(policy.classifyFailureRules(List.of("其他原因")))
                .containsExactly("custom");
    }
}
