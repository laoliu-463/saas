package com.colonel.saas.service;

import com.colonel.saas.domain.config.facade.ConfigDomainFacade;
import com.colonel.saas.domain.config.facade.dto.SampleDefaultStandardDTO;
import com.colonel.saas.domain.sample.policy.SampleEligibilityPolicy;
import com.colonel.saas.entity.CrawlerTalentInfo;
import com.colonel.saas.entity.Talent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 寄样资格评估应用服务（Application Service 薄代理）。
 *
 * <p>负责基础设施调用（配置加载、JdbcTemplate 查询），组装领域数据后
 * 委托给 {@link SampleEligibilityPolicy} 执行纯业务规则判定。</p>
 *
 * <p><b>DDD 重构说明（DDD-SAMPLE-002）：</b>核心评估逻辑（等级归一化、等级比较、
 * 达标判定）已迁移至 {@link SampleEligibilityPolicy}（领域策略层）。
 * 本类保留对外签名不变，确保调用方（SampleApplicationService）零改动。</p>
 *
 * @see SampleEligibilityPolicy
 * @see ConfigDomainFacade
 */
@Service
public class SampleEligibilityService {

    /** 配置域门面，加载寄样默认标准 */
    private final ConfigDomainFacade configDomainFacade;

    /** JDBC 模板，用于实时聚合近 30 天订单销售额 */
    private final JdbcTemplate jdbcTemplate;

    /** 寄样资格评估领域策略（纯业务规则） */
    private final SampleEligibilityPolicy eligibilityPolicy;

    public SampleEligibilityService(
            ConfigDomainFacade configDomainFacade,
            JdbcTemplate jdbcTemplate) {
        this.configDomainFacade = configDomainFacade;
        this.jdbcTemplate = jdbcTemplate;
        this.eligibilityPolicy = new SampleEligibilityPolicy();
    }

    /**
     * 评估达人是否满足寄样申请的默认标准。
     *
     * <ol>
     *   <li>第一步：加载寄样默认标准配置（最低 30 天销售额、最低达人等级）</li>
     *   <li>第二步：检查各字段是否标记为 unsupported（未同步）</li>
     *   <li>第三步：解析实际销售额和达人等级</li>
     *   <li>第四步：委托 {@link SampleEligibilityPolicy#evaluate} 执行核心判定</li>
     * </ol>
     *
     * @param talent     达人实体，可能为 null
     * @param talentInfo 爬虫达人信息，可能为 null
     * @return 资格评估结果，包含是否达标、不达标原因、标准快照和达人实际快照
     */
    public EligibilityResult evaluate(Talent talent, CrawlerTalentInfo talentInfo) {
        SampleDefaultStandardDTO standard = configDomainFacade.getSampleRules().defaultStandard();
        boolean salesUnsupported = isUnsupported(talent, "sales30d");
        boolean levelUnsupported = isUnsupported(talent, "talentLevel");
        Long monthlySales = resolveMonthlySales(talent, talentInfo, salesUnsupported);
        String level = resolveLevel(talent, talentInfo, levelUnsupported);

        // 委托领域策略执行核心判定
        SampleEligibilityPolicy.EligibilityResult policyResult = eligibilityPolicy.evaluate(
                standard.min30DaySales(),
                standard.minLevel(),
                standard.raw(),
                monthlySales,
                level,
                salesUnsupported,
                levelUnsupported);

        // 转换为应用层结果类型（保持外部调用方兼容）
        return new EligibilityResult(
                policyResult.eligible(),
                policyResult.reasons(),
                new SampleDefaultStandard(
                        policyResult.standard().min30DaySales(),
                        policyResult.standard().minLevel(),
                        policyResult.standard().raw()),
                new TalentSnapshot(
                        policyResult.actual().monthlySales(),
                        policyResult.actual().level())
        );
    }

    /** 将不达标原因映射为规则编码，供 extra.eligibilityCheck.failedRules 使用。 */
    public java.util.List<String> classifyFailureRules(java.util.List<String> reasons) {
        return eligibilityPolicy.classifyFailureRules(reasons);
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
     * 解析达人等级：优先读取 Talent.talentLevel，无值时回退到 Talent.level，
     * 委托 {@link SampleEligibilityPolicy#normalizeLevel} 统一归一化为 LV 格式。
     * 字段标记为 unsupported 时返回 null。
     */
    private String resolveLevel(Talent talent, CrawlerTalentInfo talentInfo, boolean levelUnsupported) {
        if (levelUnsupported) {
            return null;
        }
        if (talent != null && StringUtils.hasText(talent.getTalentLevel())) {
            return eligibilityPolicy.normalizeLevel(talent.getTalentLevel());
        }
        String raw = talent == null ? null : talent.getLevel();
        if (StringUtils.hasText(raw)) {
            return eligibilityPolicy.normalizeLevel(raw);
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

    // ── 结果类型（保持外部兼容） ──────────────────────────────────

    /** 寄样默认标准快照 */
    public record SampleDefaultStandard(Long min30DaySales, String minLevel, java.util.Map<String, Object> raw) {}

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
            java.util.List<String> reasons,
            SampleDefaultStandard standard,
            TalentSnapshot actual
    ) {}
}
