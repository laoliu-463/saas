package com.colonel.saas.common.enums;

import java.util.Arrays;

/**
 * 商品业务状态枚举，描述商品在团长业务流程中的推进阶段。
 *
 * <p>状态流转模型：</p>
 * <pre>
 * SYNCED/PENDING_AUDIT → APPROVED → ASSIGNED → BOUND → LINKED → FOLLOWING
 *                    ↘ REJECTED（审核拒绝）
 * </pre>
 *
 * <h3>状态说明</h3>
 * <ul>
 *   <li>{@link #PENDING_AUDIT} — 商品刚同步入库，等待审核（兼容外部 "SYNCED" 码）</li>
 *   <li>{@link #APPROVED} — 审核通过，商品可进入后续分配和推广流程</li>
 *   <li>{@link #REJECTED} — 审核拒绝，商品不满足准入条件</li>
 *   <li>{@link #BOUND} — 商品已绑定到具体推广活动</li>
 *   <li>{@link #ASSIGNED} — 商品已分配给招商经理跟进</li>
 *   <li>{@link #LINKED} — 已完成转链操作（生成推广链接），可分享给达人</li>
 *   <li>{@link #FOLLOWING} — 达人正在跟进推广中，等待出单</li>
 * </ul>
 *
 * <h3>特殊兼容逻辑</h3>
 * <p>{@link #fromCode(String)} 方法兼容旧版 "SYNCED" 码，将其映射为
 * {@link #PENDING_AUDIT}，确保历史数据平滑过渡。</p>
 *
 * <h3>使用场景</h3>
 * <ul>
 *   <li>商品管理页面的状态筛选与流转操作</li>
 *   <li>商品同步后自动设置初始状态</li>
 *   <li>审核流程中状态的正向和逆向流转</li>
 *   <li>数据分析中按状态统计商品转化漏斗</li>
 * </ul>
 */
public enum ProductBizStatus {

    /** 待审核（兼容外部 "SYNCED" 同步完成码） */
    PENDING_AUDIT("待审核"),
    /** 审核通过，可进入后续推广流程 */
    APPROVED("审核通过"),
    /** 审核拒绝，不满足准入条件 */
    REJECTED("审核拒绝"),
    /** 已绑定到具体推广活动 */
    BOUND("已绑定活动"),
    /** 已分配给招商经理跟进 */
    ASSIGNED("已分配招商"),
    /** 已完成转链操作，生成推广链接 */
    LINKED("已转链"),
    /** 达人正在跟进推广中 */
    FOLLOWING("达人跟进中");

    /** 状态的中文展示标签，用于前端下拉框和报表展示 */
    private final String label;

    ProductBizStatus(String label) {
        this.label = label;
    }

    /**
     * 获取状态的中文展示标签。
     *
     * @return 中文标签，如 "待审核"、"审核通过" 等
     */
    public String getLabel() {
        return label;
    }

    /**
     * 根据状态码字符串查找对应的枚举实例。
     *
     * <p>支持不区分大小写的名称匹配，并兼容旧版 "SYNCED" 码
     * （自动映射为 {@link #PENDING_AUDIT}）。</p>
     *
     * @param code 状态码字符串，不区分大小写；null 或空白返回 null
     * @return 匹配的枚举实例
     * @throws IllegalArgumentException 当 code 不匹配任何枚举值且非 "SYNCED" 时抛出
     */
    public static ProductBizStatus fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        // 兼容旧版 "SYNCED" 码，映射为待审核状态
        if ("SYNCED".equalsIgnoreCase(code.trim())) {
            return PENDING_AUDIT;
        }
        return Arrays.stream(values())
                .filter(item -> item.name().equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown ProductBizStatus: " + code));
    }
}
