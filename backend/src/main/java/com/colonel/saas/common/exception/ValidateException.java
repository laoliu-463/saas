package com.colonel.saas.common.exception;

/**
 * 参数校验异常。
 *
 * <p>在业务逻辑层手动校验参数时使用（与 Spring 的 {@code @Valid} 注解触发的
 * {@code MethodArgumentNotValidException} 互补）。
 * 当校验逻辑不在 Controller 入参层面、而在 Service 层进行更深层的业务校验时，
 * 使用此异常抛出校验失败信息。</p>
 *
 * <h3>与 BusinessException.param() 的区别</h3>
 * <ul>
 *   <li>{@code ValidateException} — 专注于参数格式/范围的校验失败，被
 *       {@link GlobalExceptionHandler#handleValidate(Exception)} 统一捕获处理</li>
 *   <li>{@code BusinessException.param()} — 更通用的参数错误，包含业务语义的参数问题</li>
 * </ul>
 *
 * <h3>典型使用场景</h3>
 * <pre>{@code
 * // Service 层的深层参数校验
 * if (startDate.isAfter(endDate)) {
 *     throw new ValidateException("开始日期不能晚于结束日期");
 * }
 *
 * // 批量操作中的单条校验
 * for (TalentDTO dto : list) {
 *     if (dto.getPhone() != null && !isValidPhone(dto.getPhone())) {
 *         throw new ValidateException("手机号格式不正确: " + dto.getPhone());
 *     }
 * }
 * }</pre>
 *
 * @see GlobalExceptionHandler#handleValidate(Exception) 全局异常处理入口
 * @see BusinessException#param(String) 更通用的参数错误工厂方法
 */
public class ValidateException extends RuntimeException {

    /**
     * 构造参数校验异常。
     *
     * @param message 校验失败的描述信息，将直接返回给前端展示
     */
    public ValidateException(String message) {
        super(message);
    }
}
