package com.colonel.saas.domain.sample.facade;

import com.colonel.saas.domain.sample.facade.dto.TalentRecentSampleDTO;
import com.colonel.saas.entity.SampleRequest;
import com.colonel.saas.mapper.SampleRequestMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LegacySampleDomainFacadeTest {

    @Mock
    private SampleRequestMapper sampleRequestMapper;
    @Mock
    private JdbcTemplate jdbcTemplate;

    private SampleDomainFacade facade;

    @BeforeEach
    void setUp() {
        facade = new LegacySampleDomainFacade(sampleRequestMapper, jdbcTemplate);
    }

    @Test
    void existsById_shouldReturnTrueWhenSampleFound() {
        UUID id = UUID.randomUUID();
        when(sampleRequestMapper.selectById(id)).thenReturn(new SampleRequest());

        assertThat(facade.existsById(id)).isTrue();
    }

    @Test
    void existsById_shouldReturnFalseWhenMissing() {
        UUID id = UUID.randomUUID();
        when(sampleRequestMapper.selectById(id)).thenReturn(null);

        assertThat(facade.existsById(id)).isFalse();
        assertThat(facade.existsById(null)).isFalse();
    }

    @Test
    void countSamplesByTalentIds_shouldAggregateByTalentId() {
        UUID talentId = UUID.randomUUID();
        when(jdbcTemplate.queryForList(
                argThat(sql -> sql != null && sql.contains("FROM sample_request") && sql.contains("GROUP BY talent_id")),
                any(Object[].class)))
                .thenReturn(List.of(Map.of("talent_id", talentId, "total", 3L)));

        assertThat(facade.countSamplesByTalentIds(java.util.Set.of(talentId)))
                .containsEntry(talentId, 3L);
    }

    @Test
    void countSamplesByTalentIdSince_shouldKeepLegacyMonthlySampleScope() {
        UUID talentId = UUID.randomUUID();
        LocalDateTime since = LocalDateTime.of(2026, 6, 1, 0, 0);
        when(jdbcTemplate.queryForObject(
                argThat(sql -> sql != null
                        && sql.contains("FROM sample_request")
                        && sql.contains("deleted = 0")
                        && sql.contains("talent_id = ?")
                        && sql.contains("create_time >= ?")),
                eq(Long.class),
                eq(talentId),
                eq(Timestamp.valueOf(since))))
                .thenReturn(4L);

        assertThat(facade.countSamplesByTalentIdSince(talentId, since)).isEqualTo(4L);
        assertThat(facade.countSamplesByTalentIdSince(null, since)).isZero();
        assertThat(facade.countSamplesByTalentIdSince(talentId, null)).isZero();
    }

    @Test
    void listRecentSamplesByTalentId_shouldPreserveLegacyStatusMapping() {
        UUID talentId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", UUID.randomUUID());
        row.put("request_no", "SAMPLE-REJECTED");
        row.put("status", 7);
        row.put("create_time", Timestamp.valueOf(now.minusDays(1)));
        row.put("complete_time", null);
        row.put("product_name", "样例商品");

        when(jdbcTemplate.queryForList(
                argThat(sql -> sql != null && sql.contains("FROM sample_request sr") && sql.contains("LIMIT ?")),
                eq(talentId),
                eq(20)))
                .thenReturn(List.of(row));

        List<TalentRecentSampleDTO> samples = facade.listRecentSamplesByTalentId(talentId, 20);

        assertThat(samples).hasSize(1);
        assertThat(samples.get(0).sampleRequestId()).isEqualTo("SAMPLE-REJECTED");
        assertThat(samples.get(0).status()).isEqualTo("REJECTED");
        assertThat(samples.get(0).statusText()).isEqualTo("已拒绝");
        assertThat(samples.get(0).createTime()).isEqualTo(now.minusDays(1));
    }
}
