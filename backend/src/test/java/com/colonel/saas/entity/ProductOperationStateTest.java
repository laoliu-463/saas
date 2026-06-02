package com.colonel.saas.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProductOperationStateTest {

    @Test
    void hiddenReasonShouldBeClearedWhenUpdatedToNull() throws NoSuchFieldException {
        TableField annotation = ProductOperationState.class
                .getDeclaredField("hiddenReason")
                .getAnnotation(TableField.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("hidden_reason");
        assertThat(annotation.updateStrategy()).isEqualTo(FieldStrategy.ALWAYS);
    }
}
