package com.colonel.saas.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.domain.colonel.application.ColonelActivityDetailQueryService;
import com.colonel.saas.entity.ColonelsettlementActivity;
import com.colonel.saas.service.ColonelsettlementActivityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ColonelsettlementActivityControllerTest {

    @Mock
    private ColonelsettlementActivityService activityService;
    @Mock
    private ColonelActivityDetailQueryService activityDetailQueryService;

    private ColonelsettlementActivityController controller;

    @BeforeEach
    void setUp() {
        controller = new ColonelsettlementActivityController(activityService, activityDetailQueryService);
    }

    @Test
    void page_noFilter_returnsAllActivities() {
        Page<ColonelsettlementActivity> page = new Page<>(1, 10);
        page.setRecords(java.util.List.of(new ColonelsettlementActivity()));
        page.setTotal(1);
        when(activityService.getPage(1, 10, null)).thenReturn(page);

        var response = controller.page(1, 10, null);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData().getTotal()).isEqualTo(1);
    }

    @Test
    void page_withStatusFilter_passesStatusToService() {
        Page<ColonelsettlementActivity> page = new Page<>(1, 10);
        page.setRecords(java.util.List.of());
        page.setTotal(0);
        when(activityService.getPage(1, 10, 1)).thenReturn(page);

        var response = controller.page(1, 10, 1);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData().getTotal()).isZero();
    }

    @Test
    void page_customPageSize_usesProvidedSize() {
        Page<ColonelsettlementActivity> page = new Page<>(1, 20);
        page.setRecords(java.util.List.of());
        page.setTotal(0);
        when(activityService.getPage(1, 20, null)).thenReturn(page);

        var response = controller.page(1, 20, null);

        assertThat(response.getData().getSize()).isEqualTo(20);
    }

    @Test
    void douyinDetail_returnsApplicationServiceResponse() {
        Map<String, Object> detail = Map.of("code", 0, "data", Map.of("activityId", "ACT-1"));
        when(activityDetailQueryService.getDouyinDetail("app-1", "ACT-1")).thenReturn(detail);

        var response = controller.douyinDetail("app-1", "ACT-1");

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).isSameAs(detail);
    }
}
