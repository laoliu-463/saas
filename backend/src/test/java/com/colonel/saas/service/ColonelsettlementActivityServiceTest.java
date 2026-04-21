package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.colonel.saas.entity.ColonelsettlementActivity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ColonelsettlementActivityServiceTest {

    private final ColonelsettlementActivityService service = new ColonelsettlementActivityService();

    @Test
    void getPage_noFilter_shouldReturnAllActivitiesPaginated() {
        IPage<ColonelsettlementActivity> page = service.getPage(1, 3, null);

        assertThat(page.getTotal()).isEqualTo(5);
        assertThat(page.getRecords()).hasSize(3);
        assertThat(page.getCurrent()).isEqualTo(1);
        assertThat(page.getSize()).isEqualTo(3);
    }

    @Test
    void getPage_page2_shouldReturnRemainingRecords() {
        IPage<ColonelsettlementActivity> page = service.getPage(2, 3, null);

        assertThat(page.getTotal()).isEqualTo(5);
        assertThat(page.getRecords()).hasSize(2);
    }

    @Test
    void getPage_pageBeyondEnd_shouldReturnEmpty() {
        IPage<ColonelsettlementActivity> page = service.getPage(99, 10, null);

        assertThat(page.getRecords()).isEmpty();
        assertThat(page.getTotal()).isEqualTo(5);
    }

    @Test
    void getPage_filterByStatus_shouldReturnOnlyMatching() {
        // initMockData creates 3 active (status=1: i=1,3,5) and 2 inactive (status=0: i=2,4)
        IPage<ColonelsettlementActivity> page = service.getPage(1, 10, 1);

        assertThat(page.getTotal()).isEqualTo(3);
        assertThat(page.getRecords()).hasSize(3);
        assertThat(page.getRecords()).allMatch(a -> a.getStatus() == 1);
    }

    @Test
    void getPage_filterByInactiveStatus_shouldReturnOnlyInactive() {
        IPage<ColonelsettlementActivity> page = service.getPage(1, 10, 0);

        assertThat(page.getTotal()).isEqualTo(2);
        assertThat(page.getRecords()).hasSize(2);
        assertThat(page.getRecords()).allMatch(a -> a.getStatus() == 0);
    }

    @Test
    void getPage_zeroSize_shouldNormalizeTo1() {
        IPage<ColonelsettlementActivity> page = service.getPage(1, 0, null);

        assertThat(page.getSize()).isEqualTo(1);
        assertThat(page.getRecords()).hasSize(1);
    }

    @Test
    void getPage_zeroPage_shouldNormalizeTo1() {
        IPage<ColonelsettlementActivity> page = service.getPage(0, 10, null);

        assertThat(page.getCurrent()).isEqualTo(1);
    }

    @Test
    void getPage_singlePageSize_shouldReturnAllMatching() {
        IPage<ColonelsettlementActivity> page = service.getPage(1, 1, null);

        assertThat(page.getTotal()).isEqualTo(5);
        assertThat(page.getRecords()).hasSize(1);
    }
}
