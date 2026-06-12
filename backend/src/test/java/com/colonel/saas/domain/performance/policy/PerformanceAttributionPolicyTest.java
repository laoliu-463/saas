package com.colonel.saas.domain.performance.policy;

import com.colonel.saas.entity.ColonelsettlementOrder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link PerformanceAttributionPolicy} 单测（DDD-PERF-003）。
 *
 * <p>覆盖：null 订单、纯 pick_source、纯活动归属、colonel 优先回退、双轨 default/final 同源、所有字段为 null 等场景。</p>
 */
class PerformanceAttributionPolicyTest {

    private static final UUID CHANNEL_USER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID COLONEL_USER = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID FALLBACK_USER = UUID.fromString("33333333-3333-3333-3333-333333333333");

    // ---------- 常量值 ----------

    @Test
    @DisplayName("常量值与历史 PerformanceCalculationService 字符串一致")
    void constants_matchLegacyStrings() {
        assertThat(PerformanceAttributionPolicy.CHANNEL_ATTR_PICK_SOURCE).isEqualTo("pick_source");
        assertThat(PerformanceAttributionPolicy.CHANNEL_ATTR_UNATTRIBUTED).isEqualTo("unattributed");
        assertThat(PerformanceAttributionPolicy.RECRUITER_ATTR_ACTIVITY_OWNER).isEqualTo("activity_owner");
        assertThat(PerformanceAttributionPolicy.RECRUITER_ATTR_UNATTRIBUTED).isEqualTo("unattributed");
    }

    // ---------- null / 空订单 ----------

    @Test
    @DisplayName("null 订单 → 全部字段为 null，类型都是 unattributed")
    void resolve_nullOrder_returnsEmptyResolution() {
        PerformanceAttributionPolicy.Resolution r = PerformanceAttributionPolicy.resolve(null);

        assertThat(r.defaultChannelUserId()).isNull();
        assertThat(r.finalChannelUserId()).isNull();
        assertThat(r.channelAttributionType()).isEqualTo(PerformanceAttributionPolicy.CHANNEL_ATTR_UNATTRIBUTED);
        assertThat(r.defaultRecruiterUserId()).isNull();
        assertThat(r.finalRecruiterUserId()).isNull();
        assertThat(r.recruiterAttributionType()).isEqualTo(PerformanceAttributionPolicy.RECRUITER_ATTR_UNATTRIBUTED);
    }

    @Test
    @DisplayName("订单字段全 null → 全部字段为 null，类型都是 unattributed")
    void resolve_emptyOrder_returnsEmptyResolution() {
        ColonelsettlementOrder order = new ColonelsettlementOrder();

        PerformanceAttributionPolicy.Resolution r = PerformanceAttributionPolicy.resolve(order);

        assertThat(r.defaultChannelUserId()).isNull();
        assertThat(r.channelAttributionType()).isEqualTo(PerformanceAttributionPolicy.CHANNEL_ATTR_UNATTRIBUTED);
        assertThat(r.defaultRecruiterUserId()).isNull();
        assertThat(r.recruiterAttributionType()).isEqualTo(PerformanceAttributionPolicy.RECRUITER_ATTR_UNATTRIBUTED);
    }

    // ---------- 渠道归因 ----------

    @Test
    @DisplayName("渠道有归属人 → pick_source；default 与 final 同源")
    void resolve_channelAttributed_pickSource() {
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setChannelUserId(CHANNEL_USER);

        PerformanceAttributionPolicy.Resolution r = PerformanceAttributionPolicy.resolve(order);

        assertThat(r.defaultChannelUserId()).isEqualTo(CHANNEL_USER);
        assertThat(r.finalChannelUserId()).isEqualTo(CHANNEL_USER);
        assertThat(r.channelAttributionType()).isEqualTo(PerformanceAttributionPolicy.CHANNEL_ATTR_PICK_SOURCE);
    }

    // ---------- 招商归因：colonel 优先 / fallback ----------

    @Test
    @DisplayName("colonelUserId 优先 → activity_owner")
    void resolve_colonelUserIdTakesPrecedence_overFallbackUserId() {
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setColonelUserId(COLONEL_USER);
        order.setUserId(FALLBACK_USER);

        PerformanceAttributionPolicy.Resolution r = PerformanceAttributionPolicy.resolve(order);

        assertThat(r.defaultRecruiterUserId()).isEqualTo(COLONEL_USER);
        assertThat(r.finalRecruiterUserId()).isEqualTo(COLONEL_USER);
        assertThat(r.recruiterAttributionType()).isEqualTo(PerformanceAttributionPolicy.RECRUITER_ATTR_ACTIVITY_OWNER);
    }

    @Test
    @DisplayName("colonelUserId 为 null → 回退到 order.userId")
    void resolve_colonelNullFallsBackToOrderUserId() {
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setUserId(FALLBACK_USER);

        PerformanceAttributionPolicy.Resolution r = PerformanceAttributionPolicy.resolve(order);

        assertThat(r.defaultRecruiterUserId()).isEqualTo(FALLBACK_USER);
        assertThat(r.finalRecruiterUserId()).isEqualTo(FALLBACK_USER);
        assertThat(r.recruiterAttributionType()).isEqualTo(PerformanceAttributionPolicy.RECRUITER_ATTR_ACTIVITY_OWNER);
    }

    @Test
    @DisplayName("colonel 与 userId 都为 null → unattributed")
    void resolve_noRecruiterSource_unattributed() {
        ColonelsettlementOrder order = new ColonelsettlementOrder();

        PerformanceAttributionPolicy.Resolution r = PerformanceAttributionPolicy.resolve(order);

        assertThat(r.defaultRecruiterUserId()).isNull();
        assertThat(r.recruiterAttributionType()).isEqualTo(PerformanceAttributionPolicy.RECRUITER_ATTR_UNATTRIBUTED);
    }

    // ---------- 综合场景 ----------

    @Test
    @DisplayName("完整场景：渠道 pick_source + 招商 activity_owner(colonel)")
    void resolve_fullScenario() {
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setChannelUserId(CHANNEL_USER);
        order.setColonelUserId(COLONEL_USER);
        order.setUserId(FALLBACK_USER);

        PerformanceAttributionPolicy.Resolution r = PerformanceAttributionPolicy.resolve(order);

        assertThat(r.defaultChannelUserId()).isEqualTo(CHANNEL_USER);
        assertThat(r.finalChannelUserId()).isEqualTo(CHANNEL_USER);
        assertThat(r.channelAttributionType()).isEqualTo("pick_source");
        assertThat(r.defaultRecruiterUserId()).isEqualTo(COLONEL_USER);
        assertThat(r.finalRecruiterUserId()).isEqualTo(COLONEL_USER);
        assertThat(r.recruiterAttributionType()).isEqualTo("activity_owner");
    }

    @Test
    @DisplayName("部分归属：渠道有 + 招商无 → pick_source + unattributed")
    void resolve_partialAttribution() {
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setChannelUserId(CHANNEL_USER);
        // colonelUserId 和 userId 都为 null

        PerformanceAttributionPolicy.Resolution r = PerformanceAttributionPolicy.resolve(order);

        assertThat(r.channelAttributionType()).isEqualTo("pick_source");
        assertThat(r.defaultRecruiterUserId()).isNull();
        assertThat(r.recruiterAttributionType()).isEqualTo("unattributed");
    }

    @Test
    @DisplayName("default 与 final 在当前实现下始终同源（为未来 override 留位）")
    void resolve_defaultEqualsFinal() {
        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setChannelUserId(CHANNEL_USER);
        order.setColonelUserId(COLONEL_USER);

        PerformanceAttributionPolicy.Resolution r = PerformanceAttributionPolicy.resolve(order);

        assertThat(r.defaultChannelUserId()).isEqualTo(r.finalChannelUserId());
        assertThat(r.defaultRecruiterUserId()).isEqualTo(r.finalRecruiterUserId());
    }
}
