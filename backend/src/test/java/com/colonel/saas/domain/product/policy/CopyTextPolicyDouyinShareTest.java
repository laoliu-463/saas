package com.colonel.saas.domain.product.policy;

import com.colonel.saas.entity.ProductOperationState;
import com.colonel.saas.entity.ProductSnapshot;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CopyTextPolicyDouyinShareTest {

    @Test
    void renderDouyinShare_shouldRenderExactBusinessLinesWithoutInventory() {
        ProductSnapshot snapshot = new ProductSnapshot();
        snapshot.setTitle("商品名称");
        snapshot.setShopName("店铺");
        snapshot.setPrice(2990L);
        snapshot.setActivityCosRatio(1500L);
        snapshot.setActivityAdCosRatio(500L);
        snapshot.setPromotionStartTime("2026-01-01 00:00:00");
        snapshot.setPromotionEndTime("2026-01-31 23:59:59");
        snapshot.setProductStock("999");

        ProductOperationState state = new ProductOperationState();
        state.setAuditPayload("""
                {
                  "rewardRemark":"出视频就投，控roi1.2 混剪不一定能投，要看投手评估审核",
                  "promotionStartTime":"2026-07-16 00:00:00",
                  "promotionEndTime":"2027-07-31 23:59:59"
                }
                """);
        String promotionLink = "https://haohuo.jinritemai.com/ecommerce/trade/detail/index.html?id=3820194249627009436";

        String text = CopyTextPolicy.renderDouyinShare(snapshot, state, promotionLink);

        assertThat(text).isEqualTo("""
                【抖音】商品名称
                【店铺名称】店铺
                【售价】29.9元
                【佣金率】15%
                【投放期佣金】5%
                【奖励说明】出视频就投，控roi1.2 混剪不一定能投，要看投手评估审核
                【开始时间】2026-07-16 00:00:00
                【结束时间】2027-07-31 23:59:59
                【推广链接】
                https://haohuo.jinritemai.com/ecommerce/trade/detail/index.html?id=3820194249627009436""");
        assertThat(text).doesNotContain("【库存】");
    }

    @Test
    void renderDouyinShare_shouldFormatCentsAndBasisPointsWithoutFloatingPointError() {
        ProductSnapshot snapshot = new ProductSnapshot();
        snapshot.setTitle("边界商品");
        snapshot.setShopName("边界店铺");
        snapshot.setPrice(1L);
        snapshot.setActivityCosRatio(1L);
        snapshot.setActivityAdCosRatio(155L);

        String text = CopyTextPolicy.renderDouyinShare(snapshot, new ProductOperationState(), null);

        assertThat(text)
                .contains("【售价】0.01元")
                .contains("【佣金率】0.01%")
                .contains("【投放期佣金】1.55%")
                .contains("【推广链接】\n未生成");
    }

    @Test
    void renderDouyinShare_shouldUseClearPlaceholdersForMissingRewardAndTimes() {
        ProductSnapshot snapshot = new ProductSnapshot();
        snapshot.setTitle("缺省商品");
        snapshot.setShopName("缺省店铺");
        snapshot.setPriceText("29.90");
        snapshot.setActivityCosRatio(0L);
        snapshot.setActivityAdCosRatio(0L);

        String text = CopyTextPolicy.renderDouyinShare(snapshot, null, null);

        assertThat(text)
                .contains("【售价】29.9元")
                .contains("【佣金率】0%")
                .contains("【投放期佣金】0%")
                .contains("【奖励说明】-\n")
                .contains("【开始时间】-\n")
                .contains("【结束时间】-\n");
    }
}
