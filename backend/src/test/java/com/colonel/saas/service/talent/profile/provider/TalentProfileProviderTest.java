package com.colonel.saas.service.talent.profile.provider;

import cn.hutool.http.Method;
import com.colonel.saas.service.talent.TalentInputParseResult;
import com.colonel.saas.service.talent.profile.TalentProfileFieldNames;
import com.colonel.saas.service.talent.profile.TalentProfileQuery;
import com.colonel.saas.service.talent.profile.TalentProfileResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TalentProfileProviderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void manualProviderShouldSupportOnlyManualPayloadAndMapAllKnownFields() {
        ManualTalentProvider provider = new ManualTalentProvider();
        TalentProfileQuery fullPayload = TalentProfileQuery.builder()
                .input("manual")
                .manualFill(true)
                .manualPayload(java.util.Map.ofEntries(
                        java.util.Map.entry(TalentProfileFieldNames.NICKNAME, " Nick "),
                        java.util.Map.entry(TalentProfileFieldNames.AVATAR_URL, " https://img.example/a.png "),
                        java.util.Map.entry(TalentProfileFieldNames.FANS_COUNT, "100"),
                        java.util.Map.entry(TalentProfileFieldNames.LIKE_COUNT, 200),
                        java.util.Map.entry(TalentProfileFieldNames.FOLLOWING_COUNT, "30"),
                        java.util.Map.entry(TalentProfileFieldNames.WORKS_COUNT, 40),
                        java.util.Map.entry(TalentProfileFieldNames.IP_LOCATION, " 上海 "),
                        java.util.Map.entry(TalentProfileFieldNames.TALENT_LEVEL, "LV2"),
                        java.util.Map.entry(TalentProfileFieldNames.SALES_30D, "5000"),
                        java.util.Map.entry(TalentProfileFieldNames.DOUYIN_ACCOUNT, "account"),
                        java.util.Map.entry(TalentProfileFieldNames.TALENT_UID, "uid"),
                        java.util.Map.entry(TalentProfileFieldNames.SEC_UID, "sec")
                ))
                .build();

        assertThat(provider.providerCode()).isEqualTo("manual");
        assertThat(provider.order()).isEqualTo(90);
        assertThat(provider.supports(null)).isFalse();
        assertThat(provider.supports(TalentProfileQuery.builder().manualFill(true).manualPayload(java.util.Map.of()).build())).isFalse();
        assertThat(provider.supports(fullPayload)).isTrue();

        TalentProfileResult result = provider.fetch(fullPayload);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getSyncStatus()).isEqualTo(TalentProfileResult.STATUS_SUCCESS);
        assertThat(result.getNickname()).isEqualTo("Nick");
        assertThat(result.getAvatarUrl()).isEqualTo("https://img.example/a.png");
        assertThat(result.getFansCount()).isEqualTo(100L);
        assertThat(result.getLikeCount()).isEqualTo(200L);
        assertThat(result.getFollowingCount()).isEqualTo(30L);
        assertThat(result.getWorksCount()).isEqualTo(40L);
        assertThat(result.getIpLocation()).isEqualTo("上海");
        assertThat(result.getTalentLevel()).isEqualTo("LV2");
        assertThat(result.getSales30d()).isEqualTo(5000L);
        assertThat(result.getUnsupportedFields()).isEmpty();
        assertThat(result.getRawPayload()).containsEntry("dataSource", "manual");
    }

    @Test
    void manualProviderShouldFailWhenPayloadHasNoUsableFields() {
        ManualTalentProvider provider = new ManualTalentProvider();
        TalentProfileQuery query = TalentProfileQuery.builder()
                .manualFill(true)
                .manualPayload(java.util.Map.of(
                        TalentProfileFieldNames.NICKNAME, " ",
                        TalentProfileFieldNames.FANS_COUNT, "bad-number"
                ))
                .build();

        TalentProfileResult result = provider.fetch(query);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo("MANUAL_EMPTY");
        assertThat(result.getUnsupportedFields()).contains(TalentProfileFieldNames.TALENT_LEVEL, TalentProfileFieldNames.SALES_30D);
    }

    @Test
    void configurableHttpProviderSupportsOnlyEnabledEndpointAndInput() {
        ConfigurableHttpTalentProvider disabled =
                new ConfigurableHttpTalentProvider(objectMapper, false, "https://api.example/profile", "GET", "", "");
        ConfigurableHttpTalentProvider missingEndpoint =
                new ConfigurableHttpTalentProvider(objectMapper, true, " ", "GET", "", "");
        ConfigurableHttpTalentProvider enabled =
                new ConfigurableHttpTalentProvider(objectMapper, true, "https://api.example/profile", "GET", "token", "");

        assertThat(disabled.providerCode()).isEqualTo("configurable_http");
        assertThat(disabled.order()).isEqualTo(10);
        assertThat(disabled.supports(query("uid-1"))).isFalse();
        assertThat(missingEndpoint.supports(query("uid-1"))).isFalse();
        assertThat(enabled.supports(query("uid-1"))).isTrue();
        assertThat(enabled.supports(query(" "))).isFalse();
    }

    @Test
    void configurableHttpProviderMapsSuccessfulProfileResponses() {
        ConfigurableHttpTalentProvider provider =
                new ConfigurableHttpTalentProvider(objectMapper, true, "https://api.example/profile", "POST", "", "Bearer explicit");
        String body = """
                {
                  "data": {
                    "douyin_account": "account-a",
                    "talent_uid": "uid-a",
                    "sec_uid": "sec-a",
                    "nickname": " Nick A ",
                    "avatar_url": "https://img.example/a.png",
                    "fans_count": "123",
                    "like_count": 456,
                    "following_count": 7,
                    "works_count": 8,
                    "ip_location": "Shanghai",
                    "talent_level": "A",
                    "sales_30d": "90"
                  }
                }
                """;

        TalentProfileResult result = ReflectionTestUtils.invokeMethod(provider, "mapResponse", body, "uid-a");

        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getProviderCode()).isEqualTo("configurable_http");
        assertThat(result.getSyncStatus()).isEqualTo(TalentProfileResult.STATUS_SUCCESS);
        assertThat(result.getDouyinAccount()).isEqualTo("account-a");
        assertThat(result.getTalentUid()).isEqualTo("uid-a");
        assertThat(result.getSecUid()).isEqualTo("sec-a");
        assertThat(result.getNickname()).isEqualTo("Nick A");
        assertThat(result.getFansCount()).isEqualTo(123L);
        assertThat(result.getLikeCount()).isEqualTo(456L);
        assertThat(result.getFollowingCount()).isEqualTo(7L);
        assertThat(result.getWorksCount()).isEqualTo(8L);
        assertThat(result.getIpLocation()).isEqualTo("Shanghai");
        assertThat(result.getTalentLevel()).isEqualTo("A");
        assertThat(result.getSales30d()).isEqualTo(90L);
        assertThat(result.getUnsupportedFields()).isEmpty();
        assertThat(result.getRawPayload()).containsKey("data");
    }

    @Test
    void configurableHttpProviderFailsEmptyProfilesAndParsesHttpMethods() {
        ConfigurableHttpTalentProvider provider =
                new ConfigurableHttpTalentProvider(objectMapper, true, "https://api.example/profile", "GET", "", "");

        TalentProfileResult empty = ReflectionTestUtils.invokeMethod(provider, "mapResponse", "{\"data\":{}}", "uid-a");

        assertThat(empty).isNotNull();
        assertThat(empty.isSuccess()).isFalse();
        assertThat(empty.getErrorCode()).isEqualTo("HTTP_EMPTY_PROFILE");
        assertThat(ReflectionTestUtils.<Method>invokeMethod(provider, "parseMethod", " ")).isEqualTo(Method.GET);
        assertThat(ReflectionTestUtils.<Method>invokeMethod(provider, "parseMethod", "post")).isEqualTo(Method.POST);
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(provider, "parseMethod", "missing"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void publicWebProviderSupportsAndResolvesTargetUrls() {
        PublicWebTalentProvider enabled = new PublicWebTalentProvider(objectMapper, true);
        PublicWebTalentProvider disabled = new PublicWebTalentProvider(objectMapper, false);
        TalentProfileQuery parsedUrl = TalentProfileQuery.builder()
                .input("ignored")
                .parsed(TalentInputParseResult.builder().profileUrl(" https://profile.example/u ").build())
                .build();
        TalentProfileQuery secUid = TalentProfileQuery.builder()
                .input("ignored")
                .parsed(TalentInputParseResult.builder().secUid("SEC123").build())
                .build();

        assertThat(enabled.providerCode()).isEqualTo("public_web");
        assertThat(enabled.order()).isEqualTo(20);
        assertThat(disabled.supports(query("uid-1"))).isFalse();
        assertThat(enabled.supports(query("uid-1"))).isTrue();
        assertThat(enabled.supports(null)).isFalse();
        assertThat(ReflectionTestUtils.<String>invokeMethod(enabled, "resolveTargetUrl", parsedUrl))
                .isEqualTo("https://profile.example/u");
        assertThat(ReflectionTestUtils.<String>invokeMethod(enabled, "resolveTargetUrl", query("https://profile.example/raw")))
                .isEqualTo("https://profile.example/raw");
        assertThat(ReflectionTestUtils.<String>invokeMethod(enabled, "resolveTargetUrl", secUid))
                .isEqualTo("https://www.douyin.com/user/SEC123");
        assertThat(ReflectionTestUtils.<String>invokeMethod(enabled, "resolveTargetUrl", query("uid-1")))
                .isEqualTo("https://www.douyin.com/user/uid-1");
    }

    @Test
    void publicWebProviderParsesHtmlProfileFieldsAndFailureCases() {
        PublicWebTalentProvider provider = new PublicWebTalentProvider(objectMapper, true);
        String html = """
                <html><script>{
                  "nickname": "Nick Web",
                  "avatarUrl": "https:\\/\\/img.example\\/avatar.png",
                  "follower_count": 12345,
                  "total_favorited": 45678,
                  "following_count": 12,
                  "aweme_count": 34,
                  "ip_location": "Beijing",
                  "sec_uid": "SEC_WEB",
                  "unique_id": "account_web"
                }</script></html>
                """;

        TalentProfileResult result = ReflectionTestUtils.invokeMethod(
                provider,
                "parseHtml",
                html,
                "https://www.douyin.com/user/SEC_WEB",
                queryWithParsedUid("raw", "uid-web")
        );

        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getProviderCode()).isEqualTo("public_web");
        assertThat(result.getSyncStatus()).isEqualTo(TalentProfileResult.STATUS_PARTIAL_SUCCESS);
        assertThat(result.getTalentUid()).isEqualTo("uid-web");
        assertThat(result.getNickname()).isEqualTo("Nick Web");
        assertThat(result.getAvatarUrl()).isEqualTo("https://img.example/avatar.png");
        assertThat(result.getFansCount()).isEqualTo(12345L);
        assertThat(result.getLikeCount()).isEqualTo(45678L);
        assertThat(result.getFollowingCount()).isEqualTo(12L);
        assertThat(result.getWorksCount()).isEqualTo(34L);
        assertThat(result.getSecUid()).isEqualTo("SEC_WEB");
        assertThat(result.getDouyinAccount()).isEqualTo("account_web");
        assertThat(result.getRawPayload()).containsEntry("sourceUrl", "https://www.douyin.com/user/SEC_WEB");

        JsonNode embedded = ReflectionTestUtils.invokeMethod(provider, "extractEmbeddedJson", html);
        assertThat(embedded).isNotNull();
        assertThat(embedded.path("nickname").asText()).isEqualTo("Nick Web");

        TalentProfileResult empty = ReflectionTestUtils.invokeMethod(
                provider,
                "parseHtml",
                "<html>nothing useful</html>",
                "https://www.douyin.com/user/none",
                query("none")
        );
        assertThat(empty).isNotNull();
        assertThat(empty.isSuccess()).isFalse();
        assertThat(empty.getErrorCode()).isEqualTo("PUBLIC_WEB_PARSE_EMPTY");
    }

    @Test
    void publicWebProviderShouldParseRenderDataAndNodeFallbackText() {
        PublicWebTalentProvider provider = new PublicWebTalentProvider(objectMapper, true);
        String html = """
                <html><script id="RENDER_DATA" type="application/json">
                {"user":{"nickname":"Node Nick","avatarUrl":"https:\\/\\/img.example\\/node.png"}}
                </script></html>
                """;

        TalentProfileResult result = ReflectionTestUtils.invokeMethod(
                provider,
                "parseHtml",
                html,
                "https://www.douyin.com/user/node",
                query("node")
        );

        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getNickname()).isEqualTo("Node Nick");
        assertThat(result.getAvatarUrl()).isEqualTo("https://img.example/node.png");
    }

    @Test
    void publicWebProviderDecodesEscapedAvatarUrls() {
        PublicWebTalentProvider provider = new PublicWebTalentProvider(objectMapper, true);

        assertThat(ReflectionTestUtils.<String>invokeMethod(provider, "decodeUrl", "https:\\u002F\\u002Fimg.example\\/a.png"))
                .isEqualTo("https://img.example/a.png");
        assertThat(ReflectionTestUtils.<String>invokeMethod(provider, "decodeUrl", " "))
                .isEqualTo(" ");
    }

    private TalentProfileQuery query(String input) {
        return TalentProfileQuery.builder().input(input).build();
    }

    private TalentProfileQuery queryWithParsedUid(String input, String uid) {
        return TalentProfileQuery.builder()
                .input(input)
                .parsed(TalentInputParseResult.builder().uid(uid).build())
                .build();
    }

    @Test
    void talentProfileQueryShouldExposeBuilderAndSetters() {
        TalentProfileQuery query = TalentProfileQuery.builder()
                .input("raw")
                .forceRefresh(true)
                .talentId(java.util.UUID.randomUUID())
                .manualFill(true)
                .parsed(TalentInputParseResult.builder().uid("uid").build())
                .manualPayload(java.util.Map.of("nickname", "Nick"))
                .build();
        query.setInput("updated");

        assertThat(query.getInput()).isEqualTo("updated");
        assertThat(query.isForceRefresh()).isTrue();
        assertThat(query.getTalentId()).isNotNull();
        assertThat(query.isManualFill()).isTrue();
        assertThat(query.getParsed().getUid()).isEqualTo("uid");
        assertThat(query.getManualPayload()).containsEntry("nickname", "Nick");
        assertThat(query.toString()).contains("updated");
    }
}
