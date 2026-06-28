package com.colonel.saas.domain.sample.facade;

import com.colonel.saas.domain.sample.facade.dto.TalentRecentSampleDTO;
import com.colonel.saas.entity.SampleRequest;
import com.colonel.saas.mapper.SampleRequestMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * {@link SampleDomainFacade} 遗留实现：委派 {@link SampleRequestMapper}，零行为变更。
 */
@Service
public class LegacySampleDomainFacade implements SampleDomainFacade {

    private static final int SQL_IN_BATCH_SIZE = 200;

    private final SampleRequestMapper sampleRequestMapper;
    private final JdbcTemplate jdbcTemplate;

    public LegacySampleDomainFacade(SampleRequestMapper sampleRequestMapper, JdbcTemplate jdbcTemplate) {
        this.sampleRequestMapper = sampleRequestMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean existsById(UUID sampleRequestId) {
        if (sampleRequestId == null) {
            return false;
        }
        SampleRequest sample = sampleRequestMapper.selectById(sampleRequestId);
        return sample != null;
    }

    @Override
    public Map<UUID, Long> countSamplesByTalentIds(Set<UUID> talentIds) {
        if (talentIds == null || talentIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Long> result = new HashMap<>();
        for (List<UUID> batch : partition(talentIds, SQL_IN_BATCH_SIZE)) {
            if (batch.isEmpty()) {
                continue;
            }
            String placeholders = String.join(", ", java.util.Collections.nCopies(batch.size(), "?"));
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT talent_id, COUNT(1) AS total FROM sample_request " +
                            "WHERE deleted = 0 AND talent_id IS NOT NULL AND talent_id IN (" + placeholders + ") " +
                            "GROUP BY talent_id",
                    batch.toArray());
            for (Map<String, Object> row : rows) {
                UUID talentId = parseUuid(row.get("talent_id"));
                if (talentId != null) {
                    result.put(talentId, asLong(row.get("total")));
                }
            }
        }
        return result;
    }

    @Override
    public List<TalentRecentSampleDTO> listRecentSamplesByTalentId(UUID talentId, int limit) {
        if (talentId == null || limit <= 0) {
            return List.of();
        }
        return jdbcTemplate.queryForList("""
                SELECT
                    sr.id,
                    sr.request_no,
                    sr.status,
                    sr.create_time,
                    sr.complete_time,
                    p.name AS product_name
                FROM sample_request sr
                LEFT JOIN product p ON p.id = sr.product_id
                WHERE sr.deleted = 0
                  AND sr.talent_id = ?
                ORDER BY sr.create_time DESC
                LIMIT ?
                """, talentId, limit).stream()
                .map(this::toTalentRecentSample)
                .toList();
    }

    private TalentRecentSampleDTO toTalentRecentSample(Map<String, Object> row) {
        String sampleRequestId = firstNonBlank(asText(row.get("request_no")), uuidText(row.get("id")));
        String status = sampleStatusApi(asInteger(row.get("status")));
        return new TalentRecentSampleDTO(
                sampleRequestId,
                asText(row.get("product_name")),
                status,
                sampleStatusText(status),
                toDateTime(row.get("create_time")),
                toDateTime(row.get("complete_time")));
    }

    private String sampleStatusApi(Integer status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case 1 -> "PENDING_AUDIT";
            case 2 -> "PENDING_SHIP";
            case 3, 4 -> "SHIPPED";
            case 5 -> "PENDING_TASK";
            case 6 -> "FINISHED";
            case 7 -> "REJECTED";
            case 8 -> "CLOSED";
            default -> String.valueOf(status);
        };
    }

    private String sampleStatusText(String status) {
        if (status == null || status.isBlank()) {
            return "-";
        }
        return switch (status) {
            case "PENDING_AUDIT" -> "待审核";
            case "PENDING_SHIP" -> "待发货";
            case "SHIPPED" -> "快递中";
            case "PENDING_TASK" -> "待交作业";
            case "FINISHED" -> "已完成";
            case "REJECTED" -> "已拒绝";
            case "CLOSED" -> "已关闭";
            default -> status;
        };
    }

    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    private String asText(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String uuidText(Object value) {
        if (value instanceof UUID uuid) {
            return uuid.toString();
        }
        return asText(value);
    }

    private Integer asInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private LocalDateTime toDateTime(Object value) {
        if (value instanceof LocalDateTime dateTime) {
            return dateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        return null;
    }

    private List<UUID> partitionSource(Collection<UUID> values) {
        return values.stream()
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private List<List<UUID>> partition(Collection<UUID> values, int batchSize) {
        List<UUID> list = partitionSource(values);
        List<List<UUID>> partitions = new ArrayList<>();
        for (int index = 0; index < list.size(); index += batchSize) {
            partitions.add(list.subList(index, Math.min(index + batchSize, list.size())));
        }
        return partitions;
    }

    private UUID parseUuid(Object value) {
        if (value instanceof UUID uuid) {
            return uuid;
        }
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(String.valueOf(value));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }
}
