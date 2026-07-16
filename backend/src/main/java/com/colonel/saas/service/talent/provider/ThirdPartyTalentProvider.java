package com.colonel.saas.service.talent.provider;

import com.colonel.saas.common.enums.TalentDataSource;
import com.colonel.saas.entity.Talent;
import com.colonel.saas.service.talent.TalentDataProvider;
import com.colonel.saas.service.talent.TalentEnrichContext;
import com.colonel.saas.service.talent.TalentEnrichResult;
import com.colonel.saas.service.talent.TalentInputParser;
import com.colonel.saas.service.talent.profile.TalentProfileQuery;
import com.colonel.saas.service.talent.profile.TalentProfileResult;
import com.colonel.saas.service.talent.profile.provider.ConfigurableHttpTalentProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 第三方 API 数据源 —— 达人补全 Provider。
 *
 * <p>在"真实模式"（{@code talent.enrich.mode=real}，默认值）下由 Spring 自动装配；
 * 当模式切换为 {@code test} 时本 Bean 不会注册。</p>
 *
 * <ul>
 *   <li><b>职责</b>：复用可配置 HTTP 达人资料提供者，对接第三方达人数据 API（如飞瓜、蝉妈妈等）</li>
 *   <li><b>优先级</b>：priority = 10，最高优先级的真实数据源，优先于内部业务(20)和手动数据(90)</li>
 *   <li><b>数据源标识</b>：{@link TalentDataSource#THIRD_PARTY}</li>
 *   <li><b>架构角色</b>：策略模式中的一个具体策略，由 {@link com.colonel.saas.service.talent.TalentEnrichOrchestrator} 统一调度</li>
 *   <li><b>业务域</b>：达人域 → 数据补全子领域</li>
 *   <li><b>访问控制</b>：仅内部服务调用，不对外暴露</li>
 * </ul>
 *
 * @see com.colonel.saas.service.talent.TalentDataProvider  策略接口
 * @see com.colonel.saas.service.talent.TalentEnrichOrchestrator  编排器
 * @see TalentDataSource#THIRD_PARTY  数据源枚举值
 */
@Component
@Order(20)
@ConditionalOnProperty(prefix = "talent.enrich", name = "mode", havingValue = "real", matchIfMissing = true)
public class ThirdPartyTalentProvider implements TalentDataProvider {

    private final ConfigurableHttpTalentProvider httpProvider;

    @Autowired
    public ThirdPartyTalentProvider(ConfigurableHttpTalentProvider httpProvider) {
        this.httpProvider = httpProvider;
    }

    /**
     * 测试/手动构造兜底构造器。无 HTTP Provider 时本策略显式不可用。
     */
    public ThirdPartyTalentProvider() {
        this(null);
    }

    /**
     * 返回本 Provider 对应的数据源标识。
     *
     * @return {@link TalentDataSource#THIRD_PARTY}，用于字段来源追踪
     */
    @Override
    public TalentDataSource source() {
        return TalentDataSource.THIRD_PARTY;
    }

    /**
     * 返回本 Provider 的调度优先级。
     *
     * <p>值越小越优先。本 Provider 优先级为 10，
     * 是真实模式下优先级最高的数据源。</p>
     *
     * @return 优先级数值 10
     */
    @Override
    public int priority() {
        return 10;
    }

    /**
     * 判断当前上下文是否满足本 Provider 的执行条件。
     *
     * @param context 达人补全上下文，包含待补全的 {@code Talent} 实体
     * @return {@code true} 当上下文和达人实体均非空时
     */
    @Override
    public boolean supports(TalentEnrichContext context) {
        TalentProfileQuery query = toProfileQuery(context);
        return httpProvider != null
                && query != null
                && httpProvider.supports(query);
    }

    /**
     * 执行达人数据补全。
     *
     * <p>通过 {@link ConfigurableHttpTalentProvider} 调用已配置的第三方 HTTP API，
     * 并将资料采集结果映射为旧 Enrich 编排器支持的字段名。</p>
     *
     * @param context 达人补全上下文
     * @return 第三方资料结果；未配置或无真实资料时返回空结果和明确原因
     */
    @Override
    public TalentEnrichResult enrich(TalentEnrichContext context) {
        TalentProfileQuery query = toProfileQuery(context);
        if (httpProvider == null || query == null || !httpProvider.supports(query)) {
            return TalentEnrichResult.empty(source(), "third party provider is not configured");
        }
        TalentProfileResult profile = httpProvider.fetch(query);
        if (profile == null || !profile.hasRealProfileData()) {
            String message = profile == null
                    ? "third party provider returned no response"
                    : "third party provider returned no profile data: " + profile.getErrorMessage();
            return TalentEnrichResult.empty(source(), message);
        }
        Map<String, Object> fields = toEnrichFields(profile);
        if (fields.isEmpty()) {
            return TalentEnrichResult.empty(source(), "third party provider returned unsupported profile fields");
        }
        String providerCode = StringUtils.hasText(profile.getProviderCode())
                ? profile.getProviderCode()
                : "configurable_http";
        return TalentEnrichResult.of(source(), fields, "third party provider " + providerCode + " returned profile data");
    }

    private TalentProfileQuery toProfileQuery(TalentEnrichContext context) {
        if (context == null || context.talent() == null) {
            return null;
        }
        Talent talent = context.talent();
        String input = firstText(
                talent.getProfileUrl(),
                talent.getDouyinNo(),
                talent.getSecUid(),
                talent.getUid(),
                talent.getDouyinUid(),
                talent.getTalentUid());
        if (!StringUtils.hasText(input)) {
            return null;
        }
        return TalentProfileQuery.builder()
                .input(input.trim())
                .forceRefresh(context.forceRefresh())
                .talentId(talent.getId())
                .manualFill(false)
                .parsed(TalentInputParser.parse(input))
                .build();
    }

    private Map<String, Object> toEnrichFields(TalentProfileResult profile) {
        Map<String, Object> fields = new LinkedHashMap<>();
        putIfText(fields, "nickname", profile.getNickname());
        putIfText(fields, "avatarUrl", profile.getAvatarUrl());
        putIfPresent(fields, "fans", profile.getFansCount());
        putIfPresent(fields, "likesCount", profile.getLikeCount());
        putIfPresent(fields, "followingCount", profile.getFollowingCount());
        putIfPresent(fields, "worksCount", profile.getWorksCount());
        putIfText(fields, "ipLocation", profile.getIpLocation());
        putIfText(fields, "talentLevel", profile.getTalentLevel());
        putIfPresent(fields, "sales30d", profile.getSales30d());
        return fields;
    }

    private void putIfText(Map<String, Object> fields, String key, String value) {
        if (StringUtils.hasText(value)) {
            fields.put(key, value);
        }
    }

    private void putIfPresent(Map<String, Object> fields, String key, Object value) {
        if (value != null) {
            fields.put(key, value);
        }
    }

    private String firstText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }
}
