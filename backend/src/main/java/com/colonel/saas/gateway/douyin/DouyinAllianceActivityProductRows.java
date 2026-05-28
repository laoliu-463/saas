package com.colonel.saas.gateway.douyin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 精选联盟活动商品列表（{@code alliance.colonelActivityProduct}）响应载荷解析工具类。
 *
 * <p>功能描述：提供从抖音精选联盟 API 响应的 {@code data} 节点中提取商品行列表的通用能力。
 * 抖音不同接口版本可能将列表数据放在 {@code "data"} 或 {@code "list"} 字段下，
 * 本工具类按优先级依次尝试这两个 key，返回兼容的列表结果。</p>
 *
 * <p>环境说明：所有方法均为纯数据解析，不依赖特定环境（real/test/mock），可被任意 Gateway 实现复用。</p>
 *
 * <p>所属业务领域：商品域 / 抖音精选联盟 API 适配层</p>
 *
 * @see com.colonel.saas.gateway.douyin.DouyinProductGateway
 * @see com.colonel.saas.gateway.douyin.DouyinActivityGateway
 */
public final class DouyinAllianceActivityProductRows {

    /** 工具类禁止实例化 */
    private DouyinAllianceActivityProductRows() {
    }

    /**
     * 从 API 响应的 {@code data} 节点中提取商品行列表。
     *
     * <p>处理流程：
     * <ol>
     *   <li>如果传入的 {@code dataNode} 为 null 或空，直接返回空列表</li>
     *   <li>优先从 {@code "data"} 字段提取列表</li>
     *   <li>若 {@code "data"} 为空或不存在，降级尝试 {@code "list"} 字段</li>
     *   <li>返回提取到的 Map 列表，每个 Map 代表一行商品数据</li>
     * </ol>
     *
     * @param dataNode 抖音 API 响应中的 {@code data} 节点（外层已解析的 Map）
     * @return 商品行列表；若无有效数据则返回空的不可变列表
     */
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> extract(Map<String, Object> dataNode) {
        if (dataNode == null || dataNode.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> rows = castListMap(dataNode.get("data"));
        if (rows.isEmpty()) {
            rows = castListMap(dataNode.get("list"));
        }
        return rows;
    }

    /**
     * 将原始对象安全地转换为 {@code List<Map<String, Object>>}。
     *
     * <p>处理流程：
     * <ol>
     *   <li>若 value 不是 {@link Iterable}，返回空列表</li>
     *   <li>遍历 Iterable，仅保留 {@link Map} 类型的元素</li>
     *   <li>非 Map 元素会被静默跳过（防御性编程）</li>
     * </ol>
     *
     * @param value 待转换的原始对象（通常来自 JSON 反序列化）
     * @return 转换后的 Map 列表；非可迭代对象返回空列表
     */
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> castListMap(Object value) {
        if (!(value instanceof Iterable<?> iterable)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object element : iterable) {
            if (element instanceof Map<?, ?> map) {
                result.add((Map<String, Object>) map);
            }
        }
        return result;
    }
}
