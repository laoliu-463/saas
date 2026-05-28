package com.colonel.saas.service;

import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 归属源数据标准化工具类。
 *
 * <p>职责：将不同来源的订单数据（colonel_order_info / colonelOrderInfo 等嵌套结构）
 * 扁平化为统一的顶层字段，确保归属服务能以一致的方式读取团长信息。
 *
 * <p>支持处理：
 * <ul>
 *   <li>snake_case 风格的嵌套字段（colonel_order_info、colonel_order_info_second）</li>
 *   <li>camelCase 风格的嵌套字段（colonelOrderInfo、colonelOrderInfoSecond）</li>
 *   <li>将嵌套 Map 中的 colonel_buyin_id / colonelBuyinId 和 activity_id / activityId 提取到顶层</li>
 * </ul>
 *
 * <p>设计为无状态工具类，所有方法均为静态方法，不可实例化。
 */
public final class AttributionSourceNormalizer {

    private AttributionSourceNormalizer() {
    }

    /**
     * 标准化归属源数据。
     * 将嵌套的团长信息字段扁平化到顶层 Map 中。
     *
     * <p>处理逻辑：
     * <ol>
     *   <li>从 colonel_order_info 中提取主团长信息到 colonel_buyin_id / colonel_activity_id</li>
     *   <li>从 colonel_order_info_second 中提取副团长信息到 second_colonel_buyin_id / second_colonel_activity_id</li>
     *   <li>同样处理 camelCase 风格的嵌套键</li>
     *   <li>仅在目标字段为空时写入（putIfMissing），避免覆盖已有值</li>
     * </ol>
     *
     * @param source 原始订单数据 Map，可能包含嵌套结构
     * @return 标准化后的 Map，嵌套的团长信息已扁平化；若输入为 null 或空则返回空 Map
     */
    public static Map<String, Object> normalize(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> normalized = new LinkedHashMap<>(source);
        flattenColonelInfo(
                normalized,
                asMap(source.get("colonel_order_info")),
                "colonel_buyin_id",
                "colonel_activity_id"
        );
        flattenColonelInfo(
                normalized,
                asMap(source.get("colonel_order_info_second")),
                "second_colonel_buyin_id",
                "second_colonel_activity_id"
        );
        flattenColonelInfo(
                normalized,
                asMap(source.get("colonelOrderInfo")),
                "colonel_buyin_id",
                "colonel_activity_id"
        );
        flattenColonelInfo(
                normalized,
                asMap(source.get("colonelOrderInfoSecond")),
                "second_colonel_buyin_id",
                "second_colonel_activity_id"
        );
        return normalized;
    }

    /**
     * 将嵌套的团长信息 Map 扁平化到目标 Map 中。
     *
     * @param target         目标 Map，扁平化后的字段将写入此 Map
     * @param colonelInfo    嵌套的团长信息 Map
     * @param colonelBuyinKey 目标 Map 中团长 buyin_id 的键名
     * @param activityKey    目标 Map 中活动ID的键名
     */
    private static void flattenColonelInfo(
            Map<String, Object> target,
            Map<String, Object> colonelInfo,
            String colonelBuyinKey,
            String activityKey) {
        if (colonelInfo.isEmpty()) {
            return;
        }
        putIfMissing(target, colonelBuyinKey, pick(colonelInfo, "colonel_buyin_id", "colonelBuyinId"));
        putIfMissing(target, activityKey, pick(colonelInfo, "activity_id", "activityId"));
    }

    private static void putIfMissing(Map<String, Object> target, String key, Object value) {
        if (!StringUtils.hasText(key) || value == null) {
            return;
        }
        Object existing = target.get(key);
        if (!StringUtils.hasText(existing == null ? null : String.valueOf(existing))) {
            target.put(key, value);
        }
    }

    private static Object pick(Map<String, Object> source, String... keys) {
        if (source == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (source.containsKey(key)) {
                return source.get(key);
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        if (!(value instanceof Map<?, ?> raw)) {
            return Map.of();
        }
        Map<String, Object> converted = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (entry.getKey() != null) {
                converted.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return converted;
    }
}
