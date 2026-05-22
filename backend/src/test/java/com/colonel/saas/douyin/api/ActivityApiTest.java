package com.colonel.saas.douyin.api;

import com.colonel.saas.common.exception.BusinessException;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivityApiTest {

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
    private ActivityApi activityApi;

    @BeforeEach
    void setUp() {
        lenient().when(upstreamModeSupport.isContract()).thenReturn(false);
    }

    @Test
    void detail_shouldCallBuyinColonelActivityDetailEndpoint() {
        Map<String, Object> response = new HashMap<>();
        response.put("data", Map.of("activity_id", 54321, "activity_name", "test activity"));
        when(douyinApiClient.post(eq("buyin.colonelActivityDetail"), anyMap()))
                .thenReturn(response);

        Map<String, Object> result = activityApi.detail("app-1", "54321");

        ArgumentCaptor<Map<String, Object>> captor = mapCaptor();
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

        ArgumentCaptor<Map<String, Object>> captor = mapCaptor();
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

        ArgumentCaptor<Map<String, Object>> captor = mapCaptor();
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
    void listActivities_shouldUseContractFixtureWhenUpstreamModeIsContract() {
        when(upstreamModeSupport.isContract()).thenReturn(true);
        when(contractFixtureProvider.buildActivityListResponse("app", 1, 2L, 0L, 3L, 10L, "kw"))
                .thenReturn(Map.of("contract", true));

        Map<String, Object> result = activityApi.listActivities("app", 1, 2L, 0L, 3L, 10L, "kw");

        assertThat(result).containsEntry("contract", true);
    }

    @Test
    void detail_shouldUseContractFixtureWhenUpstreamModeIsContract() {
        when(upstreamModeSupport.isContract()).thenReturn(true);
        when(contractFixtureProvider.buildActivityDetailResponse("app", "123"))
                .thenReturn(Map.of("detail", true));

        Map<String, Object> result = activityApi.detail("app", "123");

        assertThat(result).containsEntry("detail", true);
    }

    @Test
    void listActivities_shouldMapAllQueryParams() {
        when(douyinApiClient.post(eq("alliance.instituteColonelActivityList"), anyMap()))
                .thenReturn(Map.of());

        activityApi.listActivities("app-2", 3, 1L, 0L, 2L, 20L, "keyword");

        ArgumentCaptor<Map<String, Object>> captor = mapCaptor();
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
    void listActivities_shouldRejectUnsupportedSearchSortAndPage() {
        assertThatThrownBy(() -> activityApi.listActivities("app", 0, 9L, 1L, 1L, 20L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("search_type");
        assertThatThrownBy(() -> activityApi.listActivities("app", 0, 0L, 9L, 1L, 20L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("sort_type");
        assertThatThrownBy(() -> activityApi.listActivities("app", 0, 0L, 1L, 0L, 20L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("page");
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

        ArgumentCaptor<Map<String, Object>> captor = mapCaptor();
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

    @Test
    void createOrUpdate_shouldRejectInvalidRatesAndActivityTypes() {
        assertThatThrownBy(() -> activityApi.createOrUpdate(validCommand("bad", "10", 1, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("commission_rate");
        assertThatThrownBy(() -> activityApi.createOrUpdate(validCommand("-1", "10", 1, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("negative");
        assertThatThrownBy(() -> activityApi.createOrUpdate(validCommand("51", "10", 1, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cannot exceed 50");
        assertThatThrownBy(() -> activityApi.createOrUpdate(validCommand("10", "41", 1, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cannot exceed 40");
        assertThatThrownBy(() -> activityApi.createOrUpdate(validCommand("10", "10", 9, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("activity_type");
        assertThatThrownBy(() -> activityApi.createOrUpdate(validCommand("10", "10", 1, 31)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("min_promotion_days");
    }

    @Test
    void cancelActivityProduct_shouldRemoveAppIdFromPayloadBeforePosting() {
        when(douyinApiClient.post(eq("alliance.colonelActivityProductCancel"), anyMap()))
                .thenReturn(Map.of("ok", true));
        Map<String, Object> payload = new HashMap<>();
        payload.put("appId", "payload-app");
        payload.put("activity_id", 1001L);

        activityApi.cancelActivityProduct("header-app", payload);

        ArgumentCaptor<Map<String, Object>> captor = mapCaptor();
        verify(douyinApiClient).post(eq("alliance.colonelActivityProductCancel"), captor.capture());
        assertThat(captor.getValue()).containsEntry("activity_id", 1001L);
        assertThat(captor.getValue()).doesNotContainKey("appId");
    }

    private ActivityApi.ActivityCreateOrUpdateCommand validCommand(
            String commissionRate,
            String serviceRate,
            Integer activityType,
            Integer minPromotionDays) {
        return new ActivityApi.ActivityCreateOrUpdateCommand(
                "app",
                null,
                false,
                null,
                null,
                "activity",
                "desc",
                "2026-01-01",
                "2026-12-31",
                commissionRate,
                serviceRate,
                null,
                null,
                "1000",
                activityType,
                null,
                true,
                null,
                null,
                minPromotionDays,
                null,
                null,
                null,
                null,
                null
        );
    }
}
