package com.colonel.saas.douyin.api;

import com.colonel.saas.douyin.DouyinApiClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TalentApiTest {

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<Map<String, Object>> mapCaptor() {
        return (ArgumentCaptor<Map<String, Object>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(Map.class);
    }

    @Mock
    private DouyinApiClient douyinApiClient;

    @InjectMocks
    private TalentApi talentApi;

    @Test
    void convertLink_shouldPassParamsToDouyinApi() {
        when(douyinApiClient.post(eq("buyin.instPickSourceConvert"), org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn(Map.of("data", Map.of("converted_link", "https://example.com")));

        Map<String, Object> result = talentApi.convertLink(
                "https://example.com/abc",
                "EXTRA123"
        );

        assertThat(result).containsKey("data");
        ArgumentCaptor<Map<String, Object>> captor = mapCaptor();
        verify(douyinApiClient).post(eq("buyin.instPickSourceConvert"), captor.capture());
        Map<String, Object> params = captor.getValue();
        assertThat(params.get("product_url")).isEqualTo("https://example.com/abc");
        assertThat(params.get("pick_extra")).isEqualTo("EXTRA123");
    }

    @Test
    void convertLink_withNullPickExtra_shouldPassNull() {
        when(douyinApiClient.post(eq("buyin.instPickSourceConvert"), org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn(Map.of("data", Map.of()));

        talentApi.convertLink("https://example.com/abc", null);

        ArgumentCaptor<Map<String, Object>> captor = mapCaptor();
        verify(douyinApiClient).post(eq("buyin.instPickSourceConvert"), captor.capture());
        assertThat(captor.getValue().get("pick_extra")).isNull();
    }

    @Test
    void convertLink_withTooLongPickExtra_shouldTruncateLast20Chars() {
        when(douyinApiClient.post(eq("buyin.instPickSourceConvert"), org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn(Map.of("data", Map.of()));

        talentApi.convertLink("https://example.com", "123456789012345678901");

        ArgumentCaptor<Map<String, Object>> captor = mapCaptor();
        verify(douyinApiClient).post(eq("buyin.instPickSourceConvert"), captor.capture());
        Object pickExtra = captor.getValue().get("pick_extra");
        assertThat(pickExtra).isInstanceOf(String.class);
        String val = (String) pickExtra;
        assertThat(val).hasSize(20);
        assertThat(val).isEqualTo("123456789012345678901".substring(1));
    }

    @Test
    void convertLink_with20CharPickExtra_shouldKeepAsIs() {
        when(douyinApiClient.post(eq("buyin.instPickSourceConvert"), org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn(Map.of("data", Map.of()));

        talentApi.convertLink("https://example.com", "12345678901234567890");

        ArgumentCaptor<Map<String, Object>> captor = mapCaptor();
        verify(douyinApiClient).post(eq("buyin.instPickSourceConvert"), captor.capture());
        Object pickExtra = captor.getValue().get("pick_extra");
        assertThat(pickExtra).isInstanceOf(String.class);
        assertThat((String) pickExtra).hasSize(20);
    }
}
