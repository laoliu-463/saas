package com.colonel.saas.service;

import com.colonel.saas.domain.config.facade.ConfigDomainFacade;
import com.colonel.saas.domain.config.facade.dto.SampleDefaultStandardDTO;
import com.colonel.saas.domain.sample.policy.SampleEligibilityPolicy;
import com.colonel.saas.entity.CrawlerTalentInfo;
import com.colonel.saas.entity.Talent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * 寄样资格评估服务，判断达人是否满足寄样申请的默认标准（近 30 天销售额 + 达人等级）。
 *
 * <p>核心逻辑由 {@link SampleEligibilityPolicy} 负责（DDD-SAMPLE-002 抽离的纯业务规则），
 * 本服务仅承担 IO 装配：加载默认标准、读取达人实体、必要时通过 JDBC 聚合近 30 天销售额。</p>
 *
 * <ul>
 *   <li>提供 {@link #evaluate} 对达人进行寄样资格评估，返回达标状态和详细原因</li>
 *   <li>支持字段标记为 unsupported（未同步）时的降级处理，不达标但提示"请填写申请原因"</li>
 *   <li>近 30 天销售额优先从 Talent.sales30d 读取，无值时回退到 colonelsettlement_order 实时聚合</li>
 *   <li>达人等级支持 LV 格式和 A/S/B 字母格式，统一归一化后按 LV0 &lt; LV1 &lt; LV2 排序比较</li>
 * </ul>
 *
 * <p><b>业务领域：</b>寄样域 — 资格评估</p>
 * <p><b>协作关系：</b>依赖 {@link ConfigDomainFacade} 加载寄样默认标准配置（DDD-CONFIG-003）；
 * 依赖 {@link JdbcTemplate} 实时聚合近 30 天订单销售额；
 * 规则判断委派给 {@link SampleEligibilityPolicy}。</p>
 *
 * @see ConfigDomainFacade
 * @see SampleEligibilityPolicy
 * @see SampleLifecycleService
 */
@Service
public class SampleEligibilityService {

    /** 配置域门面，加载寄样默认标准 */
    private final ConfigDomainFacade configDomainFacade;

    /** JDBC 模板，用于实时聚合近 30 天订单销售额 */
    private final JdbcTemplate jdbcTemplate;

    public SampleEligibilityService(
            ConfigDomainFacade configDomainFacade,
            JdbcTemplate jdbcTemplate) {
        this.configDomainFacade = configDomainFacade;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 评估达人是否满足寄样申请的默认标准。
     *
     * <ol>
     *   <li>第一步：加载寄样默认标准配置（最低 30 天销售额、最低达人等级）</li>
     *   <li>第二步：检查各字段是否标记为 unsupported（未同步）</li>
     *   <li>第三步：解析实际销售额和达人等级</li>
     *   <li>第四步：委派 {@link SampleEligibilityPolicy} 评估，逐项比较，不达标则收集原因</li>
     * </ol>
     *
     * @param talent     达人实体，可能为 null
     * @param talentInfo 爬虫达人信息，可能为 null
     * @return 资格评估结果，包含是否达标、不达标原因、标准快照和达人实际快照
     */
    public EligibilityResult evaluate(Talent talent, CrawlerTalentInfo talentInfo) {
        SampleDefaultStandardDTO standard = configDomainFacade.getSampleRules().defaultStandard();
        boolean salesUnsupported = SampleEligibilityPolicy.isUnsupported(talent, "sales30d");
        boolean levelUnsupported = SampleEligibilityPolicy.isUnsupported(talent, "talentLevel");
        Long monthlySales = resolveMonthlySales(talent, talentInfo, salesUnsupported);
        String level = resolveLevel(talent, talentInfo, levelUnsupported);

        SampleEligibilityPolicy.Outcome outcome = SampleEligibilityPolicy.evaluate(
                standard, talent, talentInfo, monthlySales, level);

        return new EligibilityResult(
                outcome.eligible(),
                outcome.reasons(),
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
            return SampleEligibilityPolicy.normalizeLevel(talent.getTalentLevel());
        }
        String raw = talent == null ? null : talent.getLevel();
        if (StringUtils.hasText(raw)) {
            return SampleEligibilityPolicy.normalizeLevel(raw);
        }
        return null;
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
