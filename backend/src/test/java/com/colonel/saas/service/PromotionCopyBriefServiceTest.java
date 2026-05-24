package com.colonel.saas.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromotionCopyBriefServiceTest {

    @Mock
    private BusinessRuleConfigService businessRuleConfigService;

    private PromotionCopyBriefService service;

    @BeforeEach
    void setUp() {
        service = new PromotionCopyBriefService(businessRuleConfigService);
        when(businessRuleConfigService.getPromotionCopyBriefTemplate())
                .thenReturn("商品:{productName}|佣金:{commissionRate}|链接:{shortLink}|来源:{pickSource}");
    }

    @Test
    void render_shouldReplaceAllKnownPlaceholders() {
        String result = service.render("爆款牙刷", "30%", "https://short", "channel_001");

        assertThat(result).isEqualTo("商品:爆款牙刷|佣金:30%|链接:https://short|来源:channel_001");
    }

    @Test
    void render_shouldReplaceBlankAndNullValuesWithEmptyText() {
        String result = service.render(" ", null, "", "\t");

        assertThat(result).isEqualTo("商品:|佣金:|链接:|来源:");
    }

    @Test
    void renderMap_shouldReturnTemplateWhenMapMissingOrEmpty() {
        assertThat(service.render((Map<String, String>) null))
                .isEqualTo("商品:{productName}|佣金:{commissionRate}|链接:{shortLink}|来源:{pickSource}");
        assertThat(service.render(Map.of()))
                .isEqualTo("商品:{productName}|佣金:{commissionRate}|链接:{shortLink}|来源:{pickSource}");
    }

    @Test
    void renderMap_shouldReadExpectedKeysAndIgnoreUnknownKeys() {
        String result = service.render(Map.of(
                "productName", "面膜",
                "commissionRate", "45%",
                "shortLink", "https://m.cn",
                "pickSource", "biz_7",
                "unknown", "ignored"));

        assertThat(result).isEqualTo("商品:面膜|佣金:45%|链接:https://m.cn|来源:biz_7");
    }

    @Test
    void renderMap_shouldTreatMissingKeysAsEmptyText() {
        String result = service.render(Map.of("productName", "零食"));

        assertThat(result).isEqualTo("商品:零食|佣金:|链接:|来源:");
    }
}
