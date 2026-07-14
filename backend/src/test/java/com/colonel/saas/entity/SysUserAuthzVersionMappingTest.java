package com.colonel.saas.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SysUserAuthzVersionMappingTest {

    @Test
    void authzVersionShouldNeverParticipateInGenericUpdates() throws NoSuchFieldException {
        TableField annotation = SysUser.class
                .getDeclaredField("authzVersion")
                .getAnnotation(TableField.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("authz_version");
        assertThat(annotation.updateStrategy()).isEqualTo(FieldStrategy.NEVER);
    }

    @Test
    void authzVersionShouldRetainInsertDefault() {
        assertThat(new SysUser().getAuthzVersion()).isEqualTo(1L);
    }
}
