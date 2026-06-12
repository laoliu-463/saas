package com.colonel.saas.domain.sample.policy;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 寄样资格评估领域策略，纯业务规则，不依赖任何基础设施组件。
 *
 * <p>包含达人等级归一化、等级比较、资格判定逻辑。
 * 由 {@code SampleEligibilityService}（应用层）负责组装基础设施数据后委托给本策略执行。</p>
 *
 * <p><b>业务领域：</b>寄样域 — 资格评估策略</p>
 *
 * @see com.colonel.saas.service.SampleEligibilityService
 */
public class SampleEligibilityPolicy {

    /**
     * 评估达人是否满足寄样申请的默认标准。
     *
     * <p>对比标准配置中的最低销售额和最低等级与达人实际值，逐项判定并收集不达标原因。
     * unsupported（未同步）字段不做硬性拒绝，但提示需要填写申请原因。</p>
     *
     * @param min30DaySales    标准要求的最低 30 天销售额，null 表示不限
     * @param minLevel         标准要求的最低达人等级，null/空 表示不限
     * @param standardRaw      标准原始 JSON Map，用于快照
     * @param monthlySales     达人实际 30 天销售额，null 表示未同步
     * @param level            达人实际等级（已归一化），null 表示未同步
     * @param salesUnsupported 销售额字段是否标记为 unsupported
     * @param levelUnsupported 等级字段是否标记为 unsupported
     * @return 资格评估结果
     */
    public EligibilityResult evaluate(
            Long min30DaySales,
            String minLevel,
            Map<String, Object> standardRaw,
            Long monthlySales,
            String level,
            boolean salesUnsupported,
            boolean levelUnsupported) {

        List<String> reasons = new ArrayList<>();

        if (min30DaySales != null) {
            if (salesUnsupported) {
                reasons.add("近30天销售额未同步，请填写申请原因");
            } else if (monthlySales != null && monthlySales < min30DaySales) {
                reasons.add("近30天销售额未达到 " + min30DaySales);
            }
        }

        if (StringUtils.hasText(minLevel)) {
            if (levelUnsupported) {
                reasons.add("达人等级未同步，请填写申请原因");
            } else if (compareLevel(level, minLevel) < 0) {
                reasons.add("达人等级未达到 " + minLevel);
            }
        }

        return new EligibilityResult(
                reasons.isEmpty(),
                reasons,
                new SampleDefaultStandard(min30DaySales, minLevel, standardRaw),
                new TalentSnapshot(monthlySales, level)
        );
    }

    /**
     * 归一化达人等级字符串：已是 LV 格式直接返回，A/S 映射为 LV2，B 映射为 LV1，其他映射为 LV0。
     *
     * @param raw 原始等级字符串
     * @return 归一化后的 LV 格式等级
     */
    public String normalizeLevel(String raw) {
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
     * 比较两个达人等级的大小：actual >= expected 返回 >= 0，actual < expected 返回 < 0。
     */
    public int compareLevel(String actual, String expected) {
        return Integer.compare(levelRank(actual), levelRank(expected));
    }

    /**
     * 将达人等级转换为可比较的数值：LV0→0, LV1→1, LV2→2，无效格式返回 0。
     */
    public int levelRank(String level) {
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

    // ── 结果类型 ──────────────────────────────────────────────

    /** 寄样默认标准快照 */
    public record SampleDefaultStandard(Long min30DaySales, String minLevel, Map<String, Object> raw) {}

    /** 达人实际数据快照（销售额和等级可能为 null，表示未同步或 unsupported） */
    public record TalentSnapshot(Long monthlySales, String level) {}

    /**
     * 寄样资格评估结果。
     *
     * @param eligible 是否达标
     * @param reasons  不达标原因列表，达标时为空
     * @param standard 评估使用的标准快照
     * @param actual   达人实际数据快照
     */
    public record EligibilityResult(
            boolean eligible,
            List<String> reasons,
            SampleDefaultStandard standard,
            TalentSnapshot actual
    ) {}
}
