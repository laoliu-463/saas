package com.colonel.saas.domain.performance.policy;

import com.colonel.saas.entity.ColonelsettlementOrder;

import java.util.UUID;

/**
 * 业绩域最终归属策略（DDD-PERF-003）。
 *
 * <p>从 {@code PerformanceCalculationService.buildRecord} 抽离的纯归因规则，
 * 负责根据订单字段决定"渠道归因"和"招商归因"的最终归属人 + 归因类型。</p>
 *
 * <p>不依赖 Spring 容器，输入订单实体，输出 {@link Resolution} 值对象，
 * 方便单元测试和复用。后续"独家覆盖 / 默认归因"策略可在本类扩展。</p>
 *
 * <h3>归因类型常量</h3>
 * <ul>
 *   <li>渠道：{@link #CHANNEL_ATTR_PICK_SOURCE} / {@link #CHANNEL_ATTR_UNATTRIBUTED}</li>
 *   <li>招商：{@link #RECRUITER_ATTR_ACTIVITY_OWNER} / {@link #RECRUITER_ATTR_UNATTRIBUTED}</li>
 * </ul>
 */
public final class PerformanceAttributionPolicy {

    /** 渠道归因：订单通过 pick_source / 转链映射成功归属。 */
    public static final String CHANNEL_ATTR_PICK_SOURCE = "pick_source";
    /** 渠道归因：订单未通过 pick_source 归属。 */
    public static final String CHANNEL_ATTR_UNATTRIBUTED = "unattributed";
    /** 招商归因：订单通过活动归属人（colonel / activity owner）归属。 */
    public static final String RECRUITER_ATTR_ACTIVITY_OWNER = "activity_owner";
    /** 招商归因：订单未通过活动归属人归属。 */
    public static final String RECRUITER_ATTR_UNATTRIBUTED = "unattributed";

    private PerformanceAttributionPolicy() {
    }

    /**
     * 解析订单的渠道 + 招商归因（同时返回 default / final 两份快照）。
     *
     * <p>当前 default 与 final 同源（均来自订单字段），保留两份字段是为了：
     * <ul>
     *   <li>未来独家覆盖 / 认领关系调整时，{@code final} 可被覆写而 {@code default} 保留追溯</li>
     *   <li>汇总查询可同时展示"原归属"和"最终归属"用于审计</li>
     * </ul>
     *
     * @param order 订单实体（可为 null，此时全部归为 unattributed）
     * @return 归因解析结果
     */
    public static Resolution resolve(ColonelsettlementOrder order) {
        if (order == null) {
            return empty();
        }
        UUID channelUserId = order.getChannelUserId();
        UUID colonelUserId = order.getColonelUserId();
        UUID fallbackUserId = order.getUserId();
        UUID recruiterUserId = colonelUserId != null ? colonelUserId : fallbackUserId;

        return new Resolution(
                channelUserId,
                channelUserId,
                channelUserId != null ? CHANNEL_ATTR_PICK_SOURCE : CHANNEL_ATTR_UNATTRIBUTED,
                recruiterUserId,
                recruiterUserId,
                recruiterUserId != null ? RECRUITER_ATTR_ACTIVITY_OWNER : RECRUITER_ATTR_UNATTRIBUTED
        );
    }

    private static Resolution empty() {
        return new Resolution(
                null, null, CHANNEL_ATTR_UNATTRIBUTED,
                null, null, RECRUITER_ATTR_UNATTRIBUTED
        );
    }

    /**
     * 归因解析结果：default = 订单原始归因；final = 调整后最终归因（当前同源）。
     *
     * @param defaultChannelUserId    渠道默认归属人（来自订单 channelUserId）
     * @param finalChannelUserId     渠道最终归属人（当前与 default 同源）
     * @param channelAttributionType 渠道归因类型（{@code pick_source} / {@code unattributed}）
     * @param defaultRecruiterUserId 招商默认归属人（colonelUserId 优先，缺失回退到 userId）
     * @param finalRecruiterUserId  招商最终归属人（当前与 default 同源）
     * @param recruiterAttributionType 招商归因类型（{@code activity_owner} / {@code unattributed}）
     */
    public record Resolution(
            UUID defaultChannelUserId,
            UUID finalChannelUserId,
            String channelAttributionType,
            UUID defaultRecruiterUserId,
            UUID finalRecruiterUserId,
            String recruiterAttributionType
    ) {
    }
}
