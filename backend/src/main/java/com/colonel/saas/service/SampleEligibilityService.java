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
        long monthlySales = resolveMonthlySales(talent, talentInfo);
        String level = resolveLevel(talent, talentInfo);

        List<String> reasons = new ArrayList<>();
        if (standard.min30DaySales() != null && monthlySales < standard.min30DaySales()) {
            reasons.add("近30天销售额未达到 " + standard.min30DaySales());
        }
        if (StringUtils.hasText(standard.minLevel()) && compareLevel(level, standard.minLevel()) < 0) {
            reasons.add("达人等级未达到 " + standard.minLevel());
        }

        return new EligibilityResult(
                reasons.isEmpty(),
                reasons,
                new SampleDefaultStandard(standard.min30DaySales(), standard.minLevel(), standard.raw()),
                new TalentSnapshot(monthlySales, level)
        );
    }

    private long resolveMonthlySales(Talent talent, CrawlerTalentInfo talentInfo) {
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

    private String resolveLevel(Talent talent, CrawlerTalentInfo talentInfo) {
        String raw = talent == null ? null : talent.getLevel();
        if (StringUtils.hasText(raw)) {
            return normalizeLevel(raw);
        }
        long fans = 0L;
        if (talent != null && talent.getFans() != null) {
            fans = talent.getFans();
        } else if (talentInfo != null && talentInfo.getFansCount() != null) {
            fans = talentInfo.getFansCount();
        }
        if (fans >= 300_000) return "LV2";
        if (fans >= 100_000) return "LV1";
        return "LV0";
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

    public record TalentSnapshot(Long monthlySales, String level) {}

    public record EligibilityResult(
            boolean eligible,
            List<String> reasons,
            SampleDefaultStandard standard,
            TalentSnapshot actual
    ) {}
}
