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
        // Arrange
        Map<String, Object> data = new HashMap<>();
        data.put("short_link", "https://s.example/abc");
        data.put("promote_link", "https://p.example/xyz");
        Map<String, Object> response = new HashMap<>();
        response.put("data", data);
        when(douyinApiClient.post(eq("buyin.promotion.link.generate"), org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn(response);

        // Act
        PromotionApi.PromotionLinkResult result = promotionApi.generateLink(
                "ext-unique-001",
                3,
                List.of("1001", "1002"),
                true
        );

        // Assert
        assertThat(result.shortId()).hasSize(8);
        assertThat(result.shortLink()).isEqualTo("https://s.example/abc");
        assertThat(result.promoteLink()).isEqualTo("https://p.example/xyz");
        assertThat(result.uuidSeed()).isNotBlank();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> paramsCaptor = (ArgumentCaptor<Map<String, Object>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(Map.class);
        verify(douyinApiClient).post(eq("buyin.promotion.link.generate"), paramsCaptor.capture());
        Map<String, Object> params = paramsCaptor.getValue();
        assertThat(params.get("external_unique_id")).isEqualTo("ext-unique-001");
        assertThat(params.get("promotion_scene")).isEqualTo(3);
        assertThat(params.get("product_ids")).isEqualTo(List.of("1001", "1002"));
        assertThat(params.get("need_short_link")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> extra = (Map<String, Object>) params.get("extra");
        assertThat(extra.get("uuid_seed")).isEqualTo(result.uuidSeed());
        assertThat(extra.get("pick_source")).isEqualTo(result.shortId());
    }

    @Test
    void promotionLinkResultFrom_shouldHandleNullData() {
        // Arrange
        Map<String, Object> response = new HashMap<>();

        // Act
        PromotionApi.PromotionLinkResult result =
                PromotionApi.PromotionLinkResult.from(response, "ABC12345", "uuid-seed");

        // Assert
        assertThat(result.shortId()).isEqualTo("ABC12345");
        assertThat(result.shortLink()).isNull();
        assertThat(result.promoteLink()).isNull();
        assertThat(result.uuidSeed()).isEqualTo("uuid-seed");
    }

    @Test
    void generateLink_withContext_shouldSavePickSourceMapping() {
        Map<String, Object> data = new HashMap<>();
        data.put("promote_link", "https://promote.example.com?pick_source=ABC");
        data.put("short_link", "https://short.url");
        Map<String, Object> response = new HashMap<>();
        response.put("data", data);
        when(douyinApiClient.post(eq("buyin.promotion.link.generate"), org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn(response);
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();

        PromotionApi.PromotionLinkResult result = promotionApi.generateLink(
                "ext_ctx",
                1,
                List.of("pid_1"),
                false,
                new PromotionApi.PromotionContext(userId, deptId, "product_x", "act_y", "https://src.url")
        );

        assertThat(result).isNotNull();
        verify(pickSourceMappingService).saveOrUpdate(
                eq(userId),
                eq(deptId),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                eq("product_x"),
                eq("act_y"),
                eq("https://src.url"),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void generateLink_withNullContext_shouldNotFail() {
        when(douyinApiClient.post(eq("buyin.promotion.link.generate"), org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn(new HashMap<>());

        PromotionApi.PromotionLinkResult result = promotionApi.generateLink("ext", 1, List.of("p"), true, null);

        assertThat(result).isNotNull();
        assertThat(result.promoteLink()).isNull();
    }

    @Test
    void generateLink_withOverloadedMethod_shouldCallSameImplementation() {
        when(douyinApiClient.post(eq("buyin.promotion.link.generate"), org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn(Map.of("data", Map.of("promote_link", "https://x.com")));

        PromotionApi.PromotionLinkResult result = promotionApi.generateLink(
                "ext", 2, List.of("pid"), false
        );

        assertThat(result).isNotNull();
        verify(douyinApiClient).post(eq("buyin.promotion.link.generate"), org.mockito.ArgumentMatchers.anyMap());
    }

    @Test
    void promotionLinkResultFrom_withNullResponse_returnsFallback() {
        PromotionApi.PromotionLinkResult result =
                PromotionApi.PromotionLinkResult.from(null, "fallbackId", "seed-123");

        assertThat(result.shortId()).isEqualTo("fallbackId");
        assertThat(result.promoteLink()).isNull();
    }

    @Test
    void promotionLinkResult_extractShortId_pickSourceTooLong_returnsFallback() {
        PromotionApi.PromotionLinkResult result = PromotionApi.PromotionLinkResult.from(
                Map.of("data", Map.of("pick_source", "toolongpickid123", "promote_link", "https://x.com?pick_source=SHORT")),
                "fallback",
                "seed"
        );
        assertThat(result.shortId()).isEqualTo("SHORT");
    }

    @Test
    void promotionLinkResult_extractShortId_pickSourceExactly10Chars_returnsIt() {
        PromotionApi.PromotionLinkResult result = PromotionApi.PromotionLinkResult.from(
                Map.of("data", Map.of("pick_source", "1234567890", "promote_link", "https://x.com")),
                "fallback",
                "seed"
        );
        assertThat(result.shortId()).isEqualTo("1234567890");
    }

    @Test
    void promotionLinkResult_extractShortId_pickSource11Chars_fallsToPromoteLink() {
        PromotionApi.PromotionLinkResult result = PromotionApi.PromotionLinkResult.from(
                Map.of("data", Map.of("pick_source", "12345678901", "promote_link", "https://x.com?pick_source=ABCDE")),
                "fallback",
                "seed"
        );
        assertThat(result.shortId()).isEqualTo("ABCDE");
    }

    @Test
    void promotionLinkResult_extractShortId_malformedUrl_returnsFallback() {
        PromotionApi.PromotionLinkResult result = PromotionApi.PromotionLinkResult.from(
                Map.of("data", Map.of("promote_link", "ht!tp://bad url")),
                "myFallback",
                "seed"
        );
        assertThat(result.shortId()).isEqualTo("myFallback");
    }

    @Test
    void promotionLinkResult_extractShortId_noMatchingParam_returnsFallback() {
        PromotionApi.PromotionLinkResult result = PromotionApi.PromotionLinkResult.from(
                Map.of("data", Map.of("promote_link", "https://x.com?other=value")),
                "fallback",
                "seed"
        );
        assertThat(result.shortId()).isEqualTo("fallback");
    }

    @Test
    void promotionLinkResult_extractShortId_decodesUrlEncoded() {
        PromotionApi.PromotionLinkResult result = PromotionApi.PromotionLinkResult.from(
                Map.of("data", Map.of("promote_link", "https://x.com?pick_source=ABC%20D")),
                "fallback",
                "seed"
        );
        // Decoded "ABC%20D" = "ABC D" (7 chars, valid)
        assertThat(result.shortId()).isEqualTo("ABC D");
    }

    @Test
    void promotionLinkResult_extractShortId_decodedTooLong_returnsFallback() {
        PromotionApi.PromotionLinkResult result = PromotionApi.PromotionLinkResult.from(
                Map.of("data", Map.of("promote_link", "https://x.com?pick_source=ABCDEFGHIJK")),
                "fallback",
                "seed"
        );
        assertThat(result.shortId()).isEqualTo("fallback");
    }

    @Test
    void promotionLinkResult_extractShortId_pickExtraParam_alsoAccepted() {
        PromotionApi.PromotionLinkResult result = PromotionApi.PromotionLinkResult.from(
                Map.of("data", Map.of("promote_link", "https://x.com?pick_extra=ABCDEF")),
                "fallback",
                "seed"
        );
        assertThat(result.shortId()).isEqualTo("ABCDEF");
    }

    @Test
    void promotionLinkResult_extractShortId_emptyPromoteLink_returnsFallback() {
        PromotionApi.PromotionLinkResult result = PromotionApi.PromotionLinkResult.from(
                Map.of("data", Map.of("promote_link", "")),
                "fallback",
                "seed"
        );
        assertThat(result.shortId()).isEqualTo("fallback");
    }

    @Test
    void promotionLinkResult_asStringOrNull_nonNullValue() {
        PromotionApi.PromotionLinkResult result =
                PromotionApi.PromotionLinkResult.from(
                        Map.of("data", Map.of("promote_link", 12345, "short_link", Boolean.FALSE)),
                        "fallback", "seed"
                );
        // Verify from() handles non-String data types gracefully (Integer -> "12345", Boolean -> "false")
        assertThat(result.promoteLink()).isEqualTo("12345");
    }
}
