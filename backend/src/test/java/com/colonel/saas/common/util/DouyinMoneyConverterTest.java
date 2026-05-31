package com.colonel.saas.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class DouyinMoneyConverterTest {

    @Test
    @DisplayName("fenToYuan converts 9900 fen to 99.00 yuan")
    void fenToYuan_typical() {
        assertThat(DouyinMoneyConverter.fenToYuan(9900L))
                .isEqualByComparingTo(new BigDecimal("99.00"));
    }

    @Test
    @DisplayName("fenToYuan returns null for null input")
    void fenToYuan_null() {
        assertThat(DouyinMoneyConverter.fenToYuan(null)).isNull();
    }

    @Test
    @DisplayName("fenToYuan returns 0.00 for zero fen")
    void fenToYuan_zero() {
        assertThat(DouyinMoneyConverter.fenToYuan(0L))
                .isEqualByComparingTo(new BigDecimal("0.00"));
    }

    @Test
    @DisplayName("fenToYuan rounds half-up for 1 fen")
    void fenToYuan_singleFen() {
        assertThat(DouyinMoneyConverter.fenToYuan(1L))
                .isEqualByComparingTo(new BigDecimal("0.01"));
    }

    @Test
    @DisplayName("fenToYuan handles negative fen")
    void fenToYuan_negative() {
        assertThat(DouyinMoneyConverter.fenToYuan(-500L))
                .isEqualByComparingTo(new BigDecimal("-5.00"));
    }

    @Test
    @DisplayName("yuanStringToYuan parses decimal string")
    void yuanStringToYuan_decimal() {
        assertThat(DouyinMoneyConverter.yuanStringToYuan("99.50"))
                .isEqualByComparingTo(new BigDecimal("99.50"));
    }

    @Test
    @DisplayName("yuanStringToYuan trims whitespace")
    void yuanStringToYuan_whitespace() {
        assertThat(DouyinMoneyConverter.yuanStringToYuan("  12.34  "))
                .isEqualByComparingTo(new BigDecimal("12.34"));
    }

    @Test
    @DisplayName("yuanStringToYuan returns null for blank input")
    void yuanStringToYuan_blank() {
        assertThat(DouyinMoneyConverter.yuanStringToYuan("")).isNull();
        assertThat(DouyinMoneyConverter.yuanStringToYuan(null)).isNull();
        assertThat(DouyinMoneyConverter.yuanStringToYuan("   ")).isNull();
    }

    @Test
    @DisplayName("fenToStoredMinor keeps fen-compatible minor unit for UI divide-by-100")
    void fenToStoredMinor_typical() {
        assertThat(DouyinMoneyConverter.fenToStoredMinor(7600L)).isEqualTo(7600L);
        assertThat(DouyinMoneyConverter.fenToStoredMinor(1L)).isEqualTo(1L);
    }

    @Test
    @DisplayName("fenToStoredMinor returns null for null fen")
    void fenToStoredMinor_null() {
        assertThat(DouyinMoneyConverter.fenToStoredMinor(null)).isNull();
    }
}
