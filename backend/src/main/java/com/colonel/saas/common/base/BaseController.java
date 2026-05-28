package com.colonel.saas.common.base;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.common.result.PageResult;

/**
 * 控制器基类。
 *
 * <p>为所有 REST 控制器提供统一的响应构建快捷方法，
 * 避免在每个 Controller 中重复编写 {@link ApiResult} 的构造逻辑。</p>
 *
 * <h3>使用方式</h3>
 * <pre>{@code
 * @RestController
 * public class TalentController extends BaseController {
 *     @GetMapping("/talent/{id}")
 *     public ApiResult<TalentVO> getTalent(@PathVariable UUID id) {
 *         return ok(talentService.findById(id));
 *     }
 * }
 * }</pre>
 *
 * <h3>职责边界</h3>
 * <ul>
 *   <li>仅封装响应包装逻辑，不包含任何业务规则</li>
 *   <li>所有业务 Controller 均应继承此类以保持响应格式一致</li>
 *   <li>分页查询使用 {@link #okPage(IPage)} 自动将 MyBatis-Plus 分页对象转换为 {@link PageResult}</li>
 * </ul>
 *
 * @see ApiResult 统一响应结构
 * @see PageResult 分页响应结构
 */
public class BaseController {

    /**
     * 构建无数据的成功响应（状态码 200）。
     *
     * @param <T> 响应数据类型（实际为 null，泛型用于编译期类型匹配）
     * @return 包含成功状态码且 data 为 null 的 {@link ApiResult}
     */
    protected <T> ApiResult<T> ok() {
        return ApiResult.ok();
    }

    /**
     * 构建携带数据的成功响应（状态码 200）。
     *
     * @param data 响应数据，可为任意业务对象
     * @param <T>  响应数据类型
     * @return 包含成功状态码和业务数据的 {@link ApiResult}
     */
    protected <T> ApiResult<T> ok(T data) {
        return ApiResult.ok(data);
    }

    /**
     * 构建分页查询的成功响应（状态码 200）。
     *
     * <p>将 MyBatis-Plus 的 {@link IPage} 自动转换为前端友好的 {@link PageResult}，
     * 提取 total、page、size、records 四个关键字段。</p>
     *
     * @param page MyBatis-Plus 分页查询结果
     * @param <T>  分页记录的元素类型
     * @return 包含分页数据的 {@link ApiResult}
     */
    protected <T> ApiResult<PageResult<T>> okPage(IPage<T> page) {
        return ApiResult.ok(PageResult.of(page));
    }

    /**
     * 构建业务失败响应（状态码 460 业务异常）。
     *
     * <p>用于业务规则不满足时的主动失败，如余额不足、状态不允许等。
     * 与全局异常处理器的 {@code BusinessException} 不同，此方法适用于
     * 不需要抛出异常、直接在控制器中返回失败结果的场景。</p>
     *
     * @param msg 业务失败描述信息，将直接展示给前端
     * @return 包含失败状态码和错误消息的 {@link ApiResult}
     */
    protected ApiResult<Void> fail(String msg) {
        return ApiResult.fail(msg);
    }
}
