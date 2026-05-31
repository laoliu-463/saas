package com.colonel.saas.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class DouyinMoneyConverter {

    private static final BigDecimal FEN_DIVISOR = new BigDecimal("100");

    private DouyinMoneyConverter() {
    }

    public static BigDecimal fenToYuan(Long fen) {
        if (fen == null) {
            return null;
        }
        return new BigDecimal(fen).divide(FEN_DIVISOR, 2, RoundingMode.HALF_UP);
    }

    public static BigDecimal yuanStringToYuan(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return new BigDecimal(s.trim());
    }

    /**
     * 2704 等接口金额字段按「分」入参，落库为与主订单一致的 minor unit（前端展示时 ÷100 为元）。
     */
    public static Long fenToStoredMinor(Long fen) {
        BigDecimal yuan = fenToYuan(fen);
        if (yuan == null) {
            return null;
        }
        return yuan.movePointRight(2).longValue();
    }
}
