package com.colonel.saas.domain.sample.policy;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SampleOrderCopyPolicyTest {

    private final SampleOrderCopyPolicy policy = new SampleOrderCopyPolicy();

    @Test
    void format_shouldProduceExactThirteenLineOrderText() {
        String text = policy.format(new SampleOrderCopyPolicy.OrderCopyFacts(
                "轻奢防晒霜",
                "3820194249627009436",
                "轻奢美妆旗舰店",
                2,
                "50ml",
                "主播试用",
                "达人甲",
                "dy001",
                68000L,
                321L,
                "张三",
                "13800000000",
                "杭州市西湖区测试路 1 号"));

        assertThat(text).isEqualTo(String.join("\n",
                "商品名称：轻奢防晒霜",
                "商品ID：3820194249627009436",
                "店铺：轻奢美妆旗舰店",
                "申请数量：2",
                "商品规格：50ml",
                "申样备注：主播试用",
                "达人昵称：达人甲",
                "抖音号：dy001",
                "粉丝数：6.8W",
                "近30天橱窗销量：321",
                "收货人：张三",
                "收货电话：13800000000",
                "收货地址：杭州市西湖区测试路 1 号"));
        assertThat(text.split("\n", -1)).hasSize(13);
    }

    @Test
    void format_shouldHandleFollowerBoundariesAndFailClosedValues() {
        assertThat(followerLine(10000L)).isEqualTo("粉丝数：1W");
        assertThat(followerLine(9999L)).isEqualTo("粉丝数：9999");
        assertThat(followerLine(null)).isEqualTo("粉丝数：---");
        assertThat(followerLine(-1L)).isEqualTo("粉丝数：---");
    }

    @Test
    void format_shouldFailClosedWhenQuantityIsMissingOrNotPositive() {
        assertThat(quantityLine(null)).isEqualTo("申请数量：---");
        assertThat(quantityLine(0)).isEqualTo("申请数量：---");
        assertThat(quantityLine(-1)).isEqualTo("申请数量：---");
    }

    @Test
    void format_shouldKeepBlankRemarkLineAndUsePlaceholdersForMissingFacts() {
        String text = policy.format(new SampleOrderCopyPolicy.OrderCopyFacts(
                null,
                null,
                "  ",
                null,
                null,
                null,
                null,
                null,
                null,
                -1L,
                null,
                null,
                null));

        assertThat(text).containsSubsequence(
                "商品名称：---\n",
                "商品ID：---\n",
                "店铺：---\n",
                "申请数量：---\n",
                "商品规格：---\n",
                "申样备注：\n",
                "达人昵称：---\n",
                "抖音号：---\n",
                "粉丝数：---\n",
                "近30天橱窗销量：---\n",
                "收货人：---\n",
                "收货电话：---\n",
                "收货地址：---");
        assertThat(text).doesNotContain("sales30d", "sales_30d");
    }

    @Test
    void format_shouldKeepThirteenLinesWhenDynamicTextContainsControlCharacters() {
        String text = policy.format(new SampleOrderCopyPolicy.OrderCopyFacts(
                " 轻奢\r\n店铺：伪造\u0000尾 ",
                "\u0000\u0001",
                "中文，标点！\u2028第二段",
                2,
                "\u0007",
                "\r\n\u0001",
                "达人甲\n收货人：伪造",
                "dy001\r伪造",
                68000L,
                321L,
                "\u0002张三",
                "138\u00090000",
                "杭州\u2029西湖\u001F区"));

        assertThat(text).isEqualTo(String.join("\n",
                "商品名称：轻奢 店铺：伪造 尾",
                "商品ID：---",
                "店铺：中文，标点！ 第二段",
                "申请数量：2",
                "商品规格：---",
                "申样备注：",
                "达人昵称：达人甲 收货人：伪造",
                "抖音号：dy001 伪造",
                "粉丝数：6.8W",
                "近30天橱窗销量：321",
                "收货人：张三",
                "收货电话：138 0000",
                "收货地址：杭州 西湖 区"));
        assertThat(text.split("\n", -1)).hasSize(13);
        assertThat(text)
                .doesNotContain("\r", "\u2028", "\u2029", "\u0000", "\u0007")
                .doesNotContain("\n店铺：伪造", "\n收货人：伪造");
    }

    private String followerLine(Long followers) {
        String text = policy.format(new SampleOrderCopyPolicy.OrderCopyFacts(
                "商品", "P-1", "店铺", 1, "规格", "备注",
                "达人", "douyin", followers, 1L,
                "收件人", "13800000000", "地址"));
        return text.lines()
                .filter(line -> line.startsWith("粉丝数："))
                .findFirst()
                .orElseThrow();
    }

    private String quantityLine(Integer quantity) {
        String text = policy.format(new SampleOrderCopyPolicy.OrderCopyFacts(
                "商品", "P-1", "店铺", quantity, "规格", "备注",
                "达人", "douyin", 1L, 1L,
                "收件人", "13800000000", "地址"));
        return text.lines()
                .filter(line -> line.startsWith("申请数量："))
                .findFirst()
                .orElseThrow();
    }
}
