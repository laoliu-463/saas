package com.colonel.saas.common.result;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PageResultTest {

    @Test
    void of_withPage_mapsAllFields() {
        Page<String> page = new Page<>(2, 5);
        page.setTotal(42);
        page.setRecords(List.of("a", "b", "c"));

        PageResult<String> result = PageResult.of(page);

        assertThat(result.getTotal()).isEqualTo(42);
        assertThat(result.getPage()).isEqualTo(2);
        assertThat(result.getSize()).isEqualTo(5);
        assertThat(result.getRecords()).containsExactly("a", "b", "c");
    }

    @Test
    void of_withEmptyPage_mapsCorrectly() {
        Page<String> page = new Page<>(1, 10);
        page.setTotal(0);
        page.setRecords(List.of());

        PageResult<String> result = PageResult.of(page);

        assertThat(result.getTotal()).isZero();
        assertThat(result.getPage()).isEqualTo(1);
        assertThat(result.getSize()).isEqualTo(10);
        assertThat(result.getRecords()).isEmpty();
    }

    @Test
    void of_withFirstPage_mapsCorrectly() {
        Page<Integer> page = new Page<>(1, 20);
        page.setTotal(100);
        page.setRecords(List.of(1, 2, 3, 4, 5));

        PageResult<Integer> result = PageResult.of(page);

        assertThat(result.getPage()).isEqualTo(1);
        assertThat(result.getTotal()).isEqualTo(100);
    }
}