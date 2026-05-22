package com.colonel.saas.common.exception;

/**
 * 乐观锁更新结果校验（配合 MyBatis-Plus {@code @Version}）。
 */
public final class OptimisticLockSupport {

    private static final String DEFAULT_MESSAGE = "数据已被他人修改，请刷新后重试";

    private OptimisticLockSupport() {
    }

    public static void requireUpdated(int affectedRows) {
        requireUpdated(affectedRows, DEFAULT_MESSAGE);
    }

    public static void requireUpdated(int affectedRows, String message) {
        if (affectedRows == 0) {
            throw BusinessException.conflict(message);
        }
    }
}
