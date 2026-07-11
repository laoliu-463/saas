package com.colonel.saas.domain.sample.infrastructure;

import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.service.sample.SampleQueryService;
import com.colonel.saas.vo.sample.SampleBoardCard;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LegacySampleBoardQueryAdapterTest {

    private final SampleQueryService sampleQueryService = mock(SampleQueryService.class);
    private final LegacySampleBoardQueryAdapter adapter = new LegacySampleBoardQueryAdapter(sampleQueryService);

    @Test
    void getSampleBoard_shouldForwardToLegacyService() {
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        List<String> roleCodes = List.of("admin");
        Map<String, List<SampleBoardCard>> expected = Map.of("pending", List.of());
        when(sampleQueryService.getSampleBoard(userId, deptId, DataScope.ALL, roleCodes)).thenReturn(expected);

        Map<String, List<SampleBoardCard>> actual =
                adapter.getSampleBoard(userId, deptId, DataScope.ALL, roleCodes);

        assertThat(actual).isSameAs(expected);
        verify(sampleQueryService).getSampleBoard(userId, deptId, DataScope.ALL, roleCodes);
    }
}
