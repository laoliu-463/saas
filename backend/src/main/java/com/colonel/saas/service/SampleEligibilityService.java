package com.colonel.saas.service;

import com.colonel.saas.entity.CrawlerTalentInfo;
import com.colonel.saas.entity.Talent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 寄样资格评估服务，判断达人是否满足寄样申请的默认标准（近 30 天销售额 + 达人等级）。
 *
 * <p>核心逻辑：从业务规则配置加载寄样默认标准（最低 30 天销售额、最低达人等级），
 * 结合达人实体和爬虫数据解析实际值，逐项比较后输出是否达标及不达标原因列表。</p>
 *
 * <ul>
 *   <li>提供 {@link #evaluate} 对达人进行寄样资格评估，返回达标状态和详细原因</li>
 *   <li>支持字段标记为 unsupproted（未同步）时的降级处理，不达标但提示"请填写申请原因"</li>
 *   <li>近 30 天销售额优先从 Talent.sales30d 读取，无值时回退到 colonelsettlement_order 实时聚合</li>
 *   <li>达人等级支持 LV 格式和 A/S/B 字母格式，统一归一化后按 LV0 < LV1 < LV2 排序比较</li>
 * </ul>
 *
 * <p><b>业务领域：</b>寄样域 — 资格评估</p>
 * <p><b>协作关系：</b>依赖 {@link BusinessRuleConfigService} 加载寄样默认标准配置；
 * 依赖 {@link JdbcTemplate} 实时聚合近 30 天订单销售额</p>
 *
 * @see BusinessRuleConfigService
 * @see SampleLifecycleService
 */
@Service
public class SampleEligibilityService {

    /** 业务规则配置服务，加载寄样默认标准 */
    private final BusinessRuleConfigService businessRuleConfigService;

    /** JDBC 模板，用于实时聚合近 30 天订单销售额 */
    private final JdbcTemplate jdbcTemplate;

    public SampleEligibilityService(
            BusinessRuleConfigService businessRuleConfigService,
            JdbcTemplate jdbcTemplate) {
        this.businessRuleConfigService = businessRuleConfigService;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 评估达人是否满足寄样申请的默认标准。
     *
     * <ol>
     *   <li>第一步：加载寄样默认标准配置（最低 30 天销售额、最低达人等级）</li>
     *   <li>第二步：检查各字段是否标记为 unsupported（未同步）</li>
     *   <li>第三步：解析实际销售额和达人等级</li>
     *   <li>第四步：逐项比较，不达标则收集原因；unsupported 字段提示"请填写申请原因"</li>
     * </ol>
     *
     * @param talent     达人实体，可能为 null
     * @param talentInfo 爬虫达人信息，可能为 null
     * @return 资格评估结果，包含是否达标、不达标原因、标准快照和达人实际快照
     */
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

    /**
     * 解析达人近 30 天销售额：优先读取 Talent.sales30d，无值时通过 JdbcTemplate 实时聚合 colonelsettlement_order。
     * 字段标记为 unsupported 时返回 null。
     */
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

    /**
     * 解析达人等级：优先读取 Talent.talentLevel，无值时回退到 Talent.level，统一归一化为 LV 格式。
     * 字段标记为 unsupported 时返回 null。
     */
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

    /**
     * 判断指定字段是否被标记为 unsupported（未同步），通过 Talent.unsupportedFields 列表匹配。
     * Talent 为 null 或 unsupportedFields 为空时，sales30d 和 talentLevel 默认视为 unsupported。
     */
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

    /**
     * 归一化达人等级字符串：已是 LV 格式直接返回，A/S 映射为 LV2，B 映射为 LV1，其他映射为 LV0。
     */
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

    /**
     * 比较两个达人等级的大小：actual >= expected 返回 >= 0，actual < expected 返回 < 0。
     */
    private int compareLevel(String actual, String expected) {
        return Integer.compare(levelRank(actual), levelRank(expected));
    }

    /**
     * 将达人等级转换为可比较的数值：LV0→0, LV1→1, LV2→2，无效格式返回 0。
     */
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

    /** 寄样默认标准快照 */
    public record SampleDefaultStandard(Long min30DaySales, String minLevel, Map<String, Object> raw) {}

    /** 达人实际数据快照（销售额和等级可能为 null，表示未同步或 unsupported） */
    public record TalentSnapshot(Long monthlySales, String level) {
        public TalentSnapshot {
            // monthlySales/level may be null when provider marks field unsupported
        }
    }

    /**
     * 寄样资格评估结果。
     *
     * @param eligible 是否达标
     * @param reasons 不达标原因列表，达标时为空
     * @param standard 评估使用的标准快照
     * @param actual  达人实际数据快照
     */
    public record EligibilityResult(
            boolean eligible,
            List<String> reasons,
            SampleDefaultStandard standard,
            TalentSnapshot actual
    ) {}
}
