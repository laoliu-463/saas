package com.colonel.saas.domain.talent.policy;

import com.colonel.saas.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link TalentTagPolicy} 单测（DDD-TALENT-003）。
 */
class TalentTagPolicyTest {

    @Test
    @DisplayName("null / 空列表 → 空列表")
    void normalize_nullOrEmpty_returnsEmpty() {
        assertThat(TalentTagPolicy.normalize(null, List.of("A", "B"))).isEmpty();
        assertThat(TalentTagPolicy.normalize(List.of(), List.of("A", "B"))).isEmpty();
    }

    @Test
    @DisplayName("去重并保留插入顺序；trim 首尾空白")
    void normalize_dedupesAndTrims() {
        List<String> result = TalentTagPolicy.normalize(
                List.of("  A ", "B", "A", "  B  ", "C"),
                List.of("A", "B", "C"));

        assertThat(result).containsExactly("A", "B", "C");
    }

    @Test
    @DisplayName("空白标签被忽略；不影响其他标签归一化")
    void normalize_skipsBlankEntries() {
        List<String> result = TalentTagPolicy.normalize(
                Arrays.asList("", "  ", "A", null, "B"),
                Arrays.asList("A", "B"));

        assertThat(result).containsExactly("A", "B");
    }

    @Test
    @DisplayName("限制最多 3 个标签（多余的被截断）")
    void normalize_capsAtMax() {
        List<String> result = TalentTagPolicy.normalize(
                List.of("A", "B", "C", "D", "E"),
                List.of("A", "B", "C", "D", "E"));

        assertThat(result).hasSize(TalentTagPolicy.MAX_TALENT_TAGS);
        assertThat(result).containsExactly("A", "B", "C");
        assertThat(TalentTagPolicy.MAX_TALENT_TAGS).isEqualTo(3);
    }

    @Test
    @DisplayName("预设库非空且标签未命中 → 抛 BusinessException 包含所有非法标签")
    void normalize_presetsEnforced_rejectsInvalid() {
        assertThatThrownBy(() -> TalentTagPolicy.normalize(
                List.of("A", "BAD1", "BAD2", "B"),
                List.of("A", "B")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("BAD1")
                .hasMessageContaining("BAD2");
    }

    @Test
    @DisplayName("预设库为空时不强制校验，所有非空标签都通过")
    void normalize_emptyPresets_disablesEnforcement() {
        List<String> result = TalentTagPolicy.normalize(
                List.of("ANYTHING", "GOES", "HERE"),
                List.of());

        assertThat(result).containsExactly("ANYTHING", "GOES", "HERE");
    }

    @Test
    @DisplayName("预设库为 null 时等同于空，不强制校验")
    void normalize_nullPresets_disablesEnforcement() {
        List<String> result = TalentTagPolicy.normalize(
                List.of("X", "Y"),
                null);

        assertThat(result).containsExactly("X", "Y");
    }

    @Test
    @DisplayName("非法标签和重复合法标签混存 → 抛错（非法优先返回，不静默吞）")
    void normalize_invalidAndDuplicates_reportsAllInvalid() {
        assertThatThrownBy(() -> TalentTagPolicy.normalize(
                Arrays.asList("A", "BAD", "A", "ALSO_BAD"),
                Arrays.asList("A", "B")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("BAD")
                .hasMessageContaining("ALSO_BAD");
    }
}
