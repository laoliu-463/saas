package com.colonel.saas.common.enums;

import com.colonel.saas.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DataScopeTest {

    @Test
    @DisplayName("fromCode(1) → PERSONAL")
    void fromCode_1_returnsPersonal() {
        assertThat(DataScope.fromCode(1)).isEqualTo(DataScope.PERSONAL);
    }

    @Test
    @DisplayName("fromCode(2) → DEPT（V1 中 GROUP 已合并到 DEPT）")
    void fromCode_2_returnsDept() {
        assertThat(DataScope.fromCode(2)).isEqualTo(DataScope.DEPT);
    }

    @Test
    @DisplayName("fromCode(3) → ALL")
    void fromCode_3_returnsAll() {
        assertThat(DataScope.fromCode(3)).isEqualTo(DataScope.ALL);
    }

    @Test
    @DisplayName("fromCode 非法值抛出 BusinessException")
    void fromCode_invalidCode_throwsBusinessException() {
        assertThatThrownBy(() -> DataScope.fromCode(99))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("99");
    }

    @Test
    @DisplayName("PERSONAL.code = 1, DEPT.code = 2, ALL.code = 3 — 无重复码")
    void codes_areUnique() {
        assertThat(DataScope.PERSONAL.getCode()).isEqualTo(1);
        assertThat(DataScope.DEPT.getCode()).isEqualTo(2);
        assertThat(DataScope.ALL.getCode()).isEqualTo(3);

        // 验证无 code 碰撞
        long distinctCodes = java.util.Arrays.stream(DataScope.values())
                .map(DataScope::getCode)
                .distinct()
                .count();
        assertThat(distinctCodes).isEqualTo(DataScope.values().length);
    }

    @Test
    @DisplayName("枚举值仅 3 个：PERSONAL / DEPT / ALL（无 GROUP）")
    void enumValues_noGroup() {
        assertThat(DataScope.values()).containsExactly(
                DataScope.PERSONAL, DataScope.DEPT, DataScope.ALL
        );
    }

    @Test
    @DisplayName("valueOf 能解析全部枚举名")
    void valueOf_roundTrips() {
        for (DataScope scope : DataScope.values()) {
            assertThat(DataScope.valueOf(scope.name())).isEqualTo(scope);
        }
    }
}
