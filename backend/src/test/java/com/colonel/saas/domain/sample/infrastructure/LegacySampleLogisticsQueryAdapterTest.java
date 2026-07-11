package com.colonel.saas.domain.sample.infrastructure;

import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.service.sample.SampleQueryService;
import com.colonel.saas.vo.sample.SampleLogisticsVO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LegacySampleLogisticsQueryAdapterTest {

    private final SampleQueryService sampleQueryService = mock(SampleQueryService.class);
    private final LegacySampleLogisticsQueryAdapter adapter = new LegacySampleLogisticsQueryAdapter(sampleQueryService);

    @Test
    void getSampleLogistics_shouldForwardToLegacyService() {
        UUID sampleId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        List<String> roleCodes = List.of("admin");
        SampleLogisticsVO expected = new SampleLogisticsVO();
        when(sampleQueryService.getSampleLogistics(
                sampleId, userId, deptId, DataScope.ALL, roleCodes)).thenReturn(expected);

        SampleLogisticsVO actual = adapter.getSampleLogistics(
                sampleId, userId, deptId, DataScope.ALL, roleCodes);

        assertThat(actual).isSameAs(expected);
        verify(sampleQueryService).getSampleLogistics(sampleId, userId, deptId, DataScope.ALL, roleCodes);
    }
}
