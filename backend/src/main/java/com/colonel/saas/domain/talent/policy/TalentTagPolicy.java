package com.colonel.saas.domain.talent.policy;

import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.domain.shared.policy.DomainText;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 达人标签归一化 Policy（DDD-TALENT-003）。
 *
 * <p>从 {@code TalentService.normalizeTalentTags} 抽离的纯标签规则：
 * <ul>
 *   <li>去重并保留插入顺序（{@link LinkedHashSet}）</li>
 *   <li>trim 空白</li>
 *   <li>校验标签是否在预设库中（库非空时强制）</li>
 *   <li>限制最多 {@value #MAX_TALENT_TAGS} 个</li>
 * </ul>
 *
 * <p>无 Spring 依赖；{@code presets} 由调用方从 {@code BusinessRuleConfigService.getPresetTalentTags()}
 * 传入，便于单测和复用。</p>
 */
public final class TalentTagPolicy {

    /** 单个达人最多允许的标签数。 */
    public static final int MAX_TALENT_TAGS = 3;

    private TalentTagPolicy() {
    }

    /**
     * 归一化标签列表。
     *
     * @param tags    原始标签列表（可为 null 或空 → 返回空列表）
     * @param presets 标签预设库（若非空，则每个标签必须命中预设库；空库表示无限制）
     * @return 去重、trim 后的不可变列表（按 {@link LinkedHashSet} 插入顺序，截断到 {@value #MAX_TALENT_TAGS} 个）
     * @throws BusinessException 标签不在预设库中时抛出
     */
    public static List<String> normalize(List<String> tags, List<String> presets) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        List<String> presetSnapshot = presets == null ? List.of() : presets;
        boolean presetEnforced = !presetSnapshot.isEmpty();
        List<String> invalidTags = new ArrayList<>();

        for (String tag : tags) {
            if (!DomainText.hasText(tag)) {
                continue;
            }
            String normalized = tag.trim();
            if (presetEnforced && !presetSnapshot.contains(normalized)) {
                invalidTags.add(normalized);
                continue;
            }
            unique.add(normalized);
            if (unique.size() >= MAX_TALENT_TAGS) {
                break;
            }
        }

        if (!invalidTags.isEmpty()) {
            throw BusinessException.param("标签必须从预设库选择: " + String.join(", ", invalidTags));
        }
        return List.copyOf(unique);
    }
}
