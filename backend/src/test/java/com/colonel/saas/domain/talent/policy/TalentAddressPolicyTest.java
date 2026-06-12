package com.colonel.saas.domain.talent.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link TalentAddressPolicy} 单测（DDD-TALENT-003）。
 */
class TalentAddressPolicyTest {

    // ---------- trimToNull ----------

    @Test
    @DisplayName("trimToNull: 空白 / null / 空字符串 → null")
    void trimToNull_blankReturnsNull() {
        assertThat(TalentAddressPolicy.trimToNull(null)).isNull();
        assertThat(TalentAddressPolicy.trimToNull("")).isNull();
        assertThat(TalentAddressPolicy.trimToNull("   ")).isNull();
        assertThat(TalentAddressPolicy.trimToNull("\t\n")).isNull();
    }

    @Test
    @DisplayName("trimToNull: 正常字符串 → trim 后原样返回")
    void trimToNull_trimsAndReturns() {
        assertThat(TalentAddressPolicy.trimToNull("张三")).isEqualTo("张三");
        assertThat(TalentAddressPolicy.trimToNull("  张三  ")).isEqualTo("张三");
        assertThat(TalentAddressPolicy.trimToNull("13800138000")).isEqualTo("13800138000");
    }

    // ---------- normalize 三字段 ----------

    @Test
    @DisplayName("normalize: 三字段全合法 → 全部 trim 后返回")
    void normalize_allValid_trimsAll() {
        TalentAddressPolicy.NormalizedAddress addr = TalentAddressPolicy.normalize(
                "  张三  ", " 13800138000 ", " 上海市浦东新区 ");

        assertThat(addr.recipientName()).isEqualTo("张三");
        assertThat(addr.recipientPhone()).isEqualTo("13800138000");
        assertThat(addr.recipientAddress()).isEqualTo("上海市浦东新区");
    }

    @Test
    @DisplayName("normalize: 部分字段空白 → 空白字段为 null，其他保留")
    void normalize_partialBlank_nullsBlankOnly() {
        TalentAddressPolicy.NormalizedAddress addr = TalentAddressPolicy.normalize(
                "张三", "  ", "上海市");

        assertThat(addr.recipientName()).isEqualTo("张三");
        assertThat(addr.recipientPhone()).isNull();
        assertThat(addr.recipientAddress()).isEqualTo("上海市");
    }

    @Test
    @DisplayName("normalize: 三字段全空白 → 全部 null（用于清空地址语义）")
    void normalize_allBlank_returnsAllNull() {
        TalentAddressPolicy.NormalizedAddress addr = TalentAddressPolicy.normalize(
                null, "", "   ");

        assertThat(addr.recipientName()).isNull();
        assertThat(addr.recipientPhone()).isNull();
        assertThat(addr.recipientAddress()).isNull();
    }

    @Test
    @DisplayName("normalize: 全部 null 入参 → 全部 null（不抛 NPE）")
    void normalize_allNull_safe() {
        TalentAddressPolicy.NormalizedAddress addr = TalentAddressPolicy.normalize(null, null, null);

        assertThat(addr.recipientName()).isNull();
        assertThat(addr.recipientPhone()).isNull();
        assertThat(addr.recipientAddress()).isNull();
    }
}
