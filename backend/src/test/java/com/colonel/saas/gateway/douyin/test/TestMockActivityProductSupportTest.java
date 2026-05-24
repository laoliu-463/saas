package com.colonel.saas.gateway.douyin.test;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class TestMockActivityProductSupportTest {

    @Test
    void resolveMockActivityStatus_shouldUseCatalogStatusForDemoActivities() {
        assertThat(TestMockActivityProductSupport.resolveMockActivityStatus(100017L)).isEqualTo(5);
        assertThat(TestMockActivityProductSupport.resolveMockActivityStatus(100001L)).isEqualTo(1);
    }

    @Test
    void resolveMockProductStatus_shouldPreferPromotingForPromotingActivity() {
        assertThat(TestMockActivityProductSupport.resolveMockProductStatus(100017L, 12)).isEqualTo(1);
        assertThat(TestMockActivityProductSupport.resolveMockProductStatus(100017L, 17)).isEqualTo(2);
    }

    @Test
    void mockPromotionEndDate_shouldStayInFuture() {
        LocalDate end = LocalDate.parse(TestMockActivityProductSupport.mockPromotionEndDate());
        assertThat(end).isAfter(LocalDate.now());
    }
}
