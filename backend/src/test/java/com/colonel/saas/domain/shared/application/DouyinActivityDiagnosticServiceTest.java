package com.colonel.saas.domain.shared.application;

import com.colonel.saas.domain.shared.application.dto.DouyinActivityListProbeQuery;
import com.colonel.saas.domain.shared.application.dto.DouyinActivityMutateProbeCommand;
import com.colonel.saas.domain.shared.application.port.DouyinActivityDiagnosticPort;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DouyinActivityDiagnosticServiceTest {

    private final DouyinActivityDiagnosticPort port = mock(DouyinActivityDiagnosticPort.class);
    private final DouyinActivityDiagnosticService service = new DouyinActivityDiagnosticService(port);

    @Test
    void listActivities_shouldDelegateToPort() {
        DouyinActivityListProbeQuery query =
                new DouyinActivityListProbeQuery("app-1", 1, 2L, 3L, 4L, 5L, "activity");
        Map<String, Object> remoteResponse = Map.of("total", 1L);
        when(port.listActivities(query)).thenReturn(remoteResponse);

        assertThat(service.listActivities(query)).isEqualTo(remoteResponse);

        verify(port).listActivities(query);
    }

    @Test
    void mutateAndCancel_shouldDelegateToPort() {
        DouyinActivityMutateProbeCommand command = new DouyinActivityMutateProbeCommand(
                "app-1",
                100L,
                false,
                false,
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
        Map<String, Object> payload = Map.of("activity_id", 100L);
        Map<String, Object> mutateResponse = Map.of("code", 0);
        Map<String, Object> cancelResponse = Map.of("success", true);
        when(port.createOrUpdateActivity(command)).thenReturn(mutateResponse);
        when(port.cancelActivityProduct("app-1", payload)).thenReturn(cancelResponse);

        assertThat(service.createOrUpdateActivity(command)).isEqualTo(mutateResponse);
        assertThat(service.cancelActivityProduct("app-1", payload)).isEqualTo(cancelResponse);

        verify(port).createOrUpdateActivity(command);
        verify(port).cancelActivityProduct("app-1", payload);
    }
}
