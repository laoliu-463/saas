package com.colonel.saas.service.talent.provider;

import com.colonel.saas.common.enums.TalentDataSource;
import com.colonel.saas.entity.Talent;
import com.colonel.saas.service.talent.TalentDataProvider;
import com.colonel.saas.service.talent.TalentEnrichContext;
import com.colonel.saas.service.talent.TalentEnrichResult;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 手动录入数据源 —— 达人补全 Provider（兜底实现）。
 *
 * <p>无 {@code @ConditionalOnProperty} 限制，在任何模式下都会注册。
 * 优先级最低（90），作为所有外部数据源都无数据时的兜底回退。</p>
 *
 * <ul>
 *   <li><b>职责</b>：读取 {@code Talent} 实体中已有的手动录入字段，作为补全结果返回</li>
 *   <li><b>优先级</b>：priority = 90，最低优先级，仅当高优先级 Provider 均未返回数据时才生效</li>
 *   <li><b>数据源标识</b>：{@link TalentDataSource#MANUAL}</li>
 *   <li><b>架构角色</b>：策略模式中的兜底策略，由 {@link com.colonel.saas.service.talent.TalentEnrichOrchestrator} 统一调度</li>
 *   <li><b>业务域</b>：达人域 → 数据补全子领域</li>
 *   <li><b>访问控制</b>：仅内部服务调用，不对外暴露</li>
 * </ul>
 *
 * <p>提取的字段范围：nickname、avatarUrl、fans、likesCount、followingCount、worksCount、ipLocation。
 * 只有非空字段才会被放入结果 Map；若全部为空则返回 {@link TalentEnrichResult#empty}。</p>
 *
 * @see com.colonel.saas.service.talent.TalentDataProvider  策略接口
 * @see com.colonel.saas.service.talent.TalentEnrichOrchestrator  编排器
 * @see TalentDataSource#MANUAL  数据源枚举值
 */
@Component
@Order(90)
public class ManualTalentProvider implements TalentDataProvider {

    /**
     * 返回本 Provider 对应的数据源标识。
     *
     * @return {@link TalentDataSource#MANUAL}，表示数据来自手动录入
     */
    @Override
    public TalentDataSource source() {
        return TalentDataSource.MANUAL;
    }

    /**
     * 返回本 Provider 的调度优先级。
     *
     * <p>值越小越优先。本 Provider 优先级为 90，是所有 Provider 中最低的，
     * 仅在高优先级数据源均无数据时才被使用。</p>
     *
     * @return 优先级数值 90
     */
    @Override
    public int priority() {
        return 90;
    }

    /**
     * 判断当前上下文是否满足本 Provider 的执行条件。
     *
     * @param context 达人补全上下文，包含待补全的 {@code Talent} 实体
     * @return {@code true} 当上下文和达人实体均非空时
     */
    @Override
    public boolean supports(TalentEnrichContext context) {
        return context != null && context.talent() != null;
    }

    /**
     * 从已有 Talent 实体中提取手动录入的字段作为补全结果。
     *
     * <p>处理流程：</p>
     * <ol>
     *   <li>从上下文中获取 {@code Talent} 实体</li>
     *   <li>依次检查 7 个字段（nickname、avatarUrl、fans、likesCount、followingCount、worksCount、ipLocation）</li>
     *   <li>仅非空字段（字符串类型使用 {@code StringUtils.hasText} 判定，数值类型检查 null）放入结果 Map</li>
     *   <li>若结果 Map 为空，返回 {@link TalentEnrichResult#empty}；否则返回包含所有非空字段的结果</li>
     * </ol>
     *
     * @param context 达人补全上下文
     * @return 补全结果：包含手动录入的非空字段，或空结果
     */
    @Override
    public TalentEnrichResult enrich(TalentEnrichContext context) {
        // 第一步：从上下文中获取达人实体
        Talent talent = context.talent();

        // 第二步：逐字段检查，仅非空字段放入结果 Map
        Map<String, Object> fields = new LinkedHashMap<>();
        if (StringUtils.hasText(talent.getNickname())) {
            fields.put("nickname", talent.getNickname().trim());
        }
        if (StringUtils.hasText(talent.getAvatarUrl())) {
            fields.put("avatarUrl", talent.getAvatarUrl().trim());
        }
        if (talent.getFans() != null) {
            fields.put("fans", talent.getFans());
        }
        if (talent.getLikesCount() != null) {
            fields.put("likesCount", talent.getLikesCount());
        }
        if (talent.getFollowingCount() != null) {
            fields.put("followingCount", talent.getFollowingCount());
        }
        if (talent.getWorksCount() != null) {
            fields.put("worksCount", talent.getWorksCount());
        }
        if (StringUtils.hasText(talent.getIpLocation())) {
            fields.put("ipLocation", talent.getIpLocation().trim());
        }

        // 第三步：根据结果 Map 是否为空决定返回值
        if (fields.isEmpty()) {
            return TalentEnrichResult.empty(source(), "manual data is empty");
        }
        return TalentEnrichResult.of(source(), fields, "manual data merged");
    }
}
