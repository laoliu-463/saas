package com.colonel.saas.common.base;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.common.result.PageResult;

public class BaseController {

    protected <T> ApiResult<T> ok() {
        return ApiResult.ok();
    }

    protected <T> ApiResult<T> ok(T data) {
        return ApiResult.ok(data);
    }

    protected <T> ApiResult<PageResult<T>> okPage(IPage<T> page) {
        return ApiResult.ok(PageResult.of(page));
    }

    protected ApiResult<Void> fail(String msg) {
        return ApiResult.fail(msg);
    }
}
