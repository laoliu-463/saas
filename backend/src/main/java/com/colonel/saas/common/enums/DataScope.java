package com.colonel.saas.common.enums;

import com.colonel.saas.common.exception.BusinessException;

/**
 * 数据范围枚举。
 */
public enum DataScope {
    PERSONAL(1),
    DEPT(2),
    ALL(3);

    private final int code;

    DataScope(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static DataScope fromCode(int code) {
        for (DataScope scope : values()) {
            if (scope.code == code) {
                return scope;
            }
        }
        throw new BusinessException("非法数据范围: " + code);
    }
}
