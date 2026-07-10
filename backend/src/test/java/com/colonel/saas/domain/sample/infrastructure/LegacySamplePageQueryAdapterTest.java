package com.colonel.saas.domain.sample.infrastructure;

import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.result.PageResult;
import com.colonel.saas.service.sample.SampleQueryService;
import com.colonel.saas.vo.sample.SampleVO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LegacySamplePageQueryAdapterTest {

    private final SampleQueryService sampleQueryService = mock(SampleQueryService.class);
    private final LegacySamplePageQueryAdapter adapter = new LegacySamplePageQueryAdapter(sampleQueryService);

    @Test
    void fullPageQuery_shouldForwardToLegacyService() {
        PageResult<SampleVO> expected = new PageResult<>();
        when(sampleQueryService.getSamplePage(
                anyLong(), anyLong(), nullable(String.class), nullable(String.class), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any()))
                .thenReturn(expected);

        PageResult<SampleVO> actual = adapter.getSamplePage(
                1, 20, null, null, List.of(), null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, UUID.randomUUID(), null, DataScope.ALL, List.of("admin"));

        assertThat(actual).isSameAs(expected);
        verify(sampleQueryService).getSamplePage(
                anyLong(), anyLong(), nullable(String.class), nullable(String.class), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any());
    }

    @Test
    void simplePageQuery_shouldForwardToLegacyService() {
        PageResult<SampleVO> expected = new PageResult<>();
        when(sampleQueryService.getSamplePage(
                anyLong(), anyLong(), nullable(String.class), nullable(String.class), any(), any(), any(), any()))
                .thenReturn(expected);

        PageResult<SampleVO> actual = adapter.getSamplePage(
                1, 10, null, null, UUID.randomUUID(), null, DataScope.ALL, List.of("admin"));

        assertThat(actual).isSameAs(expected);
        verify(sampleQueryService).getSamplePage(
                anyLong(), anyLong(), nullable(String.class), nullable(String.class), any(), any(), any(), any());
    }
}
