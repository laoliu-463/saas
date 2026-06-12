package com.colonel.saas.domain.sample.policy;

import com.colonel.saas.domain.config.facade.dto.SampleDefaultStandardDTO;
import com.colonel.saas.entity.Talent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SampleEligibilityPolicy} 单测（DDD-SAMPLE-002）。
 *
 * <p>覆盖纯业务规则：unsupported 字段判定、达人等级归一化与比较、达标/不达标原因聚合。</p>
 */
class SampleEligibilityPolicyTest {

    private static SampleDefaultStandardDTO standard(Long minSales, String minLevel) {
        return new SampleDefaultStandardDTO(minSales, minLevel, Map.of());
    }

    // ---------- isUnsupported ----------

    @Test
    @DisplayName("isUnsupported: talent 为 null 时 sales30d / talentLevel 视为 unsupported")
    void isUnsupported_nullTalent_flagsSalesAndLevel() {
        assertThat(SampleEligibilityPolicy.isUnsupported(null, "sales30d")).isTrue();
        assertThat(SampleEligibilityPolicy.isUnsupported(null, "talentLevel")).isTrue();
        assertThat(SampleEligibilityPolicy.isUnsupported(null, "otherField")).isFalse();
    }

    @Test
    @DisplayName("isUnsupported: unsupportedFields 为 null 时 sales30d / talentLevel 视为 unsupported")
    void isUnsupported_nullUnsupportedFields_flagsSalesAndLevel() {
        Talent talent = new Talent();
        talent.setUnsupportedFields(null);
        assertThat(SampleEligibilityPolicy.isUnsupported(talent, "sales30d")).isTrue();
        assertThat(SampleEligibilityPolicy.isUnsupported(talent, "talentLevel")).isTrue();
    }

    @Test
    @DisplayName("isUnsupported: unsupportedFields 为空列表时不视任何字段为 unsupported")
    void isUnsupported_emptyList_flagsNothing() {
        Talent talent = new Talent();
        talent.setUnsupportedFields(List.of());
        assertThat(SampleEligibilityPolicy.isUnsupported(talent, "sales30d")).isFalse();
        assertThat(SampleEligibilityPolicy.isUnsupported(talent, "talentLevel")).isFalse();
    }

    @Test
    @DisplayName("isUnsupported: 大小写不敏感、忽略空值与首尾空格")
    void isUnsupported_caseInsensitiveAndTrimmed() {
        Talent talent = new Talent();
        talent.setUnsupportedFields(List.of("", "  Sales30d  ", "OTHER"));
        assertThat(SampleEligibilityPolicy.isUnsupported(talent, "sales30d")).isTrue();
        assertThat(SampleEligibilityPolicy.isUnsupported(talent, "talentLevel")).isFalse();
    }

    // ---------- normalizeLevel ----------

    @Test
    @DisplayName("normalizeLevel: LV 格式直接返回；A/S 映射 LV2；B 映射 LV1；其他 LV0")
    void normalizeLevel_variousInputs() {
        assertThat(SampleEligibilityPolicy.normalizeLevel("LV2")).isEqualTo("LV2");
        assertThat(SampleEligibilityPolicy.normalizeLevel("lv1")).isEqualTo("LV1");
        assertThat(SampleEligibilityPolicy.normalizeLevel("A")).isEqualTo("LV2");
        assertThat(SampleEligibilityPolicy.normalizeLevel("S")).isEqualTo("LV2");
        assertThat(SampleEligibilityPolicy.normalizeLevel("B")).isEqualTo("LV1");
        assertThat(SampleEligibilityPolicy.normalizeLevel("X")).isEqualTo("LV0");
        assertThat(SampleEligibilityPolicy.normalizeLevel("")).isEqualTo("LV0");
        assertThat(SampleEligibilityPolicy.normalizeLevel(null)).isEqualTo("LV0");
    }

    // ---------- compareLevel / levelRank ----------

    @Test
    @DisplayName("compareLevel: LV2 > LV1 > LV0；A/S 等价 LV2")
    void compareLevel_ordersCorrectly() {
        assertThat(SampleEligibilityPolicy.compareLevel("LV2", "LV1")).isPositive();
        assertThat(SampleEligibilityPolicy.compareLevel("LV1", "LV0")).isPositive();
        assertThat(SampleEligibilityPolicy.compareLevel("LV0", "LV0")).isZero();
        assertThat(SampleEligibilityPolicy.compareLevel("A", "LV1")).isPositive();
        assertThat(SampleEligibilityPolicy.compareLevel("B", "LV0")).isPositive();
        assertThat(SampleEligibilityPolicy.compareLevel(null, "LV0")).isZero();
    }

    // ---------- evaluate: 销售 + 等级判定 ----------

    @Test
    @DisplayName("evaluate: 同时满足销售和等级 → eligible=true，无 reasons")
    void evaluate_passesAllChecks() {
        Talent talent = new Talent();
        talent.setSales30d(8000L);
        talent.setTalentLevel("LV2");
        talent.setUnsupportedFields(List.of());

        SampleEligibilityPolicy.Outcome outcome = SampleEligibilityPolicy.evaluate(
                standard(5000L, "LV1"), talent, null, 8000L, "LV2");

        assertThat(outcome.eligible()).isTrue();
        assertThat(outcome.reasons()).isEmpty();
    }

    @Test
    @DisplayName("evaluate: 销售低于阈值 → 拒绝并提示未达金额")
    void evaluate_salesBelowThreshold() {
        Talent talent = new Talent();
        talent.setSales30d(2000L);
        talent.setTalentLevel("LV2");
        talent.setUnsupportedFields(List.of());

        SampleEligibilityPolicy.Outcome outcome = SampleEligibilityPolicy.evaluate(
                standard(10000L, "LV1"), talent, null, 2000L, "LV2");

        assertThat(outcome.eligible()).isFalse();
        assertThat(outcome.reasons()).anyMatch(r -> r.contains("10000"));
    }

    @Test
    @DisplayName("evaluate: 等级低于阈值 → 拒绝并提示未达等级")
    void evaluate_levelBelowThreshold() {
        Talent talent = new Talent();
        talent.setSales30d(50000L);
        talent.setTalentLevel("LV0");
        talent.setUnsupportedFields(List.of());

        SampleEligibilityPolicy.Outcome outcome = SampleEligibilityPolicy.evaluate(
                standard(1000L, "LV2"), talent, null, 50000L, "LV0");

        assertThat(outcome.eligible()).isFalse();
        assertThat(outcome.reasons()).anyMatch(r -> r.contains("达人等级"));
    }

    @Test
    @DisplayName("evaluate: sales30d 标记为 unsupported → 拒绝并提示填写原因")
    void evaluate_salesUnsupportedSuggestsReason() {
        Talent talent = new Talent();
        talent.setSales30d(50000L);
        talent.setTalentLevel("LV2");
        talent.setUnsupportedFields(List.of("sales30d"));

        SampleEligibilityPolicy.Outcome outcome = SampleEligibilityPolicy.evaluate(
                standard(1000L, "LV1"), talent, null, 50000L, "LV2");

        assertThat(outcome.eligible()).isFalse();
        assertThat(outcome.reasons()).anyMatch(r -> r.contains("未同步") && r.contains("销售额"));
    }

    @Test
    @DisplayName("evaluate: talentLevel 标记为 unsupported → 拒绝并提示填写原因")
    void evaluate_levelUnsupportedSuggestsReason() {
        Talent talent = new Talent();
        talent.setSales30d(50000L);
        talent.setTalentLevel("LV2");
        talent.setUnsupportedFields(List.of("talentLevel"));

        SampleEligibilityPolicy.Outcome outcome = SampleEligibilityPolicy.evaluate(
                standard(1000L, "LV1"), talent, null, 50000L, "LV2");

        assertThat(outcome.eligible()).isFalse();
        assertThat(outcome.reasons()).anyMatch(r -> r.contains("未同步") && r.contains("达人等级"));
    }

    @Test
    @DisplayName("evaluate: standard 为 null → 不输出任何不达标原因（视为无门槛）")
    void evaluate_nullStandardPasses() {
        SampleEligibilityPolicy.Outcome outcome = SampleEligibilityPolicy.evaluate(
                null, null, null, null, null);
        assertThat(outcome.eligible()).isTrue();
        assertThat(outcome.reasons()).isEmpty();
    }

    @Test
    @DisplayName("evaluate: sales30d 标准为 null → 只检查等级")
    void evaluate_onlyLevelCheckedWhenSalesStandardNull() {
        Talent talent = new Talent();
        talent.setTalentLevel("LV0");
        talent.setUnsupportedFields(List.of());

        SampleEligibilityPolicy.Outcome outcome = SampleEligibilityPolicy.evaluate(
                standard(null, "LV2"), talent, null, 0L, "LV0");

        assertThat(outcome.eligible()).isFalse();
        assertThat(outcome.reasons()).hasSize(1);
        assertThat(outcome.reasons().get(0)).contains("达人等级");
    }
}
