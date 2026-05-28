package com.colonel.saas.common.exception;

/**
 * 无权限异常（HTTP 403 Forbidden）。
 *
 * <p>当用户已通过身份认证，但不具备执行当前操作所需的权限时抛出。
 * 与 {@link BusinessException#forbidden(String)} 的区别在于：
 * 此异常通过 {@link GlobalExceptionHandler} 映射为独立的 403 响应，
 * 而非嵌入到统一的业务异常响应格式中。</p>
 *
 * <h3>典型使用场景</h3>
 * <ul>
 *   <li>当前用户的数据范围（{@link com.colonel.saas.common.enums.DataScope}）不包含目标数据</li>
 *   <li>用户角色不具备某功能模块的访问权限</li>
 *   <li>操作需要管理员权限但当前用户为普通用户</li>
 * </ul>
 *
 * <h3>前端处理</h3>
 * <p>前端收到 403 状态码后，通常应展示"无权限"提示页面或弹窗，
 * 而非跳转到登录页面（与 401 未授权的处理逻辑不同）。</p>
 *
 * @see GlobalExceptionHandler#handleForbidden(ForbiddenException) 异常处理入口
 * @see BusinessException#forbidden(String) 业务层的无权限异常工厂方法
 */
public class ForbiddenException extends RuntimeException {

    /**
     * 构造无权限异常。
     *
     * @param message 权限不足的描述信息，如"无权访问他人数据"、"需要管理员权限"等
     */
    public ForbiddenException(String message) {
        super(message);
    }
}

