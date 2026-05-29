package com.colonel.saas.common.enums;

import java.util.Arrays;

/**
 * 达人跟进状态枚举，描述招商组长与达人之间的合作推进阶段。
 *
 * <p>状态流转模型（有向图）：</p>
 * <pre>
 * NOT_CONTACTED → INVITED → REPLIED → COOPERATING → PROMOTING → CLOSED
 * </pre>
 *
 * <h3>状态说明</h3>
 * <ul>
 *   <li>{@link #NOT_CONTACTED} — 尚未与达人建立联系，初始状态</li>
 *   <li>{@link #INVITED} — 已向达人发出合作邀约，等待回复</li>
 *   <li>{@link #REPLIED} — 达人已回复，进入沟通阶段</li>
 *   <li>{@link #COOPERATING} — 双方确认合作意向，开始对接具体合作细节</li>
 *   <li>{@link #PROMOTING} — 达人已开始推广商品，合作进入执行阶段</li>
 *   <li>{@link #CLOSED} — 合作结束，可能是正常完成或中途终止</li>
 * </ul>
 *
 * <h3>使用场景</h3>
 * <ul>
 *   <li>达人管理页面的状态筛选与展示</li>
 *   <li>跟进行为触发的状态变更（如发送邀约、收到回复等）</li>
 *   <li>数据分析中按跟进状态统计达人转化漏斗</li>
 * </ul>
 */
public enum TalentFollowStatus {

    /** 尚未与达人建立联系，初始状态 */
    NOT_CONTACTED("未联系"),
    /** 已向达人发出合作邀约，等待回复 */
    INVITED("已邀约"),
    /** 达人已回复，进入沟通阶段 */
    REPLIED("已回复"),
    /** 双方确认合作意向，开始对接具体合作细节 */
    COOPERATING("已合作"),
    /** 达人已开始推广商品，合作进入执行阶段 */
    PROMOTING("推广中"),
    /** 合作结束（正常完成或中途终止） */
    CLOSED("已结束");

    /** 状态的中文展示标签，用于前端下拉框和报表展示 */
    private final String label;

    TalentFollowStatus(String label) {
        this.label = label;
    }

    /**
     * 获取状态的中文展示标签。
     *
     * @return 中文标签，如 "未联系"、"已邀约" 等
     */
    public String getLabel() {
        return label;
    }

    /**
     * 根据枚举名称字符串（不区分大小写）查找对应的枚举实例。
     *
     * <p>用于从前端提交的状态码（如 "NOT_CONTACTED"）反序列化为枚举对象。
     * null 或空白字符串将返回 null，不做转换。</p>
     *
     * @param code 枚举名称字符串，不区分大小写
     * @return 匹配的枚举实例，如果 code 为 null 或空白则返回 null
     * @throws IllegalArgumentException 当 code 不匹配任何枚举值时抛出
     */
    public static TalentFollowStatus fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return Arrays.stream(values())
                .filter(item -> item.name().equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown TalentFollowStatus: " + code));
    }
}
