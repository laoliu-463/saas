package com.colonel.saas.service;

import com.colonel.saas.entity.CrawlerTalentInfo;
import com.colonel.saas.entity.Talent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class SampleEligibilityService {

    private final BusinessRuleConfigService businessRuleConfigService;
    private final JdbcTemplate jdbcTemplate;

    public SampleEligibilityService(
            BusinessRuleConfigService businessRuleConfigService,
            JdbcTemplate jdbcTemplate) {
        this.businessRuleConfigService = businessRuleConfigService;
        this.jdbcTemplate = jdbcTemplate;
    }

    public EligibilityResult evaluate(Talent talent, CrawlerTalentInfo talentInfo) {
        BusinessRuleConfigService.SampleDefaultStandardConfig standard = businessRuleConfigService.getSampleDefaultStandard();
        boolean salesUnsupported = isUnsupported(talent, "sales30d");
        boolean levelUnsupported = isUnsupported(talent, "talentLevel");
        Long monthlySales = resolveMonthlySales(talent, talentInfo, salesUnsupported);
        String level = resolveLevel(talent, talentInfo, levelUnsupported);

        List<String> reasons = new ArrayList<>();
        if (standard.min30DaySales() != null) {
            if (salesUnsupported) {
                reasons.add("近30天销售额未同步，请填写申请原因");
            } else if (monthlySales != null && monthlySales < standard.min30DaySales()) {
                reasons.add("近30天销售额未达到 " + standard.min30DaySales());
            }
        }
        if (StringUtils.hasText(standard.minLevel())) {
            if (levelUnsupported) {
                reasons.add("达人等级未同步，请填写申请原因");
            } else if (compareLevel(level, standard.minLevel()) < 0) {
                reasons.add("达人等级未达到 " + standard.minLevel());
            }
        }

        return new EligibilityResult(
                reasons.isEmpty(),
                reasons,
                new SampleDefaultStandard(standard.min30DaySales(), standard.minLevel(), standard.raw()),
                new TalentSnapshot(monthlySales, level)
        );
    }

    private Long resolveMonthlySales(Talent talent, CrawlerTalentInfo talentInfo, boolean salesUnsupported) {
        if (salesUnsupported) {
            return null;
        }
        if (talent != null && talent.getSales30d() != null) {
            return talent.getSales30d();
        }
        String talentUid = talent != null && StringUtils.hasText(talent.getDouyinUid())
                ? talent.getDouyinUid()
                : (talentInfo == null ? null : talentInfo.getTalentId());
        if (!StringUtils.hasText(talentUid)) {
            return 0L;
        }
        Long value = jdbcTemplate.query("""
                SELECT COALESCE(SUM(order_amount), 0)
                FROM colonelsettlement_order
                WHERE deleted = 0
                  AND create_time >= NOW() - INTERVAL '30 day'
                  AND COALESCE(extra_data ->> 'talent_uid', extra_data ->> 'author_id', talent_name) = ?
                """, rs -> rs.next() ? rs.getLong(1) : 0L, talentUid);
        return value == null ? 0L : value;
    }

    private String resolveLevel(Talent talent, CrawlerTalentInfo talentInfo, boolean levelUnsupported) {
        if (levelUnsupported) {
            return null;
        }
        if (talent != null && StringUtils.hasText(talent.getTalentLevel())) {
            return normalizeLevel(talent.getTalentLevel());
        }
        String raw = talent == null ? null : talent.getLevel();
        if (StringUtils.hasText(raw)) {
            return normalizeLevel(raw);
        }
        return null;
    }

    private boolean isUnsupported(Talent talent, String field) {
        if (talent == null) {
            return "talentLevel".equals(field) || "sales30d".equals(field);
        }
        if (talent.getUnsupportedFields() == null) {
            return "talentLevel".equals(field) || "sales30d".equals(field);
        }
        if (talent.getUnsupportedFields().isEmpty()) {
            return false;
        }
        return talent.getUnsupportedFields().stream()
                .filter(StringUtils::hasText)
                .map(value -> value.trim())
                .anyMatch(field::equalsIgnoreCase);
    }

    private String normalizeLevel(String raw) {
        String level = raw.trim().toUpperCase();
        if (level.startsWith("LV")) {
            return level;
        }
        return switch (level) {
            case "A", "S" -> "LV2";
            case "B" -> "LV1";
            default -> "LV0";
        };
    }

    private int compareLevel(String actual, String expected) {
        return Integer.compare(levelRank(actual), levelRank(expected));
    }

    private int levelRank(String level) {
        if (!StringUtils.hasText(level)) {
            return 0;
        }
        String normalized = normalizeLevel(level);
        if (!normalized.startsWith("LV")) {
            return 0;
        }
        try {
            return Integer.parseInt(normalized.substring(2));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    public record SampleDefaultStandard(Long min30DaySales, String minLevel, Map<String, Object> raw) {}

    public record TalentSnapshot(Long monthlySales, String level) {
        public TalentSnapshot {
            // monthlySales/level may be null when provider marks field unsupported
        }
    }

    public record EligibilityResult(
            boolean eligible,
            List<String> reasons,
            SampleDefaultStandard standard,
            TalentSnapshot actual
    ) {}
}
