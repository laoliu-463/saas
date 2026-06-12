package com.colonel.saas.domain.sample.policy;

import com.colonel.saas.domain.config.facade.dto.SampleDefaultStandardDTO;
import com.colonel.saas.entity.CrawlerTalentInfo;
import com.colonel.saas.entity.Talent;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 寄样资格评估 Policy（DDD-SAMPLE-002）。
 *
 * <p>从 {@code SampleEligibilityService} 抽离出的纯业务规则：
 * 字段 unsupported 判定、达人等级归一化与比较、不达标原因聚合。
 * 不依赖 Spring 容器，不做 IO 调用，方便单测与复用。</p>
 *
 * <p>所有方法均为静态工具方法；IO 与配置读取由 {@code SampleEligibilityService} 负责。</p>
 */
public final class SampleEligibilityPolicy {

    private SampleEligibilityPolicy() {
    }

    /**
     * 标准 + 达人数据 + 已聚合的月度销售额 → 资格评估纯结果。
     *
     * @param standard          寄样默认标准（最低 30 天销售额、最低达人等级）
     * @param talent            达人实体（可能为 null，用于 unsupported 判定）
     * @param talentInfo        爬虫达人信息（可能为 null）
     * @param monthlySales      已聚合的近 30 天销售额（可能为 null，表示 unsupported）
     * @param level             已归一化的达人等级（可能为 null）
     * @return 不带副作用的纯评估结果
     */
    public static Outcome evaluate(
            SampleDefaultStandardDTO standard,
            Talent talent,
            CrawlerTalentInfo talentInfo,
            Long monthlySales,
            String level) {
        boolean salesUnsupported = isUnsupported(talent, "sales30d");
        boolean levelUnsupported = isUnsupported(talent, "talentLevel");

        List<String> reasons = new ArrayList<>();
        if (standard != null && standard.min30DaySales() != null) {
            if (salesUnsupported) {
                reasons.add("近30天销售额未同步，请填写申请原因");
            } else if (monthlySales != null && monthlySales < standard.min30DaySales()) {
                reasons.add("近30天销售额未达到 " + standard.min30DaySales());
            }
        }
        if (standard != null && StringUtils.hasText(standard.minLevel())) {
            if (levelUnsupported) {
                reasons.add("达人等级未同步，请填写申请原因");
            } else if (compareLevel(level, standard.minLevel()) < 0) {
                reasons.add("达人等级未达到 " + standard.minLevel());
            }
        }
        return new Outcome(reasons.isEmpty(), reasons);
    }

    /**
     * 判断指定字段是否被标记为 unsupported（未同步），通过 {@link Talent#getUnsupportedFields()} 匹配。
     * Talent 为 null 或 unsupportedFields 为空时，sales30d 和 talentLevel 默认视为 unsupported。
     */
    public static boolean isUnsupported(Talent talent, String field) {
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
                .map(String::trim)
                .anyMatch(value -> value.equalsIgnoreCase(field));
    }

    /**
     * 归一化达人等级字符串：已是 LV 格式直接返回，A/S 映射为 LV2，B 映射为 LV1，其他映射为 LV0。
     */
    public static String normalizeLevel(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "LV0";
        }
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

    /**
     * 比较两个达人等级的大小：actual &gt;= expected 返回 &gt;= 0，actual &lt; expected 返回 &lt; 0。
     */
    public static int compareLevel(String actual, String expected) {
        return Integer.compare(levelRank(actual), levelRank(expected));
    }

    /**
     * 将达人等级转换为可比较的数值：LV0→0, LV1→1, LV2→2，无效格式返回 0。
     */
    public static int levelRank(String level) {
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

    /**
     * 纯评估结果（不含 IO/快照/标准），由调用方结合自身上下文装配成最终对外结果。
     */
    public record Outcome(boolean eligible, List<String> reasons) {
    }
}
