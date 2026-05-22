package com.colonel.saas.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TalentProfileSyncLogTest {

    @Test
    void shouldExposeAssignedValues() {
        UUID id = UUID.randomUUID();
        UUID talentId = UUID.randomUUID();
        LocalDateTime startedAt = LocalDateTime.now().minusMinutes(1);
        LocalDateTime finishedAt = LocalDateTime.now();
        TalentProfileSyncLog log = new TalentProfileSyncLog();
        log.setId(id);
        log.setTalentId(talentId);
        log.setInputValue("douyin-id");
        log.setProviderCode("manual");
        log.setSyncStatus("SUCCESS");
        log.setFetchedFields(List.of("nickname", "avatarUrl"));
        log.setUnsupportedFields(List.of("secUid"));
        log.setRawPayload(Map.of("nickname", "达人"));
        log.setErrorCode("E_NONE");
        log.setErrorMessage("none");
        log.setStartedAt(startedAt);
        log.setFinishedAt(finishedAt);

        assertThat(log.getId()).isEqualTo(id);
        assertThat(log.getTalentId()).isEqualTo(talentId);
        assertThat(log.getInputValue()).isEqualTo("douyin-id");
        assertThat(log.getProviderCode()).isEqualTo("manual");
        assertThat(log.getSyncStatus()).isEqualTo("SUCCESS");
        assertThat(log.getFetchedFields()).containsExactly("nickname", "avatarUrl");
        assertThat(log.getUnsupportedFields()).containsExactly("secUid");
        assertThat(log.getRawPayload()).containsEntry("nickname", "达人");
        assertThat(log.getErrorCode()).isEqualTo("E_NONE");
        assertThat(log.getErrorMessage()).isEqualTo("none");
        assertThat(log.getStartedAt()).isEqualTo(startedAt);
        assertThat(log.getFinishedAt()).isEqualTo(finishedAt);
    }
}
