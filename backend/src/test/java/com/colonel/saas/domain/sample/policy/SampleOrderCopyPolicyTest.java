package com.colonel.saas.domain.sample.policy;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SampleOrderCopyPolicyTest {

    private final SampleOrderCopyPolicy policy = new SampleOrderCopyPolicy();

    @Test
    void shouldFormatCopyTextInConfirmedFieldOrder() {
        String text = policy.format(new SampleOrderCopyPolicy.OrderCopyFacts(
                "示例商品",
                "3828773814163079204",
                "示例店铺",
                1,
                "示例规格",
                null,
                "示例达人",
                "example_douyin",
                22_000L,
                3_571L,
                "示例收货人",
                "15000000000",
                "示例收货地址"));

        assertThat(text).isEqualTo("""
                商品名称：示例商品
                商品ID：3828773814163079204
                店铺：示例店铺
                申请数量：1
                商品规格：示例规格
                申样备注：
                达人昵称：示例达人
                抖音号：example_douyin
                粉丝数：2.2W
                近30天橱窗销量：3571
                收货人：示例收货人
                收货电话：15000000000
                收货地址：示例收货地址""");
    }
}
