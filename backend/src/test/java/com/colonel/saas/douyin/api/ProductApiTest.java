package com.colonel.saas.douyin.api;

import com.colonel.saas.douyin.DouyinApiClient;
import com.colonel.saas.gateway.douyin.contract.DouyinContractFixtureProvider;
import com.colonel.saas.gateway.douyin.contract.DouyinUpstreamModeSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductApiTest {

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<Map<String, Object>> mapCaptor() {
        return (ArgumentCaptor<Map<String, Object>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(Map.class);
    }

    @Mock
    private DouyinApiClient douyinApiClient;

    @Mock
    private DouyinUpstreamModeSupport upstreamModeSupport;

    @Mock
    private DouyinContractFixtureProvider contractFixtureProvider;

    @InjectMocks
    private ProductApi productApi;

    @BeforeEach
    void setUp() {
        lenient().when(upstreamModeSupport.isContract()).thenReturn(false);
    }

    @Test
    void listActivities_shouldCallAllianceInstituteColonelActivityList() {
        when(douyinApiClient.post(eq("alliance.instituteColonelActivityList"), anyMap()))
                .thenReturn(new HashMap<>());

        productApi.listActivities("app-1", null, null, null, null, null, null);

        ArgumentCaptor<Map<String, Object>> captor = mapCaptor();
        verify(douyinApiClient).post(eq("alliance.instituteColonelActivityList"), captor.capture());
        Map<String, Object> params = captor.getValue();
        assertThat(params.get("appId")).isEqualTo("app-1");
        assertThat(params.get("status")).isEqualTo(0);
        assertThat(params.get("search_type")).isEqualTo(0L);
        assertThat(params.get("sort_type")).isEqualTo(1L);
        assertThat(params.get("page")).isEqualTo(1L);
        assertThat(params.get("page_size")).isEqualTo(20L);
    }

    @Test
    void listActivities_shouldRejectPageSizeOver20() {
        assertThatThrownBy(() -> productApi.listActivities("app-1", 0, 0L, 1L, 1L, 21L, null))
                .hasMessageContaining("page_size");
    }

    @Test
    void listActivities_shouldRejectUnsupportedSearchType() {
        assertThatThrownBy(() -> productApi.listActivities("app-1", 0, 3L, 1L, 1L, 20L, null))
                .hasMessageContaining("search_type");
    }

    @Test
    void listProductsByActivity_shouldCallAllianceColonelActivityProduct() {
        when(douyinApiClient.post(eq("alliance.colonelActivityProduct"), anyMap()))
                .thenReturn(new HashMap<>());

        productApi.listProductsByActivity("app-1", "12345", 500, "bad");

        ArgumentCaptor<Map<String, Object>> captor = mapCaptor();
        verify(douyinApiClient).post(eq("alliance.colonelActivityProduct"), captor.capture());
        Map<String, Object> params = captor.getValue();
        assertThat(params.get("appId")).isEqualTo("app-1");
        assertThat(params.get("activity_id")).isEqualTo(12345L);
        assertThat(params.get("count")).isEqualTo(20);
        assertThat(params.get("search_type")).isEqualTo(4L);
        assertThat(params.get("sort_type")).isEqualTo(1L);
        assertThat(params.get("retrieve_mode")).isEqualTo(1L);
        assertThat(params.get("cursor")).isEqualTo("bad");
    }

    @Test
    void listProductsByActivity_shouldUseCursorModeWhenCursorMissing() {
        when(douyinApiClient.post(eq("alliance.colonelActivityProduct"), anyMap()))
                .thenReturn(new HashMap<>());

        productApi.listProductsByActivity("app-1", "12345", null, null);

        ArgumentCaptor<Map<String, Object>> captor = mapCaptor();
        verify(douyinApiClient).post(eq("alliance.colonelActivityProduct"), captor.capture());
        Map<String, Object> params = captor.getValue();
        assertThat(params.get("count")).isEqualTo(20);
        assertThat(params.get("retrieve_mode")).isEqualTo(1L);
        assertThat(params).doesNotContainKey("page");
    }

    @Test
    void listProductsByActivity_shouldRejectNonNumericActivityId() {
        assertThatThrownBy(() -> productApi.listProductsByActivity("app-1", "abc", 20, null))
                .hasMessageContaining("activityId");
    }

    @Test
    void getProductSkusV2_shouldCallBuyinProductSkusV2() {
        when(douyinApiClient.post(eq("buyin.productSkus.v2"), anyMap()))
                .thenReturn(Map.of("data", Map.of("skus", Map.of())));

        productApi.getProductSkusV2("3810562766247428542");

        ArgumentCaptor<Map<String, Object>> captor = mapCaptor();
        verify(douyinApiClient).post(eq("buyin.productSkus.v2"), captor.capture());
        assertThat(captor.getValue()).containsEntry("product_id", 3810562766247428542L);
    }

    @Test
    void getProductSkusV2_shouldRejectNonNumericProductId() {
        assertThatThrownBy(() -> productApi.getProductSkusV2("not-a-product"))
                .hasMessageContaining("productId");
    }

    @Test
    @SuppressWarnings("unchecked")
    void listProductsByActivity_shouldAdaptDualCommissionFields() {
        Map<String, Object> item = new HashMap<>();
        item.put("cos_type", "1");
        item.put("ad_service_ratio", "10");
        item.put("activity_ad_cos_ratio", "8");

        Map<String, Object> data = new HashMap<>();
        data.put("data", List.of(item));

        Map<String, Object> response = new HashMap<>();
        response.put("data", data);

        when(douyinApiClient.post(eq("alliance.colonelActivityProduct"), anyMap()))
                .thenReturn(response);

        Map<String, Object> result = productApi.listProductsByActivity("app-1", "12345", 20, null);
        Map<String, Object> resultData = (Map<String, Object>) result.get("data");
        Map<String, Object> first = (Map<String, Object>) ((List<?>) resultData.get("data")).get(0);

        assertThat(first.get("cos_type")).isEqualTo(1);
        assertThat(first.get("dual_commission_enabled")).isEqualTo(true);
        assertThat(first.get("ad_service_ratio")).isEqualTo("10");
        assertThat(first.get("activity_ad_cos_ratio")).isEqualTo("8");
    }
}
