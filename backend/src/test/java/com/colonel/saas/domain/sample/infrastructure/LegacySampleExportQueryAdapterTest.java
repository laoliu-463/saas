package com.colonel.saas.domain.sample.infrastructure;

import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.service.sample.SampleQueryService;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class LegacySampleExportQueryAdapterTest {

    private final SampleQueryService sampleQueryService = mock(SampleQueryService.class);
    private final LegacySampleExportQueryAdapter adapter = new LegacySampleExportQueryAdapter(sampleQueryService);

    @Test
    void exportSamples_shouldForwardToLegacyService() throws Exception {
        UUID channelUserId = UUID.randomUUID();
        UUID recruiterUserId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        List<UUID> channelUserIds = List.of(channelUserId);
        List<String> roleCodes = List.of("channel_leader");
        LocalDateTime applyStartTime = LocalDateTime.of(2026, 7, 1, 0, 0);
        LocalDateTime applyEndTime = LocalDateTime.of(2026, 7, 2, 0, 0);
        LocalDateTime homeworkStartTime = LocalDateTime.of(2026, 7, 3, 0, 0);
        LocalDateTime homeworkEndTime = LocalDateTime.of(2026, 7, 4, 0, 0);
        HttpServletResponse response = mock(HttpServletResponse.class);

        adapter.exportSamples(
                "PENDING_AUDIT", "keyword", channelUserIds, recruiterUserId, "product", "shop", "tracking",
                "request", "talent", "FREE_SAMPLE", "MERCHANT", "VIDEO", "recipient", "13800000000",
                applyStartTime, applyEndTime, homeworkStartTime, homeworkEndTime, "SF", userId, deptId,
                DataScope.DEPT, roleCodes, response);

        verify(sampleQueryService).exportSamples(
                "PENDING_AUDIT", "keyword", channelUserIds, recruiterUserId, "product", "shop", "tracking",
                "request", "talent", "FREE_SAMPLE", "MERCHANT", "VIDEO", "recipient", "13800000000",
                applyStartTime, applyEndTime, homeworkStartTime, homeworkEndTime, "SF", userId, deptId,
                DataScope.DEPT, roleCodes, response);
    }
}
