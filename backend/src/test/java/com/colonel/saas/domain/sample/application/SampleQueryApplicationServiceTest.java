package com.colonel.saas.domain.sample.application;

import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.result.PageResult;
import com.colonel.saas.config.DddRefactorProperties;
import com.colonel.saas.domain.sample.application.port.SampleDetailQueryPort;
import com.colonel.saas.domain.sample.application.port.SamplePageQueryPort;
import com.colonel.saas.domain.sample.facade.SampleDomainFacade;
import com.colonel.saas.service.sample.SampleQueryService;
import com.colonel.saas.vo.sample.SampleVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SampleQueryApplicationServiceTest {

    @Mock
    private SampleQueryService sampleQueryService;
    @Mock
    private SamplePageQueryPort samplePageQueryPort;
    @Mock
    private SampleDetailQueryPort sampleDetailQueryPort;
    @Mock
    private SampleDomainFacade sampleDomainFacade;
    @Mock
    private DddRefactorProperties dddRefactorProperties;

    @Test
    void fullPageQuery_shouldUsePagePort() {
        SampleQueryApplicationService applicationService = newApplicationService();
        PageResult<SampleVO> expected = pageResult();
        when(samplePageQueryPort.getSamplePage(
                anyLong(), anyLong(), anyString(), anyString(), any(), any(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), any(), any(), any(), any(), anyString(), any(), any(), any(), any()))
                .thenReturn(expected);

        PageResult<SampleVO> actual = applicationService.getSamplePage(
                2, 20, "达人", "PENDING_AUDIT", List.of(UUID.randomUUID()), UUID.randomUUID(),
                "商品", "店铺", "SF123", "SR123", "昵称", "FREE_SAMPLE", "MERCHANT", "VIDEO",
                "收件人", "13800000000", null, null, null, null, "顺丰", UUID.randomUUID(), UUID.randomUUID(),
                DataScope.PERSONAL, List.of("channel_staff"));

        assertThat(actual).isSameAs(expected);
        verify(samplePageQueryPort).getSamplePage(
                eq(2L), eq(20L), eq("达人"), eq("PENDING_AUDIT"), any(), any(), eq("商品"), eq("店铺"),
                eq("SF123"), eq("SR123"), eq("昵称"), eq("FREE_SAMPLE"), eq("MERCHANT"), eq("VIDEO"),
                eq("收件人"), eq("13800000000"), any(), any(), any(), any(), eq("顺丰"), any(), any(),
                eq(DataScope.PERSONAL), any());
        verify(sampleQueryService, never()).getSamplePage(
                anyLong(), anyLong(), anyString(), anyString(), any(), any(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), any(), any(), any(), any(), anyString(), any(), any(), any(), any());
    }

    @Test
    void simplePageQuery_shouldUsePagePort() {
        SampleQueryApplicationService applicationService = newApplicationService();
        PageResult<SampleVO> expected = pageResult();
        when(samplePageQueryPort.getSamplePage(
                anyLong(), anyLong(), nullable(String.class), nullable(String.class), any(), any(), any(), any()))
                .thenReturn(expected);

        PageResult<SampleVO> actual = applicationService.getSamplePage(
                1, 10, null, null, UUID.randomUUID(), null, DataScope.ALL, List.of("admin"));

        assertThat(actual).isSameAs(expected);
        verify(samplePageQueryPort).getSamplePage(
                eq(1L), eq(10L), isNull(), isNull(), any(), any(), eq(DataScope.ALL), any());
        verify(sampleQueryService, never()).getSamplePage(
                anyLong(), anyLong(), anyString(), anyString(), any(), any(), any(), any());
    }

    private SampleQueryApplicationService newApplicationService() {
        return new SampleQueryApplicationService(
                sampleQueryService,
                samplePageQueryPort,
                sampleDetailQueryPort,
                sampleDomainFacade,
                dddRefactorProperties);
    }

    private PageResult<SampleVO> pageResult() {
        PageResult<SampleVO> result = new PageResult<>();
        result.setPage(1);
        result.setSize(10);
        result.setTotal(0);
        result.setRecords(List.of());
        return result;
    }
}
