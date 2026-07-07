package com.colonel.saas.domain.shared.infrastructure;

import com.colonel.saas.domain.shared.application.dto.DouyinActivityListProbeQuery;
import com.colonel.saas.domain.shared.application.dto.DouyinActivityMutateProbeCommand;
import com.colonel.saas.gateway.douyin.DouyinActivityGateway;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DouyinActivityDiagnosticGatewayAdapterTest {

    private final DouyinActivityGateway gateway = mock(DouyinActivityGateway.class);
    private final DouyinActivityDiagnosticGatewayAdapter adapter =
            new DouyinActivityDiagnosticGatewayAdapter(gateway);

    @Test
    void listActivities_shouldMapQueryToGatewayAndReturnLegacyMap() {
        when(gateway.listActivities(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new DouyinActivityGateway.ActivityListResult(false, 99L, 1L, List.of(
                        new DouyinActivityGateway.ActivityItem(
                                1001L,
                                "activity",
                                "2026-07-01",
                                "2026-07-02",
                                1,
                                "online",
                                "2026-06-01",
                                "2026-06-02",
                                null,
                                2001L))));

        Map<String, Object> result = adapter.listActivities(
                new DouyinActivityListProbeQuery("app-1", 1, 2L, 3L, 4L, 5L, "activity"));

        ArgumentCaptor<DouyinActivityGateway.ActivityListQuery> captor =
                ArgumentCaptor.forClass(DouyinActivityGateway.ActivityListQuery.class);
        verify(gateway).listActivities(captor.capture());
        DouyinActivityGateway.ActivityListQuery request = captor.getValue();
        assertThat(request.appId()).isEqualTo("app-1");
        assertThat(request.status()).isEqualTo(1);
        assertThat(request.searchType()).isEqualTo(2L);
        assertThat(request.sortType()).isEqualTo(3L);
        assertThat(request.page()).isEqualTo(4L);
        assertThat(request.pageSize()).isEqualTo(5L);
        assertThat(request.activityInfo()).isEqualTo("activity");
        assertThat(result)
                .containsEntry("test", false)
                .containsEntry("institutionId", 99L)
                .containsEntry("total", 1L);
        assertThat((List<?>) result.get("activityList")).hasSize(1);
    }

    @Test
    void detailAndCancel_shouldDelegateToGateway() {
        Map<String, Object> detail = Map.of("activity_id", 1001L);
        Map<String, Object> payload = Map.of("activity_id", 1001L);
        Map<String, Object> cancel = Map.of("success", true);
        when(gateway.activityDetail("app-1", "1001")).thenReturn(detail);
        when(gateway.cancelActivityProduct("app-1", payload)).thenReturn(cancel);

        assertThat(adapter.activityDetail("app-1", "1001")).isEqualTo(detail);
        assertThat(adapter.cancelActivityProduct("app-1", payload)).isEqualTo(cancel);

        verify(gateway).activityDetail("app-1", "1001");
        verify(gateway).cancelActivityProduct("app-1", payload);
    }

    @Test
    void createOrUpdateActivity_shouldMapCommandToGatewayCommand() {
        DouyinActivityMutateProbeCommand command = new DouyinActivityMutateProbeCommand(
                "app-1",
                100L,
                false,
                true,
                "normal",
                "activity",
                "desc",
                "2026-07-01 00:00:00",
                "2026-07-02 00:00:00",
                "10",
                "5",
                "wechat",
                "13800000000",
                "1000",
                1,
                "S1",
                true,
                "cat",
                90,
                7,
                0,
                0,
                "0",
                "0",
                0);
        Map<String, Object> response = Map.of("code", 0);
        when(gateway.createOrUpdateActivity(org.mockito.ArgumentMatchers.any())).thenReturn(response);

        assertThat(adapter.createOrUpdateActivity(command)).isEqualTo(response);

        ArgumentCaptor<DouyinActivityGateway.ActivityMutateCommand> captor =
                ArgumentCaptor.forClass(DouyinActivityGateway.ActivityMutateCommand.class);
        verify(gateway).createOrUpdateActivity(captor.capture());
        DouyinActivityGateway.ActivityMutateCommand gatewayCommand = captor.getValue();
        assertThat(gatewayCommand.appId()).isEqualTo("app-1");
        assertThat(gatewayCommand.activityId()).isEqualTo(100L);
        assertThat(gatewayCommand.activityName()).isEqualTo("activity");
        assertThat(gatewayCommand.activityDesc()).isEqualTo("desc");
        assertThat(gatewayCommand.commissionRate()).isEqualTo("10");
        assertThat(gatewayCommand.serviceRate()).isEqualTo("5");
        assertThat(gatewayCommand.online()).isTrue();
    }
}
