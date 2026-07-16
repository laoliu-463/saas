package com.colonel.saas.domain.performance.policy;

import java.util.UUID;

/**
 * 业绩归属判定策略（DDD-PERF-003 / DDD-PERF-005）。
 *
 * <p>根据默认归因与独家链路覆盖计算最终的招商员与渠道员。</p>
 */
public final class PerformanceAttributionPolicy {

    private PerformanceAttributionPolicy() {
    }

    public record AttributionInput(
            UUID defaultChannelId,
            UUID defaultRecruiterId,
            UUID defaultChannelDeptId,
            UUID defaultRecruiterDeptId,
            ExclusiveOwner merchantExclusiveOwner,
            ExclusiveOwner talentExclusiveOwner,
            ManualOwner manualOwner) {

        public AttributionInput(
                UUID defaultChannelId,
                UUID defaultRecruiterId,
                UUID defaultChannelDeptId,
                UUID defaultRecruiterDeptId,
                ExclusiveOwner merchantExclusiveOwner,
                ExclusiveOwner talentExclusiveOwner) {
            this(defaultChannelId, defaultRecruiterId, defaultChannelDeptId, defaultRecruiterDeptId,
                    merchantExclusiveOwner, talentExclusiveOwner, null);
        }
    }

    public record AttributionResult(
            UUID finalChannelId,
            UUID finalRecruiterId,
            UUID finalChannelDeptId,
            UUID finalRecruiterDeptId,
            String channelAttributionType,
            String recruiterAttributionType) {
    }

    public record ExclusiveOwner(UUID userId, UUID deptId) {
    }

    /** 已审批人工调整；任一维度为空表示该维度继续沿用系统规则。 */
    public record ManualOwner(
            UUID channelUserId,
            UUID recruiterUserId,
            UUID channelDeptId,
            UUID recruiterDeptId) {
    }

    /**
     * 计算最终业绩归属。
     *
     * <p>优先级：独家商家优先覆盖招商；独家达人同时覆盖招商与渠道；否则使用默认归因。</p>
     */
    public static AttributionResult resolve(AttributionInput input) {
        if (input == null) {
            return new AttributionResult(null, null, null, null, "DEFAULT", "DEFAULT");
        }
        if (input.manualOwner() != null
                && (input.manualOwner().channelUserId() != null || input.manualOwner().recruiterUserId() != null)) {
            ManualOwner manual = input.manualOwner();
            boolean channelAdjusted = manual.channelUserId() != null;
            boolean recruiterAdjusted = manual.recruiterUserId() != null;
            return new AttributionResult(
                    channelAdjusted ? manual.channelUserId() : input.defaultChannelId(),
                    recruiterAdjusted ? manual.recruiterUserId() : input.defaultRecruiterId(),
                    channelAdjusted ? manual.channelDeptId() : input.defaultChannelDeptId(),
                    recruiterAdjusted ? manual.recruiterDeptId() : input.defaultRecruiterDeptId(),
                    channelAdjusted ? "MANUAL_ADJUSTMENT" : "DEFAULT",
                    recruiterAdjusted ? "MANUAL_ADJUSTMENT" : "DEFAULT"
            );
        }
        // 1. 独家商家归属
        if (input.merchantExclusiveOwner() != null) {
            return new AttributionResult(
                    input.defaultChannelId(),
                    input.merchantExclusiveOwner().userId(),
                    input.defaultChannelDeptId(),
                    input.merchantExclusiveOwner().deptId(),
                    "DEFAULT",
                    "EXCLUSIVE_MERCHANT"
            );
        }

        // 2. 独家达人归属
        if (input.talentExclusiveOwner() != null) {
            return new AttributionResult(
                    input.talentExclusiveOwner().userId(),
                    input.talentExclusiveOwner().userId(),
                    input.talentExclusiveOwner().deptId(),
                    input.talentExclusiveOwner().deptId(),
                    "EXCLUSIVE_TALENT",
                    "EXCLUSIVE_TALENT"
            );
        }

        // 3. 默认归因
        return new AttributionResult(
                input.defaultChannelId(),
                input.defaultRecruiterId(),
                input.defaultChannelDeptId(),
                input.defaultRecruiterDeptId(),
                "DEFAULT",
                "DEFAULT"
        );
    }
}
