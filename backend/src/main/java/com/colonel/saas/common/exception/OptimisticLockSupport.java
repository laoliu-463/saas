package com.colonel.saas.common.exception;

/**
 * 乐观锁更新结果校验工具类（配合 MyBatis-Plus {@code @Version} 使用）。
 *
 * <p>当使用 {@link com.colonel.saas.common.base.VersionedEntity} 的实体执行更新操作时，
 * MyBatis-Plus 会在 UPDATE SQL 的 WHERE 子句中追加 {@code AND version = #{原版本号}}。
 * 如果在读取和更新之间有其他事务修改了同一行，版本号不匹配，UPDATE 影响行数为 0。</p>
 *
 * <h3>使用方式</h3>
 * <pre>{@code
 * // 在 Service 层更新后校验
 * int affected = talentMapper.updateById(talent);
 * OptimisticLockSupport.requireUpdated(affected);
 *
 * // 自定义错误消息
 * int affected = productMapper.updateById(product);
 * OptimisticLockSupport.requireUpdated(affected, "商品信息已被他人修改，请刷新后重试");
 * }</pre>
 *
 * <h3>异常行为</h3>
 * <p>当影响行数为 0 时，抛出 {@link BusinessException#conflict(String)}（状态码 409），
 * 前端收到后应提示用户刷新页面重新加载最新数据。</p>
 *
 * @see com.colonel.saas.common.base.VersionedEntity 带乐观锁的实体基类
 * @see BusinessException#conflict(String) 数据冲突异常工厂方法
 */
public final class OptimisticLockSupport {

    /** 默认的乐观锁冲突提示消息 */
    private static final String DEFAULT_MESSAGE = "数据已被他人修改，请刷新后重试";

    /** 工具类禁止实例化 */
    private OptimisticLockSupport() {
    }

    /**
     * 校验更新操作的影响行数，为 0 时抛出冲突异常（使用默认消息）。
     *
     * @param affectedRows MyBatis-Plus 的 updateById / update 等方法返回的影响行数
     * @throws BusinessException 冲突异常（状态码 409），当影响行数为 0 时抛出
     */
    public static void requireUpdated(int affectedRows) {
        requireUpdated(affectedRows, DEFAULT_MESSAGE);
    }

    /**
     * 校验更新操作的影响行数，为 0 时抛出冲突异常（使用自定义消息）。
     *
     * @param affectedRows MyBatis-Plus 的 updateById / update 等方法返回的影响行数
     * @param message      自定义的冲突提示消息
     * @throws BusinessException 冲突异常（状态码 409），当影响行数为 0 时抛出
     */
    public static void requireUpdated(int affectedRows, String message) {
        if (affectedRows == 0) {
            throw BusinessException.conflict(message);
        }
    }
}
