package com.colonel.saas.domain.product.policy;

import com.colonel.saas.domain.config.facade.ConfigDomainFacade;
import com.colonel.saas.domain.config.facade.dto.PromotionTemplateDTO;
import com.colonel.saas.entity.ProductOperationState;
import com.colonel.saas.entity.ProductSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link CopyTextPolicy} 单测（DDD-PRODUCT-004）。
 *
 * <p>覆盖纯渲染逻辑：displayText 边界、firstText 优先级、模板渲染、硬编码降级、JSON 解析兜底。</p>
 */
class CopyTextPolicyTest {

    // ---------- displayText ----------

    @Test
    @DisplayName("displayText: null / blank / 'null' / 'undefined' / 空白 → \"-\"")
    void displayText_normalizesNullLikeValues() {
        assertThat(CopyTextPolicy.displayText(null)).isEqualTo("-");
        assertThat(CopyTextPolicy.displayText("")).isEqualTo("-");
        assertThat(CopyTextPolicy.displayText("   ")).isEqualTo("-");
        assertThat(CopyTextPolicy.displayText("null")).isEqualTo("-");
        assertThat(CopyTextPolicy.displayText("Null")).isEqualTo("-");
        assertThat(CopyTextPolicy.displayText("undefined")).isEqualTo("-");
        assertThat(CopyTextPolicy.displayText("  undefined  ")).isEqualTo("-");
    }

    @Test
    @DisplayName("displayText: 正常字符串 trim 后返回；数字转字符串")
    void displayText_returnsTrimmedValue() {
        assertThat(CopyTextPolicy.displayText("hello")).isEqualTo("hello");
        assertThat(CopyTextPolicy.displayText("  hello  ")).isEqualTo("hello");
        assertThat(CopyTextPolicy.displayText(123)).isEqualTo("123");
    }

    // ---------- firstText ----------

    @Test
    @DisplayName("firstText: null / 全空 → null；首个非空胜出并 trim")
    void firstText_picksFirstNonBlank() {
        assertThat(CopyTextPolicy.firstText((String[]) null)).isNull();
        assertThat(CopyTextPolicy.firstText(new String[0])).isNull();
        assertThat(CopyTextPolicy.firstText(null, "", "  ")).isNull();
        assertThat(CopyTextPolicy.firstText("", "  ", "winner")).isEqualTo("winner");
        assertThat(CopyTextPolicy.firstText("  short  ", "long")).isEqualTo("short");
        assertThat(CopyTextPolicy.firstText((String) null)).isNull();
    }

    // ---------- render: 模板路径 ----------

    @Test
    @DisplayName("render: 模板存在 → 替换占位符；null 字段显示 \"-\"")
    void render_templateSubstitutesPlaceholders() {
        ConfigDomainFacade facade = mock(ConfigDomainFacade.class);
        when(facade.getPromotionTemplate()).thenReturn(template("{productName} | {commissionRate} | {shortLink} | {custom_text}"));

        ProductSnapshot snapshot = snapshot("商品A", "店铺X", "99.00", "20%");
        ProductOperationState state = stateWithAuditJson(
                "{\"sellingPoints\":[\"卖点1\"],\"promotionScript\":\"话术A\",\"exclusivePriceRemark\":\"专属价说明A\"}");

        String text = CopyTextPolicy.render(facade, snapshot, state, "https://t.cn/abc");

        assertThat(text).isEqualTo("商品A | 20% | https://t.cn/abc | 专属价说明A");
    }

    @Test
    @DisplayName("render: 模板存在但 snapshot 字段全空 → 全部占位符替换为 \"-\"")
    void render_templateHandlesNullSnapshotFields() {
        ConfigDomainFacade facade = mock(ConfigDomainFacade.class);
        when(facade.getPromotionTemplate()).thenReturn(template("商品={productName} 价格={productId} 链接={shortLink}"));

        ProductSnapshot snapshot = new ProductSnapshot();  // 全 null
        ProductOperationState state = stateWithAuditJson(null);

        String text = CopyTextPolicy.render(facade, snapshot, state, null);

        assertThat(text).isEqualTo("商品=- 价格=- 链接=-");
    }

    // ---------- render: 降级到 hardcoded ----------

    @Test
    @DisplayName("render: configDomainFacade 为 null → 走硬编码简介")
    void render_nullFacadeFallsBackToHardcoded() {
        ProductSnapshot snapshot = snapshot("商品名A", "店铺X", "99.00", "20%");
        ProductOperationState state = stateWithAuditJson(
                "{\"sellingPoints\":[\"卖点1\",\"卖点2\"],\"promotionScript\":\"话术A\"," +
                "\"sampleThresholdSales\":\"1000\",\"sampleThresholdLevel\":\"1\"," +
                "\"exclusivePriceRemark\":\"专属价说明A\"}");

        String text = CopyTextPolicy.render(null, snapshot, state, "https://t.cn/abc");

        assertThat(text).contains("【商品】商品名A（店铺X）");
        assertThat(text).contains("【售价】99.00");
        assertThat(text).contains("【佣金率】20%");
        assertThat(text).contains("【卖点】卖点1、卖点2");
        assertThat(text).contains("【话术】话术A");
        assertThat(text).contains("【寄样门槛】销售额≥1000 / 等级≥LV1");
        assertThat(text).contains("【专属价说明】专属价说明A");
        assertThat(text).contains("【链接】https://t.cn/abc");
        assertThat(text).doesNotContain("未生成");
    }

    @Test
    @DisplayName("render: 模板为空字符串 → 走硬编码简介")
    void render_emptyTemplateFallsBackToHardcoded() {
        ConfigDomainFacade facade = mock(ConfigDomainFacade.class);
        when(facade.getPromotionTemplate()).thenReturn(template(""));

        ProductSnapshot snapshot = snapshot("商品名B", "店铺Y", null, null);

        String text = CopyTextPolicy.render(facade, snapshot, null, null);

        assertThat(text).contains("【商品】商品名B（店铺Y）");
        assertThat(text).contains("【售价】-");
        assertThat(text).contains("【佣金率】-");
        assertThat(text).contains("【推广链接】未生成");
    }

    @Test
    @DisplayName("render: 硬编码 + promotionLink 为空 → 显示【推广链接】未生成")
    void render_hardcodedEmptyLinkShowsPlaceholder() {
        ProductSnapshot snapshot = snapshot("P", null, null, null);

        String text = CopyTextPolicy.render(null, snapshot, null, "");

        assertThat(text).contains("【推广链接】未生成");
    }

    @Test
    @DisplayName("render: 硬编码 + auditPayload 损坏 JSON → 静默吞异常，使用 \"-\" 默认值")
    void render_hardcodedMalformedJsonFallsBackToDashes() {
        ProductSnapshot snapshot = snapshot("P", null, "0.01", "0%");
        ProductOperationState state = stateWithAuditJson("{this is not valid json");

        String text = CopyTextPolicy.render(null, snapshot, state, "https://t.cn/ok");

        assertThat(text).contains("【卖点】-");
        assertThat(text).contains("【话术】-");
        assertThat(text).contains("【寄样门槛】销售额≥- / 等级≥LV-");
        assertThat(text).contains("【专属价说明】-");
        assertThat(text).contains("【链接】https://t.cn/ok");
    }

    @Test
    @DisplayName("render: 硬编码 + sellingPoints 单元素 → 正常显示，不带顿号")
    void render_hardcodedSingleSellingPoint() {
        ProductSnapshot snapshot = snapshot("P", null, null, null);
        ProductOperationState state = stateWithAuditJson("{\"sellingPoints\":[\"唯一卖点\"]}");

        String text = CopyTextPolicy.render(null, snapshot, state, null);

        assertThat(text).contains("【卖点】唯一卖点");
        assertThat(text).doesNotContain("、");
    }

    @Test
    @DisplayName("render: 硬编码 + state 为 null → 全部 audit 字段显示 \"-\"")
    void render_hardcodedNullStateSafe() {
        ProductSnapshot snapshot = snapshot("P", "shop", null, null);

        String text = CopyTextPolicy.render(null, snapshot, null, null);

        assertThat(text).contains("【卖点】-");
        assertThat(text).contains("【话术】-");
        assertThat(text).contains("【寄样门槛】销售额≥- / 等级≥LV-");
    }

    @Test
    @DisplayName("render: template + state 为 null → 不抛 NPE，所有 audit 占位符显示 \"-\"")
    void render_templateNullStateSafe() {
        ConfigDomainFacade facade = mock(ConfigDomainFacade.class);
        when(facade.getPromotionTemplate()).thenReturn(template("custom={custom_text} fee={serviceFeeRate}"));

        ProductSnapshot snapshot = snapshot("P", null, null, null);

        String text = CopyTextPolicy.render(facade, snapshot, null, null);

        assertThat(text).isEqualTo("custom=- fee=-");
    }

    // ---------- helpers ----------

    private static PromotionTemplateDTO template(String copyBrief) {
        return new PromotionTemplateDTO(copyBrief, null, null);
    }

    private static ProductSnapshot snapshot(String title, String shopName, String priceText, String cosRatioText) {
        ProductSnapshot s = new ProductSnapshot();
        s.setTitle(title);
        s.setShopName(shopName);
        s.setPriceText(priceText);
        s.setActivityCosRatioText(cosRatioText);
        return s;
    }

    private static ProductOperationState stateWithAuditJson(String json) {
        ProductOperationState state = new ProductOperationState();
        state.setAuditPayload(json);
        return state;
    }
}
