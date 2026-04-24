package com.colonel.saas.common.base;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BaseEntityTest {

    @Test
    void defaultDeleted_isZero() {
        TestEntity entity = new TestEntity();
        assertThat(entity.getDeleted()).isEqualTo(0);
    }

    @Test
    void id_canBeSetAndRetrieved() {
        TestEntity entity = new TestEntity();
        UUID id = UUID.randomUUID();
        entity.setId(id);
        assertThat(entity.getId()).isEqualTo(id);
    }

    @Test
    void timestamps_canBeSetAndRetrieved() {
        TestEntity entity = new TestEntity();
        LocalDateTime now = LocalDateTime.now();
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        assertThat(entity.getCreateTime()).isEqualTo(now);
        assertThat(entity.getUpdateTime()).isEqualTo(now);
    }

    @Test
    void auditFields_canBeSetAndRetrieved() {
        TestEntity entity = new TestEntity();
        UUID userId = UUID.randomUUID();
        entity.setCreateBy(userId);
        entity.setUpdateBy(userId);
        assertThat(entity.getCreateBy()).isEqualTo(userId);
        assertThat(entity.getUpdateBy()).isEqualTo(userId);
    }

    private static class TestEntity extends BaseEntity {
    }
}