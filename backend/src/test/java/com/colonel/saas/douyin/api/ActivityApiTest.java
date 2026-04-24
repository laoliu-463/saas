package com.colonel.saas.douyin.api;

import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.douyin.DouyinApiClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivityApiTest {

    @Mock
    private DouyinApiClient douyinApiClient;

    @InjectMocks
    private ActivityApi activityApi;

    @Test
    void detail_shouldCallBuyinColonelActivityDetailEndpoint() {
        Map<String, Object> response = new HashMap<>();
        response.put("data", Map.of("activity_id", 54321, "activity_name", "test activity"));
        when(douyinApiClient.post(eq("buyin.colonelActivityDetail"), anyMap()))
                .thenReturn(response);

        Map<String, Object> result = activityApi.detail("app-1", "54321");

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(douyinApiClient).post(eq("buyin.colonelActivityDetail"), captor.capture());
        Map<String, Object> params = captor.getValue();
        assertThat(params.get("appId")).isEqualTo("app-1");
        assertThat(params.get("activity_id")).isEqualTo(54321L);
        assertThat(result.get("data")).isNotNull();
    }

    @Test
    void detail_shouldThrowWhenActivityIdIsBlank() {
        assertThatThrownBy(() -> activityApi.detail("app-1", ""))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("activity_id");
    }

    @Test
    void detail_shouldWorkWithNullAppId() {
        when(douyinApiClient.post(eq("buyin.colonelActivityDetail"), anyMap()))
                .thenReturn(Map.of("data", Map.of()));

        activityApi.detail(null, "12345");

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(douyinApiClient).post(eq("buyin.colonelActivityDetail"), captor.capture());
        Map<String, Object> params = captor.getValue();
        assertThat(params).doesNotContainKey("appId");
        assertThat(params.get("activity_id")).isEqualTo(12345L);
    }

    @Test
    void list_shouldCallAllianceInstituteColonelActivityListEndpoint() {
        when(douyinApiClient.post(eq("alliance.instituteColonelActivityList"), anyMap()))
                .thenReturn(Map.of("data", java.util.List.of()));

        activityApi.list("app-1");

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(douyinApiClient).post(eq("alliance.instituteColonelActivityList"), captor.capture());
        Map<String, Object> params = captor.getValue();
        assertThat(params.get("appId")).isEqualTo("app-1");
        assertThat(params.get("status")).isEqualTo(0);
        assertThat(params.get("search_type")).isEqualTo(0L);
        assertThat(params.get("page")).isEqualTo(1L);
        assertThat(params.get("page_size")).isEqualTo(20L);
        assertThat(params.get("sort_type")).isEqualTo(1L);
    }

    @Test
    void listActivities_shouldMapAllQueryParams() {
        when(douyinApiClient.post(eq("alliance.instituteColonelActivityList"), anyMap()))
                .thenReturn(Map.of());

        activityApi.listActivities("app-2", 3, 1L, 0L, 2L, 20L, "keyword");

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(douyinApiClient).post(eq("alliance.instituteColonelActivityList"), captor.capture());
        Map<String, Object> params = captor.getValue();
        assertThat(params.get("appId")).isEqualTo("app-2");
        assertThat(params.get("status")).isEqualTo(3);
        assertThat(params.get("search_type")).isEqualTo(1L);
        assertThat(params.get("sort_type")).isEqualTo(0L);
        assertThat(params.get("page")).isEqualTo(2L);
        assertThat(params.get("page_size")).isEqualTo(20L);
        assertThat(params.get("activity_info")).isEqualTo("keyword");
    }

    @Test
    void listActivities_shouldRejectPageSizeOver20() {
        assertThatThrownBy(() -> activityApi.listActivities("app-2", 0, 0L, 1L, 1L, 21L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("page_size");
    }

    @Test
    void listActivities_shouldRejectUnsupportedStatus() {
        assertThatThrownBy(() -> activityApi.listActivities("app-2", 6, 0L, 1L, 1L, 20L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("status");
    }

    @Test
    void createOrUpdate_shouldCallAllianceColonelActivityCreateOrUpdateEndpoint() {
        when(douyinApiClient.post(eq("alliance.colonelActivityCreateOrUpdate"), anyMap()))
                .thenReturn(Map.of("data", Map.of("activity_id", 1)));

        ActivityApi.ActivityCreateOrUpdateCommand command = new ActivityApi.ActivityCreateOrUpdateCommand(
                "app-key-1",
                1001L,
                true,
                false,
                "0,1",
                "test activity",
                "test description",
                "2026-01-01",
                "2026-12-31",
                "10",
                "10",
                "wx001",
                "13800000000",
                "100000",
                1,
                null,
                true,
                "101,102",
                4,
                90,
                0,
                1,
                "12",
                "8",
                1
        );

        activityApi.createOrUpdate(command);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(douyinApiClient).post(eq("alliance.colonelActivityCreateOrUpdate"), captor.capture());
        Map<String, Object> params = captor.getValue();
        assertThat(params.get("appId")).isEqualTo("app-key-1");
        assertThat(params.get("activity_id")).isEqualTo(1001L);
        assertThat(params.get("application_limited")).isEqualTo(true);
        assertThat(params.get("activity_name")).isEqualTo("test activity");
        assertThat(params.get("activity_desc")).isEqualTo("test description");
        assertThat(params.get("apply_start_time")).isEqualTo("2026-01-01");
        assertThat(params.get("apply_end_time")).isEqualTo("2026-12-31");
        assertThat(params.get("commission_rate")).isEqualTo("10");
        assertThat(params.get("service_rate")).isEqualTo("10");
        assertThat(params.get("estimated_single_sale")).isEqualTo("100000");
        assertThat(params.get("activity_type")).isEqualTo(1);
        assertThat(params.get("online")).isEqualTo(true);
        assertThat(params.get("ad_commission_rate")).isEqualTo("12");
        assertThat(params.get("ad_service_rate")).isEqualTo("8");
        assertThat(params.get("cos_limit_type")).isEqualTo(1);
    }

    @Test
    void createOrUpdate_shouldThrowWhenRequiredFieldsMissing() {
        ActivityApi.ActivityCreateOrUpdateCommand command = new ActivityApi.ActivityCreateOrUpdateCommand(
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null
        );

        assertThatThrownBy(() -> activityApi.createOrUpdate(command))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void createOrUpdate_shouldRequireShopTypeWhenApplicationLimited() {
        ActivityApi.ActivityCreateOrUpdateCommand command = new ActivityApi.ActivityCreateOrUpdateCommand(
                "app-key-1",
                null,
                true,
                true,
                null,
                "test activity",
                "test desc",
                "2026-01-01",
                "2026-12-31",
                "10",
                "10",
                null,
                null,
                "1000",
                1,
                null,
                true,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        assertThatThrownBy(() -> activityApi.createOrUpdate(command))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("shop_type");
    }

    @Test
    void createOrUpdate_shouldRequireSpecifiedShopIdsWhenActivityTypeIsTwo() {
        ActivityApi.ActivityCreateOrUpdateCommand command = new ActivityApi.ActivityCreateOrUpdateCommand(
                "app-key-1",
                null,
                false,
                null,
                null,
                "test activity",
                "test desc",
                "2026-01-01",
                "2026-12-31",
                "10",
                "10",
                null,
                null,
                "1000",
                2,
                null,
                true,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        assertThatThrownBy(() -> activityApi.createOrUpdate(command))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("specified_shop_ids");
    }

    @Test
    void createOrUpdate_shouldRejectInvalidCosLimitType() {
        ActivityApi.ActivityCreateOrUpdateCommand command = new ActivityApi.ActivityCreateOrUpdateCommand(
                "app-key-1",
                null,
                false,
                null,
                null,
                "test activity",
                "test desc",
                "2026-01-01",
                "2026-12-31",
                "10",
                "10",
                null,
                null,
                "1000",
                1,
                null,
                true,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                9
        );

        assertThatThrownBy(() -> activityApi.createOrUpdate(command))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cos_limit_type");
    }
}
