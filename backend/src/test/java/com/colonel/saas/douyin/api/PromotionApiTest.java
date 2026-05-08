package com.colonel.saas.douyin.api;

import com.colonel.saas.douyin.DouyinApiClient;
import com.colonel.saas.service.PickSourceMappingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromotionApiTest {

    @Mock
    private DouyinApiClient douyinApiClient;
    @Mock
    private PickSourceMappingService pickSourceMappingService;

    @InjectMocks
    private PromotionApi promotionApi;

    @Test
    void generateLink_shouldCallDouyinApiAndParseResult() {
        Map<String, Object> data = new HashMap<>();
        data.put("short_link", "https://s.example/abc");
        data.put("promote_link", "https://p.example/xyz");
        Map<String, Object> response = new HashMap<>();
        response.put("data", data);
        when(douyinApiClient.post(eq("buyin.promotion.link.generate"), any())).thenReturn(response);

        PromotionApi.PromotionLinkResult result = promotionApi.generateLink(
                "ext-unique-001",
                3,
                List.of("1001", "1002"),
                true
        );

        assertThat(result.shortId()).hasSize(8);
        assertThat(result.shortLink()).isEqualTo("https://s.example/abc");
        assertThat(result.promoteLink()).isEqualTo("https://p.example/xyz");
        assertThat(result.uuidSeed()).isNotBlank();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> paramsCaptor =
                (ArgumentCaptor<Map<String, Object>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(Map.class);
        verify(douyinApiClient).post(eq("buyin.promotion.link.generate"), paramsCaptor.capture());
        Map<String, Object> params = paramsCaptor.getValue();
        assertThat(params.get("external_unique_id")).isEqualTo("ext-unique-001");
        assertThat(params.get("promotion_scene")).isEqualTo(3);
        assertThat(params.get("product_ids")).isEqualTo(List.of("1001", "1002"));
        assertThat(params.get("need_short_link")).isEqualTo(true);
    }

    @Test
    void promotionLinkResultFrom_shouldHandleNullData() {
        PromotionApi.PromotionLinkResult result =
                PromotionApi.PromotionLinkResult.from(new HashMap<>(), "ABC12345", "uuid-seed");

        assertThat(result.shortId()).isEqualTo("ABC12345");
        assertThat(result.shortLink()).isNull();
        assertThat(result.promoteLink()).isNull();
        assertThat(result.uuidSeed()).isEqualTo("uuid-seed");
    }

    @Test
    void generateLink_withContext_shouldSavePickSourceMapping() {
        Map<String, Object> response = Map.of(
                "data",
                Map.of(
                        "converted_link", "https://promote.example.com?pick_source=ABC12345&pick_extra=channel_demo_01",
                        "short_link", "https://short.url"
                )
        );
        when(douyinApiClient.post(eq("buyin.instPickSourceConvert"), any())).thenReturn(response);
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        String desiredPickExtra = "channel_demo_01";

        PromotionApi.PromotionLinkResult result = promotionApi.generateLink(
                "ext_ctx",
                1,
                List.of("pid_1"),
                false,
                new PromotionApi.PromotionContext(
                        userId,
                        deptId,
                        "product_x",
                        "act_y",
                        "https://src.url",
                        "PRODUCT_LIBRARY",
                        desiredPickExtra
                )
        );

        assertThat(result.promoteLink()).contains("pick_source");
        assertThat(result.pickExtra()).isEqualTo("channel_demo_01");
        verify(pickSourceMappingService).saveOrUpdate(
                eq(userId),
                eq(null),
                eq(deptId),
                eq(null),
                eq(null),
                eq(result.shortId()),
                any(UUID.class),
                eq(result.pickSource()),
                eq("product_x"),
                eq("act_y"),
                eq("https://src.url"),
                eq(result.promoteLink()),
                eq(null),
                eq("PRODUCT_LIBRARY"),
                eq("channel_demo_01")
        );
    }

    @Test
    void promotionLinkResultFrom_shouldTreatProductUrlAsPromoteLink() {
        PromotionApi.PromotionLinkResult result = PromotionApi.PromotionLinkResult.from(
                Map.of("data", Map.of(
                        "product_url", "https://haohuo.jinritemai.com/ecommerce/trade/detail/index.html?id=1&pick_source=MuRpGc&pick_extra=channel_demo"
                )),
                "fallback",
                "seed"
        );

        assertThat(result.promoteLink()).contains("pick_source=MuRpGc");
        assertThat(result.pickSource()).isEqualTo("MuRpGc");
        assertThat(result.shortId()).isEqualTo("MuRpGc");
        assertThat(result.pickExtra()).isEqualTo("channel_demo");
    }

    @Test
    void generateLink_withNullContext_shouldNotFail() {
        when(douyinApiClient.post(eq("buyin.promotion.link.generate"), any())).thenReturn(new HashMap<>());

        PromotionApi.PromotionLinkResult result = promotionApi.generateLink("ext", 1, List.of("p"), true, null);

        assertThat(result).isNotNull();
        assertThat(result.promoteLink()).isNull();
    }

    @Test
    void promotionLinkResultFrom_shouldExtractShortIdFromPromoteLink() {
        PromotionApi.PromotionLinkResult result = PromotionApi.PromotionLinkResult.from(
                Map.of("data", Map.of("promote_link", "https://x.com?pick_source=ABCDE")),
                "fallback",
                "seed"
        );

        assertThat(result.shortId()).isEqualTo("ABCDE");
        assertThat(result.pickSource()).isEqualTo("ABCDE");
    }

    @Test
    void promotionLinkResultFrom_shouldPreferDesiredPickExtraWhenResponseLacksIt() {
        PromotionApi.PromotionLinkResult result = PromotionApi.PromotionLinkResult.from(
                Map.of("data", Map.of("promote_link", "https://x.com?pick_source=ABCDE")),
                "fallback",
                "seed",
                "channel_user-1"
        );

        assertThat(result.shortId()).isEqualTo("ABCDE");
        assertThat(result.pickSource()).isEqualTo("ABCDE");
        assertThat(result.pickExtra()).isEqualTo("channel_user-1");
    }

    @Test
    void promotionLinkResultFrom_shouldFallbackWhenPromoteLinkIsMalformed() {
        PromotionApi.PromotionLinkResult result = PromotionApi.PromotionLinkResult.from(
                Map.of("data", Map.of("promote_link", "ht!tp://bad url")),
                "fallback",
                "seed"
        );

        assertThat(result.shortId()).isEqualTo("fallback");
    }
}
