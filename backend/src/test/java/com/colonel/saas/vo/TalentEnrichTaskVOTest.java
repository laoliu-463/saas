package com.colonel.saas.vo;

import com.colonel.saas.entity.TalentEnrichTask;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TalentEnrichTaskVOTest {

    @Test
    void from_mapsTaskFieldsWithoutAuditMetadata() {
        UUID taskId = UUID.randomUUID();
        UUID talentId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();

        TalentEnrichTask task = new TalentEnrichTask();
        task.setId(taskId);
        task.setTalentId(talentId);
        task.setInputValue("dy_123");
        task.setInputType("UID");
        task.setSourceType("CRAWLER");
        task.setTaskStatus("RUNNING");
        task.setRetryCount(1);
        task.setNextRetryTime(now.plusMinutes(5));
        task.setErrorMsg(null);
        task.setCreateTime(now);
        task.setUpdateTime(now);
        task.setCreateBy(UUID.randomUUID());
        task.setUpdateBy(UUID.randomUUID());
        task.setDeleted(0);

        TalentEnrichTaskVO vo = TalentEnrichTaskVO.from(task);

        assertThat(vo.getId()).isEqualTo(taskId.toString());
        assertThat(vo.getTalentId()).isEqualTo(talentId.toString());
        assertThat(vo.getInputValue()).isEqualTo("dy_123");
        assertThat(vo.getInputType()).isEqualTo("UID");
        assertThat(vo.getSourceType()).isEqualTo("CRAWLER");
        assertThat(vo.getTaskStatus()).isEqualTo("RUNNING");
        assertThat(vo.getRetryCount()).isEqualTo(1);
        assertThat(vo.getNextRetryTime()).isEqualTo(now.plusMinutes(5));
        assertThat(vo.getCreateTime()).isEqualTo(now);
        assertThat(vo.getUpdateTime()).isEqualTo(now);
    }

    @Test
    void from_null_returnsNull() {
        assertThat(TalentEnrichTaskVO.from(null)).isNull();
    }
}
