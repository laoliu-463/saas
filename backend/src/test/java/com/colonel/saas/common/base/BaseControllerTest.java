package com.colonel.saas.common.base;

import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.common.result.PageResult;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BaseControllerTest {

    private final BaseController controller = new BaseController() {};

    @Test
    void ok_noData_returnsSuccessWithNull() {
        ApiResult<Void> result = controller.ok();
        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).isNull();
    }

    @Test
    void ok_withData_returnsSuccessWithData() {
        ApiResult<String> result = controller.ok("hello");
        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).isEqualTo("hello");
    }

    @Test
    void okPage_withPage_returnsPageResult() {
        Page<String> page = new Page<>(1, 10);
        page.setTotal(42);
        page.setRecords(List.of("a", "b"));

        ApiResult<PageResult<String>> result = controller.okPage(page);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).isNotNull();
        assertThat(result.getData().getTotal()).isEqualTo(42);
        assertThat(result.getData().getPage()).isEqualTo(1);
        assertThat(result.getData().getSize()).isEqualTo(10);
        assertThat(result.getData().getRecords()).containsExactly("a", "b");
    }

    @Test
    void fail_returnsErrorResult() {
        ApiResult<Void> result = controller.fail("操作失败");
        assertThat(result.getCode()).isEqualTo(460);
        assertThat(result.getMsg()).isEqualTo("操作失败");
    }
}